"""Servidor central R.E.I. para a rede local do escritório."""

from __future__ import annotations

import csv
import ast
import hashlib
import hmac
import html
import io
import json
import logging
import re
import os
import secrets
import sqlite3
import mimetypes
import unicodedata
from decimal import Decimal, InvalidOperation
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from http.cookies import SimpleCookie
from pathlib import Path
from urllib.parse import parse_qs, quote_plus, urlparse

ROOT = Path(__file__).resolve().parent
WEB_ROOT = ROOT.parent / "web"
CONFIG_PATH = ROOT / "config.json"
MAX_BODY_BYTES = 50 * 1024 * 1024


def load_config() -> dict:
    path = CONFIG_PATH if CONFIG_PATH.exists() else ROOT / "config.example.json"
    return json.loads(path.read_text(encoding="utf-8"))


CONFIG = load_config()
DATABASE = ROOT / CONFIG.get("database", "data/rei_central.db")
DATABASE.parent.mkdir(parents=True, exist_ok=True)
SCHEMA_ITEMS_PATH = DATABASE.parent / "schema_items.json"
REI_ITEM_AREAS = {
    "modules": "Módulos contratados",
    "technical": "Técnico",
    "stock": "Estoque",
    "finance": "Financeiro",
    "fiscal": "Fiscal",
    "supervision": "Supervisão",
}
SURVEY_FIELD_TYPES = {
    "checkbox": "Caixa de seleção",
    "text": "Texto curto",
    "textarea": "Texto longo",
    "choice": "Múltipla escolha",
    "date": "Data",
    "datetime-local": "Data e hora",
    "photo": "Foto",
    "email": "E-mail",
    "number": "Número",
    "signature": "Assinatura",
}

REQUIRED_MODES = {"never", "always", "conditional"}
CONDITION_SOURCES = {"module", "survey_field", "report_field", "checklist"}
CONDITION_OPERATORS = {
    "module": {"checked", "not_checked"},
    "checklist": {"checked", "not_checked"},
    "survey_field": {"equals", "not_equals", "not_blank", "blank", "greater_than"},
    "report_field": {"equals", "not_equals", "not_blank", "blank", "greater_than"},
}
SCHEMA_RULE_VERSION = "required-rules-v1"


def empty_schema_items() -> dict:
    return {
        "rei": {
            "modules": [],
            "technical": [],
            "stock": [],
            "finance": [],
            "fiscal": [],
            "supervision": [],
        },
        "levantamento": [],
    }


REI_SCOPES = {
    "technical": "tecnico",
    "stock": "estoque",
    "finance": "financeiro",
    "fiscal": "fiscal",
    "supervision": "supervisao",
}


def normalized_comparison_text(value: object) -> str:
    """Normaliza apenas para comparação, sem modificar o valor persistido."""
    return " ".join(unicodedata.normalize("NFKC", str(value or "")).split()).casefold()


def legacy_rei_item_key(area: str, topic: str, label: str, field_type: str) -> str:
    if area == "modules":
        return f"dados::modulos::{label}"
    scope = REI_SCOPES.get(area, area)
    prefix = "" if field_type == "checkbox" else "reiField::"
    return f"{prefix}{scope}::{topic}::{label}"


def normalize_required_when(value: object, strict: bool = False) -> dict | None:
    if value in (None, "", {}):
        return None
    if not isinstance(value, dict):
        if strict:
            raise ValueError("A condição de obrigatoriedade deve ser estruturada.")
        return None
    match = str(value.get("match") or "any").strip().lower()
    if match not in {"any", "all"}:
        raise ValueError("A combinação da regra deve ser 'any' ou 'all'.")
    raw_conditions = value.get("conditions")
    if not isinstance(raw_conditions, list):
        raise ValueError("A regra condicional deve possuir uma lista de condições.")
    conditions: list[dict] = []
    for raw in raw_conditions:
        if not isinstance(raw, dict):
            raise ValueError("Condição de obrigatoriedade inválida.")
        if "match" in raw and not raw.get("source"):
            nested = normalize_required_when(raw, strict=True)
            if nested:
                conditions.append(nested)
            continue
        source = str(raw.get("source") or "").strip().lower()
        operator = str(raw.get("operator") or "").strip().lower()
        key = str(raw.get("key") or "").strip()
        if source not in CONDITION_SOURCES:
            raise ValueError(f"Origem de condição desconhecida: {source or 'vazia'}.")
        if operator not in CONDITION_OPERATORS[source]:
            raise ValueError(
                f"Operador '{operator or 'vazio'}' não é aceito para {source}."
            )
        if not key:
            raise ValueError("Selecione o campo ou item usado na condição.")
        condition = {"source": source, "key": key, "operator": operator}
        if operator in {"equals", "not_equals", "greater_than"}:
            condition["value"] = str(raw.get("value") or "").strip()
        conditions.append(condition)
    if not conditions:
        raise ValueError("Adicione pelo menos uma condição para a obrigatoriedade.")
    return {"match": match, "conditions": conditions}


def normalize_requirement_metadata(
    raw: dict, normalized: dict, strict: bool = False
) -> None:
    required_mode = str(raw.get("requiredMode") or "never").strip().lower()
    if required_mode not in REQUIRED_MODES:
        raise ValueError(f"Modalidade de obrigatoriedade inválida: {required_mode}.")
    normalized["requiredMode"] = required_mode
    required_when = normalize_required_when(raw.get("requiredWhen"), strict)
    if required_mode == "conditional":
        if not required_when:
            raise ValueError("Item condicional precisa de pelo menos uma condição.")
        normalized["requiredWhen"] = required_when
    legacy_keys = list(normalized.get("legacyKeys", [])) + [
        str(key).strip()
        for key in (raw.get("legacyKeys") if isinstance(raw.get("legacyKeys"), list) else [])
        if str(key).strip()
    ]
    if legacy_keys:
        normalized["legacyKeys"] = list(dict.fromkeys(legacy_keys))


def normalize_rei_item(
    raw: object, area: str, topic: str, strict: bool = False
) -> dict | None:
    source = raw if isinstance(raw, dict) else {"label": raw, "type": "text"}
    label = str(source.get("label") or "").strip()
    if not label:
        return None
    default_type = "checkbox" if area == "modules" else "text"
    field_type = str(source.get("type") or default_type).strip().lower()
    if field_type not in SURVEY_FIELD_TYPES:
        if strict:
            raise ValueError(f"Tipo de campo desconhecido: {field_type}.")
        field_type = default_type
    if area == "modules":
        field_type = "checkbox"
    legacy_key = legacy_rei_item_key(area, topic, label, field_type)
    key = str(source.get("key") or legacy_key).strip()
    normalized = {"key": key, "label": label, "type": field_type, "options": []}
    if field_type == "choice":
        options = [
            str(option).strip()
            for option in (source.get("options") if isinstance(source.get("options"), list) else [])
            if str(option).strip()
        ]
        normalized["options"] = options or ["Sim", "Não"]
    if key != legacy_key:
        normalized["legacyKeys"] = list(
            dict.fromkeys([*(source.get("legacyKeys") or []), legacy_key])
        )
    normalize_requirement_metadata(source, normalized, strict)
    return normalized


def normalize_survey_field(raw: object, strict: bool = False) -> dict | None:
    if not isinstance(raw, dict):
        return None
    label = str(raw.get("label") or "").strip()
    key = str(raw.get("key") or "").strip()
    field_type = str(raw.get("type") or "text").strip().lower()
    if field_type not in SURVEY_FIELD_TYPES or field_type == "checkbox":
        if strict:
            raise ValueError(f"Tipo de campo do levantamento inválido: {field_type}.")
        field_type = "text"
    if not label or not key:
        return None
    normalized = {"key": key, "label": label, "type": field_type, "options": []}
    if field_type == "choice":
        options = [
            str(option).strip()
            for option in (raw.get("options") if isinstance(raw.get("options"), list) else [])
            if str(option).strip()
        ]
        normalized["options"] = options or ["Sim", "Não"]
    normalize_requirement_metadata(raw, normalized, strict)
    return normalized


def normalize_schema_items(data: dict | None, strict: bool = False) -> dict:
    normalized = empty_schema_items()
    if not isinstance(data, dict):
        return normalized
    rei = data.get("rei") if isinstance(data.get("rei"), dict) else {}
    for area in normalized["rei"]:
        if area == "modules":
            normalized["rei"][area] = [
                item
                for raw in (rei.get(area) if isinstance(rei.get(area), list) else [])
                if (item := normalize_rei_item(raw, area, "modulos", strict))
            ]
            continue
        groups = []
        for group in (rei.get(area) if isinstance(rei.get(area), list) else []):
            if not isinstance(group, dict):
                continue
            title = str(group.get("title", "")).strip()
            items = []
            for item in (
                group.get("items") if isinstance(group.get("items"), list) else []
            ):
                normalized_item = normalize_rei_item(item, area, title, strict)
                if normalized_item:
                    items.append(normalized_item)
            if title:
                groups.append({"title": title, "items": items})
        normalized["rei"][area] = groups
    levantamento = (
        data.get("levantamento") if isinstance(data.get("levantamento"), list) else []
    )
    for section in levantamento:
        if not isinstance(section, dict):
            continue
        title = str(section.get("title", "")).strip()
        fields = []
        for field in (
            section.get("fields") if isinstance(section.get("fields"), list) else []
        ):
            item = normalize_survey_field(field, strict)
            if title and item:
                fields.append(item)
        if title:
            normalized["levantamento"].append({"title": title, "fields": fields})
    return normalized


def load_schema_items() -> dict:
    if not SCHEMA_ITEMS_PATH.exists():
        return empty_schema_items()
    try:
        raw = json.loads(SCHEMA_ITEMS_PATH.read_text(encoding="utf-8"))
        normalized = normalize_schema_items(raw)
        if canonical(raw) != canonical(normalized):
            SCHEMA_ITEMS_PATH.write_text(
                json.dumps(normalized, ensure_ascii=False, indent=2), encoding="utf-8"
            )
        return normalized
    except (json.JSONDecodeError, OSError):
        logging.exception("Erro ao carregar itens personalizados")
        return empty_schema_items()


def save_schema_items(data: dict) -> None:
    SCHEMA_ITEMS_PATH.parent.mkdir(parents=True, exist_ok=True)
    normalized = normalize_schema_items(data, strict=True)
    validate_schema_references(effective_schema_from_custom(normalized))
    SCHEMA_ITEMS_PATH.write_text(
        json.dumps(normalized, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def field_display_parts(
    item: object, default_type_label: str = "Texto curto"
) -> tuple[str, str]:
    if isinstance(item, dict):
        label = str(item.get("label", "")).strip()
        field_type = str(item.get("type", "text")).strip()
        if field_type not in SURVEY_FIELD_TYPES:
            field_type = "text"
        type_label = SURVEY_FIELD_TYPES.get(field_type, field_type)
        return label, type_label
    return str(item).strip(), default_type_label


def format_rei_item(item: object) -> str:
    return field_display_parts(item)[0]


def extract_js_array(source: str, marker: str) -> str:
    start = source.index(marker) + len(marker)
    depth = 0
    in_string = False
    escaped = False
    for index, char in enumerate(source[start:], start):
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
        else:
            if char == '"':
                in_string = True
            elif char == "[":
                depth += 1
            elif char == "]":
                depth -= 1
                if depth == 0:
                    return source[start : index + 1]
    raise ValueError(f"Array não encontrado: {marker}")


def load_default_schema_items() -> dict:
    defaults = empty_schema_items()
    try:
        schema_source = (WEB_ROOT / "schema.js").read_text(encoding="utf-8")
        module_labels = ast.literal_eval(extract_js_array(schema_source, "modules: "))
        defaults["rei"]["modules"] = [
            normalize_rei_item(label, "modules", "modulos") for label in module_labels
        ]
        for area in ["technical", "stock", "finance", "fiscal", "supervision"]:
            groups = ast.literal_eval(extract_js_array(schema_source, f"{area}: "))
            defaults["rei"][area] = []
            for group in groups:
                if not isinstance(group, list) or len(group) < 2:
                    continue
                title = str(group[0])
                defaults["rei"][area].append(
                    {
                        "title": title,
                        "items": [
                            normalize_rei_item(
                                {"label": str(item), "type": "checkbox"},
                                area,
                                title,
                            )
                            for item in group[1]
                        ],
                    }
                )
    except Exception:
        logging.exception("Erro ao carregar schema padrão do R.E.I.")

    try:
        app_source = (WEB_ROOT / "app.js").read_text(encoding="utf-8")
        survey_literal = extract_js_array(
            app_source, "const surveySections = "
        ).replace("yesNo", '["Sim", "Não"]')
        survey_sections = ast.literal_eval(survey_literal)
        defaults["levantamento"] = []
        for section in survey_sections:
            if not isinstance(section, list) or len(section) < 2:
                continue
            fields = []
            for field in section[1]:
                if not isinstance(field, list) or len(field) < 2:
                    continue
                field_type = (
                    str(field[2])
                    if len(field) >= 3 and isinstance(field[2], str)
                    else "text"
                )
                item = {
                    "key": str(field[0]),
                    "label": str(field[1]),
                    "type": field_type,
                    "options": [],
                    "requiredMode": "never",
                }
                if (
                    field_type == "choice"
                    and len(field) >= 4
                    and isinstance(field[3], list)
                ):
                    item["options"] = [str(option) for option in field[3]]
                fields.append(item)
            defaults["levantamento"].append(
                {"title": str(section[0]), "fields": fields}
            )
    except Exception:
        logging.exception("Erro ao carregar schema padrão do levantamento")
    return apply_default_required_rules(defaults)


def condition(source: str, key: str, operator: str, value: str | None = None) -> dict:
    item = {"source": source, "key": key, "operator": operator}
    if value is not None:
        item["value"] = value
    return item


def condition_group(match: str, *conditions: dict) -> dict:
    return {"match": match, "conditions": list(conditions)}


def required_rule(match: str, *conditions: dict) -> dict:
    return {"match": match, "conditions": list(conditions)}


def iter_rei_items(schema: dict, include_modules: bool = True):
    rei = schema.get("rei") if isinstance(schema.get("rei"), dict) else {}
    if include_modules:
        for item in rei.get("modules", []):
            if isinstance(item, dict):
                yield "modules", "modulos", item
    for area in REI_SCOPES:
        for group in rei.get(area, []):
            if not isinstance(group, dict):
                continue
            for item in group.get("items", []):
                if isinstance(item, dict):
                    yield area, str(group.get("title") or ""), item


def find_rei_item(schema: dict, area: str, label: str, topic: str = "") -> dict:
    wanted = normalized_comparison_text(label)
    for item_area, item_topic, item in iter_rei_items(schema):
        if item_area != area:
            continue
        if topic and normalized_comparison_text(item_topic) != normalized_comparison_text(topic):
            continue
        if normalized_comparison_text(item.get("label")) == wanted:
            return item
    raise ValueError(f"Item padrão não encontrado: {area}/{topic}/{label}")


def module_key(schema: dict, label: str) -> str:
    return str(find_rei_item(schema, "modules", label).get("key") or "")


def checklist_key(schema: dict, area: str, label: str, topic: str = "") -> str:
    return str(find_rei_item(schema, area, label, topic).get("key") or "")


def set_item_requirement(
    schema: dict,
    area: str,
    label: str,
    rule: dict,
    topic: str = "",
) -> None:
    item = find_rei_item(schema, area, label, topic)
    item["requiredMode"] = "conditional"
    item["requiredWhen"] = rule


def survey_field(schema: dict, key: str) -> dict:
    for section in schema.get("levantamento", []):
        for field in section.get("fields", []):
            if str(field.get("key") or "") == key:
                return field
    raise ValueError(f"Campo padrão do levantamento não encontrado: {key}")


def apply_default_required_rules(schema: dict) -> dict:
    """Acrescenta as regras corporativas ao schema base sem alterar chaves persistidas."""
    fiscal_modules = [
        module_key(schema, "Nota Fiscal Eletrônica"),
        module_key(schema, "Emissão de NFC-e"),
        module_key(schema, "Nota Fiscal Eletrônica de Serviço"),
    ]
    nfe, nfce, nfse = fiscal_modules
    financeiro = module_key(schema, "Financeiro")
    boleto = module_key(schema, "Boleto")
    estoque = module_key(schema, "Estoque")
    compras = module_key(schema, "Compras")
    manifesto = module_key(schema, "Manifesto")
    faturamento = module_key(schema, "Faturamento")
    sintegra = module_key(schema, "Sintegra")
    sped = module_key(schema, "SPED Fiscal / PIS-COFINS")
    ordem_servico = module_key(schema, "Ordem de Serviço")
    custos = module_key(schema, "Custos")
    pdv = module_key(schema, "PDV – Ponto de Venda")
    customizacao = module_key(schema, "Customização")

    fiscal_any = required_rule(
        "any", *(condition("module", key, "checked") for key in fiscal_modules)
    )
    for label, topic in [
        ("Instalação do certificado no TGA", "Emissão de NF-e"),
        ("Certificado digital na pasta Instaladores", "Instalação e ambiente"),
        ("Configurar regime tributário", "Emissão de NF-e"),
        ("Confirmar alíquotas com o contador (PIS, COFINS etc.)", "Emissão de NF-e"),
    ]:
        set_item_requirement(schema, "technical", label, fiscal_any, topic)
    set_item_requirement(
        schema,
        "technical",
        "Certificado A1 inserido no banco de dados",
        required_rule(
            "all",
            condition("survey_field", "estoqueCertificado", "equals", "A1"),
            condition_group(
                "any", *(condition("module", key, "checked") for key in fiscal_modules)
            ),
        ),
        "Emissão de NF-e",
    )
    for label in [
        "Conferir série da NF-e",
        "Conferir local do PDF/XML da NF-e na filial",
    ]:
        set_item_requirement(
            schema,
            "technical",
            label,
            required_rule("all", condition("module", nfe, "checked")),
            "Emissão de NF-e",
        )
    set_item_requirement(
        schema,
        "technical",
        "Parametrizar os CFOPs",
        required_rule(
            "any",
            condition("module", nfe, "checked"),
            condition("module", nfce, "checked"),
        ),
        "Emissão de NF-e",
    )

    stock_rules = {
        "Cupom fiscal": required_rule("all", condition("module", nfce, "checked")),
        "NFS-e": required_rule("all", condition("module", nfse, "checked")),
        "Configuração de e-mail": required_rule(
            "all",
            condition("survey_field", "estoqueEmailNf", "not_blank"),
            condition_group(
                "any", *(condition("module", key, "checked") for key in fiscal_modules)
            ),
        ),
    }
    for label, rule in stock_rules.items():
        set_item_requirement(schema, "stock", label, rule)
    set_item_requirement(
        schema,
        "stock",
        "Produto ou serviço",
        required_rule(
            "any",
            condition("module", nfse, "checked"),
            condition("module", estoque, "checked"),
            condition("survey_field", "estoqueControlaEstoque", "equals", "Sim"),
            condition("module", ordem_servico, "checked"),
            condition("survey_field", "estoqueOrdemServico", "equals", "Sim"),
        ),
        "Cadastros",
    )

    financial_any = required_rule(
        "any",
        condition("module", financeiro, "checked"),
        condition("survey_field", "financeiroContasPagarReceber", "equals", "Sim"),
        condition("module", boleto, "checked"),
    )
    for label in ["Cadastrar conta/caixa", "Cadastrar forma de pagamento"]:
        set_item_requirement(schema, "finance", label, financial_any, "Cadastros")
    set_item_requirement(
        schema,
        "finance",
        "Cadastro de contas a pagar/receber (F7)",
        required_rule(
            "any",
            condition("module", financeiro, "checked"),
            condition("survey_field", "financeiroContasPagarReceber", "equals", "Sim"),
        ),
        "Cadastros",
    )
    for label in ["Cartão", "Conciliação de cartão"]:
        set_item_requirement(
            schema,
            "finance",
            label,
            required_rule(
                "all", condition("survey_field", "financeiroCartao", "equals", "Sim")
            ),
            "Boletos e cartão",
        )
    for label in ["Compensação", "Devolução de cheque"]:
        set_item_requirement(
            schema,
            "finance",
            label,
            required_rule(
                "all", condition("survey_field", "financeiroCheque", "equals", "Sim")
            ),
            "Extratos e documentos",
        )
    set_item_requirement(
        schema,
        "finance",
        "Cadastro de depósito/saque/transferência (F9)",
        required_rule(
            "all", condition("survey_field", "financeiroFluxoCaixa", "equals", "Sim")
        ),
        "Extratos e documentos",
    )
    set_item_requirement(
        schema,
        "fiscal",
        "Fechamento de caixa",
        required_rule(
            "all", condition("survey_field", "financeiroFluxoCaixa", "equals", "Sim")
        ),
        "Relatórios financeiros",
    )
    remessa_rule = required_rule(
        "any",
        condition(
            "survey_field",
            "financeiroTipoIntegracaoBoleto",
            "equals",
            "Arquivo de remessa e retorno",
        ),
        condition("survey_field", "financeiroTipoIntegracaoBoleto", "equals", "Ambos"),
    )
    api_rule = required_rule(
        "any",
        condition("survey_field", "financeiroTipoIntegracaoBoleto", "equals", "API"),
        condition("survey_field", "financeiroTipoIntegracaoBoleto", "equals", "Ambos"),
    )
    for label in ["Remessa", "Retorno", "Homologação de boleto"]:
        set_item_requirement(schema, "finance", label, remessa_rule, "Boletos e cartão")
    set_item_requirement(
        schema, "finance", "Homologação de API", api_rule, "Boletos e cartão"
    )

    inventory_any = required_rule(
        "any",
        condition("module", estoque, "checked"),
        condition("survey_field", "estoqueControlaEstoque", "equals", "Sim"),
    )
    for label in ["Cadastro de grupo", "Tipo do item", "Ajuste de saldo"]:
        set_item_requirement(schema, "stock", label, inventory_any, "Cadastros")
    set_item_requirement(
        schema, "fiscal", "Estoque e movimentação", inventory_any, "Relatórios de estoque"
    )
    for label, area, topic in [
        ("Cadastro de cliente/fornecedor", "stock", "Cadastros"),
        ("Pedido de compra", "stock", "Entradas"),
        ("Relatório de compra", "fiscal", "Relatórios de estoque"),
    ]:
        set_item_requirement(
            schema,
            area,
            label,
            required_rule("all", condition("module", compras, "checked")),
            topic,
        )
    set_item_requirement(
        schema,
        "stock",
        "NF-e com financeiro",
        required_rule(
            "all",
            condition("module", compras, "checked"),
            condition("module", financeiro, "checked"),
        ),
        "Entradas",
    )
    set_item_requirement(
        schema,
        "stock",
        "NF-e sem financeiro",
        required_rule(
            "all",
            condition("module", compras, "checked"),
            condition("module", financeiro, "not_checked"),
        ),
        "Entradas",
    )
    set_item_requirement(
        schema,
        "stock",
        "Manifesto",
        required_rule("all", condition("module", manifesto, "checked")),
        "Entradas",
    )

    devolucao = condition("survey_field", "estoqueDevolucao", "equals", "Sim")
    devolucao_rules = [
        ("Devolução de compra com NF-e", "Entradas", compras, "checked", nfe, "checked"),
        ("Devolução de compra sem NF-e", "Entradas", compras, "checked", nfe, "not_checked"),
        ("Devolução de venda com NF-e", "Saídas", faturamento, "checked", nfe, "checked"),
        ("Devolução de venda sem NF-e", "Saídas", faturamento, "checked", nfe, "not_checked"),
    ]
    for label, topic, first_key, first_operator, second_key, second_operator in devolucao_rules:
        set_item_requirement(
            schema,
            "stock",
            label,
            required_rule(
                "all",
                devolucao,
                condition("module", first_key, first_operator),
                condition("module", second_key, second_operator),
            ),
            topic,
        )

    for label, area, topic in [
        ("Extrato de venda", "stock", "Saídas"),
        ("Relatório de venda", "fiscal", "Relatórios de estoque"),
    ]:
        set_item_requirement(
            schema,
            area,
            label,
            required_rule("all", condition("module", faturamento, "checked")),
            topic,
        )
    set_item_requirement(
        schema,
        "stock",
        "NF-e de venda",
        required_rule(
            "all",
            condition("module", faturamento, "checked"),
            condition("module", nfe, "checked"),
        ),
        "Saídas",
    )

    for label, module in [
        ("Gerar Sintegra", sintegra),
        ("Gerar SPED", sped),
        ("Envio de XML para a contabilidade", sped),
        ("Relatório de entradas e saídas", sped),
    ]:
        set_item_requirement(
            schema,
            "fiscal",
            label,
            required_rule("all", condition("module", module, "checked")),
            "Módulo Fiscal",
        )

    workflow_rule = required_rule(
        "any",
        condition("report_field", "qtdWorkflow", "greater_than", "0"),
        condition("survey_field", "geralWorkflow", "not_blank"),
    )
    set_item_requirement(
        schema,
        "technical",
        "Workflow de trava de usuário",
        workflow_rule,
        "Instalação e ambiente",
    )
    set_item_requirement(
        schema,
        "technical",
        "Verificar Workflow de trava de usuários",
        workflow_rule,
        "Configuração e cadastros",
    )

    other_group = next(
        group
        for group in schema["rei"]["stock"]
        if normalized_comparison_text(group.get("title")) == normalized_comparison_text("Outros")
    )
    new_stock_items = [
        "Configurar e testar o fluxo de Ordem de Serviço",
        "Validar separação de produtos e serviços no faturamento",
        "Configurar e testar balança",
        "Configurar e testar controle de lote",
        "Configurar composição de produtos",
        "Configurar produtos similares",
        "Configurar controle de série do produto",
        "Configurar e testar comissão",
        "Configurar formação de preço e custos",
        "Configurar e testar PDV online",
        "Configurar PDV offline e sincronização",
        "Validar e testar customizações contratadas",
    ]
    existing_labels = {
        normalized_comparison_text(item.get("label")) for item in other_group["items"]
    }
    for label in new_stock_items:
        if normalized_comparison_text(label) not in existing_labels:
            other_group["items"].append(
                normalize_rei_item(
                    {"label": label, "type": "checkbox"}, "stock", "Outros"
                )
            )

    new_rules = {
        "Configurar e testar o fluxo de Ordem de Serviço": required_rule(
            "any",
            condition("module", ordem_servico, "checked"),
            condition("survey_field", "estoqueOrdemServico", "equals", "Sim"),
        ),
        "Validar separação de produtos e serviços no faturamento": required_rule(
            "all",
            condition("module", ordem_servico, "checked"),
            condition("module", nfe, "checked"),
            condition("module", nfse, "checked"),
        ),
        "Configurar e testar balança": required_rule(
            "all", condition("survey_field", "estoqueBalanca", "equals", "Sim")
        ),
        "Configurar e testar controle de lote": required_rule(
            "all", condition("survey_field", "estoqueLote", "equals", "Sim")
        ),
        "Configurar composição de produtos": required_rule(
            "all", condition("survey_field", "estoqueComposicao", "equals", "Sim")
        ),
        "Configurar produtos similares": required_rule(
            "all", condition("survey_field", "estoqueSimilar", "equals", "Sim")
        ),
        "Configurar controle de série do produto": required_rule(
            "all", condition("survey_field", "estoqueSerieProduto", "equals", "Sim")
        ),
        "Configurar e testar comissão": required_rule(
            "all", condition("survey_field", "estoqueComissao", "equals", "Sim")
        ),
        "Configurar formação de preço e custos": required_rule(
            "any",
            condition("survey_field", "estoqueFormacaoPreco", "equals", "Sim"),
            condition("module", custos, "checked"),
        ),
        "Configurar e testar PDV online": required_rule(
            "all", condition("survey_field", "estoquePdv", "equals", "Online")
        ),
        "Configurar PDV offline e sincronização": required_rule(
            "all", condition("survey_field", "estoquePdv", "equals", "Offline")
        ),
        "Validar e testar customizações contratadas": required_rule(
            "any",
            condition("module", customizacao, "checked"),
            condition("survey_field", "geralCustomizacao", "not_blank"),
        ),
    }
    for label, rule in new_rules.items():
        set_item_requirement(schema, "stock", label, rule, "Outros")

    survey_field(schema, "empresa")["requiredMode"] = "always"
    survey_field(schema, "financeiroCartaoMaquina").update(
        {
            "requiredMode": "conditional",
            "requiredWhen": required_rule(
                "all", condition("survey_field", "financeiroCartao", "equals", "Sim")
            ),
        }
    )
    boleto_field = survey_field(schema, "financeiroTipoIntegracaoBoleto")
    boleto_field.update(
        {
            "requiredMode": "conditional",
            "requiredWhen": required_rule(
                "all", condition("module", boleto, "checked")
            ),
        }
    )
    return schema


def fixed_validation_requirements() -> dict:
    completed_options = ["Concluído", "Concluído, mas deseja novos serviços"]
    return {
        "rei_completion": [
            {"key": "cliente", "label": "Cliente / Projeto", "section": "Identificação", "type": "text", "options": [], "requiredMode": "always", "valueSource": "report_field"},
            {"key": "consultor", "label": "Consultor", "section": "Identificação", "type": "text", "options": [], "requiredMode": "always", "valueSource": "report_field"},
            {"key": "inicio", "label": "Início", "section": "Identificação", "type": "date", "options": [], "requiredMode": "always", "valueSource": "report_field"},
            {"key": "termino", "label": "Término", "section": "Identificação", "type": "date", "options": [], "requiredMode": "always", "valueSource": "report_field"},
            {"key": "servicosExecutados", "label": "Serviços executados", "section": "Entrega", "type": "textarea", "options": [], "requiredMode": "always", "valueSource": "report_field"},
            {"key": "deliveryStatus", "label": "Posicionamento da entrega", "section": "Entrega", "type": "choice", "options": completed_options, "requiredMode": "always", "valueSource": "report_field"},
            {"key": "assinaturaAnalistaImagem", "label": "Assinatura do técnico", "section": "Entrega", "type": "signature", "options": [], "requiredMode": "always", "valueSource": "report_field"},
            {"key": "assinaturaClienteImagem", "label": "Assinatura do cliente", "section": "Entrega", "type": "signature", "options": [], "requiredMode": "always", "valueSource": "report_field"},
            {
                "key": "pendencias", "label": "Pendências", "section": "Entrega", "type": "textarea", "options": [], "requiredMode": "conditional", "valueSource": "report_field",
                "requiredWhen": required_rule("all", condition("report_field", "deliveryStatus", "equals", "Concluído, mas deseja novos serviços")),
            },
        ]
    }


def merge_schema_item_lists(base: list[dict], custom: list[dict]) -> list[dict]:
    result = [json.loads(json.dumps(item, ensure_ascii=False)) for item in base]
    by_key = {str(item.get("key") or ""): index for index, item in enumerate(result)}
    for item in custom:
        key = str(item.get("key") or "")
        copied = json.loads(json.dumps(item, ensure_ascii=False))
        if key and key in by_key:
            base_item = result[by_key[key]]
            copied["legacyKeys"] = list(
                dict.fromkeys([*(base_item.get("legacyKeys") or []), *(copied.get("legacyKeys") or [])])
            )
            result[by_key[key]] = copied
        else:
            by_key[key] = len(result)
            result.append(copied)
    return result


def merge_schema_groups(base: list[dict], custom: list[dict], child_key: str) -> list[dict]:
    result = [json.loads(json.dumps(group, ensure_ascii=False)) for group in base]
    for custom_group in custom:
        title = str(custom_group.get("title") or "")
        target = next(
            (
                group
                for group in result
                if normalized_comparison_text(group.get("title"))
                == normalized_comparison_text(title)
            ),
            None,
        )
        if target is None:
            result.append(json.loads(json.dumps(custom_group, ensure_ascii=False)))
        else:
            target[child_key] = merge_schema_item_lists(
                target.get(child_key, []), custom_group.get(child_key, [])
            )
    return result


def effective_schema_from_custom(custom: dict | None) -> dict:
    defaults = load_default_schema_items()
    custom = normalize_schema_items(custom)
    effective = empty_schema_items()
    effective["rei"]["modules"] = merge_schema_item_lists(
        defaults["rei"]["modules"], custom["rei"]["modules"]
    )
    for area in REI_SCOPES:
        effective["rei"][area] = merge_schema_groups(
            defaults["rei"][area], custom["rei"][area], "items"
        )
    effective["levantamento"] = merge_schema_groups(
        defaults["levantamento"], custom["levantamento"], "fields"
    )
    effective["validation"] = {"fixed": fixed_validation_requirements()}
    effective["ruleModelVersion"] = SCHEMA_RULE_VERSION
    version_payload = canonical(effective).encode("utf-8")
    effective["schemaVersion"] = hashlib.sha256(version_payload).hexdigest()[:16]
    return effective


def load_effective_schema_items() -> dict:
    return effective_schema_from_custom(load_schema_items())


def iter_condition_leaves(rule: dict | None):
    if not isinstance(rule, dict):
        return
    for item in rule.get("conditions", []):
        if isinstance(item, dict) and "match" in item and not item.get("source"):
            yield from iter_condition_leaves(item)
        elif isinstance(item, dict):
            yield item


def validate_schema_references(schema: dict) -> None:
    module_keys = {
        str(item.get("key") or "") for item in schema.get("rei", {}).get("modules", [])
    }
    checklist_keys: set[str] = set()
    typed_rei_keys: set[str] = set()
    all_definitions: list[dict] = []
    for area, _topic, item in iter_rei_items(schema, include_modules=False):
        all_definitions.append(item)
        target = checklist_keys if item.get("type") == "checkbox" else typed_rei_keys
        target.add(str(item.get("key") or ""))
    survey_keys: set[str] = set()
    for section in schema.get("levantamento", []):
        for field in section.get("fields", []):
            survey_keys.add(str(field.get("key") or ""))
            all_definitions.append(field)
    fixed = schema.get("validation", {}).get("fixed", {})
    fixed_definitions = [
        item
        for definitions in fixed.values()
        for item in definitions
        if isinstance(item, dict)
    ]
    all_definitions.extend(fixed_definitions)
    report_keys = {
        "cliente", "consultor", "inicio", "termino", "servicosExecutados",
        "deliveryStatus", "pendencias", "assinaturaAnalistaImagem",
        "assinaturaClienteImagem", "qtdWorkflow", "tipoCertificado",
        "observacoesTecnicas", "usuariosTga", "diasContratados", "diasUtilizados",
        "rating",
        *typed_rei_keys,
    }
    known = {
        "module": module_keys,
        "checklist": checklist_keys,
        "survey_field": survey_keys,
        "report_field": report_keys,
    }
    for definition in all_definitions:
        mode = str(definition.get("requiredMode") or "never")
        if mode == "conditional":
            rule = normalize_required_when(definition.get("requiredWhen"), strict=True)
            definition["requiredWhen"] = rule
            for leaf in iter_condition_leaves(rule):
                source = str(leaf.get("source") or "")
                key = str(leaf.get("key") or "")
                if key not in known.get(source, set()):
                    raise ValueError(
                        f"A condição referencia uma chave desconhecida: {source}/{key}."
                    )


def slugify_key(text: str, prefix: str = "campo") -> str:
    slug = re.sub(r"[^a-zA-Z0-9]+", "_", text.strip().lower()).strip("_")
    return f"{prefix}_{slug or secrets.token_hex(4)}"


def schema_redirect(kind: str, text: str) -> str:
    return f"/admin/items?{kind}={quote_plus(text)}"


def add_rei_topic(area: str, title: str) -> None:
    area = area if area in REI_ITEM_AREAS and area != "modules" else ""
    title = title.strip()
    if not area or len(title) < 2:
        raise ValueError("Informe uma área e um tópico válido")
    data = load_schema_items()
    groups = data["rei"][area]
    if not any(group["title"].strip().lower() == title.lower() for group in groups):
        groups.append({"title": title, "items": []})
    save_schema_items(data)


def add_rei_item(
    area: str,
    topic: str,
    label: str,
    field_type: str = "checkbox",
    options_text: str = "",
    required_mode: str = "never",
    required_when: dict | None = None,
) -> None:
    area = area if area in REI_ITEM_AREAS else ""
    topic = topic.strip()
    label = label.strip()
    field_type = field_type if field_type in SURVEY_FIELD_TYPES else "checkbox"
    if not area or len(label) < 2:
        raise ValueError("Informe uma área e um item válido")
    data = load_schema_items()
    if area == "modules":
        existing_labels = {
            normalized_comparison_text(item.get("label"))
            for item in data["rei"]["modules"]
        }
        if normalized_comparison_text(label) not in existing_labels:
            data["rei"]["modules"].append(
                {
                    "key": f"custom::module::{secrets.token_hex(8)}",
                    "label": label,
                    "type": "checkbox",
                    "options": [],
                    "requiredMode": "never",
                }
            )
        save_schema_items(data)
        return
    if len(topic) < 2:
        raise ValueError("Informe o tópico onde o item será exibido")
    groups = data["rei"][area]
    group = next(
        (entry for entry in groups if entry["title"].strip().lower() == topic.lower()),
        None,
    )
    if not group:
        group = {"title": topic, "items": []}
        groups.append(group)
    item = {
        "key": f"custom::rei::{secrets.token_hex(8)}",
        "label": label,
        "type": field_type,
        "options": [],
        "requiredMode": required_mode,
    }
    if required_mode == "conditional":
        item["requiredWhen"] = required_when
    if field_type == "choice":
        options = [
            part.strip() for part in re.split(r"[,;\n]+", options_text) if part.strip()
        ]
        item["options"] = options or ["Sim", "Não"]
    existing = {
        (
            str(existing.get("label", existing)).strip().lower()
            if isinstance(existing, dict)
            else str(existing).strip().lower()
        )
        for existing in group["items"]
    }
    if label.lower() not in existing:
        group["items"].append(item)
    save_schema_items(data)


def update_rei_item(
    area: str,
    topic: str,
    key: str,
    label: str,
    field_type: str,
    options_text: str,
    required_mode: str,
    required_when: dict | None,
) -> None:
    area = area if area in REI_ITEM_AREAS else ""
    topic = topic.strip()
    key = key.strip()
    label = label.strip()
    if not area or not key or len(label) < 2:
        raise ValueError("Informe um item válido para edição.")
    if area == "modules":
        field_type = "checkbox"
        required_mode = "never"
        required_when = None
    elif field_type not in SURVEY_FIELD_TYPES:
        raise ValueError("Selecione um tipo de campo válido.")

    data = load_schema_items()
    defaults = load_default_schema_items()
    if area == "modules":
        custom_items = data["rei"]["modules"]
        default_item = next(
            (item for item in defaults["rei"]["modules"] if item.get("key") == key),
            None,
        )
    else:
        if not topic:
            raise ValueError("Informe o tópico do item.")
        custom_group = next(
            (
                group
                for group in data["rei"][area]
                if normalized_comparison_text(group.get("title"))
                == normalized_comparison_text(topic)
            ),
            None,
        )
        if custom_group is None:
            custom_group = {"title": topic, "items": []}
            data["rei"][area].append(custom_group)
        custom_items = custom_group["items"]
        default_group = next(
            (
                group
                for group in defaults["rei"][area]
                if normalized_comparison_text(group.get("title"))
                == normalized_comparison_text(topic)
            ),
            None,
        )
        default_item = next(
            (item for item in (default_group or {}).get("items", []) if item.get("key") == key),
            None,
        )
    custom_item = next((item for item in custom_items if item.get("key") == key), None)
    source = custom_item or default_item
    if source is None:
        raise ValueError("Item do R.E.I. não encontrado.")
    updated = {
        "key": key,
        "label": label,
        "type": field_type,
        "options": [],
        "requiredMode": required_mode,
    }
    legacy_keys = list(source.get("legacyKeys") or [])
    if legacy_keys:
        updated["legacyKeys"] = legacy_keys
    if field_type == "choice":
        options = [
            part.strip() for part in re.split(r"[,;\n]+", options_text) if part.strip()
        ]
        updated["options"] = options or ["Sim", "Não"]
    if required_mode == "conditional":
        updated["requiredWhen"] = required_when
    if custom_item:
        custom_items[custom_items.index(custom_item)] = updated
    else:
        custom_items.append(updated)
    save_schema_items(data)


def delete_rei_topic(area: str, title: str) -> None:
    area = area if area in REI_ITEM_AREAS and area != "modules" else ""
    title = title.strip()
    if not area or len(title) < 2:
        raise ValueError("Informe uma área e um tópico válido")
    data = load_schema_items()
    before = len(data["rei"][area])
    data["rei"][area] = [
        group
        for group in data["rei"][area]
        if str(group.get("title", "")).strip().lower() != title.lower()
    ]
    if len(data["rei"][area]) == before:
        raise ValueError("Tópico não encontrado ou não pode ser excluído")
    save_schema_items(data)


def delete_rei_item(area: str, topic: str, item_key: str, label: str = "") -> None:
    area = area if area in REI_ITEM_AREAS else ""
    topic = topic.strip()
    item_key = item_key.strip()
    label = label.strip()
    if not area or (not item_key and len(label) < 2):
        raise ValueError("Informe uma área e um item válido")
    data = load_schema_items()
    if area == "modules":
        before = len(data["rei"]["modules"])
        data["rei"]["modules"] = [
            item
            for item in data["rei"]["modules"]
            if not (
                (item_key and str(item.get("key") or "") == item_key)
                or (
                    label
                    and normalized_comparison_text(item.get("label"))
                    == normalized_comparison_text(label)
                )
            )
        ]
        if len(data["rei"]["modules"]) == before:
            raise ValueError("Item não encontrado ou não pode ser excluído")
        save_schema_items(data)
        return
    if len(topic) < 2:
        raise ValueError("Informe o tópico do item")
    group = next(
        (
            entry
            for entry in data["rei"][area]
            if entry["title"].strip().lower() == topic.lower()
        ),
        None,
    )
    if not group:
        raise ValueError("Tópico não encontrado")
    before = len(group["items"])
    group["items"] = [
        item
        for item in group["items"]
        if not (
            (item_key and str(item.get("key") or "") == item_key)
            or (
                label
                and normalized_comparison_text(item.get("label"))
                == normalized_comparison_text(label)
            )
        )
    ]
    if len(group["items"]) == before:
        raise ValueError("Item não encontrado ou não pode ser excluído")
    save_schema_items(data)


def add_survey_topic(title: str) -> None:
    title = title.strip()
    if len(title) < 2:
        raise ValueError("Informe um tópico válido")
    data = load_schema_items()
    if not any(
        section["title"].strip().lower() == title.lower()
        for section in data["levantamento"]
    ):
        data["levantamento"].append({"title": title, "fields": []})
    save_schema_items(data)


def delete_survey_topic(title: str) -> None:
    title = title.strip()
    if len(title) < 2:
        raise ValueError("Informe um tópico válido")
    data = load_schema_items()
    before = len(data["levantamento"])
    data["levantamento"] = [
        section
        for section in data["levantamento"]
        if str(section.get("title", "")).strip().lower() != title.lower()
    ]
    if len(data["levantamento"]) == before:
        raise ValueError("Tópico não encontrado ou não pode ser excluído")
    save_schema_items(data)


def delete_survey_item(topic: str, key: str) -> None:
    topic = topic.strip()
    key = key.strip()
    if len(topic) < 2 or len(key) < 2:
        raise ValueError("Informe tópico e campo válidos")
    data = load_schema_items()
    section = next(
        (
            entry
            for entry in data["levantamento"]
            if entry["title"].strip().lower() == topic.lower()
        ),
        None,
    )
    if not section:
        raise ValueError("Tópico não encontrado")
    before = len(section["fields"])
    section["fields"] = [
        field for field in section["fields"] if str(field.get("key", "")).strip() != key
    ]
    if len(section["fields"]) == before:
        raise ValueError("Campo não encontrado ou não pode ser excluído")
    save_schema_items(data)


def add_survey_item(
    topic: str,
    label: str,
    field_type: str,
    options_text: str,
    required_mode: str = "never",
    required_when: dict | None = None,
) -> None:
    topic = topic.strip()
    label = label.strip()
    field_type = field_type if field_type in SURVEY_FIELD_TYPES else "text"
    if len(topic) < 2 or len(label) < 2:
        raise ValueError("Informe tópico e item válidos")
    data = load_schema_items()
    section = next(
        (
            entry
            for entry in data["levantamento"]
            if entry["title"].strip().lower() == topic.lower()
        ),
        None,
    )
    if not section:
        section = {"title": topic, "fields": []}
        data["levantamento"].append(section)
    key = slugify_key(f"{topic}_{label}", "custom")
    existing_keys = {
        field["key"] for item in data["levantamento"] for field in item["fields"]
    }
    if key in existing_keys:
        key = f"{key}_{secrets.token_hex(3)}"
    field = {
        "key": key,
        "label": label,
        "type": field_type,
        "options": [],
        "requiredMode": required_mode,
    }
    if required_mode == "conditional":
        field["requiredWhen"] = required_when
    if field_type == "choice":
        options = [
            part.strip() for part in re.split(r"[,;\n]+", options_text) if part.strip()
        ]
        field["options"] = options or ["Sim", "Não"]
    section["fields"].append(field)
    save_schema_items(data)


def update_survey_item(
    topic: str,
    key: str,
    label: str,
    field_type: str,
    options_text: str,
    required_mode: str = "never",
    required_when: dict | None = None,
) -> None:
    topic = topic.strip()
    key = key.strip()
    label = label.strip()
    field_type = field_type if field_type in SURVEY_FIELD_TYPES else "text"
    if len(topic) < 2 or not key or len(label) < 2:
        raise ValueError("Informe um nome e um tipo válidos para o campo")

    data = load_schema_items()
    defaults = load_default_schema_items()
    custom_section = next(
        (
            entry
            for entry in data["levantamento"]
            if entry["title"].strip().lower() == topic.lower()
        ),
        None,
    )
    default_section = next(
        (
            entry
            for entry in defaults["levantamento"]
            if entry["title"].strip().lower() == topic.lower()
        ),
        None,
    )
    custom_field = (
        next(
            (
                field
                for field in custom_section.get("fields", [])
                if str(field.get("key", "")).strip() == key
            ),
            None,
        )
        if custom_section
        else None
    )
    default_field = (
        next(
            (
                field
                for field in default_section.get("fields", [])
                if str(field.get("key", "")).strip() == key
            ),
            None,
        )
        if default_section
        else None
    )
    if not custom_field and not default_field:
        raise ValueError("Campo do levantamento não encontrado")

    updated = {
        "key": key,
        "label": label,
        "type": field_type,
        "options": [],
        "requiredMode": required_mode,
    }
    if required_mode == "conditional":
        updated["requiredWhen"] = required_when
    if field_type == "choice":
        options = [
            part.strip() for part in re.split(r"[,;\n]+", options_text) if part.strip()
        ]
        updated["options"] = options or ["Sim", "Não"]
    if not custom_section:
        custom_section = {"title": topic, "fields": []}
        data["levantamento"].append(custom_section)
    if custom_field:
        custom_section["fields"][custom_section["fields"].index(custom_field)] = updated
    else:
        # Um campo padrão é personalizado como uma substituição com a mesma chave.
        custom_section["fields"].append(updated)
    save_schema_items(data)


def render_pills(items: list[str]) -> str:
    return "".join(f'<span class="pill">{html.escape(item)}</span>' for item in items)


def render_field_pill(
    item: object,
    default_type_label: str = "Texto curto",
    trailing_html: str = "",
    schema: dict | None = None,
) -> str:
    label, type_label = field_display_parts(item, default_type_label)
    if not label:
        return ""
    requirement_html = ""
    if isinstance(item, dict) and schema:
        caption, css_class, summary = requirement_caption(item, schema)
        requirement_html = (
            f'<span class="required-badge {css_class}">{html.escape(caption)}</span>'
            f'<small class="condition-summary">{html.escape(summary)}</small>'
        )
    return (
        '<div class="pill field-pill">'
        f'<span class="field-pill-copy"><strong>{html.escape(label)}</strong>'
        f"<small>Tipo: {html.escape(type_label)}</small>{requirement_html}</span>{trailing_html}</div>"
    )


def render_item_source(
    title: str,
    items: list[object],
    empty: str = "Nenhum item cadastrado.",
    default_type_label: str = "Texto curto",
    area: str = "",
    topic: str = "",
    schema: dict | None = None,
) -> str:
    rendered = [
        render_field_pill(
            item,
            default_type_label,
            render_rei_edit(item, area, topic, schema) if isinstance(item, dict) and schema else "",
            schema,
        )
        for item in items
    ]
    rendered = [item for item in rendered if item]
    if not rendered:
        return f'<div class="subblock"><span class="source-label">{html.escape(title)}</span><div class="empty">{html.escape(empty)}</div></div>'
    return f'<div class="subblock"><span class="source-label">{html.escape(title)}</span><div class="field-pill-list">{"".join(rendered)}</div></div>'


def render_delete_form(
    action: str, fields: dict[str, str], label: str = "Excluir"
) -> str:
    inputs = "".join(
        f'<input type="hidden" name="{html.escape(name)}" value="{html.escape(value)}">'
        for name, value in fields.items()
    )
    return (
        f'<form method="post" action="{html.escape(action)}" class="inline-delete">'
        f'{inputs}<button class="delete mini-delete" type="submit" title="{html.escape(label)}">{html.escape(label)}</button></form>'
    )


def requirement_caption(item: dict, schema: dict) -> tuple[str, str, str]:
    mode = str(item.get("requiredMode") or "never")
    if mode == "always":
        return "Obrigatório", "required-always", "Obrigatório em sua fase"
    if mode == "conditional":
        return (
            "Obrigatório por condição",
            "required-conditional",
            condition_summary(item.get("requiredWhen"), schema),
        )
    return "Opcional", "required-never", "Não bloqueia a finalização"


def condition_key_catalog(schema: dict) -> list[tuple[str, str, str]]:
    values: list[tuple[str, str, str]] = []
    for area, topic, item in iter_rei_items(schema):
        source = "module" if area == "modules" else "checklist"
        values.append(
            (
                source,
                str(item.get("key") or ""),
                f"{REI_ITEM_AREAS.get(area, area)} · {topic} · {item.get('label', '')}",
            )
        )
    for section in schema.get("levantamento", []):
        for field in section.get("fields", []):
            values.append(
                (
                    "survey_field",
                    str(field.get("key") or ""),
                    f"Levantamento · {section.get('title', '')} · {field.get('label', '')}",
                )
            )
    report_labels = {
        "cliente": "Cliente / Projeto",
        "consultor": "Consultor",
        "inicio": "Início",
        "termino": "Término",
        "servicosExecutados": "Serviços executados",
        "deliveryStatus": "Posicionamento da entrega",
        "pendencias": "Pendências",
        "qtdWorkflow": "Quantidade de Workflow",
        "tipoCertificado": "Tipo do certificado",
        "observacoesTecnicas": "Observações técnicas",
    }
    values.extend(("report_field", key, f"R.E.I. · {label}") for key, label in report_labels.items())
    return values


def render_condition_row(index: int, condition_item: dict, schema: dict) -> str:
    source = str(condition_item.get("source") or "survey_field")
    operator = str(condition_item.get("operator") or "equals")
    key = str(condition_item.get("key") or "")
    value = str(condition_item.get("value") or "")
    source_labels = {
        "module": "Módulo contratado",
        "survey_field": "Campo do levantamento",
        "report_field": "Campo do R.E.I.",
        "checklist": "Item de checklist",
    }
    operator_labels = {
        "checked": "Marcado",
        "not_checked": "Não marcado",
        "equals": "Igual a",
        "not_equals": "Diferente de",
        "not_blank": "Preenchido",
        "blank": "Vazio",
        "greater_than": "Maior que",
    }
    source_options = "".join(
        f'<option value="{name}" {"selected" if source == name else ""}>{html.escape(label)}</option>'
        for name, label in source_labels.items()
    )
    operator_options = "".join(
        f'<option value="{name}" {"selected" if operator == name else ""}>{html.escape(label)}</option>'
        for name, label in operator_labels.items()
    )
    return f"""<div class="condition-row" data-condition-index="{index}">
      <select name="condition_source_{index}" aria-label="Origem da condição">{source_options}</select>
      <input name="condition_key_{index}" value="{html.escape(key)}" list="condition-key-catalog" placeholder="Selecione o campo ou item" aria-label="Campo ou item da condição">
      <select name="condition_operator_{index}" aria-label="Operador da condição">{operator_options}</select>
      <input name="condition_value_{index}" value="{html.escape(value)}" placeholder="Valor, quando aplicável" aria-label="Valor da condição">
      <button type="button" class="remove-condition" title="Remover condição">Remover</button>
    </div>"""


def render_required_editor(item: dict, schema: dict, allow_rules: bool = True) -> str:
    mode = str(item.get("requiredMode") or "never")
    required_when = item.get("requiredWhen") if isinstance(item.get("requiredWhen"), dict) else None
    leaves = list(iter_condition_leaves(required_when))
    mode_options = "".join(
        f'<option value="{value}" {"selected" if mode == value else ""}>{caption}</option>'
        for value, caption in [
            ("never", "Opcional"),
            ("always", "Obrigatório"),
            ("conditional", "Obrigatório por condição"),
        ]
    )
    if not allow_rules:
        return '<input type="hidden" name="required_mode" value="never"><p class="rule-note">Módulos são fontes das condições e não tarefas obrigatórias.</p>'
    original = html.escape(canonical(required_when) if required_when else "")
    match = str((required_when or {}).get("match") or "any")
    rows = "".join(render_condition_row(index, leaf, schema) for index, leaf in enumerate(leaves))
    return f"""<div class="requirement-editor">
      <label>Obrigatoriedade</label><select name="required_mode" class="required-mode">{mode_options}</select>
      <input type="hidden" name="required_when_original" value="{original}">
      <input type="hidden" name="rule_changed" value="0">
      <div class="conditional-editor {'' if mode == 'conditional' else 'is-hidden'}">
        <label>Ativar quando</label><select name="required_match" class="required-match"><option value="any" {"selected" if match == "any" else ""}>Qualquer condição</option><option value="all" {"selected" if match == "all" else ""}>Todas as condições</option></select>
        <div class="condition-list">{rows}</div>
        <button type="button" class="add-condition">Adicionar condição</button>
      </div>
    </div>"""


def parse_required_form(form: dict[str, str]) -> tuple[str, dict | None]:
    mode = str(form.get("required_mode") or "never").strip().lower()
    if mode not in REQUIRED_MODES:
        raise ValueError("Modalidade de obrigatoriedade inválida.")
    if mode != "conditional":
        return mode, None
    original = str(form.get("required_when_original") or "").strip()
    if form.get("rule_changed") != "1" and original:
        try:
            return mode, normalize_required_when(json.loads(original), strict=True)
        except json.JSONDecodeError as error:
            raise ValueError("Regra original inválida.") from error
    indexes = sorted(
        {
            int(match.group(1))
            for name in form
            if (match := re.fullmatch(r"condition_source_(\d+)", name))
        }
    )
    conditions = []
    for index in indexes:
        source = form.get(f"condition_source_{index}", "")
        key = form.get(f"condition_key_{index}", "")
        operator = form.get(f"condition_operator_{index}", "")
        if not source and not key and not operator:
            continue
        conditions.append(
            {
                "source": source,
                "key": key,
                "operator": operator,
                "value": form.get(f"condition_value_{index}", ""),
            }
        )
    return mode, normalize_required_when(
        {"match": form.get("required_match", "any"), "conditions": conditions},
        strict=True,
    )


def render_rei_edit(item: dict, area: str, topic: str, schema: dict) -> str:
    key = str(item.get("key") or "")
    label = str(item.get("label") or "")
    field_type = str(item.get("type") or "checkbox")
    options_text = ", ".join(str(option) for option in item.get("options", []))
    type_options = "".join(
        f'<option value="{html.escape(value)}" {"selected" if value == field_type else ""}>{html.escape(caption)}</option>'
        for value, caption in SURVEY_FIELD_TYPES.items()
        if value != "signature"
    )
    disabled_type = (
        '<input type="hidden" name="type" value="checkbox"><p class="rule-note">Tipo: Caixa de seleção</p>'
        if area == "modules"
        else f'<label>Tipo do campo</label><select name="type">{type_options}</select>'
    )
    return f"""<details class="field-edit"><summary>Editar</summary>
      <form method="post" action="/admin/items/rei-item/edit">
        <h3>Editar item do R.E.I.</h3>
        <input type="hidden" name="area" value="{html.escape(area)}">
        <input type="hidden" name="topic" value="{html.escape(topic)}">
        <input type="hidden" name="key" value="{html.escape(key)}">
        <label>Nome exibido</label><input name="label" value="{html.escape(label)}" required minlength="2">
        {disabled_type}
        <label>Opções da múltipla escolha</label><textarea name="options" placeholder="Sim, Não, Parcial">{html.escape(options_text)}</textarea>
        {render_required_editor(item, schema, allow_rules=area != "modules")}
        <div class="edit-actions"><button type="button" class="cancel-edit" onclick="this.closest('details').removeAttribute('open')">Cancelar</button><button type="submit">Salvar alterações</button></div>
      </form></details>"""


def render_survey_edit(field: dict, topic: str, schema: dict) -> str:
    key = str(field.get("key", "")).strip()
    label = str(field.get("label", "")).strip()
    field_type = str(field.get("type", "text")).strip()
    options_text = ", ".join(
        str(option) for option in field.get("options", []) if str(option).strip()
    )
    type_options = "".join(
        f'<option value="{html.escape(value)}" {"selected" if value == field_type else ""}>{html.escape(caption)}</option>'
        for value, caption in SURVEY_FIELD_TYPES.items()
        if value not in {"checkbox", "signature"}
    )
    return f"""<details class="field-edit"><summary>Editar</summary>
      <form method="post" action="/admin/items/survey-item/edit">
        <h3>Editar campo do levantamento</h3>
        <input type="hidden" name="topic" value="{html.escape(topic)}">
        <input type="hidden" name="key" value="{html.escape(key)}">
        <label>Nome do campo</label><input name="label" value="{html.escape(label)}" required minlength="2">
        <label>Tipo do campo</label><select name="type">{type_options}</select>
        <label>Opções da múltipla escolha</label><textarea name="options" placeholder="Sim, Não, Parcial">{html.escape(options_text)}</textarea>
        {render_required_editor(field, schema)}
        <div class="edit-actions"><button type="button" class="cancel-edit" onclick="this.closest('details').removeAttribute('open')">Cancelar</button><button type="submit">Salvar alterações</button></div>
      </form></details>"""


def render_deletable_rei_items(
    area: str,
    topic: str,
    items: list[object],
    schema: dict,
    empty: str = "Nenhum item personalizado neste tópico.",
) -> str:
    if not items:
        return f'<div class="subblock"><span class="source-label">Personalizado</span><div class="empty">{html.escape(empty)}</div></div>'
    pills = []
    for item in items:
        label = (
            str(item.get("label", item)).strip()
            if isinstance(item, dict)
            else str(item).strip()
        )
        if not label:
            continue
        delete_form = render_delete_form(
            "/admin/items/rei-item/delete",
            {
                "area": area,
                "topic": topic,
                "key": str(item.get("key") or "") if isinstance(item, dict) else "",
                "label": label,
            },
        )
        controls = render_rei_edit(item, area, topic, schema) if isinstance(item, dict) else ""
        pills.append(render_field_pill(item, "Texto curto", controls + delete_form, schema))
    if not pills:
        return f'<div class="subblock"><span class="source-label">Personalizado</span><div class="empty">{html.escape(empty)}</div></div>'
    return f'<div class="subblock"><span class="source-label">Personalizado</span><div class="field-pill-list">{"".join(pills)}</div></div>'


def render_editable_survey_fields(
    topic: str,
    fields: list[dict],
    source: str,
    empty: str,
    allow_delete: bool,
    schema: dict,
) -> str:
    if not fields:
        return f'<div class="subblock"><span class="source-label">{html.escape(source)}</span><div class="empty">{html.escape(empty)}</div></div>'
    pills = []
    for field in fields:
        key = str(field.get("key", "")).strip()
        label = str(field.get("label", "")).strip()
        if not key or not label:
            continue
        controls = render_survey_edit(field, topic, schema)
        if allow_delete:
            controls += render_delete_form(
                "/admin/items/survey-item/delete",
                {"topic": topic, "key": key},
            )
        pills.append(render_field_pill(field, "Texto curto", controls, schema))
    if not pills:
        return f'<div class="subblock"><span class="source-label">{html.escape(source)}</span><div class="empty">{html.escape(empty)}</div></div>'
    return f'<div class="subblock"><span class="source-label">{html.escape(source)}</span><div class="field-pill-list">{"".join(pills)}</div></div>'


def render_rei_area(
    area: str, default_groups: list[dict], custom_groups: list[dict], schema: dict
) -> str:
    titles: list[str] = []
    for group in default_groups + custom_groups:
        title = str(group.get("title", "")).strip()
        if title and not any(existing.lower() == title.lower() for existing in titles):
            titles.append(title)
    if not titles:
        return '<div class="empty">Nenhum tópico cadastrado.</div>'
    blocks = []
    for title in titles:
        default = next(
            (
                group
                for group in default_groups
                if str(group.get("title", "")).strip().lower() == title.lower()
            ),
            {"items": []},
        )
        custom_group = next(
            (
                group
                for group in custom_groups
                if str(group.get("title", "")).strip().lower() == title.lower()
            ),
            None,
        )
        custom = custom_group or {"items": []}
        default_items = default.get("items", [])
        custom_items = custom.get("items", [])
        custom_by_key = {str(item.get("key") or ""): item for item in custom_items}
        default_keys = {str(item.get("key") or "") for item in default_items}
        effective_defaults = [
            custom_by_key.get(str(item.get("key") or ""), item) for item in default_items
        ]
        custom_only = [
            item for item in custom_items if str(item.get("key") or "") not in default_keys
        ]
        blocks.append(
            f'<div class="topic"><div class="topic-head"><strong>{html.escape(title)}</strong></div>'
            + render_item_source(
                "Padrão",
                effective_defaults,
                "Sem itens padrão neste tópico.",
                "Caixa de seleção",
                area,
                title,
                schema,
            )
            + render_deletable_rei_items(
                area,
                title,
                custom_only,
                schema,
                "Nenhum item personalizado neste tópico.",
            )
            + "</div>"
        )
    return "".join(blocks)


def render_survey_sections(
    default_sections: list[dict], custom_sections: list[dict], schema: dict
) -> str:
    titles: list[str] = []
    for section in default_sections + custom_sections:
        title = str(section.get("title", "")).strip()
        if title and not any(existing.lower() == title.lower() for existing in titles):
            titles.append(title)
    if not titles:
        return '<div class="empty">Nenhum campo cadastrado.</div>'
    blocks = []
    for title in titles:
        default = next(
            (
                section
                for section in default_sections
                if str(section.get("title", "")).strip().lower() == title.lower()
            ),
            {"fields": []},
        )
        custom_section = next(
            (
                section
                for section in custom_sections
                if str(section.get("title", "")).strip().lower() == title.lower()
            ),
            None,
        )
        custom = custom_section or {"fields": []}
        default_items = default.get("fields", [])
        custom_items = custom.get("fields", [])
        custom_by_key = {
            str(field.get("key", "")).strip(): field for field in custom_items
        }
        default_keys = {str(field.get("key", "")).strip() for field in default_items}
        effective_defaults = [
            custom_by_key.get(str(field.get("key", "")).strip(), field)
            for field in default_items
        ]
        custom_only = [
            field
            for field in custom_items
            if str(field.get("key", "")).strip() not in default_keys
        ]
        blocks.append(
            f'<div class="topic"><div class="topic-head"><strong>{html.escape(title)}</strong></div>'
            + render_editable_survey_fields(
                title,
                effective_defaults,
                "Padrão",
                "Sem campos padrão neste tópico.",
                False,
                schema,
            )
            + render_editable_survey_fields(
                title,
                custom_only,
                "Personalizado",
                "Nenhum campo personalizado neste tópico.",
                True,
                schema,
            )
            + "</div>"
        )
    return "".join(blocks)


def connect() -> sqlite3.Connection:
    """Abre uma conexão curta com as garantias usadas por todas as rotas."""
    connection = sqlite3.connect(DATABASE, timeout=20)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA journal_mode=WAL")
    connection.execute("PRAGMA foreign_keys=ON")
    return connection


def initialize_database() -> None:
    """Cria e evolui o banco de forma retrocompatível, sem apagar dados legados."""
    with connect() as db:
        db.executescript("""
            CREATE TABLE IF NOT EXISTS reports (
                id TEXT PRIMARY KEY,
                client TEXT NOT NULL,
                consultant TEXT NOT NULL DEFAULT '',
                started_at TEXT,
                ended_at TEXT,
                contracted_days TEXT,
                used_days TEXT,
                delivery_status TEXT NOT NULL DEFAULT '',
                services_executed TEXT NOT NULL DEFAULT '',
                pending_issues TEXT NOT NULL DEFAULT '',
                checked_items INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER NOT NULL,
                received_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_by_user_id INTEGER,
                stage TEXT NOT NULL DEFAULT '',
                owner_username TEXT NOT NULL DEFAULT '' COLLATE NOCASE,
                assigned_username TEXT NOT NULL DEFAULT '' COLLATE NOCASE,
                updated_by_username TEXT NOT NULL DEFAULT '' COLLATE NOCASE
            );
            CREATE INDEX IF NOT EXISTS idx_reports_completed_at ON reports(completed_at);
            CREATE INDEX IF NOT EXISTS idx_reports_client ON reports(client);
            CREATE INDEX IF NOT EXISTS idx_reports_delivery_status ON reports(delivery_status);

            CREATE TABLE IF NOT EXISTS report_check_items (
                report_id TEXT NOT NULL,
                item_key TEXT NOT NULL,
                PRIMARY KEY(report_id, item_key),
                FOREIGN KEY(report_id) REFERENCES reports(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS report_attachments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                report_id TEXT NOT NULL,
                name TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                device_uri TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(report_id) REFERENCES reports(id) ON DELETE CASCADE
            );
            CREATE INDEX IF NOT EXISTS idx_attachments_report ON report_attachments(report_id);

            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                full_name TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('supervisor', 'implantador')),
                active INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

            CREATE TABLE IF NOT EXISTS sessions (
                token_hash TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                expires_at TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);

            CREATE TABLE IF NOT EXISTS device_heartbeats (
                user_id INTEGER NOT NULL,
                username TEXT NOT NULL COLLATE NOCASE,
                device_id TEXT NOT NULL,
                app_version TEXT NOT NULL,
                last_seen TEXT NOT NULL,
                client_last_seen INTEGER NOT NULL,
                pending_count INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                PRIMARY KEY(user_id, device_id),
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            CREATE INDEX IF NOT EXISTS idx_device_heartbeats_seen ON device_heartbeats(last_seen DESC);
            CREATE INDEX IF NOT EXISTS idx_device_heartbeats_username ON device_heartbeats(username);
            """)
        report_columns = {row[1] for row in db.execute("PRAGMA table_info(reports)")}
        report_migrations = {
            "created_by_user_id": "INTEGER",
            "stage": "TEXT NOT NULL DEFAULT ''",
            "owner_username": "TEXT NOT NULL DEFAULT '' COLLATE NOCASE",
            "assigned_username": "TEXT NOT NULL DEFAULT '' COLLATE NOCASE",
            "updated_by_username": "TEXT NOT NULL DEFAULT '' COLLATE NOCASE",
        }
        for column, definition in report_migrations.items():
            if column not in report_columns:
                db.execute(f"ALTER TABLE reports ADD COLUMN {column} {definition}")

        for row in db.execute(
            "SELECT id, payload_json, stage, owner_username, assigned_username, updated_by_username "
            "FROM reports WHERE stage=''"
        ).fetchall():
            try:
                payload = json.loads(row["payload_json"] or "{}")
                fields = (payload.get("report") or {}).get("fields") or {}
                if not isinstance(fields, dict):
                    raise ValueError("fields não é um objeto")
                stage = str(fields.get("_stage") or "rei").strip() or "rei"
                owner = (
                    str(fields.get("_ownerUsername") or fields.get("_createdBy") or "")
                    .strip()
                    .casefold()
                )
                assigned = (
                    str(fields.get("_assignedImplantadorUsername") or "")
                    .strip()
                    .casefold()
                )
                updated_by = (
                    str(fields.get("_updatedBy") or fields.get("_createdBy") or owner)
                    .strip()
                    .casefold()
                )
                db.execute(
                    """
                    UPDATE reports SET
                        stage=CASE WHEN stage='' THEN ? ELSE stage END,
                        owner_username=CASE WHEN owner_username='' THEN ? ELSE owner_username END,
                        assigned_username=CASE WHEN assigned_username='' THEN ? ELSE assigned_username END,
                        updated_by_username=CASE WHEN updated_by_username='' THEN ? ELSE updated_by_username END
                    WHERE id=?
                    """,
                    (stage, owner, assigned, updated_by, row["id"]),
                )
            except Exception as error:
                logging.warning(
                    "Falha no backfill de propriedade do relatório %s: %s",
                    row["id"],
                    error,
                )

        db.execute("CREATE INDEX IF NOT EXISTS idx_reports_stage ON reports(stage)")
        db.execute(
            "CREATE INDEX IF NOT EXISTS idx_reports_owner_username ON reports(owner_username)"
        )
        db.execute(
            "CREATE INDEX IF NOT EXISTS idx_reports_assigned_username ON reports(assigned_username)"
        )
        db.execute(
            "CREATE INDEX IF NOT EXISTS idx_reports_created_by_user_id ON reports(created_by_user_id)"
        )


def password_hash(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 210_000)
    return f"{salt.hex()}:{digest.hex()}"


def password_valid(password: str, stored: str) -> bool:
    try:
        salt_hex, expected_hex = stored.split(":", 1)
        digest = hashlib.pbkdf2_hmac(
            "sha256", password.encode("utf-8"), bytes.fromhex(salt_hex), 210_000
        )
        return hmac.compare_digest(digest.hex(), expected_hex)
    except (ValueError, TypeError):
        return False


def create_user(username: str, full_name: str, password: str, role: str) -> int:
    username = username.strip().lower()
    full_name = full_name.strip()
    if len(username) < 3 or not username.replace("_", "").replace(".", "").isalnum():
        raise ValueError(
            "Usuário deve ter ao menos 3 caracteres e usar apenas letras, números, ponto ou sublinhado"
        )
    if len(full_name) < 3:
        raise ValueError("Nome completo é obrigatório")
    if len(password) < 8:
        raise ValueError("A senha deve ter ao menos 8 caracteres")
    if role not in {"supervisor", "implantador"}:
        raise ValueError("Perfil inválido")
    now = datetime.now(timezone.utc).isoformat()
    try:
        with connect() as db:
            cursor = db.execute(
                "INSERT INTO users(username, full_name, password_hash, role, active, created_at, updated_at) VALUES(?,?,?,?,1,?,?)",
                (username, full_name, password_hash(password), role, now, now),
            )
            return int(cursor.lastrowid)
    except sqlite3.IntegrityError as error:
        raise ValueError("Este nome de usuário já está cadastrado") from error


def authenticate(username: str, password: str) -> sqlite3.Row | None:
    with connect() as db:
        row = db.execute(
            "SELECT * FROM users WHERE username=? AND active=1",
            (username.strip().lower(),),
        ).fetchone()
    return row if row and password_valid(password, row["password_hash"]) else None


def validate_new_password(password: str, confirmation: str) -> None:
    if len(password) < 8:
        raise ValueError("A nova senha deve ter ao menos 8 caracteres")
    if password != confirmation:
        raise ValueError("A confirmação da nova senha não confere")


def change_user_password(
    user_id: int, current_password: str, new_password: str, confirmation: str
) -> None:
    validate_new_password(new_password, confirmation)
    with connect() as db:
        user = db.execute(
            "SELECT password_hash FROM users WHERE id=? AND active=1", (user_id,)
        ).fetchone()
        if not user or not password_valid(current_password, user["password_hash"]):
            raise ValueError("A senha atual está incorreta")
        db.execute(
            "UPDATE users SET password_hash=?,updated_at=? WHERE id=?",
            (
                password_hash(new_password),
                datetime.now(timezone.utc).isoformat(),
                user_id,
            ),
        )


def reset_user_password(user_id: int, new_password: str, confirmation: str) -> None:
    validate_new_password(new_password, confirmation)
    with connect() as db:
        cursor = db.execute(
            "UPDATE users SET password_hash=?,updated_at=? WHERE id=?",
            (
                password_hash(new_password),
                datetime.now(timezone.utc).isoformat(),
                user_id,
            ),
        )
        if cursor.rowcount != 1:
            raise ValueError("Usuário não encontrado")


def create_session(user_id: int) -> str:
    token = secrets.token_urlsafe(40)
    token_digest = hashlib.sha256(token.encode()).hexdigest()
    now = datetime.now(timezone.utc)
    with connect() as db:
        db.execute("DELETE FROM sessions WHERE expires_at < ?", (now.isoformat(),))
        db.execute(
            "INSERT INTO sessions(token_hash, user_id, expires_at, created_at) VALUES(?,?,?,?)",
            (
                token_digest,
                user_id,
                (now + timedelta(days=30)).isoformat(),
                now.isoformat(),
            ),
        )
    return token


def user_from_token(token: str) -> dict | None:
    if not token:
        return None
    digest = hashlib.sha256(token.encode()).hexdigest()
    now = datetime.now(timezone.utc).isoformat()
    with connect() as db:
        row = db.execute(
            "SELECT u.id, u.username, u.full_name, u.role, u.active FROM sessions s "
            "JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>? AND u.active=1",
            (digest, now),
        ).fetchone()
    return dict(row) if row else None


def revoke_session(token: str) -> None:
    if token:
        with connect() as db:
            db.execute(
                "DELETE FROM sessions WHERE token_hash=?",
                (hashlib.sha256(token.encode()).hexdigest(),),
            )


def users_count() -> int:
    with connect() as db:
        return int(db.execute("SELECT COUNT(*) FROM users").fetchone()[0])


def save_device_heartbeat(payload: dict, user: dict) -> dict:
    username = str(payload.get("username") or "").strip().casefold()
    if username != str(user.get("username") or "").strip().casefold():
        raise ReportWriteRejected(
            403,
            "heartbeat_identity_mismatch",
            "O usuário do dispositivo não corresponde ao usuário autenticado.",
        )
    device_id = str(payload.get("deviceId") or "").strip()
    if not re.fullmatch(r"[A-Za-z0-9._:-]{8,128}", device_id):
        raise ReportWriteRejected(
            422, "invalid_device_id", "Identificador do dispositivo inválido."
        )
    app_version = str(payload.get("appVersion") or "").strip()
    if not app_version or len(app_version) > 100:
        raise ReportWriteRejected(
            422, "invalid_app_version", "Versão do aplicativo inválida."
        )
    try:
        client_last_seen = int(payload.get("lastSeen") or 0)
        pending_count = int(payload.get("pendingCount") or 0)
    except (TypeError, ValueError):
        raise ReportWriteRejected(
            422, "invalid_heartbeat", "Dados numéricos do diagnóstico são inválidos."
        )
    if client_last_seen <= 0 or pending_count < 0 or pending_count > 1_000_000:
        raise ReportWriteRejected(
            422, "invalid_heartbeat", "Situação de sincronização inválida."
        )
    raw_error = payload.get("lastError")
    if raw_error is not None and not isinstance(raw_error, str):
        raise ReportWriteRejected(
            422, "invalid_heartbeat", "Mensagem de erro inválida."
        )
    last_error = str(raw_error or "").strip()[:500] or None
    last_seen = datetime.now(timezone.utc).isoformat()
    with connect() as db:
        db.execute(
            """
            INSERT INTO device_heartbeats(
                user_id, username, device_id, app_version, last_seen,
                client_last_seen, pending_count, last_error
            ) VALUES(?,?,?,?,?,?,?,?)
            ON CONFLICT(user_id, device_id) DO UPDATE SET
                username=excluded.username,
                app_version=excluded.app_version,
                last_seen=excluded.last_seen,
                client_last_seen=excluded.client_last_seen,
                pending_count=excluded.pending_count,
                last_error=excluded.last_error
            """,
            (
                int(user["id"]),
                username,
                device_id,
                app_version,
                last_seen,
                client_last_seen,
                pending_count,
                last_error,
            ),
        )
    return {
        "username": username,
        "deviceId": device_id,
        "appVersion": app_version,
        "lastSeen": last_seen,
        "pendingCount": pending_count,
        "lastError": last_error,
    }


def list_device_heartbeats(user: dict) -> list[dict]:
    where = "" if user.get("role") == "supervisor" else "WHERE d.user_id=?"
    params: tuple = () if not where else (int(user["id"]),)
    with connect() as db:
        rows = db.execute(
            f"""
            SELECT d.username, d.device_id, d.app_version, d.last_seen,
                   d.pending_count, d.last_error
            FROM device_heartbeats d
            {where}
            ORDER BY d.last_seen DESC, d.username, d.device_id
            """,
            params,
        ).fetchall()
    return [
        {
            "username": row["username"],
            "deviceId": row["device_id"],
            "appVersion": row["app_version"],
            "lastSeen": row["last_seen"],
            "pendingCount": row["pending_count"],
            "lastError": row["last_error"],
        }
        for row in rows
    ]


def list_users(role: str | None = None) -> list[dict]:
    where = "WHERE active=1"
    params: list[object] = []
    if role in {"supervisor", "implantador"}:
        where += " AND role=?"
        params.append(role)
    with connect() as db:
        return [
            dict(row)
            for row in db.execute(
                f"SELECT id, username, full_name, role FROM users {where} ORDER BY full_name, username",
                params,
            )
        ]


def report_value(report: dict, key: str) -> object:
    if key == "deliveryStatus":
        return report.get("deliveryStatus") or ""
    if key == "rating":
        return report.get("rating") or ""
    return report_fields(report).get(key) or ""


def definition_keys(definition: dict) -> list[str]:
    return list(
        dict.fromkeys(
            [
                str(definition.get("key") or "").strip(),
                *[
                    str(key).strip()
                    for key in definition.get("legacyKeys", [])
                    if str(key).strip()
                ],
            ]
        )
    )


def schema_definitions_by_key(schema: dict) -> dict[str, dict]:
    result: dict[str, dict] = {}
    for _area, _topic, item in iter_rei_items(schema):
        for key in definition_keys(item):
            result[key] = item
    return result


def checked_requirement(report: dict, key: str, schema: dict) -> bool:
    checks = {str(item) for item in report.get("checks", [])}
    definition = schema_definitions_by_key(schema).get(key)
    keys = definition_keys(definition) if definition else [key]
    return any(candidate in checks for candidate in keys)


def condition_value(report: dict, condition_item: dict, schema: dict) -> object:
    source = str(condition_item.get("source") or "")
    key = str(condition_item.get("key") or "")
    if source in {"module", "checklist"}:
        return checked_requirement(report, key, schema)
    return report_value(report, key)


def evaluate_condition(report: dict, condition_item: dict, schema: dict) -> bool:
    if "match" in condition_item and not condition_item.get("source"):
        return evaluate_required_when(report, condition_item, schema)
    source = str(condition_item.get("source") or "")
    operator = str(condition_item.get("operator") or "")
    value = condition_value(report, condition_item, schema)
    expected = condition_item.get("value") or ""
    if source in {"module", "checklist"}:
        return bool(value) if operator == "checked" else not bool(value)
    normalized = normalized_comparison_text(value)
    normalized_expected = normalized_comparison_text(expected)
    if operator == "equals":
        return normalized == normalized_expected
    if operator == "not_equals":
        return normalized != normalized_expected
    if operator == "not_blank":
        return bool(str(value or "").strip())
    if operator == "blank":
        return not str(value or "").strip()
    if operator == "greater_than":
        try:
            return Decimal(str(value).replace(",", ".")) > Decimal(
                str(expected).replace(",", ".")
            )
        except (InvalidOperation, ValueError):
            return False
    return False


def evaluate_required_when(report: dict, rule: dict | None, schema: dict) -> bool:
    if not isinstance(rule, dict):
        return False
    results = [
        evaluate_condition(report, item, schema)
        for item in rule.get("conditions", [])
        if isinstance(item, dict)
    ]
    if not results:
        return False
    return all(results) if rule.get("match") == "all" else any(results)


def condition_labels(schema: dict) -> dict[str, str]:
    labels: dict[str, str] = {}
    for _area, _topic, item in iter_rei_items(schema):
        labels[str(item.get("key") or "")] = str(item.get("label") or "")
    for section in schema.get("levantamento", []):
        for field in section.get("fields", []):
            labels[str(field.get("key") or "")] = str(field.get("label") or "")
    for items in schema.get("validation", {}).get("fixed", {}).values():
        for item in items:
            labels[str(item.get("key") or "")] = str(item.get("label") or "")
    labels.update(
        {
            "qtdWorkflow": "Quantidade de Workflow",
            "geralWorkflow": "Workflow",
            "geralCustomizacao": "Customização",
        }
    )
    return labels


def condition_summary(rule: dict | None, schema: dict) -> str:
    if not isinstance(rule, dict):
        return "Condição configurada"
    labels = condition_labels(schema)
    operator_labels = {
        "checked": "está marcado",
        "not_checked": "não está marcado",
        "equals": "é igual a",
        "not_equals": "é diferente de",
        "not_blank": "está preenchido",
        "blank": "está vazio",
        "greater_than": "é maior que",
    }
    parts: list[str] = []
    for item in rule.get("conditions", []):
        if not isinstance(item, dict):
            continue
        if "match" in item and not item.get("source"):
            parts.append(f"({condition_summary(item, schema)})")
            continue
        key = str(item.get("key") or "")
        text = f"{labels.get(key, key)} {operator_labels.get(str(item.get('operator') or ''), '')}".strip()
        if item.get("operator") in {"equals", "not_equals", "greater_than"}:
            text += f" {item.get('value', '')}"
        parts.append(text)
    connector = " e " if rule.get("match") == "all" else " ou "
    return connector.join(parts) or "Condição configurada"


def requirement_active(report: dict, definition: dict, schema: dict) -> bool:
    mode = str(definition.get("requiredMode") or "never")
    if mode == "always":
        return True
    if mode == "conditional":
        return evaluate_required_when(report, definition.get("requiredWhen"), schema)
    return False


def valid_image_or_uri(value: object) -> bool:
    text = str(value or "").strip()
    if not text:
        return False
    if text.startswith("data:image/") and "," in text:
        return True
    parsed = urlparse(text)
    return parsed.scheme.lower() in {"content", "file", "http", "https"}


def requirement_fulfilled(report: dict, definition: dict, schema: dict) -> bool:
    field_type = str(definition.get("type") or "text").lower()
    if field_type == "checkbox":
        return any(
            checked_requirement(report, key, schema) for key in definition_keys(definition)
        )
    values = [report_value(report, key) for key in definition_keys(definition)]
    if definition.get("key") == "empresa":
        values.append(report_value(report, "cliente"))
    value = next((candidate for candidate in values if str(candidate or "").strip()), "")
    if field_type == "choice":
        options = {
            normalized_comparison_text(option) for option in definition.get("options", [])
        }
        return bool(value) and (
            not options or normalized_comparison_text(value) in options
        )
    if field_type == "photo":
        return valid_image_or_uri(value)
    return bool(str(value or "").strip())


def phase_requirements(schema: dict, phase: str):
    if phase in {"survey_completion", "rei_completion"}:
        for section in schema.get("levantamento", []):
            for field in section.get("fields", []):
                yield str(section.get("title") or "Levantamento"), field
    if phase == "rei_completion":
        for area in ("technical", "stock", "finance", "fiscal"):
            section_label = REI_ITEM_AREAS.get(area, area)
            for group in schema.get("rei", {}).get(area, []):
                for item in group.get("items", []):
                    yield f"{section_label} · {group.get('title', '')}", item
    if phase == "supervision_submission":
        for group in schema.get("rei", {}).get("supervision", []):
            for item in group.get("items", []):
                yield f"Supervisão · {group.get('title', '')}", item
    for definition in schema.get("validation", {}).get("fixed", {}).get(phase, []):
        yield str(definition.get("section") or "Relatório"), definition


def validate_required_requirements(report: dict, schema: dict, phase: str) -> list[dict]:
    """Valida uma fase sem acessar banco, sessão ou dados enviados pelo cliente."""
    errors: list[dict] = []
    seen: set[str] = set()
    for section, definition in phase_requirements(schema, phase):
        key = str(definition.get("key") or "")
        if not key or key in seen or not requirement_active(report, definition, schema):
            continue
        seen.add(key)
        if requirement_fulfilled(report, definition, schema):
            continue
        mode = str(definition.get("requiredMode") or "never")
        errors.append(
            {
                "key": key,
                "label": str(definition.get("label") or key),
                "section": section,
                "phase": phase,
                "reason": (
                    "Marque este item obrigatório."
                    if definition.get("type") == "checkbox"
                    else "Preencha este campo obrigatório."
                ),
                "requiredBecause": (
                    condition_summary(definition.get("requiredWhen"), schema)
                    if mode == "conditional"
                    else "Obrigatório para concluir esta etapa."
                ),
            }
        )
    return errors


def validation_snapshot(report: dict, schema: dict, phase: str) -> str:
    active = [
        definition
        for _section, definition in phase_requirements(schema, phase)
        if requirement_active(report, definition, schema)
    ]
    required_keys = list(dict.fromkeys(str(item.get("key") or "") for item in active))
    fulfilled = [
        str(item.get("key") or "")
        for item in active
        if requirement_fulfilled(report, item, schema)
    ]
    return json.dumps(
        {
            "schemaVersion": schema.get("schemaVersion") or SCHEMA_RULE_VERSION,
            "validatedAt": datetime.now(timezone.utc).isoformat(),
            "phase": phase,
            "requiredKeys": required_keys,
            "fulfilledKeys": list(dict.fromkeys(fulfilled)),
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )


class ReportWriteRejected(ValueError):
    def __init__(
        self, status: int, code: str, message: str, details: list[dict] | None = None
    ):
        super().__init__(message)
        self.status = status
        self.code = code
        self.details = details or []


def reject_report(
    status: int,
    code: str,
    message: str,
    details: list[dict] | None = None,
) -> None:
    raise ReportWriteRejected(status, code, message, details)


def report_fields(report: dict) -> dict:
    fields = report.get("fields")
    return fields if isinstance(fields, dict) else {}


def report_stage(report: dict) -> str:
    return str(report_fields(report).get("_stage") or "rei").strip() or "rei"


def concluded_delivery(report: dict) -> bool:
    return (
        str(report.get("deliveryStatus") or "").strip().casefold().startswith("conclu")
    )


def supervision_field(key: object) -> bool:
    value = str(key or "")
    return (
        value == "_supervisorName"
        or value.startswith("_supervision")
        or value.startswith("reiField::supervisao::")
    )


def supervision_check(value: object) -> bool:
    return str(value or "").startswith("supervisao::")


def supervision_snapshot(report: dict) -> dict:
    fields = report_fields(report)
    return {
        "fields": {
            key: fields[key] for key in sorted(fields) if supervision_field(key)
        },
        "checks": sorted(
            {
                str(item)
                for item in (report.get("checks") or [])
                if supervision_check(item)
            }
        ),
        "rating": str(report.get("rating") or ""),
    }


def without_supervision(report: dict) -> dict:
    clean = json.loads(json.dumps(report, ensure_ascii=False))
    fields = report_fields(clean)
    clean["fields"] = {
        key: value for key, value in fields.items() if not supervision_field(key)
    }
    clean["checks"] = [
        item for item in (clean.get("checks") or []) if not supervision_check(item)
    ]
    clean["rating"] = ""
    return clean


def evaluation_present(report: dict) -> bool:
    snapshot = supervision_snapshot(report)
    return bool(
        snapshot["checks"]
        or snapshot["rating"].strip()
        or any(str(value).strip() for value in snapshot["fields"].values())
    )


def canonical(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def actor_names(user: dict) -> set[str]:
    return {
        str(user.get("username") or "").strip().casefold(),
        str(user.get("full_name") or "").strip().casefold(),
    } - {""}


def validate_evaluation(report: dict, user: dict) -> None:
    fields = report_fields(report)
    score_text = str(fields.get("_supervisionScore") or "").strip().replace(",", ".")
    try:
        score = float(score_text)
    except ValueError:
        reject_report(
            422,
            "invalid_evaluation",
            "A nota da supervisão deve ser informada entre 0 e 10.",
        )
    if score < 0 or score > 10:
        reject_report(
            422, "invalid_evaluation", "A nota da supervisão deve estar entre 0 e 10."
        )
    if not str(fields.get("_supervisionReviewedAt") or "").strip():
        reject_report(
            422,
            "invalid_evaluation",
            "A data da avaliação da supervisão é obrigatória.",
        )
    supervisor_name = str(fields.get("_supervisorName") or "").strip().casefold()
    if supervisor_name not in actor_names(user):
        reject_report(
            422,
            "invalid_evaluation",
            "O supervisor informado não corresponde ao usuário autenticado.",
        )


def ownership_values(fields: dict) -> dict[str, str]:
    keys = ("_createdBy", "_ownerUsername", "_assignedImplantadorUsername")
    return {key: str(fields.get(key) or "").strip().casefold() for key in keys}


def validate_assigned_implantador(db: sqlite3.Connection, fields: dict) -> None:
    assigned = str(fields.get("_assignedImplantadorUsername") or "").strip().casefold()
    if not assigned:
        reject_report(
            422,
            "invalid_assignment",
            "Selecione um implantador responsável pelo levantamento.",
        )
    found = db.execute(
        "SELECT 1 FROM users WHERE username=? AND role='implantador' AND active=1",
        (assigned,),
    ).fetchone()
    if not found:
        reject_report(
            422,
            "invalid_assignment",
            "O responsável informado não é um implantador ativo.",
        )


def validate_supervisor_client_update(
    current_report: dict, received_report: dict
) -> None:
    allowed_fields = {
        "cliente",
        "empresa",
        "contato",
        "telefone",
        "email",
        "cnpj",
        "inscricaoEstadual",
        "_assignedImplantadorUsername",
        "_assignedImplantadorName",
    }
    current_fields = report_fields(current_report)
    received_fields = report_fields(received_report)
    changed_fields = {
        key
        for key in set(current_fields) | set(received_fields)
        if canonical(current_fields.get(key)) != canonical(received_fields.get(key))
    }
    if changed_fields - allowed_fields:
        reject_report(
            403,
            "permission_denied",
            "Supervisor pode alterar somente os dados básicos e o responsável pelo levantamento.",
        )
    current_body = {
        key: value for key, value in current_report.items() if key != "fields"
    }
    received_body = {
        key: value for key, value in received_report.items() if key != "fields"
    }
    if canonical(current_body) != canonical(received_body):
        reject_report(
            403,
            "permission_denied",
            "Supervisor não pode alterar o conteúdo preenchido no levantamento.",
        )


def validate_report_write(
    db: sqlite3.Connection,
    current: sqlite3.Row | None,
    received_report: dict,
    user: dict | None,
    trusted_api_key: bool = False,
) -> None:
    """Aplica no servidor as mesmas regras de autoria e transição vistas nas interfaces."""
    if trusted_api_key:
        return
    if not user:
        reject_report(
            403,
            "permission_denied",
            "Usuário sem permissão para salvar este relatório.",
        )

    role = str(user.get("role") or "")
    username = str(user.get("username") or "").strip().casefold()
    received_fields = report_fields(received_report)
    received_stage = report_stage(received_report)
    valid_stages = {"levantamento_pendente", "rei_pendente", "rei"}
    if received_stage not in valid_stages:
        reject_report(422, "invalid_stage", "Estágio do relatório inválido.")

    received_evaluation = evaluation_present(received_report)
    received_owners = ownership_values(received_fields)
    if current is None:
        if received_stage == "rei_pendente":
            reject_report(
                422,
                "invalid_transition",
                "Um levantamento deve ser criado como pendente antes de ser concluído.",
            )
        if received_evaluation:
            reject_report(
                422,
                "invalid_evaluation",
                "Não é possível avaliar um relatório que ainda não foi entregue.",
            )
        if role == "supervisor" and received_stage != "levantamento_pendente":
            reject_report(
                403,
                "permission_denied",
                "Supervisor não pode criar uma implantação em nome do implantador.",
            )
        if role == "supervisor":
            validate_assigned_implantador(db, received_fields)
        if role == "implantador":
            other_owner = next(
                (
                    value
                    for value in received_owners.values()
                    if value and value != username
                ),
                "",
            )
            if other_owner:
                reject_report(
                    403,
                    "permission_denied",
                    "Implantador não pode criar relatório para outro responsável.",
                )
        return

    try:
        current_payload = json.loads(current["payload_json"] or "{}")
    except (TypeError, json.JSONDecodeError):
        reject_report(
            409,
            "invalid_current_state",
            "O relatório salvo possui um estado inválido e precisa ser revisado.",
        )
    current_report = current_payload.get("report") or {}
    current_fields = report_fields(current_report)
    current_stage = report_stage(current_report)
    if current_stage not in valid_stages:
        reject_report(
            409,
            "invalid_current_state",
            "O relatório salvo possui um estágio inválido.",
        )

    allowed_transitions = {
        "levantamento_pendente": {"levantamento_pendente", "rei_pendente"},
        "rei_pendente": {"rei_pendente", "rei"},
        "rei": {"rei"},
    }
    if received_stage not in allowed_transitions[current_stage]:
        stage_order = {"levantamento_pendente": 0, "rei_pendente": 1, "rei": 2}
        if stage_order[received_stage] < stage_order[current_stage]:
            reject_report(
                409,
                "state_conflict",
                f"O relatório não pode retornar de {current_stage} para {received_stage}.",
            )
        reject_report(
            422,
            "invalid_transition",
            f"Transição não permitida: {current_stage} para {received_stage}.",
        )
    if (
        current_stage == "levantamento_pendente"
        and received_stage == "rei_pendente"
        and not str(received_fields.get("_surveyCompletedAt") or "").strip()
    ):
        reject_report(
            422,
            "invalid_transition",
            "A conclusão do levantamento exige a data de conclusão.",
        )
    if (
        current_stage == "rei_pendente"
        and received_stage == "rei_pendente"
        and canonical(current_report) != canonical(received_report)
    ):
        reject_report(
            409, "survey_completed", "Levantamento concluído não pode mais ser editado."
        )
    if current_stage == "rei_pendente" and received_stage == "rei":
        transition_fields = {"_stage", "_ownerUsername"}
        changed_survey_fields = {
            key
            for key, value in current_fields.items()
            if key not in transition_fields
            and canonical(received_fields.get(key)) != canonical(value)
        }
        if changed_survey_fields:
            reject_report(
                409,
                "survey_completed",
                "As respostas do levantamento concluído não podem ser alteradas ao iniciar o R.E.I.",
            )
    if concluded_delivery(current_report) and not concluded_delivery(received_report):
        reject_report(
            409,
            "report_already_completed",
            "Relatório concluído não pode voltar para pendente ou não concluído.",
        )

    current_supervision = supervision_snapshot(current_report)
    received_supervision = supervision_snapshot(received_report)
    supervision_changed = canonical(current_supervision) != canonical(
        received_supervision
    )
    current_evaluation = evaluation_present(current_report)

    created_by = str(current["created_by_username"] or "").strip().casefold()
    current_owners = ownership_values(current_fields)
    responsible = {created_by, *current_owners.values()} - {""}

    if role == "implantador":
        if username not in responsible:
            reject_report(
                403,
                "not_report_owner",
                "Implantador não pode alterar relatório de outro implantador.",
            )
        for key, old_value in current_owners.items():
            new_value = received_owners[key]
            if old_value and new_value != old_value:
                reject_report(
                    403,
                    "ownership_change_denied",
                    "Implantador não pode alterar o responsável pelo relatório.",
                )
            if not old_value and new_value and new_value != username:
                reject_report(
                    403,
                    "ownership_change_denied",
                    "Implantador não pode atribuir o relatório a outro usuário.",
                )
        if supervision_changed:
            reject_report(
                403,
                "supervision_only",
                "Somente supervisor pode alterar os campos de avaliação.",
            )
        return

    if role != "supervisor":
        reject_report(
            403, "permission_denied", "Perfil sem permissão para salvar relatórios."
        )

    if current_stage == "levantamento_pendente":
        if received_stage != current_stage:
            reject_report(
                403,
                "permission_denied",
                "Somente o implantador responsável pode concluir o levantamento.",
            )
        if supervision_changed:
            reject_report(
                422,
                "invalid_evaluation",
                "Levantamento pendente não pode receber avaliação.",
            )
        validate_assigned_implantador(db, received_fields)
        validate_supervisor_client_update(current_report, received_report)
        return

    if not supervision_changed:
        if canonical(current_report) == canonical(received_report):
            return
        reject_report(
            403,
            "permission_denied",
            "Supervisor não pode alterar o conteúdo técnico da implantação.",
        )

    if current_evaluation:
        reject_report(
            409,
            "evaluation_locked",
            "A avaliação já foi enviada e não pode ser substituída.",
        )
    if current_stage != "rei" or not concluded_delivery(current_report):
        reject_report(
            422,
            "invalid_evaluation",
            "Somente implantação concluída pode ser avaliada.",
        )
    if canonical(without_supervision(current_report)) != canonical(
        without_supervision(received_report)
    ):
        reject_report(
            403,
            "permission_denied",
            "A avaliação não pode alterar o conteúdo da implantação.",
        )
    validate_evaluation(received_report, user)


def saved_report_from_row(current: sqlite3.Row | None) -> dict:
    if current is None:
        return {}
    try:
        payload = json.loads(current["payload_json"] or "{}")
    except (TypeError, json.JSONDecodeError):
        return {}
    report = payload.get("report")
    return report if isinstance(report, dict) else {}


def preserve_server_validation_snapshot(current_report: dict, received_report: dict) -> None:
    fields = report_fields(received_report)
    current_snapshot = report_fields(current_report).get("_requiredValidationSnapshot")
    fields.pop("_requiredValidationSnapshot", None)
    if current_snapshot:
        fields["_requiredValidationSnapshot"] = current_snapshot


def attempted_validation_phases(current_report: dict, received_report: dict) -> list[str]:
    phases: list[str] = []
    current_stage = report_stage(current_report) if current_report else ""
    received_stage = report_stage(received_report)
    if current_stage == "levantamento_pendente" and received_stage == "rei_pendente":
        phases.append("survey_completion")
    if concluded_delivery(received_report) and not concluded_delivery(current_report):
        phases.append("rei_completion")
    if evaluation_present(received_report) and not evaluation_present(current_report):
        phases.append("supervision_submission")
    return phases


def apply_required_validation(
    current_report: dict, received_report: dict, schema: dict
) -> None:
    for phase in attempted_validation_phases(current_report, received_report):
        missing = validate_required_requirements(received_report, schema, phase)
        if missing:
            reject_report(
                422,
                "required_items_missing",
                f"Existem {len(missing)} requisito(s) obrigatório(s) pendente(s).",
                missing,
            )
        if phase in {"survey_completion", "rei_completion"}:
            report_fields(received_report)["_requiredValidationSnapshot"] = (
                validation_snapshot(received_report, schema, phase)
            )


def save_report(
    payload: dict, user: dict | None = None, trusted_api_key: bool = False
) -> str:
    report_id = str(payload.get("reportId") or "").strip()
    report = payload.get("report") or {}
    if not isinstance(report, dict):
        reject_report(422, "invalid_content", "Estrutura do relatório inválida.")
    fields = report.get("fields") or {}
    if not isinstance(fields, dict):
        reject_report(
            422, "invalid_content", "Estrutura dos campos do relatório inválida."
        )
    checks_value = report.get("checks") or []
    if not isinstance(checks_value, list) or any(
        not isinstance(item, str) for item in checks_value
    ):
        reject_report(
            422, "invalid_content", "Estrutura do checklist do relatório inválida."
        )
    attachments_value = report.get("attachments") or []
    if not isinstance(attachments_value, list) or any(
        not isinstance(item, dict) for item in attachments_value
    ):
        reject_report(
            422, "invalid_content", "Estrutura dos anexos do relatório inválida."
        )
    if not report_id:
        reject_report(422, "invalid_content", "reportId é obrigatório.")
    now = datetime.now(timezone.utc).isoformat()
    try:
        completed_at = int(payload.get("completedAt") or 0)
    except (TypeError, ValueError):
        reject_report(
            422, "invalid_content", "Data de conclusão do relatório inválida."
        )
    checks = list(dict.fromkeys(checks_value))
    attachments = attachments_value
    report["checks"] = checks
    report["attachments"] = attachments

    with connect() as db:
        db.execute("BEGIN IMMEDIATE")
        current = db.execute(
            "SELECT r.payload_json, u.username AS created_by_username "
            "FROM reports r LEFT JOIN users u ON u.id=r.created_by_user_id WHERE r.id=?",
            (report_id,),
        ).fetchone()
        current_report = saved_report_from_row(current)
        preserve_server_validation_snapshot(current_report, report)
        validate_report_write(db, current, report, user, trusted_api_key)
        schema = load_effective_schema_items()
        apply_required_validation(current_report, report, schema)
        fields = report_fields(report)
        client = str(fields.get("cliente") or fields.get("empresa") or "").strip()
        stage = report_stage(report)
        owner_username = (
            str(fields.get("_ownerUsername") or fields.get("_createdBy") or "")
            .strip()
            .casefold()
        )
        assigned_username = (
            str(fields.get("_assignedImplantadorUsername") or "").strip().casefold()
        )
        updated_by_username = (
            str((user or {}).get("username") or ("api" if trusted_api_key else ""))
            .strip()
            .casefold()
        )
        payload["report"] = report
        raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
        created_by_user_id = (
            int(user["id"]) if user and user.get("id") is not None else None
        )
        db.execute(
            """
            INSERT INTO reports (
                id, client, consultant, started_at, ended_at, contracted_days,
                used_days, delivery_status, services_executed, pending_issues,
                checked_items, completed_at, received_at, updated_at, payload_json, created_by_user_id,
                stage, owner_username, assigned_username, updated_by_username
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                client=excluded.client,
                consultant=excluded.consultant,
                started_at=excluded.started_at,
                ended_at=excluded.ended_at,
                contracted_days=excluded.contracted_days,
                used_days=excluded.used_days,
                delivery_status=excluded.delivery_status,
                services_executed=excluded.services_executed,
                pending_issues=excluded.pending_issues,
                checked_items=excluded.checked_items,
                completed_at=excluded.completed_at,
                updated_at=excluded.updated_at,
                payload_json=excluded.payload_json,
                created_by_user_id=COALESCE(reports.created_by_user_id, excluded.created_by_user_id),
                stage=excluded.stage,
                owner_username=excluded.owner_username,
                assigned_username=excluded.assigned_username,
                updated_by_username=excluded.updated_by_username
            """,
            (
                report_id,
                client,
                str(fields.get("consultor") or ""),
                fields.get("inicio"),
                fields.get("termino"),
                fields.get("diasContratados"),
                fields.get("diasUtilizados"),
                str(report.get("deliveryStatus") or ""),
                str(fields.get("servicosExecutados") or ""),
                str(fields.get("pendencias") or ""),
                len(checks),
                completed_at,
                now,
                now,
                raw,
                created_by_user_id,
                stage,
                owner_username,
                assigned_username,
                updated_by_username,
            ),
        )
        db.execute("DELETE FROM report_check_items WHERE report_id=?", (report_id,))
        db.executemany(
            "INSERT INTO report_check_items(report_id, item_key) VALUES (?, ?)",
            [(report_id, str(item)) for item in checks],
        )
        db.execute("DELETE FROM report_attachments WHERE report_id=?", (report_id,))
        db.executemany(
            "INSERT INTO report_attachments(report_id, name, mime_type, device_uri) VALUES (?, ?, ?, ?)",
            [
                (
                    report_id,
                    str(item.get("name") or "Arquivo"),
                    str(item.get("mimeType") or "application/octet-stream"),
                    str(item.get("uri") or ""),
                )
                for item in attachments
            ],
        )
    return report_id


def reports_csv() -> bytes:
    output = io.StringIO()
    columns = [
        "id",
        "client",
        "consultant",
        "started_at",
        "ended_at",
        "contracted_days",
        "used_days",
        "delivery_status",
        "checked_items",
        "completed_at",
        "received_at",
        "created_by_username",
        "created_by_name",
    ]
    writer = csv.DictWriter(output, fieldnames=columns)
    writer.writeheader()
    with connect() as db:
        for row in db.execute(
            "SELECT r.id, r.client, r.consultant, r.started_at, r.ended_at, r.contracted_days, r.used_days, "
            "r.delivery_status, r.checked_items, r.completed_at, r.received_at, "
            "u.username AS created_by_username, u.full_name AS created_by_name "
            "FROM reports r LEFT JOIN users u ON u.id=r.created_by_user_id ORDER BY r.completed_at DESC"
        ):
            writer.writerow(dict(row))
    return output.getvalue().encode("utf-8-sig")


def list_reports_for_user(
    user: dict, limit: int = 100, full: bool = False
) -> list[dict]:
    limit = min(max(limit, 1), 1000)
    select_payload = ",r.payload_json" if full else ""
    where = ""
    params: list[object] = []
    if user["role"] != "supervisor":
        username = str(user["username"]).strip().casefold()
        where = (
            "WHERE (r.created_by_user_id=? OR r.owner_username=? COLLATE NOCASE "
            "OR r.assigned_username=? COLLATE NOCASE) "
        )
        params.extend((int(user["id"]), username, username))
    params.append(limit)
    with connect() as db:
        rows = []
        for row in db.execute(
            "SELECT r.id,r.client,r.consultant,r.started_at,r.ended_at,r.delivery_status,r.checked_items,"
            "r.completed_at,r.received_at,u.username AS created_by_username,u.full_name AS created_by_name "
            f"{select_payload} FROM reports r LEFT JOIN users u ON u.id=r.created_by_user_id "
            f"{where}ORDER BY r.completed_at DESC LIMIT ?",
            params,
        ):
            item = dict(row)
            if full:
                payload = json.loads(item.pop("payload_json") or "{}")
                item["payload"] = payload
                item["report"] = payload.get("report") or {}
            rows.append(item)
    return rows


DASHBOARD_STAGE_LABELS = {
    "levantamento_pendente": "Levantamentos pendentes",
    "rei_pendente": "R.E.I. pendentes",
    "rei": "Implantações",
}


def parse_dashboard_date(value: object) -> datetime | None:
    text = str(value or "").strip()
    if not text:
        return None
    for pattern in ("%d/%m/%Y", "%Y-%m-%d"):
        try:
            return datetime.strptime(text[:10], pattern).replace(tzinfo=timezone.utc)
        except ValueError:
            pass
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
        return parsed.replace(tzinfo=parsed.tzinfo or timezone.utc).astimezone(
            timezone.utc
        )
    except ValueError:
        return None


def supervisor_dashboard(query: dict[str, list[str]]) -> dict:
    now = datetime.now(timezone.utc)
    today = now.date()
    try:
        stale_days = min(max(int(query.get("staleDays", ["7"])[0]), 1), 365)
    except ValueError:
        stale_days = 7
    period_value = query.get("period", ["90"])[0]
    period_days = (
        None
        if period_value == "all"
        else min(max(int(period_value) if period_value.isdigit() else 90, 1), 3650)
    )
    implantador = str(query.get("implantador", [""])[0]).strip().casefold()
    stage_filter = str(query.get("stage", [""])[0]).strip()
    overdue_only = query.get("overdue", ["0"])[0] in {"1", "true", "yes"}
    blockers_only = query.get("blockers", ["0"])[0] in {"1", "true", "yes"}

    where = ["1=1"]
    params: list[object] = []
    responsible_sql = (
        "LOWER(COALESCE(NULLIF(r.assigned_username,''),NULLIF(r.owner_username,''),"
        "CASE WHEN creator.role='implantador' THEN creator.username ELSE '' END))"
    )
    if period_days is not None:
        where.append("r.completed_at>=?")
        params.append(int((now - timedelta(days=period_days)).timestamp() * 1000))
    if stage_filter in DASHBOARD_STAGE_LABELS:
        where.append("r.stage=?")
        params.append(stage_filter)
    if implantador:
        where.append(f"{responsible_sql}=?")
        params.append(implantador)
    if blockers_only:
        where.append("TRIM(r.pending_issues)<>''")

    with connect() as db:
        users = [
            dict(row)
            for row in db.execute(
                "SELECT username,full_name FROM users WHERE role='implantador' AND active=1 ORDER BY full_name,username"
            )
        ]
        rows = db.execute(
            """
            SELECT r.id,r.client,r.stage,r.owner_username,r.assigned_username,r.updated_by_username,
                   r.started_at,r.ended_at,r.delivery_status,r.pending_issues,r.completed_at,
                   r.received_at,r.updated_at,r.payload_json,
                   creator.username AS creator_username,creator.role AS creator_role
            FROM reports r
            LEFT JOIN users creator ON creator.id=r.created_by_user_id
            WHERE """ + " AND ".join(where) + " ORDER BY r.updated_at DESC",
            params,
        ).fetchall()
        heartbeat_rows = db.execute(
            "SELECT username,device_id,app_version,last_seen,pending_count,last_error "
            "FROM device_heartbeats ORDER BY last_seen DESC"
        ).fetchall()

    user_names = {item["username"].casefold(): item["full_name"] for item in users}
    workload = {
        item["username"].casefold(): {
            "username": item["username"],
            "fullName": item["full_name"],
            "active": 0,
            "overdue": 0,
            "stale": 0,
            "blockers": 0,
            "pendingEvaluations": 0,
            "concludedMonth": 0,
            "lastSync": None,
            "pendingSync": 0,
            "syncErrors": 0,
        }
        for item in users
        if not implantador or item["username"].casefold() == implantador
    }
    stage_totals: dict[str, int] = {key: 0 for key in DASHBOARD_STAGE_LABELS}
    overdue_records: list[dict] = []
    stale_records: list[dict] = []
    pending_evaluations: list[dict] = []
    blocker_records: list[dict] = []
    duration_values: list[int] = []
    scores: list[float] = []
    concluded_month = 0

    def responsible_username(row: sqlite3.Row) -> str:
        assigned = str(row["assigned_username"] or "").strip().casefold()
        owner = str(row["owner_username"] or "").strip().casefold()
        creator = (
            str(row["creator_username"] or "").strip().casefold()
            if row["creator_role"] == "implantador"
            else ""
        )
        return assigned or owner or creator

    for row in rows:
        try:
            payload = json.loads(row["payload_json"] or "{}")
            report = payload.get("report") or {}
            fields = report_fields(report)
        except Exception as error:
            logging.warning(
                "Dashboard ignorou campos inválidos do relatório %s: %s",
                row["id"],
                error,
            )
            report, fields = {}, {}
        status = str(row["delivery_status"] or "").strip()
        status_folded = status.casefold()
        cancelled = status_folded.startswith("cancel")
        concluded = status_folded.startswith("conclu") and not cancelled
        responsible = responsible_username(row)
        scheduled = parse_dashboard_date(fields.get("_surveyScheduledAt"))
        deadline = (
            scheduled
            if row["stage"] == "levantamento_pendente"
            else parse_dashboard_date(row["ended_at"])
        )
        overdue = (
            not concluded and not cancelled and deadline is not None and deadline < now
        )
        updated_at = parse_dashboard_date(row["updated_at"]) or parse_dashboard_date(
            row["received_at"]
        )
        days_stale = max((today - updated_at.date()).days, 0) if updated_at else 0
        stale = not concluded and not cancelled and days_stale >= stale_days
        blocker = bool(str(row["pending_issues"] or "").strip()) and not cancelled
        evaluated = evaluation_present(report)
        evaluation_pending = concluded and not evaluated

        if overdue_only and not overdue:
            continue
        if row["stage"] in stage_totals:
            stage_totals[row["stage"]] += 1

        summary = {
            "id": row["id"],
            "client": row["client"],
            "stage": row["stage"],
            "stageLabel": DASHBOARD_STAGE_LABELS.get(row["stage"], row["stage"]),
            "assignedUsername": responsible,
            "assignedName": user_names.get(
                responsible, responsible or "Sem atribuição"
            ),
            "deliveryStatus": status,
            "updatedAt": row["updated_at"],
            "completedAt": row["completed_at"],
            "deadline": deadline.isoformat() if deadline else None,
            "daysStale": days_stale,
            "blocker": str(row["pending_issues"] or "").strip()[:240] or None,
        }
        if overdue:
            overdue_records.append(summary)
        if stale:
            stale_records.append(summary)
        if blocker:
            blocker_records.append(summary)
        if evaluation_pending:
            pending_evaluations.append(summary)

        person = workload.setdefault(
            responsible or "sem_atribuicao",
            {
                "username": responsible or "",
                "fullName": user_names.get(responsible, "Sem atribuição"),
                "active": 0,
                "overdue": 0,
                "stale": 0,
                "blockers": 0,
                "pendingEvaluations": 0,
                "concludedMonth": 0,
                "lastSync": None,
                "pendingSync": 0,
                "syncErrors": 0,
            },
        )
        if not concluded and not cancelled:
            person["active"] += 1
        person["overdue"] += int(overdue)
        person["stale"] += int(stale)
        person["blockers"] += int(blocker)
        person["pendingEvaluations"] += int(evaluation_pending)

        if concluded:
            start = parse_dashboard_date(row["started_at"])
            end = parse_dashboard_date(row["ended_at"])
            if start and end and end.date() >= start.date():
                duration_values.append((end.date() - start.date()).days + 1)
            completion_date = (
                end.date()
                if end
                else datetime.fromtimestamp(
                    int(row["completed_at"]) / 1000, timezone.utc
                ).date()
            )
            if (
                completion_date.year == today.year
                and completion_date.month == today.month
            ):
                concluded_month += 1
                person["concludedMonth"] += 1
        if concluded:
            score_text = (
                str(fields.get("_supervisionScore") or "").replace(",", ".").strip()
            )
            try:
                score = float(score_text)
                if 0 <= score <= 10:
                    scores.append(score)
            except ValueError:
                pass

    sync_errors: list[dict] = []
    for heartbeat in heartbeat_rows:
        username = str(heartbeat["username"] or "").casefold()
        if implantador and username != implantador:
            continue
        person = workload.get(username)
        if person:
            person["pendingSync"] += int(heartbeat["pending_count"] or 0)
            if not person["lastSync"]:
                person["lastSync"] = heartbeat["last_seen"]
            if heartbeat["last_error"]:
                person["syncErrors"] += 1
        if heartbeat["last_error"]:
            sync_errors.append(
                {
                    "username": heartbeat["username"],
                    "fullName": user_names.get(username, heartbeat["username"]),
                    "deviceId": heartbeat["device_id"],
                    "appVersion": heartbeat["app_version"],
                    "lastSeen": heartbeat["last_seen"],
                    "pendingCount": heartbeat["pending_count"],
                    "error": heartbeat["last_error"],
                }
            )

    result_workload = sorted(
        workload.values(),
        key=lambda item: (-item["active"], item["fullName"].casefold()),
    )
    return {
        "generatedAt": now.isoformat(),
        "filters": {
            "implantador": implantador,
            "period": period_value,
            "stage": stage_filter,
            "overdue": overdue_only,
            "blockers": blockers_only,
            "staleDays": stale_days,
        },
        "filterOptions": {
            "implantadores": users,
            "stages": [
                {"value": key, "label": label}
                for key, label in DASHBOARD_STAGE_LABELS.items()
            ],
        },
        "indicators": {
            "total": sum(stage_totals.values()),
            "overdue": len(overdue_records),
            "stale": len(stale_records),
            "pendingEvaluations": len(pending_evaluations),
            "blockers": len(blocker_records),
            "concludedMonth": concluded_month,
            "averageDurationDays": (
                round(sum(duration_values) / len(duration_values), 1)
                if duration_values
                else None
            ),
            "averageScore": round(sum(scores) / len(scores), 1) if scores else None,
            "syncErrors": len(sync_errors),
        },
        "byStage": [
            {"stage": key, "label": DASHBOARD_STAGE_LABELS[key], "count": count}
            for key, count in stage_totals.items()
        ],
        "workload": result_workload,
        "lists": {
            "overdue": overdue_records[:100],
            "stale": stale_records[:100],
            "pendingEvaluations": pending_evaluations[:100],
            "blockers": blocker_records[:100],
            "syncErrors": sync_errors[:100],
        },
    }


def admin_html(user: dict | None, message: str = "", error: str = "") -> str:
    notice = f'<div class="notice">{html.escape(message)}</div>' if message else ""
    alert = f'<div class="error">{html.escape(error)}</div>' if error else ""
    base_start = """<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1"><title>R.E.I. • Usuários</title>
    <link rel="icon" href="/web/assets/favicon.ico" sizes="any">
    <link rel="icon" type="image/png" href="/web/assets/favicon-192.png">
    <link rel="apple-touch-icon" href="/web/assets/favicon-192.png">
    <style>
    :root{--navy:#263a7a;--dark:#172653;--green:#58ad45;--bg:#f4f6fa;--line:#e1e5ee;--muted:#727b90}
    *{box-sizing:border-box}body{margin:0;font-family:Inter,Segoe UI,Arial,sans-serif;background:var(--bg);color:#20283b}
    header{background:#fff;border-bottom:1px solid var(--line);padding:14px 5%;display:flex;align-items:center;justify-content:space-between;gap:12px}
    .brand{display:flex;align-items:center;gap:12px}.brand img{width:46px;height:46px;object-fit:contain;display:block}.brand .theme-logo-dark{display:none}.login .brand{justify-content:center;width:100%}.login .brand img{width:128px;height:128px}
    .spacer{flex:1}nav{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.nav{display:inline-flex;align-items:center;justify-content:center;text-decoration:none;border-radius:11px;padding:10px 14px;font-weight:800;color:var(--navy);background:#eef1f7}.nav.active{background:var(--navy);color:#fff}.nav.gear{width:42px;padding:10px;cursor:pointer;border:0;background:#eef1f7}.nav.gear svg{width:19px;height:19px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
    main{max-width:1080px;margin:34px auto;padding:0 20px}.hero{background:linear-gradient(135deg,var(--dark),var(--navy));color:#fff;border-radius:24px;padding:28px;margin-bottom:22px}
    .hero h1{margin:0 0 7px}.hero p{margin:0;color:#d7def7}.grid{display:grid;grid-template-columns:360px 1fr;gap:20px}.card{background:#fff;border:1px solid var(--line);border-radius:20px;padding:22px}
    h2{margin:0 0 17px;font-size:19px}label{display:block;font-size:12px;font-weight:700;color:#596174;margin:12px 0 6px}
    input,select{width:100%;padding:12px;border:1px solid #cfd5e2;border-radius:11px;font-size:14px;background:#fbfcff}
    button{border:0;border-radius:11px;padding:12px 17px;font-weight:700;cursor:pointer;background:var(--navy);color:#fff}.full{width:100%;margin-top:18px}
    .logout{background:#eef1f7;color:var(--navy)}.modal{position:fixed;inset:0;background:rgba(12,19,40,.48);display:grid;place-items:center;z-index:20;padding:18px}.modal .card{width:min(500px,100%);max-height:88vh;overflow:auto}.admin-modal .card{width:min(400px,100%)}.admin-settings-card .row{display:flex;align-items:center;gap:12px;margin-bottom:16px}.admin-settings-card h2{margin:0 0 4px;font-size:18px}.admin-settings-card .danger{background:#c0392b;color:#fff}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:12px 10px;border-bottom:1px solid var(--line);font-size:13px}
    th{color:var(--muted);font-size:11px;text-transform:uppercase}.badge{display:inline-block;padding:5px 9px;border-radius:20px;background:#e9f5e6;color:#3b7131;font-size:11px;font-weight:700}
    .badge.implantador{background:#e9edfb;color:var(--navy)}.notice,.error{padding:12px 15px;border-radius:11px;margin-bottom:15px}.notice{background:#e9f5e6;color:#35682d}.error{background:#fdeaea;color:#9a3030}
    .login{max-width:430px;margin:70px auto}.muted{color:var(--muted);font-size:13px}.user-actions{display:flex;align-items:flex-start;gap:8px;flex-wrap:wrap}.password-reset{position:relative}.password-reset summary{list-style:none;cursor:pointer;border-radius:11px;padding:12px 14px;font-weight:700;color:var(--navy);background:#eef1f7;white-space:nowrap}.password-reset summary::-webkit-details-marker{display:none}.password-reset[open] form{position:absolute;right:0;top:48px;z-index:4;width:260px;padding:14px;background:#fff;border:1px solid var(--line);border-radius:14px;box-shadow:0 16px 35px rgba(23,38,83,.18)}.password-reset form label{margin-top:0}.password-reset form input{margin-bottom:10px}.password-reset form button{width:100%}@media(max-width:800px){.grid{grid-template-columns:1fr}.password-reset[open] form{position:fixed;left:18px;right:18px;top:20%;width:auto}}
    html[data-theme="dark"]{--navy:#afc0ff;--dark:#1b2d64;--green:#91da7d;--bg:#080e1b;--line:#46536e;--muted:#d4dbea;color-scheme:dark}html[data-theme="dark"] body{color:#f7f9ff;background:#080e1b}html[data-theme="dark"] header,html[data-theme="dark"] .card,html[data-theme="dark"] .password-reset[open] form{background:#151d2d;border-color:var(--line)}html[data-theme="dark"] header{box-shadow:0 5px 18px rgba(0,0,0,.24)}html[data-theme="dark"] input,html[data-theme="dark"] select{background:#0d1524;border-color:#4d5b77;color:#f7f9ff}html[data-theme="dark"] label,html[data-theme="dark"] .muted{color:#d4dbea}html[data-theme="dark"] .logout,html[data-theme="dark"] .password-reset summary{background:#222d42;color:#f1f4ff}html[data-theme="dark"] th,html[data-theme="dark"] td{border-color:var(--line)}html[data-theme="dark"] .brand .theme-logo-light{display:none}html[data-theme="dark"] .brand .theme-logo-dark{display:block;filter:brightness(1.12) drop-shadow(0 0 1px rgba(255,255,255,.85)) drop-shadow(0 0 6px rgba(143,168,255,.22))}html[data-theme="dark"] header .brand img{width:56px;height:56px}html[data-theme="dark"] .login .brand img{width:136px;height:136px}
    html[data-theme="dark"] .hero{background:linear-gradient(135deg,#1b2d64,#30478f);border:1px solid #425a9c}html[data-theme="dark"] button,html[data-theme="dark"] .nav.active{background:#405aa8;color:#fff}
    </style><script>!function(){var m=localStorage.getItem("reiTheme")||"system",q=matchMedia("(prefers-color-scheme: dark)"),a=function(){var d=m==="dark"||(m==="system"&&q.matches);document.documentElement.dataset.theme=d?"dark":"light"};a();q.addEventListener&&q.addEventListener("change",a)}();window.openAdminSettings=function(e){e&&(e.preventDefault(),e.stopPropagation());var t=document.createElement("div");t.className="modal admin-modal",t.innerHTML='<section class="card admin-settings-card"><div class="row"><div><h2>Configurações da conta</h2><p class="muted">Sair do sistema.</p></div><div class="spacer"></div><button class="btn secondary" onclick="closeAdminModal()" title="Fechar" aria-label="Fechar">Fechar</button></div><div class="row"><form method="post" action="/admin/logout" style="flex:1"><button class="btn danger" type="submit">Sair do sistema</button></form></div></section>',document.body.appendChild(t),t.addEventListener("click",function(e){e.target===t&&closeAdminModal()})};window.closeAdminModal=function(){var e=document.querySelector(".admin-modal");e&&e.remove()}</script></head><body>"""
    base_end = "</main></body></html>"
    if users_count() == 0:
        return (
            base_start
            + """<main class="login"><div class="card"><div class="brand"><img class="theme-logo-light" src="/web/assets/logo_dubrasil_blue.png" alt="DuBrasil Soluções"><img class="theme-logo-dark" src="/web/assets/logo_dubrasil_white.png" alt="" aria-hidden="true"></div>
        <h2 style="margin-top:25px">Criar supervisor inicial</h2><p class="muted">Este primeiro usuário administrará os demais acessos.</p>"""
            + alert
            + """
        <form method="post" action="/admin/setup"><label>Nome completo</label><input name="full_name" required minlength="3">
        <label>Usuário</label><input name="username" required minlength="3" autocomplete="username"><label>Senha</label>
        <input type="password" name="password" required minlength="8" autocomplete="new-password"><button class="full">Criar supervisor</button></form></div>"""
            + base_end
        )
    if not user:
        return (
            base_start
            + """<main class="login"><div class="card"><div class="brand"><img class="theme-logo-light" src="/web/assets/logo_dubrasil_blue.png" alt="DuBrasil Soluções"><img class="theme-logo-dark" src="/web/assets/logo_dubrasil_white.png" alt="" aria-hidden="true"></div>
        <h2 style="margin-top:25px">Acesso ao R.E.I.</h2><p class="muted">O perfil do usuário define automaticamente a área que será aberta.</p>"""
            + alert
            + """<form method="post" action="/login">
        <label>Usuário</label><input name="username" required autocomplete="username"><label>Senha</label>
        <input type="password" name="password" required autocomplete="current-password"><button class="full">Entrar</button></form>
        <script>localStorage.removeItem('reiToken');</script></div>"""
            + base_end
        )
    if user["role"] != "supervisor":
        return (
            base_start
            + '<main class="login"><div class="card"><h2>Acesso restrito</h2><p>Somente supervisores podem administrar usuários.</p></div>'
            + base_end
        )
    with connect() as db:
        users = db.execute(
            "SELECT id,username,full_name,role,active,created_at FROM users ORDER BY full_name"
        ).fetchall()
    rows = "".join(
        f"<tr><td><strong>{html.escape(row['full_name'])}</strong><br><span class='muted'>@{html.escape(row['username'])}</span></td>"
        f"<td><span class='badge {row['role']}'>{html.escape(row['role'].title())}</span></td>"
        f"<td>{'Ativo' if row['active'] else 'Inativo'}</td>"
        f"<td><div class='user-actions'><form method='post' action='/admin/users/toggle'><input type='hidden' name='id' value='{row['id']}'>"
        f"<button class='logout'>{'Desativar' if row['active'] else 'Ativar'}</button></form>"
        f"<details class='password-reset'><summary>Alterar senha</summary><form method='post' action='/admin/users/password'>"
        f"<input type='hidden' name='id' value='{row['id']}'><label>Nova senha</label><input type='password' name='new_password' required minlength='8' autocomplete='new-password'>"
        f"<label>Confirmar nova senha</label><input type='password' name='confirmation' required minlength='8' autocomplete='new-password'>"
        f"<button type='submit'>Salvar nova senha</button></form></details></div></td></tr>"
        for row in users
    )
    return (
        base_start
        + f"""<header><div class="brand"><img class="theme-logo-light" src="/web/assets/logo_dubrasil_blue.png" alt="DuBrasil Soluções"><img class="theme-logo-dark" src="/web/assets/logo_dubrasil_white.png" alt="" aria-hidden="true"></div><div class="spacer"></div><nav><a class="nav" href="/web">Painel</a><a class="nav active" href="/admin">Usuários</a><a class="nav" href="/admin/items">Itens dos relatórios</a><button class="nav gear" type="button" onclick="openAdminSettings(event); return false;" title="Configurações da conta" aria-label="Configurações da conta"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06a1.7 1.7 0 0 0-1.88-.34A1.7 1.7 0 0 0 14 20.92V21h-4v-.08A1.7 1.7 0 0 0 9 19.37a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.63 15 1.7 1.7 0 0 0 3.08 14H3v-4h.08A1.7 1.7 0 0 0 4.63 9a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 9 4.63 1.7 1.7 0 0 0 10 3.08V3h4v.08A1.7 1.7 0 0 0 15 4.63a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.37 9c.2.61.77 1.02 1.55 1.02H21v4h-.08A1.7 1.7 0 0 0 19.4 15z"/></svg></button></nav></header><main>
    <div class="hero"><h1>Gestão de usuários</h1><p>Cadastre supervisores e implantadores que terão acesso ao aplicativo.</p></div>{notice}{alert}
    <div class="grid"><section class="card"><h2>Novo usuário</h2><form method="post" action="/admin/users">
    <label>Nome completo</label><input name="full_name" required minlength="3"><label>Usuário</label><input name="username" required minlength="3">
    <label>Perfil</label><select name="role"><option value="implantador">Implantador</option><option value="supervisor">Supervisor</option></select>
    <label>Senha provisória</label><input type="password" name="password" required minlength="8"><button class="full">Cadastrar usuário</button></form></section>
    <section class="card"><h2>Usuários cadastrados</h2><div style="overflow:auto"><table><thead><tr><th>Usuário</th><th>Perfil</th><th>Status</th><th>Ação</th></tr></thead><tbody>{rows}</tbody></table></div></section></div>"""
        + base_end
    )


def admin_items_html(user: dict | None, message: str = "", error: str = "") -> str:
    notice = f'<div class="notice">{html.escape(message)}</div>' if message else ""
    alert = f'<div class="error">{html.escape(error)}</div>' if error else ""
    base_start = """<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1"><title>R.E.I. • Itens</title>
    <link rel="icon" href="/web/assets/favicon.ico" sizes="any">
    <link rel="icon" type="image/png" href="/web/assets/favicon-192.png">
    <style>
    :root{--navy:#263a7a;--dark:#172653;--green:#58ad45;--bg:#f4f6fa;--line:#e1e5ee;--muted:#727b90;--soft:#f8faff}
    *{box-sizing:border-box}body{margin:0;font-family:Inter,Segoe UI,Arial,sans-serif;background:var(--bg);color:#20283b}
    header{position:sticky;top:0;z-index:5;background:rgba(255,255,255,.96);backdrop-filter:blur(10px);border-bottom:1px solid var(--line);padding:14px 5%;display:flex;align-items:center;justify-content:space-between;gap:12px}
    .brand{display:flex;align-items:center;gap:12px}.brand img{width:42px;height:42px;object-fit:contain;display:block}
    .spacer{flex:1}nav{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.nav{display:inline-flex;align-items:center;justify-content:center;text-decoration:none;border-radius:999px;padding:9px 13px;font-weight:800;color:var(--navy);background:#eef1f7;font-size:13px}.nav.active{background:var(--navy);color:#fff}.nav.gear{width:38px;padding:9px;cursor:pointer;border:0}.nav.gear svg{width:18px;height:18px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
    main{max-width:1220px;margin:22px auto;padding:0 18px 28px}.hero{background:linear-gradient(135deg,var(--dark),var(--navy));color:#fff;border-radius:22px;padding:20px 22px;margin-bottom:16px;display:flex;align-items:flex-end;justify-content:space-between;gap:16px}
    .hero h1{margin:0 0 5px;font-size:28px}.hero p{margin:0;color:#d7def7}.hero small{display:block;color:#bfc9f5;font-weight:800;text-transform:uppercase;letter-spacing:.08em;font-size:11px;margin-bottom:6px}
    .section-title{display:flex;align-items:center;justify-content:space-between;margin:18px 0 10px}.section-title h2{margin:0;font-size:18px}.section-title span{color:var(--muted);font-size:13px}
    .item-switch{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin:14px 0 16px}.item-switch button{display:flex;align-items:center;justify-content:space-between;gap:14px;text-align:left;background:#fff;color:#172653;border:1px solid var(--line);border-radius:18px;padding:16px;box-shadow:0 10px 24px rgba(23,38,83,.04)}.item-switch button.active{background:var(--navy);color:#fff;border-color:var(--navy)}.item-switch strong{display:block;font-size:17px}.item-switch span{display:block;font-size:12px;color:var(--muted);margin-top:3px}.item-switch button.active span{color:#d7def7}.item-count{display:inline-flex;align-items:center;justify-content:center;min-width:42px;height:42px;border-radius:14px;background:var(--navy);color:#fff;font-size:18px;font-weight:900}.item-switch button.active .item-count{background:#fff;color:var(--navy)}
    .forms-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;align-items:stretch}.lists-grid{display:grid;grid-template-columns:1fr;gap:16px}.is-hidden{display:none!important}.wide{grid-column:1/-1}
    .card{background:#fff;border:1px solid var(--line);border-radius:18px;padding:16px;box-shadow:0 10px 24px rgba(23,38,83,.04)}.form-card{display:flex;flex-direction:column}.form-card h2,.list-card h2{margin:0 0 4px;font-size:16px;color:#172653}.card-subtitle{margin:0 0 12px;color:var(--muted);font-size:12px;line-height:1.35}
    .fields{display:grid;gap:9px;margin-top:auto}.two-col{display:grid;grid-template-columns:1fr 1fr;gap:9px}.field-row{display:grid;gap:6px}
    label{display:block;font-size:11px;font-weight:850;color:#596174}input,select,textarea{width:100%;padding:10px 11px;border:1px solid #cfd5e2;border-radius:12px;font-size:13px;background:#fbfcff;outline:none}input:focus,select:focus,textarea:focus{border-color:var(--navy);box-shadow:0 0 0 3px rgba(38,58,122,.10)}
    textarea{min-height:74px;resize:vertical}button{border:0;border-radius:12px;padding:11px 14px;font-weight:850;cursor:pointer;background:var(--navy);color:#fff}.full{width:100%;margin-top:3px}.logout{background:#eef1f7;color:var(--navy)}
    .notice,.error{padding:12px 15px;border-radius:12px;margin-bottom:14px}.notice{background:#e9f5e6;color:#35682d}.error{background:#fdeaea;color:#9a3030}
    .muted{color:var(--muted);font-size:13px}.pill{display:inline-flex;align-items:center;margin:3px 4px 3px 0;padding:6px 9px;border-radius:999px;background:#eef1f7;color:#172653;font-size:12px;font-weight:750;line-height:1.2}
    .field-pill-list{display:flex;align-items:stretch;flex-wrap:wrap;gap:6px}.field-pill{align-items:center;gap:8px;margin:0;padding:7px 9px;border-radius:13px}.field-pill-copy{display:grid;gap:2px;min-width:0}.field-pill-copy strong{font-size:12px;line-height:1.2}.field-pill-copy small{color:#737d94;font-size:9px;font-weight:700;line-height:1.15}.inline-delete,.topic-delete{display:inline-flex;margin:0}.mini-delete{padding:4px 7px;border-radius:999px;font-size:10px;line-height:1}
    .required-badge{display:inline-flex;width:max-content;padding:3px 7px;border-radius:999px;font-size:9px;font-weight:900}.required-never{background:#e9edf5;color:#596174}.required-always{background:#fde8e6;color:#a52b22}.required-conditional{background:#fff0d8;color:#87540a}.condition-summary{max-width:420px}.requirement-editor{display:grid;gap:7px;padding:10px;border:1px solid var(--line);border-radius:13px;background:var(--soft)}.conditional-editor{display:grid;gap:8px}.condition-list{display:grid;gap:8px}.condition-row{display:grid;grid-template-columns:.8fr 1.6fr .8fr 1fr auto;gap:7px;align-items:center}.condition-row select,.condition-row input{min-width:0}.add-condition{background:#eef1f7;color:var(--navy)}.remove-condition{background:#fdeaea;color:#a52b22;padding:9px}.rule-note{margin:0;color:var(--muted);font-size:11px}
    .field-edit{display:inline-flex}.field-edit summary{list-style:none;cursor:pointer;padding:4px 7px;border-radius:999px;background:#fff;color:var(--navy);font-size:10px;font-weight:900;line-height:1}.field-edit summary::-webkit-details-marker{display:none}.field-edit[open]:before{content:"";position:fixed;inset:0;z-index:20;background:rgba(10,18,38,.58);backdrop-filter:blur(2px)}.field-edit form{display:none}.field-edit[open] form{position:fixed;z-index:21;left:50%;top:50%;transform:translate(-50%,-50%);display:grid;gap:9px;width:min(440px,calc(100vw - 28px));padding:18px;border:1px solid var(--line);border-radius:18px;background:#fff;box-shadow:0 24px 70px rgba(10,18,38,.3);text-align:left}.field-edit form h3{margin:0 0 4px;font-size:18px}.edit-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:4px}.cancel-edit{background:#eef1f7;color:var(--navy)}
    .topic{border:1px solid var(--line);border-radius:15px;padding:12px;margin:9px 0;background:var(--soft)}.topic-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:8px}.topic strong{display:block;color:#172653;font-size:13px}.delete{background:#eef1f7;color:#c0392b;font-size:12px;padding:8px 10px;border-radius:10px}.area-title{display:flex;align-items:center;gap:8px;margin:14px 0 8px;color:var(--navy);font-size:14px}.area-title:before{content:"";width:7px;height:22px;border-radius:999px;background:var(--green)}
    .subblock{padding:8px 0;border-top:1px solid rgba(225,229,238,.75)}.subblock:first-of-type{border-top:0}.source-label{display:inline-flex;margin:0 0 6px;padding:4px 8px;border-radius:999px;background:#fff;color:#66708a;font-size:10px;font-weight:900;text-transform:uppercase;letter-spacing:.06em}
    .empty{display:flex;align-items:center;min-height:44px;padding:10px 12px;border:1px dashed #cfd5e2;border-radius:13px;background:#fbfcff;color:var(--muted);font-size:13px}
    .modal{position:fixed;inset:0;background:rgba(12,19,40,.48);display:grid;place-items:center;z-index:20;padding:18px}.modal .card{width:min(500px,100%);max-height:88vh;overflow:auto}.admin-modal .card{width:min(400px,100%)}.admin-settings-card .row{display:flex;align-items:center;gap:12px;margin-bottom:16px}.admin-settings-card h2{margin:0 0 4px;font-size:18px}.admin-settings-card .danger{background:#c0392b;color:#fff}
    @media(max-width:1100px){.forms-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.lists-grid{grid-template-columns:1fr}}
    @media(max-width:900px){.condition-row{grid-template-columns:1fr 1fr}.condition-row .remove-condition{grid-column:1/-1}}@media(max-width:700px){header{align-items:flex-start;flex-direction:column}.hero{display:block}.item-switch,.forms-grid{grid-template-columns:1fr}.two-col,.condition-row{grid-template-columns:1fr}main{padding:0 12px 24px}.card{padding:14px}}
    html[data-theme="dark"]{--navy:#9bb0ff;--dark:#101933;--green:#75c361;--bg:#0d1220;--line:#343d51;--muted:#aeb8ce;--soft:#121827;color-scheme:dark}html[data-theme="dark"] body{color:#eef2ff}html[data-theme="dark"] header,html[data-theme="dark"] .card,html[data-theme="dark"] .item-switch button,html[data-theme="dark"] .source-label,html[data-theme="dark"] .field-edit[open] form,html[data-theme="dark"] .admin-settings-card{background:#171d2b;border-color:var(--line);color:#eef2ff}html[data-theme="dark"] input,html[data-theme="dark"] select,html[data-theme="dark"] textarea,html[data-theme="dark"] .empty{background:#111725;border-color:#3b465d;color:#eef2ff}html[data-theme="dark"] .form-card h2,html[data-theme="dark"] .list-card h2,html[data-theme="dark"] .topic strong,html[data-theme="dark"] .area-title{color:#dbe3ff}html[data-theme="dark"] .pill,html[data-theme="dark"] .delete,html[data-theme="dark"] .logout,html[data-theme="dark"] .field-edit summary,html[data-theme="dark"] .cancel-edit,html[data-theme="dark"] .add-condition{background:#222a3b;color:#dbe3ff}html[data-theme="dark"] .required-never{background:#2b3549;color:#d2daed}html[data-theme="dark"] .required-always{background:#4b2528;color:#ffb7b1}html[data-theme="dark"] .required-conditional{background:#49391e;color:#ffd48c}
    </style><script>
    !function(){var m=localStorage.getItem("reiTheme")||"system",q=matchMedia("(prefers-color-scheme: dark)"),a=function(){var d=m==="dark"||(m==="system"&&q.matches);document.documentElement.dataset.theme=d?"dark":"light"};a();q.addEventListener&&q.addEventListener("change",a)}();
    window.openAdminSettings=function(e){e&&(e.preventDefault(),e.stopPropagation());var t=document.createElement("div");t.className="modal admin-modal",t.innerHTML='<section class="card admin-settings-card"><div class="row"><div><h2>Configurações da conta</h2><p class="muted">Sair do sistema.</p></div><div class="spacer"></div><button class="btn secondary" onclick="closeAdminModal()" title="Fechar" aria-label="Fechar">Fechar</button></div><div class="row"><form method="post" action="/admin/logout" style="flex:1"><button class="btn danger" type="submit">Sair do sistema</button></form></div></section>',document.body.appendChild(t),t.addEventListener("click",function(e){e.target===t&&closeAdminModal()})};window.closeAdminModal=function(){var e=document.querySelector(".admin-modal");e&&e.remove()}
    function showItemForms(group){
      document.querySelectorAll('[data-item-form]').forEach(function(el){el.classList.toggle('is-hidden',el.dataset.itemForm!==group);});
      document.querySelectorAll('[data-form-tab]').forEach(function(el){el.classList.toggle('active',el.dataset.formTab===group);});
    }
    function showRegisteredItems(group){
      document.querySelectorAll('[data-item-list]').forEach(function(el){el.classList.toggle('is-hidden',el.dataset.itemList!==group);});
      document.querySelectorAll('[data-list-tab]').forEach(function(el){el.classList.toggle('active',el.dataset.listTab===group);});
    }
    document.addEventListener('DOMContentLoaded',function(){showItemForms('rei');showRegisteredItems('rei');});
    </script></head><body>"""
    base_end = "</main></body></html>"
    if not user or user["role"] != "supervisor":
        return (
            base_start
            + '<main><section class="card"><h2>Acesso restrito</h2><p>Somente supervisores podem gerenciar os itens.</p><p><a class="nav" href="/admin">Voltar</a></p></section>'
            + base_end
        )
    data = load_schema_items()
    defaults = load_default_schema_items()
    effective_schema = effective_schema_from_custom(data)
    area_options = "".join(
        f'<option value="{html.escape(key)}">{html.escape(label)}</option>'
        for key, label in REI_ITEM_AREAS.items()
    )
    rei_topics_by_area: dict[str, list[str]] = {}
    for area in REI_ITEM_AREAS:
        titles: list[str] = []
        if area != "modules":
            for group in defaults["rei"][area] + data["rei"][area]:
                title = str(group.get("title", "")).strip()
                if title and not any(
                    existing.lower() == title.lower() for existing in titles
                ):
                    titles.append(title)
        rei_topics_by_area[area] = titles
    rei_topics_json = json.dumps(rei_topics_by_area, ensure_ascii=False).replace(
        "<", "\\u003c"
    )
    rei_blocks = []
    for area, label in REI_ITEM_AREAS.items():
        custom = data["rei"][area]
        if area == "modules":
            default_keys = {str(item.get("key") or "") for item in defaults["rei"]["modules"]}
            custom_by_key = {str(item.get("key") or ""): item for item in custom}
            effective_defaults = [
                custom_by_key.get(str(item.get("key") or ""), item)
                for item in defaults["rei"]["modules"]
            ]
            custom_only = [item for item in custom if str(item.get("key") or "") not in default_keys]
            body = render_item_source(
                "Padrão",
                effective_defaults,
                "Nenhum módulo padrão cadastrado.",
                "Caixa de seleção",
                "modules",
                "modulos",
                effective_schema,
            ) + render_deletable_rei_items(
                "modules", "", custom_only, effective_schema, "Nenhum módulo personalizado cadastrado."
            )
        else:
            body = render_rei_area(area, defaults["rei"][area], custom, effective_schema)
        rei_blocks.append(f'<h3 class="area-title">{html.escape(label)}</h3>{body}')
    levantamento_blocks = render_survey_sections(
        defaults["levantamento"], data["levantamento"], effective_schema
    )
    rei_type_options = "".join(
        f'<option value="{html.escape(key)}">{html.escape(label)}</option>'
        for key, label in SURVEY_FIELD_TYPES.items()
        if key != "signature"
    )
    survey_type_options = "".join(
        f'<option value="{html.escape(key)}">{html.escape(label)}</option>'
        for key, label in SURVEY_FIELD_TYPES.items()
        if key not in {"checkbox", "signature"}
    )
    blank_requirement_editor = render_required_editor(
        {"requiredMode": "never"}, effective_schema
    )
    condition_template = render_condition_row(
        999999,
        {"source": "survey_field", "operator": "equals", "key": "", "value": ""},
        effective_schema,
    ).replace("999999", "__INDEX__")
    condition_key_datalist = "".join(
        f'<option value="{html.escape(key)}">{html.escape(label)} [{html.escape(source)}]</option>'
        for source, key, label in condition_key_catalog(effective_schema)
    )
    rei_total = len(data["rei"]["modules"]) + sum(
        len(group.get("items", []))
        for area, groups in data["rei"].items()
        if area != "modules"
        for group in groups
    )
    levantamento_total = sum(
        len(section.get("fields", [])) for section in data["levantamento"]
    )
    return (
        base_start
        + f"""<header><div class="brand"><img class="theme-logo-light" src="/web/assets/logo_dubrasil_blue.png" alt="DuBrasil Soluções"><img class="theme-logo-dark" src="/web/assets/logo_dubrasil_white.png" alt="" aria-hidden="true"></div><div class="spacer"></div><nav><a class="nav" href="/web">Painel</a><a class="nav" href="/admin">Usuários</a><a class="nav active" href="/admin/items">Itens dos relatórios</a><button class="nav gear" type="button" onclick="openAdminSettings(event); return false;" title="Configurações da conta" aria-label="Configurações da conta"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06a1.7 1.7 0 0 0-1.88-.34A1.7 1.7 0 0 0 14 20.92V21h-4v-.08A1.7 1.7 0 0 0 9 19.37a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.63 15 1.7 1.7 0 0 0 3.08 14H3v-4h.08A1.7 1.7 0 0 0 4.63 9a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 9 4.63 1.7 1.7 0 0 0 10 3.08V3h4v.08A1.7 1.7 0 0 0 15 4.63a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.37 9c.2.61.77 1.02 1.55 1.02H21v4h-.08A1.7 1.7 0 0 0 19.4 15z"/></svg></button></nav></header><main>
    <div class="hero"><div><small>Configuração dinâmica</small><h1>Gestão de itens e tópicos</h1><p>Cadastre campos do levantamento e itens de preenchimento da implantação.</p></div></div>{notice}{alert}
    <div class="section-title"><h2>Cadastros rápidos</h2><span>Os novos itens são sincronizados com o app Android.</span></div>
    <div class="item-switch" role="tablist" aria-label="Selecionar tipo de item">
      <button type="button" class="active" data-form-tab="rei" onclick="showItemForms('rei')"><span><strong>Itens do R.E.I.</strong><span>Tópicos e campos do relatório de implantação</span></span><b class="item-count">{rei_total}</b></button>
      <button type="button" data-form-tab="levantamento" onclick="showItemForms('levantamento')"><span><strong>Itens do Levantamento</strong><span>Tópicos e campos do levantamento de dados</span></span><b class="item-count">{levantamento_total}</b></button>
    </div>
    <div class="forms-grid">
      <section class="card form-card" data-item-form="rei"><h2>Tópico do R.E.I.</h2><p class="card-subtitle">Crie uma nova categoria dentro das áreas do relatório.</p><form method="post" action="/admin/items/rei-topic" class="fields">
        <div class="field-row"><label>Área</label><select name="area">{area_options}</select></div><div class="field-row"><label>Nome do tópico</label><input name="topic" required placeholder="Ex.: Cadastros adicionais"></div>
        <button class="full">Cadastrar tópico</button></form></section>
      <section class="card form-card" data-item-form="rei"><h2>Item do R.E.I.</h2><p class="card-subtitle">Inclua texto, múltipla escolha, data, foto ou data/hora.</p><form method="post" action="/admin/items/rei-item" class="fields">
        <div class="two-col"><div class="field-row"><label>Área</label><select id="rei-item-area" name="area">{area_options}</select></div><div class="field-row"><label>Tópico</label><select id="rei-item-topic" name="topic" aria-label="Tópico do R.E.I."></select></div></div>
        <div class="two-col"><div class="field-row"><label>Tipo</label><select name="type">{rei_type_options}</select></div><div class="field-row"><label>Campo</label><input name="label" required placeholder="Ex.: Conferir parametrização de comissão"></div></div>
        <div class="field-row"><label>Opções da múltipla escolha</label><textarea name="options" placeholder="Sim, Não, Parcial"></textarea></div>
        {blank_requirement_editor}
        <button class="full">Cadastrar item</button></form></section>
      <section class="card form-card is-hidden" data-item-form="levantamento"><h2>Tópico do levantamento</h2><p class="card-subtitle">Crie uma nova etapa para o formulário de levantamento.</p><form method="post" action="/admin/items/survey-topic" class="fields">
        <div class="field-row"><label>Nome do tópico</label><input name="topic" required placeholder="Ex.: Comercial"></div><button class="full">Cadastrar tópico</button></form></section>
      <section class="card form-card is-hidden" data-item-form="levantamento"><h2>Campo do levantamento</h2><p class="card-subtitle">Inclua texto, múltipla escolha, data, foto ou data/hora.</p><form method="post" action="/admin/items/survey-item" class="fields">
        <div class="two-col"><div class="field-row"><label>Tópico</label><input name="topic" required placeholder="Ex.: Financeiro"></div><div class="field-row"><label>Tipo</label><select name="type">{survey_type_options}</select></div></div>
        <div class="field-row"><label>Campo</label><input name="label" required placeholder="Ex.: Utiliza cobrança recorrente?"></div>
        <div class="field-row"><label>Opções da múltipla escolha</label><textarea name="options" placeholder="Sim, Não, Parcial"></textarea></div>
        {blank_requirement_editor}
        <button class="full">Cadastrar campo</button></form></section>
    </div>
    <datalist id="condition-key-catalog">{condition_key_datalist}</datalist>
    <template id="condition-row-template">{condition_template}</template>
    <script>
    !function(){{
      var topics={rei_topics_json},area=document.getElementById('rei-item-area'),topic=document.getElementById('rei-item-topic');
      function refreshTopics(){{
        var selected=area.value,items=topics[selected]||[];
        topic.innerHTML='';
        if(selected==='modules'){{
          topic.add(new Option('Não se aplica a módulos contratados',''));
          topic.disabled=true;topic.required=false;return;
        }}
        topic.disabled=false;topic.required=true;
        if(!items.length){{topic.add(new Option('Cadastre um tópico nesta área primeiro',''));return;}}
        items.forEach(function(title){{topic.add(new Option(title,title));}});
      }}
      area.addEventListener('change',refreshTopics);refreshTopics();
      var template=document.getElementById('condition-row-template');
      function markChanged(editor){{var changed=editor.querySelector('[name="rule_changed"]');if(changed)changed.value='1';}}
      document.addEventListener('change',function(event){{
        var editor=event.target.closest('.requirement-editor');
        if(!editor)return;
        if(event.target.classList.contains('required-mode')){{
          var conditional=editor.querySelector('.conditional-editor');
          conditional.classList.toggle('is-hidden',event.target.value!=='conditional');
        }}
        markChanged(editor);
      }});
      document.addEventListener('input',function(event){{var editor=event.target.closest('.requirement-editor');if(editor)markChanged(editor);}});
      document.addEventListener('click',function(event){{
        var add=event.target.closest('.add-condition');
        if(add){{
          var editor=add.closest('.requirement-editor'),list=editor.querySelector('.condition-list');
          var index=Date.now().toString()+Math.floor(Math.random()*1000).toString();
          list.insertAdjacentHTML('beforeend',template.innerHTML.replaceAll('__INDEX__',index));markChanged(editor);return;
        }}
        var remove=event.target.closest('.remove-condition');
        if(remove){{var editor=remove.closest('.requirement-editor');remove.closest('.condition-row').remove();markChanged(editor);}}
      }});
    }}();
    </script>
    <div class="section-title"><h2>Itens cadastrados</h2><span>Lista dos itens personalizados adicionados ao padrão atual.</span></div>
    <div class="item-switch" role="tablist" aria-label="Selecionar lista de itens cadastrados">
      <button type="button" class="active" data-list-tab="rei" onclick="showRegisteredItems('rei')"><span><strong>Itens do R.E.I.</strong><span>Visualizar tópicos e campos da implantação</span></span><b class="item-count">{rei_total}</b></button>
      <button type="button" data-list-tab="levantamento" onclick="showRegisteredItems('levantamento')"><span><strong>Itens do Levantamento</strong><span>Visualizar tópicos e campos do levantamento</span></span><b class="item-count">{levantamento_total}</b></button>
    </div>
    <div class="lists-grid">
      <section class="card list-card" data-item-list="rei"><h2>R.E.I.</h2><p class="card-subtitle">Itens exibidos nas abas da implantação e na avaliação.</p>{''.join(rei_blocks)}</section>
      <section class="card list-card is-hidden" data-item-list="levantamento"><h2>Levantamento</h2><p class="card-subtitle">Campos exibidos para coleta de dados do cliente.</p>{levantamento_blocks}</section>
    </div>"""
        + base_end
    )


def admin_query(kind: str, text: str) -> str:
    return f"/admin?{kind}={quote_plus(text)}"


class LegacyReiHandler(BaseHTTPRequestHandler):
    server_version = "REI-Office/1.0"

    def log_message(self, fmt: str, *args) -> None:
        logging.info("%s - %s", self.address_string(), fmt % args)

    def authorized(self) -> bool:
        return self.headers.get("X-API-Key", "") == CONFIG.get("api_key")

    def send_json(
        self, status: int, value: dict | list, cookie: str | None = None
    ) -> None:
        body = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        if cookie:
            self.send_header("Set-Cookie", cookie)
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.send_json(200, {"status": "ok", "database": DATABASE.name})
            return
        if not self.authorized():
            self.send_json(401, {"error": "não autorizado"})
            return
        if parsed.path == "/api/reports":
            limit = min(
                max(int(parse_qs(parsed.query).get("limit", [100])[0]), 1), 1000
            )
            with connect() as db:
                rows = [
                    dict(row)
                    for row in db.execute(
                        "SELECT id, client, consultant, started_at, ended_at, delivery_status, "
                        "checked_items, completed_at, received_at FROM reports ORDER BY completed_at DESC LIMIT ?",
                        (limit,),
                    )
                ]
            self.send_json(200, rows)
            return
        if parsed.path == "/api/bi/reports.csv":
            body = reports_csv()
            self.send_response(200)
            self.send_header("Content-Type", "text/csv; charset=utf-8")
            self.send_header(
                "Content-Disposition", "attachment; filename=rei_reports.csv"
            )
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        self.send_json(404, {"error": "rota não encontrada"})

    def do_POST(self) -> None:
        if self.path != "/api/reports":
            self.send_json(404, {"error": "rota não encontrada"})
            return
        if not self.authorized():
            self.send_json(401, {"error": "não autorizado"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > MAX_BODY_BYTES:
                raise ValueError("tamanho da requisição inválido")
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            report_id = save_report(payload, trusted_api_key=True)
            self.send_json(200, {"status": "saved", "reportId": report_id})
        except ReportWriteRejected as error:
            response = {"error": str(error), "code": error.code}
            if error.details:
                response["requirements"] = error.details
            self.send_json(error.status, response)
        except (ValueError, json.JSONDecodeError) as error:
            self.send_json(400, {"error": str(error)})
        except Exception:
            logging.exception("Erro ao salvar relatório")
            self.send_json(500, {"error": "erro interno"})


class ReiHandler(BaseHTTPRequestHandler):
    server_version = "REI-Office/2.0"

    def log_message(self, fmt: str, *args) -> None:
        logging.info("%s - %s", self.address_string(), fmt % args)

    def send_json(
        self, status: int, value: dict | list, cookie: str | None = None
    ) -> None:
        body = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        if cookie:
            self.send_header("Set-Cookie", cookie)
        self.end_headers()
        self.wfile.write(body)

    def send_html(
        self, content: str, status: int = 200, cookie: str | None = None
    ) -> None:
        body = content.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        if cookie:
            self.send_header("Set-Cookie", cookie)
        self.end_headers()
        self.wfile.write(body)

    def send_static(self, path: str) -> None:
        relative = (
            "index.html" if path in {"/web", "/web/"} else path.removeprefix("/web/")
        )
        target = (WEB_ROOT / relative).resolve()
        root = WEB_ROOT.resolve()
        if target != root and root not in target.parents:
            self.send_json(403, {"error": "acesso negado"})
            return
        if not target.exists() or not target.is_file():
            self.send_json(404, {"error": "arquivo não encontrado"})
            return
        body = target.read_bytes()
        content_type = (
            mimetypes.guess_type(target.name)[0] or "application/octet-stream"
        )
        if content_type.startswith("text/") or target.suffix in {
            ".js",
            ".css",
            ".json",
            ".svg",
        }:
            content_type += "; charset=utf-8"
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-store, max-age=0")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def redirect(self, location: str, cookie: str | None = None) -> None:
        self.send_response(303)
        self.send_header("Location", location)
        if cookie:
            self.send_header("Set-Cookie", cookie)
        self.end_headers()

    def read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0 or length > MAX_BODY_BYTES:
            raise ValueError("tamanho da requisição inválido")
        return self.rfile.read(length)

    def read_form(self) -> dict[str, str]:
        values = parse_qs(self.read_body().decode("utf-8"), keep_blank_values=True)
        return {key: entries[0] for key, entries in values.items()}

    def request_token(self) -> str:
        authorization = self.headers.get("Authorization", "")
        if authorization.startswith("Bearer "):
            return authorization[7:].strip()
        cookie = SimpleCookie(self.headers.get("Cookie", ""))
        return cookie.get("rei_session").value if cookie.get("rei_session") else ""

    def request_user(self) -> dict | None:
        return user_from_token(self.request_token())

    def api_supervisor(self) -> dict | None:
        if self.headers.get("X-API-Key", "") == CONFIG.get("api_key"):
            return {
                "id": None,
                "username": "api",
                "full_name": "Integração BI",
                "role": "supervisor",
                "active": 1,
            }
        user = self.request_user()
        return user if user and user["role"] == "supervisor" else None

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/":
            user = self.request_user()
            self.redirect("/web" if user else "/login")
            return
        if parsed.path == "/app":
            self.redirect("/")
            return
        if parsed.path == "/login":
            user = self.request_user()
            if user:
                self.redirect("/web")
            else:
                query = parse_qs(parsed.query)
                self.send_html(admin_html(None, error=query.get("error", [""])[0]))
            return
        if parsed.path in {"/web", "/web/", "/web/index.html"}:
            if not self.request_user():
                self.redirect("/login")
                return
            self.send_static(parsed.path)
            return
        if parsed.path.startswith("/web/"):
            self.send_static(parsed.path)
            return
        if parsed.path == "/admin":
            user = self.request_user()
            if users_count() > 0 and not user:
                self.redirect("/login")
                return
            if user and user["role"] != "supervisor":
                self.redirect("/web")
                return
            query = parse_qs(parsed.query)
            self.send_html(
                admin_html(
                    user, query.get("message", [""])[0], query.get("error", [""])[0]
                )
            )
            return
        if parsed.path == "/admin/items":
            user = self.request_user()
            if not user:
                self.redirect("/login")
                return
            if user["role"] != "supervisor":
                self.redirect("/web")
                return
            query = parse_qs(parsed.query)
            self.send_html(
                admin_items_html(
                    user, query.get("message", [""])[0], query.get("error", [""])[0]
                )
            )
            return
        if parsed.path == "/health":
            self.send_json(
                200, {"status": "ok", "database": DATABASE.name, "users": users_count()}
            )
            return
        if parsed.path == "/api/auth/me":
            user = self.request_user()
            if not user:
                self.send_json(401, {"error": "não autorizado"})
            else:
                self.send_json(200, {"user": user})
            return
        if parsed.path == "/api/device-heartbeats":
            user = self.request_user()
            if not user:
                self.send_json(401, {"error": "não autorizado"})
                return
            self.send_json(200, list_device_heartbeats(user))
            return
        if parsed.path == "/api/dashboard/supervisor":
            user = self.request_user()
            if not user or user["role"] != "supervisor":
                self.send_json(403, {"error": "acesso exclusivo para supervisor"})
                return
            try:
                self.send_json(200, supervisor_dashboard(parse_qs(parsed.query)))
            except Exception:
                logging.exception("Erro ao calcular dashboard gerencial")
                self.send_json(
                    500, {"error": "não foi possível calcular o dashboard gerencial"}
                )
            return
        if parsed.path == "/api/reports":
            user = self.request_user() or self.api_supervisor()
            if not user:
                self.send_json(401, {"error": "não autorizado"})
                return
            query = parse_qs(parsed.query)
            self.send_json(
                200,
                list_reports_for_user(
                    user,
                    int(query.get("limit", [100])[0]),
                    query.get("full", ["0"])[0] in {"1", "true", "yes"},
                ),
            )
            return
        if parsed.path == "/api/users":
            user = self.request_user()
            if not user or user["role"] != "supervisor":
                self.send_json(403, {"error": "acesso exclusivo para supervisor"})
                return
            query = parse_qs(parsed.query)
            role = query.get("role", [""])[0]
            self.send_json(200, list_users(role if role else None))
            return
        if parsed.path == "/api/schema-overrides":
            user = self.request_user()
            if not user:
                self.send_json(401, {"error": "não autorizado"})
                return
            self.send_json(200, load_effective_schema_items())
            return
        if parsed.path == "/api/bi/reports.csv":
            if not self.api_supervisor():
                self.send_json(403, {"error": "acesso exclusivo para supervisor"})
                return
            body = reports_csv()
            self.send_response(200)
            self.send_header("Content-Type", "text/csv; charset=utf-8")
            self.send_header(
                "Content-Disposition", "attachment; filename=rei_reports.csv"
            )
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        self.send_json(404, {"error": "rota não encontrada"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/admin/setup":
            if users_count() != 0:
                self.redirect(admin_query("error", "Configuração inicial já realizada"))
                return
            try:
                form = self.read_form()
                user_id = create_user(
                    form.get("username", ""),
                    form.get("full_name", ""),
                    form.get("password", ""),
                    "supervisor",
                )
                token = create_session(user_id)
                self.redirect(
                    admin_query("message", "Supervisor criado com sucesso"),
                    f"rei_session={token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000",
                )
            except ValueError as error:
                self.send_html(admin_html(None, error=str(error)), 400)
            return
        if parsed.path in {"/login", "/admin/login"}:
            form = self.read_form()
            user = authenticate(form.get("username", ""), form.get("password", ""))
            if not user:
                self.send_html(
                    admin_html(None, error="Usuário ou senha inválidos"), 401
                )
                return
            token = create_session(int(user["id"]))
            self.redirect(
                "/web",
                f"rei_session={token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000",
            )
            return
        if parsed.path == "/admin/logout":
            revoke_session(self.request_token())
            self.redirect(
                "/login", "rei_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0"
            )
            return
        if parsed.path == "/admin/users":
            user = self.request_user()
            if not user or user["role"] != "supervisor":
                self.redirect(admin_query("error", "Acesso negado"))
                return
            try:
                form = self.read_form()
                create_user(
                    form.get("username", ""),
                    form.get("full_name", ""),
                    form.get("password", ""),
                    form.get("role", ""),
                )
                self.redirect(admin_query("message", "Usuário cadastrado com sucesso"))
            except ValueError as error:
                self.redirect(admin_query("error", str(error)))
            return
        if parsed.path == "/admin/users/toggle":
            user = self.request_user()
            if not user or user["role"] != "supervisor":
                self.redirect(admin_query("error", "Acesso negado"))
                return
            form = self.read_form()
            target_id = int(form.get("id", "0"))
            if target_id == user["id"]:
                self.redirect(
                    admin_query("error", "Você não pode desativar seu próprio usuário")
                )
                return
            with connect() as db:
                db.execute(
                    "UPDATE users SET active=CASE active WHEN 1 THEN 0 ELSE 1 END,updated_at=? WHERE id=?",
                    (datetime.now(timezone.utc).isoformat(), target_id),
                )
            self.redirect(admin_query("message", "Status atualizado"))
            return
        if parsed.path == "/admin/users/password":
            user = self.request_user()
            if not user or user["role"] != "supervisor":
                self.redirect(admin_query("error", "Acesso negado"))
                return
            try:
                form = self.read_form()
                reset_user_password(
                    int(form.get("id", "0")),
                    form.get("new_password", ""),
                    form.get("confirmation", ""),
                )
                self.redirect(admin_query("message", "Senha alterada com sucesso"))
            except (ValueError, TypeError) as error:
                self.redirect(admin_query("error", str(error)))
            return
        if parsed.path in {
            "/admin/items/rei-topic/delete",
            "/admin/items/survey-topic/delete",
        }:
            user = self.request_user()
            if not user or user["role"] != "supervisor":
                self.redirect(schema_redirect("error", "Acesso negado"))
            else:
                self.redirect(
                    schema_redirect(
                        "error",
                        "A exclusão de tópicos está bloqueada. Exclua somente os itens cadastrados.",
                    )
                )
            return
        if parsed.path in {
            "/admin/items/rei-topic",
            "/admin/items/rei-item",
            "/admin/items/rei-item/edit",
            "/admin/items/rei-item/delete",
            "/admin/items/survey-topic",
            "/admin/items/survey-item",
            "/admin/items/survey-item/edit",
            "/admin/items/survey-item/delete",
        }:
            user = self.request_user()
            if not user or user["role"] != "supervisor":
                self.redirect(schema_redirect("error", "Acesso negado"))
                return
            try:
                form = self.read_form()
                if parsed.path == "/admin/items/rei-topic":
                    add_rei_topic(form.get("area", ""), form.get("topic", ""))
                    message = "Tópico do R.E.I. cadastrado"
                elif parsed.path == "/admin/items/rei-item":
                    required_mode, required_when = parse_required_form(form)
                    add_rei_item(
                        form.get("area", ""),
                        form.get("topic", ""),
                        form.get("label", ""),
                        form.get("type", "text"),
                        form.get("options", ""),
                        required_mode,
                        required_when,
                    )
                    message = "Item do R.E.I. cadastrado"
                elif parsed.path == "/admin/items/rei-item/edit":
                    required_mode, required_when = parse_required_form(form)
                    update_rei_item(
                        form.get("area", ""),
                        form.get("topic", ""),
                        form.get("key", ""),
                        form.get("label", ""),
                        form.get("type", "checkbox"),
                        form.get("options", ""),
                        required_mode,
                        required_when,
                    )
                    message = "Item do R.E.I. atualizado"
                elif parsed.path == "/admin/items/rei-item/delete":
                    delete_rei_item(
                        form.get("area", ""),
                        form.get("topic", ""),
                        form.get("key", ""),
                        form.get("label", ""),
                    )
                    message = "Item do R.E.I. excluído"
                elif parsed.path == "/admin/items/survey-topic":
                    add_survey_topic(form.get("topic", ""))
                    message = "Tópico do levantamento cadastrado"
                elif parsed.path == "/admin/items/survey-item/delete":
                    delete_survey_item(form.get("topic", ""), form.get("key", ""))
                    message = "Campo do levantamento excluído"
                elif parsed.path == "/admin/items/survey-item/edit":
                    required_mode, required_when = parse_required_form(form)
                    update_survey_item(
                        form.get("topic", ""),
                        form.get("key", ""),
                        form.get("label", ""),
                        form.get("type", "text"),
                        form.get("options", ""),
                        required_mode,
                        required_when,
                    )
                    message = "Campo do levantamento atualizado"
                else:
                    required_mode, required_when = parse_required_form(form)
                    add_survey_item(
                        form.get("topic", ""),
                        form.get("label", ""),
                        form.get("type", "text"),
                        form.get("options", ""),
                        required_mode,
                        required_when,
                    )
                    message = "Campo do levantamento cadastrado"
                self.redirect(schema_redirect("message", message))
            except ValueError as error:
                self.redirect(schema_redirect("error", str(error)))
            return

        if parsed.path == "/api/auth/login":
            try:
                payload = json.loads(self.read_body().decode("utf-8"))
                user = authenticate(
                    str(payload.get("username", "")), str(payload.get("password", ""))
                )
                if not user:
                    self.send_json(401, {"error": "usuário ou senha inválidos"})
                    return
                token = create_session(int(user["id"]))
                self.send_json(
                    200,
                    {
                        "token": token,
                        "user": {
                            "id": user["id"],
                            "username": user["username"],
                            "fullName": user["full_name"],
                            "role": user["role"],
                        },
                    },
                    f"rei_session={token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000",
                )
            except (ValueError, json.JSONDecodeError) as error:
                self.send_json(400, {"error": str(error)})
            return
        if parsed.path == "/api/auth/logout":
            revoke_session(self.request_token())
            self.send_json(
                200,
                {"status": "ok"},
                "rei_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0",
            )
            return
        if parsed.path == "/api/auth/change-password":
            user = self.request_user()
            if not user:
                self.send_json(401, {"error": "não autorizado"})
                return
            try:
                payload = json.loads(self.read_body().decode("utf-8"))
                change_user_password(
                    int(user["id"]),
                    str(payload.get("currentPassword", "")),
                    str(payload.get("newPassword", "")),
                    str(payload.get("confirmation", "")),
                )
                self.send_json(200, {"status": "ok"})
            except (ValueError, json.JSONDecodeError) as error:
                self.send_json(400, {"error": str(error)})
            return
        if parsed.path == "/api/device-heartbeats":
            user = self.request_user()
            if not user:
                self.send_json(401, {"error": "não autorizado"})
                return
            try:
                payload = json.loads(self.read_body().decode("utf-8"))
                if not isinstance(payload, dict):
                    raise ReportWriteRejected(
                        422, "invalid_heartbeat", "Estrutura do diagnóstico inválida."
                    )
                self.send_json(
                    200,
                    {"status": "ok", "device": save_device_heartbeat(payload, user)},
                )
            except ReportWriteRejected as error:
                self.send_json(error.status, {"error": str(error), "code": error.code})
            except json.JSONDecodeError:
                self.send_json(
                    422,
                    {
                        "error": "JSON do diagnóstico inválido.",
                        "code": "invalid_heartbeat",
                    },
                )
            return
        if parsed.path != "/api/reports":
            self.send_json(404, {"error": "rota não encontrada"})
            return
        user = self.request_user()
        trusted_api_key = not user and self.headers.get("X-API-Key", "") == CONFIG.get(
            "api_key"
        )
        if not user and not trusted_api_key:
            self.send_json(401, {"error": "não autorizado"})
            return
        try:
            payload = json.loads(self.read_body().decode("utf-8"))
            report_id = save_report(payload, user, trusted_api_key)
            self.send_json(200, {"status": "saved", "reportId": report_id})
        except ReportWriteRejected as error:
            response = {"error": str(error), "code": error.code}
            if error.details:
                response["requirements"] = error.details
            self.send_json(error.status, response)
        except (ValueError, json.JSONDecodeError) as error:
            self.send_json(400, {"error": str(error)})
        except Exception:
            logging.exception("Erro ao salvar relatório")
            self.send_json(500, {"error": "erro interno"})


def main() -> None:
    logging.basicConfig(
        level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s"
    )
    initialize_database()
    host = str(CONFIG.get("host", "0.0.0.0"))
    port = int(CONFIG.get("port", 8765))
    logging.info("Servidor R.E.I. em http://%s:%s", host, port)
    logging.info("Banco central: %s", DATABASE)
    ThreadingHTTPServer((host, port), ReiHandler).serve_forever()


if __name__ == "__main__":
    main()
