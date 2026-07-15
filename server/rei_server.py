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
    "text": "Texto curto",
    "textarea": "Texto longo",
    "choice": "Múltipla escolha",
    "date": "Data",
    "datetime-local": "Data e hora",
    "photo": "Foto",
    "email": "E-mail",
    "number": "Número",
}


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


def normalize_schema_items(data: dict | None) -> dict:
    normalized = empty_schema_items()
    if not isinstance(data, dict):
        return normalized
    rei = data.get("rei") if isinstance(data.get("rei"), dict) else {}
    for area in normalized["rei"]:
        if area == "modules":
            normalized["rei"][area] = [
                str(item).strip()
                for item in (rei.get(area) if isinstance(rei.get(area), list) else [])
                if str(item).strip()
            ]
            continue
        groups = []
        for group in (rei.get(area) if isinstance(rei.get(area), list) else []):
            if not isinstance(group, dict):
                continue
            title = str(group.get("title", "")).strip()
            items = []
            for item in (group.get("items") if isinstance(group.get("items"), list) else []):
                if isinstance(item, dict):
                    label = str(item.get("label", "")).strip()
                    field_type = str(item.get("type", "text")).strip()
                    if field_type not in SURVEY_FIELD_TYPES:
                        field_type = "text"
                    options = [
                        str(option).strip()
                        for option in (item.get("options") if isinstance(item.get("options"), list) else [])
                        if str(option).strip()
                    ]
                    if label:
                        normalized_item = {"label": label, "type": field_type}
                        if field_type == "choice":
                            normalized_item["options"] = options or ["Sim", "Não"]
                        items.append(normalized_item)
                else:
                    label = str(item).strip()
                    if label:
                        items.append({"label": label, "type": "text"})
            if title:
                groups.append({"title": title, "items": items})
        normalized["rei"][area] = groups
    levantamento = data.get("levantamento") if isinstance(data.get("levantamento"), list) else []
    for section in levantamento:
        if not isinstance(section, dict):
            continue
        title = str(section.get("title", "")).strip()
        fields = []
        for field in (section.get("fields") if isinstance(section.get("fields"), list) else []):
            if not isinstance(field, dict):
                continue
            label = str(field.get("label", "")).strip()
            key = str(field.get("key", "")).strip()
            field_type = str(field.get("type", "text")).strip()
            if field_type not in SURVEY_FIELD_TYPES:
                field_type = "text"
            options = [
                str(option).strip()
                for option in (field.get("options") if isinstance(field.get("options"), list) else [])
                if str(option).strip()
            ]
            if title and label and key:
                item = {"key": key, "label": label, "type": field_type}
                if field_type == "choice":
                    item["options"] = options or ["Sim", "Não"]
                fields.append(item)
        if title:
            normalized["levantamento"].append({"title": title, "fields": fields})
    return normalized


def load_schema_items() -> dict:
    if not SCHEMA_ITEMS_PATH.exists():
        return empty_schema_items()
    try:
        return normalize_schema_items(json.loads(SCHEMA_ITEMS_PATH.read_text(encoding="utf-8")))
    except (json.JSONDecodeError, OSError):
        logging.exception("Erro ao carregar itens personalizados")
        return empty_schema_items()


def save_schema_items(data: dict) -> None:
    SCHEMA_ITEMS_PATH.parent.mkdir(parents=True, exist_ok=True)
    SCHEMA_ITEMS_PATH.write_text(
        json.dumps(normalize_schema_items(data), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def field_display_parts(item: object, default_type_label: str = "Texto curto") -> tuple[str, str]:
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
                    return source[start:index + 1]
    raise ValueError(f"Array não encontrado: {marker}")


def load_default_schema_items() -> dict:
    defaults = empty_schema_items()
    try:
        schema_source = (WEB_ROOT / "schema.js").read_text(encoding="utf-8")
        defaults["rei"]["modules"] = ast.literal_eval(extract_js_array(schema_source, "modules: "))
        for area in ["technical", "stock", "finance", "fiscal", "supervision"]:
            groups = ast.literal_eval(extract_js_array(schema_source, f"{area}: "))
            defaults["rei"][area] = [
                {"title": str(group[0]), "items": [str(item) for item in group[1]]}
                for group in groups
                if isinstance(group, list) and len(group) >= 2
            ]
    except Exception:
        logging.exception("Erro ao carregar schema padrão do R.E.I.")

    try:
        app_source = (WEB_ROOT / "app.js").read_text(encoding="utf-8")
        survey_literal = extract_js_array(app_source, "const surveySections = ").replace("yesNo", '["Sim", "Não"]')
        survey_sections = ast.literal_eval(survey_literal)
        defaults["levantamento"] = []
        for section in survey_sections:
            if not isinstance(section, list) or len(section) < 2:
                continue
            fields = []
            for field in section[1]:
                if not isinstance(field, list) or len(field) < 2:
                    continue
                field_type = str(field[2]) if len(field) >= 3 and isinstance(field[2], str) else "text"
                item = {"key": str(field[0]), "label": str(field[1]), "type": field_type}
                if field_type == "choice" and len(field) >= 4 and isinstance(field[3], list):
                    item["options"] = [str(option) for option in field[3]]
                fields.append(item)
            defaults["levantamento"].append({"title": str(section[0]), "fields": fields})
    except Exception:
        logging.exception("Erro ao carregar schema padrão do levantamento")
    return defaults


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


def add_rei_item(area: str, topic: str, label: str, field_type: str = "text", options_text: str = "") -> None:
    area = area if area in REI_ITEM_AREAS else ""
    topic = topic.strip()
    label = label.strip()
    field_type = field_type if field_type in SURVEY_FIELD_TYPES else "text"
    if not area or len(label) < 2:
        raise ValueError("Informe uma área e um item válido")
    data = load_schema_items()
    if area == "modules":
        if label.lower() not in {item.lower() for item in data["rei"]["modules"]}:
            data["rei"]["modules"].append(label)
        save_schema_items(data)
        return
    if len(topic) < 2:
        raise ValueError("Informe o tópico onde o item será exibido")
    groups = data["rei"][area]
    group = next((entry for entry in groups if entry["title"].strip().lower() == topic.lower()), None)
    if not group:
        group = {"title": topic, "items": []}
        groups.append(group)
    item = {"label": label, "type": field_type}
    if field_type == "choice":
        options = [part.strip() for part in re.split(r"[,;\n]+", options_text) if part.strip()]
        item["options"] = options or ["Sim", "Não"]
    existing = {str(existing.get("label", existing)).strip().lower() if isinstance(existing, dict) else str(existing).strip().lower() for existing in group["items"]}
    if label.lower() not in existing:
        group["items"].append(item)
    save_schema_items(data)


def delete_rei_topic(area: str, title: str) -> None:
    area = area if area in REI_ITEM_AREAS and area != "modules" else ""
    title = title.strip()
    if not area or len(title) < 2:
        raise ValueError("Informe uma área e um tópico válido")
    data = load_schema_items()
    before = len(data["rei"][area])
    data["rei"][area] = [
        group for group in data["rei"][area]
        if str(group.get("title", "")).strip().lower() != title.lower()
    ]
    if len(data["rei"][area]) == before:
        raise ValueError("Tópico não encontrado ou não pode ser excluído")
    save_schema_items(data)


def delete_rei_item(area: str, topic: str, label: str) -> None:
    area = area if area in REI_ITEM_AREAS else ""
    topic = topic.strip()
    label = label.strip()
    if not area or len(label) < 2:
        raise ValueError("Informe uma área e um item válido")
    data = load_schema_items()
    if area == "modules":
        before = len(data["rei"]["modules"])
        data["rei"]["modules"] = [
            item for item in data["rei"]["modules"]
            if str(item).strip().lower() != label.lower()
        ]
        if len(data["rei"]["modules"]) == before:
            raise ValueError("Item não encontrado ou não pode ser excluído")
        save_schema_items(data)
        return
    if len(topic) < 2:
        raise ValueError("Informe o tópico do item")
    group = next((entry for entry in data["rei"][area] if entry["title"].strip().lower() == topic.lower()), None)
    if not group:
        raise ValueError("Tópico não encontrado")
    before = len(group["items"])
    group["items"] = [
        item for item in group["items"]
        if (str(item.get("label", item)).strip().lower() if isinstance(item, dict) else str(item).strip().lower()) != label.lower()
    ]
    if len(group["items"]) == before:
        raise ValueError("Item não encontrado ou não pode ser excluído")
    save_schema_items(data)


def add_survey_topic(title: str) -> None:
    title = title.strip()
    if len(title) < 2:
        raise ValueError("Informe um tópico válido")
    data = load_schema_items()
    if not any(section["title"].strip().lower() == title.lower() for section in data["levantamento"]):
        data["levantamento"].append({"title": title, "fields": []})
    save_schema_items(data)


def delete_survey_topic(title: str) -> None:
    title = title.strip()
    if len(title) < 2:
        raise ValueError("Informe um tópico válido")
    data = load_schema_items()
    before = len(data["levantamento"])
    data["levantamento"] = [
        section for section in data["levantamento"]
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
    section = next((entry for entry in data["levantamento"] if entry["title"].strip().lower() == topic.lower()), None)
    if not section:
        raise ValueError("Tópico não encontrado")
    before = len(section["fields"])
    section["fields"] = [
        field for field in section["fields"]
        if str(field.get("key", "")).strip() != key
    ]
    if len(section["fields"]) == before:
        raise ValueError("Campo não encontrado ou não pode ser excluído")
    save_schema_items(data)


def add_survey_item(topic: str, label: str, field_type: str, options_text: str) -> None:
    topic = topic.strip()
    label = label.strip()
    field_type = field_type if field_type in SURVEY_FIELD_TYPES else "text"
    if len(topic) < 2 or len(label) < 2:
        raise ValueError("Informe tópico e item válidos")
    data = load_schema_items()
    section = next((entry for entry in data["levantamento"] if entry["title"].strip().lower() == topic.lower()), None)
    if not section:
        section = {"title": topic, "fields": []}
        data["levantamento"].append(section)
    key = slugify_key(f"{topic}_{label}", "custom")
    existing_keys = {field["key"] for item in data["levantamento"] for field in item["fields"]}
    if key in existing_keys:
        key = f"{key}_{secrets.token_hex(3)}"
    field = {"key": key, "label": label, "type": field_type}
    if field_type == "choice":
        options = [part.strip() for part in re.split(r"[,;\n]+", options_text) if part.strip()]
        field["options"] = options or ["Sim", "Não"]
    section["fields"].append(field)
    save_schema_items(data)


def update_survey_item(topic: str, key: str, label: str, field_type: str, options_text: str) -> None:
    topic = topic.strip()
    key = key.strip()
    label = label.strip()
    field_type = field_type if field_type in SURVEY_FIELD_TYPES else "text"
    if len(topic) < 2 or not key or len(label) < 2:
        raise ValueError("Informe um nome e um tipo válidos para o campo")

    data = load_schema_items()
    defaults = load_default_schema_items()
    custom_section = next(
        (entry for entry in data["levantamento"] if entry["title"].strip().lower() == topic.lower()),
        None,
    )
    default_section = next(
        (entry for entry in defaults["levantamento"] if entry["title"].strip().lower() == topic.lower()),
        None,
    )
    custom_field = next(
        (field for field in custom_section.get("fields", []) if str(field.get("key", "")).strip() == key),
        None,
    ) if custom_section else None
    default_field = next(
        (field for field in default_section.get("fields", []) if str(field.get("key", "")).strip() == key),
        None,
    ) if default_section else None
    if not custom_field and not default_field:
        raise ValueError("Campo do levantamento não encontrado")

    updated = {"key": key, "label": label, "type": field_type}
    if field_type == "choice":
        options = [part.strip() for part in re.split(r"[,;\n]+", options_text) if part.strip()]
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


def render_field_pill(item: object, default_type_label: str = "Texto curto", trailing_html: str = "") -> str:
    label, type_label = field_display_parts(item, default_type_label)
    if not label:
        return ""
    return (
        '<div class="pill field-pill">'
        f'<span class="field-pill-copy"><strong>{html.escape(label)}</strong>'
        f'<small>Tipo: {html.escape(type_label)}</small></span>{trailing_html}</div>'
    )


def render_item_source(
    title: str,
    items: list[object],
    empty: str = "Nenhum item cadastrado.",
    default_type_label: str = "Texto curto",
) -> str:
    rendered = [render_field_pill(item, default_type_label) for item in items]
    rendered = [item for item in rendered if item]
    if not rendered:
        return f'<div class="subblock"><span class="source-label">{html.escape(title)}</span><div class="empty">{html.escape(empty)}</div></div>'
    return f'<div class="subblock"><span class="source-label">{html.escape(title)}</span><div class="field-pill-list">{"".join(rendered)}</div></div>'


def render_delete_form(action: str, fields: dict[str, str], label: str = "Excluir") -> str:
    inputs = "".join(
        f'<input type="hidden" name="{html.escape(name)}" value="{html.escape(value)}">'
        for name, value in fields.items()
    )


def render_survey_edit(field: dict, topic: str) -> str:
    key = str(field.get("key", "")).strip()
    label = str(field.get("label", "")).strip()
    field_type = str(field.get("type", "text")).strip()
    options_text = ", ".join(str(option) for option in field.get("options", []) if str(option).strip())
    type_options = "".join(
        f'<option value="{html.escape(value)}" {"selected" if value == field_type else ""}>{html.escape(caption)}</option>'
        for value, caption in SURVEY_FIELD_TYPES.items()
    )
    return f'''<details class="field-edit"><summary>Editar</summary>
      <form method="post" action="/admin/items/survey-item/edit">
        <h3>Editar campo do levantamento</h3>
        <input type="hidden" name="topic" value="{html.escape(topic)}">
        <input type="hidden" name="key" value="{html.escape(key)}">
        <label>Nome do campo</label><input name="label" value="{html.escape(label)}" required minlength="2">
        <label>Tipo do campo</label><select name="type">{type_options}</select>
        <label>Opções da múltipla escolha</label><textarea name="options" placeholder="Sim, Não, Parcial">{html.escape(options_text)}</textarea>
        <div class="edit-actions"><button type="button" class="cancel-edit" onclick="this.closest('details').removeAttribute('open')">Cancelar</button><button type="submit">Salvar alterações</button></div>
      </form></details>'''
    return (
        f'<form method="post" action="{html.escape(action)}" class="inline-delete">'
        f'{inputs}<button class="delete mini-delete" type="submit" title="{html.escape(label)}">{html.escape(label)}</button></form>'
    )


def render_deletable_rei_items(area: str, topic: str, items: list[object], empty: str = "Nenhum item personalizado neste tópico.") -> str:
    if not items:
        return f'<div class="subblock"><span class="source-label">Personalizado</span><div class="empty">{html.escape(empty)}</div></div>'
    pills = []
    for item in items:
        label = str(item.get("label", item)).strip() if isinstance(item, dict) else str(item).strip()
        if not label:
            continue
        delete_form = render_delete_form(
            "/admin/items/rei-item/delete",
            {"area": area, "topic": topic, "label": label},
        )
        pills.append(render_field_pill(item, "Texto curto", delete_form))
    if not pills:
        return f'<div class="subblock"><span class="source-label">Personalizado</span><div class="empty">{html.escape(empty)}</div></div>'
    return f'<div class="subblock"><span class="source-label">Personalizado</span><div class="field-pill-list">{"".join(pills)}</div></div>'


def render_editable_survey_fields(
    topic: str,
    fields: list[dict],
    source: str,
    empty: str,
    allow_delete: bool,
) -> str:
    if not fields:
        return f'<div class="subblock"><span class="source-label">{html.escape(source)}</span><div class="empty">{html.escape(empty)}</div></div>'
    pills = []
    for field in fields:
        key = str(field.get("key", "")).strip()
        label = str(field.get("label", "")).strip()
        if not key or not label:
            continue
        controls = render_survey_edit(field, topic)
        if allow_delete:
            controls += render_delete_form(
                "/admin/items/survey-item/delete",
                {"topic": topic, "key": key},
            )
        pills.append(render_field_pill(field, "Texto curto", controls))
    if not pills:
        return f'<div class="subblock"><span class="source-label">{html.escape(source)}</span><div class="empty">{html.escape(empty)}</div></div>'
    return f'<div class="subblock"><span class="source-label">{html.escape(source)}</span><div class="field-pill-list">{"".join(pills)}</div></div>'


def render_rei_area(area: str, default_groups: list[dict], custom_groups: list[dict]) -> str:
    titles: list[str] = []
    for group in default_groups + custom_groups:
        title = str(group.get("title", "")).strip()
        if title and not any(existing.lower() == title.lower() for existing in titles):
            titles.append(title)
    if not titles:
        return '<div class="empty">Nenhum tópico cadastrado.</div>'
    blocks = []
    for title in titles:
        default = next((group for group in default_groups if str(group.get("title", "")).strip().lower() == title.lower()), {"items": []})
        custom_group = next((group for group in custom_groups if str(group.get("title", "")).strip().lower() == title.lower()), None)
        custom = custom_group or {"items": []}
        blocks.append(
            f'<div class="topic"><div class="topic-head"><strong>{html.escape(title)}</strong></div>'
            + render_item_source("Padrão", default.get("items", []), "Sem itens padrão neste tópico.", "Caixa de seleção")
            + render_deletable_rei_items(area, title, custom.get("items", []), "Nenhum item personalizado neste tópico.")
            + "</div>"
        )
    return "".join(blocks)


def render_survey_sections(default_sections: list[dict], custom_sections: list[dict]) -> str:
    titles: list[str] = []
    for section in default_sections + custom_sections:
        title = str(section.get("title", "")).strip()
        if title and not any(existing.lower() == title.lower() for existing in titles):
            titles.append(title)
    if not titles:
        return '<div class="empty">Nenhum campo cadastrado.</div>'
    blocks = []
    for title in titles:
        default = next((section for section in default_sections if str(section.get("title", "")).strip().lower() == title.lower()), {"fields": []})
        custom_section = next((section for section in custom_sections if str(section.get("title", "")).strip().lower() == title.lower()), None)
        custom = custom_section or {"fields": []}
        default_items = default.get("fields", [])
        custom_items = custom.get("fields", [])
        custom_by_key = {str(field.get("key", "")).strip(): field for field in custom_items}
        default_keys = {str(field.get("key", "")).strip() for field in default_items}
        effective_defaults = [custom_by_key.get(str(field.get("key", "")).strip(), field) for field in default_items]
        custom_only = [field for field in custom_items if str(field.get("key", "")).strip() not in default_keys]
        blocks.append(
            f'<div class="topic"><div class="topic-head"><strong>{html.escape(title)}</strong></div>'
            + render_editable_survey_fields(title, effective_defaults, "Padrão", "Sem campos padrão neste tópico.", False)
            + render_editable_survey_fields(title, custom_only, "Personalizado", "Nenhum campo personalizado neste tópico.", True)
            + "</div>"
        )
    return "".join(blocks)


def connect() -> sqlite3.Connection:
    connection = sqlite3.connect(DATABASE, timeout=20)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA journal_mode=WAL")
    connection.execute("PRAGMA foreign_keys=ON")
    return connection


def initialize_database() -> None:
    with connect() as db:
        db.executescript(
            """
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
                payload_json TEXT NOT NULL
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
            """
        )
        report_columns = {row[1] for row in db.execute("PRAGMA table_info(reports)")}
        if "created_by_user_id" not in report_columns:
            db.execute("ALTER TABLE reports ADD COLUMN created_by_user_id INTEGER")


def password_hash(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 210_000)
    return f"{salt.hex()}:{digest.hex()}"


def password_valid(password: str, stored: str) -> bool:
    try:
        salt_hex, expected_hex = stored.split(":", 1)
        digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), bytes.fromhex(salt_hex), 210_000)
        return hmac.compare_digest(digest.hex(), expected_hex)
    except (ValueError, TypeError):
        return False


def create_user(username: str, full_name: str, password: str, role: str) -> int:
    username = username.strip().lower()
    full_name = full_name.strip()
    if len(username) < 3 or not username.replace("_", "").replace(".", "").isalnum():
        raise ValueError("Usuário deve ter ao menos 3 caracteres e usar apenas letras, números, ponto ou sublinhado")
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
        row = db.execute("SELECT * FROM users WHERE username=? AND active=1", (username.strip().lower(),)).fetchone()
    return row if row and password_valid(password, row["password_hash"]) else None


def validate_new_password(password: str, confirmation: str) -> None:
    if len(password) < 8:
        raise ValueError("A nova senha deve ter ao menos 8 caracteres")
    if password != confirmation:
        raise ValueError("A confirmação da nova senha não confere")


def change_user_password(user_id: int, current_password: str, new_password: str, confirmation: str) -> None:
    validate_new_password(new_password, confirmation)
    with connect() as db:
        user = db.execute("SELECT password_hash FROM users WHERE id=? AND active=1", (user_id,)).fetchone()
        if not user or not password_valid(current_password, user["password_hash"]):
            raise ValueError("A senha atual está incorreta")
        db.execute(
            "UPDATE users SET password_hash=?,updated_at=? WHERE id=?",
            (password_hash(new_password), datetime.now(timezone.utc).isoformat(), user_id),
        )


def reset_user_password(user_id: int, new_password: str, confirmation: str) -> None:
    validate_new_password(new_password, confirmation)
    with connect() as db:
        cursor = db.execute(
            "UPDATE users SET password_hash=?,updated_at=? WHERE id=?",
            (password_hash(new_password), datetime.now(timezone.utc).isoformat(), user_id),
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
            (token_digest, user_id, (now + timedelta(days=30)).isoformat(), now.isoformat()),
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
            db.execute("DELETE FROM sessions WHERE token_hash=?", (hashlib.sha256(token.encode()).hexdigest(),))


def users_count() -> int:
    with connect() as db:
        return int(db.execute("SELECT COUNT(*) FROM users").fetchone()[0])


def list_users(role: str | None = None) -> list[dict]:
    where = "WHERE active=1"
    params: list[object] = []
    if role in {"supervisor", "implantador"}:
        where += " AND role=?"
        params.append(role)
    with connect() as db:
        return [dict(row) for row in db.execute(
            f"SELECT id, username, full_name, role FROM users {where} ORDER BY full_name, username",
            params,
        )]


def save_report(payload: dict, created_by_user_id: int | None = None) -> str:
    report_id = str(payload.get("reportId") or "").strip()
    report = payload.get("report") or {}
    fields = report.get("fields") or {}
    if not report_id:
        raise ValueError("reportId é obrigatório")
    client = str(fields.get("cliente") or "").strip()
    if not client:
        raise ValueError("cliente é obrigatório")

    now = datetime.now(timezone.utc).isoformat()
    completed_at = int(payload.get("completedAt") or 0)
    checks = list(dict.fromkeys(report.get("checks") or []))
    attachments = report.get("attachments") or []
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))

    with connect() as db:
        db.execute(
            """
            INSERT INTO reports (
                id, client, consultant, started_at, ended_at, contracted_days,
                used_days, delivery_status, services_executed, pending_issues,
                checked_items, completed_at, received_at, updated_at, payload_json, created_by_user_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                created_by_user_id=COALESCE(reports.created_by_user_id, excluded.created_by_user_id)
            """,
            (
                report_id, client, str(fields.get("consultor") or ""),
                fields.get("inicio"), fields.get("termino"), fields.get("diasContratados"),
                fields.get("diasUtilizados"), str(report.get("deliveryStatus") or ""),
                str(fields.get("servicosExecutados") or ""), str(fields.get("pendencias") or ""),
                len(checks), completed_at, now, now, raw, created_by_user_id,
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
            [(
                report_id,
                str(item.get("name") or "Arquivo"),
                str(item.get("mimeType") or "application/octet-stream"),
                str(item.get("uri") or ""),
            ) for item in attachments],
        )
    return report_id


def reports_csv() -> bytes:
    output = io.StringIO()
    columns = [
        "id", "client", "consultant", "started_at", "ended_at", "contracted_days",
        "used_days", "delivery_status", "checked_items", "completed_at", "received_at",
        "created_by_username", "created_by_name",
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


def list_reports_for_user(user: dict, limit: int = 100, full: bool = False) -> list[dict]:
    limit = min(max(limit, 1), 1000)
    select_payload = ",r.payload_json" if full else ""
    params: list[object] = [limit]
    with connect() as db:
        rows = []
        for row in db.execute(
            "SELECT r.id,r.client,r.consultant,r.started_at,r.ended_at,r.delivery_status,r.checked_items,"
            "r.completed_at,r.received_at,u.username AS created_by_username,u.full_name AS created_by_name "
            f"{select_payload} FROM reports r LEFT JOIN users u ON u.id=r.created_by_user_id "
            "ORDER BY r.completed_at DESC LIMIT ?",
            params,
        ):
            item = dict(row)
            if full:
                payload = json.loads(item.pop("payload_json") or "{}")
                fields = (payload.get("report") or {}).get("fields") or {}
                if user["role"] != "supervisor":
                    username = str(user["username"]).strip().lower()
                    created_by = str(item.get("created_by_username") or "").strip().lower()
                    owner = str(fields.get("_ownerUsername") or "").strip().lower()
                    assigned = str(fields.get("_assignedImplantadorUsername") or "").strip().lower()
                    stage = str(fields.get("_stage") or "").strip()
                    if stage == "levantamento_pendente" and assigned and assigned != username:
                        continue
                    if stage != "levantamento_pendente" and username not in {created_by, owner, assigned}:
                        continue
                item["payload"] = payload
                item["report"] = payload.get("report") or {}
            elif user["role"] != "supervisor" and str(item.get("created_by_username") or "").strip().lower() != str(user["username"]).strip().lower():
                continue
            rows.append(item)
    return rows


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
    header{background:#fff;border-bottom:1px solid var(--line);padding:18px 5%;display:flex;align-items:center;justify-content:space-between}
    .brand{display:flex;align-items:center;gap:12px}.brand img{width:46px;height:46px;object-fit:contain;display:block}.login .brand{justify-content:center;width:100%}.login .brand img{width:128px;height:128px}
    nav{display:flex;gap:8px;flex-wrap:wrap}.nav{display:inline-flex;align-items:center;justify-content:center;text-decoration:none;border-radius:11px;padding:10px 14px;font-weight:800;color:var(--navy);background:#eef1f7}.nav.active{background:var(--navy);color:#fff}.nav.gear{width:42px;padding:10px}.nav.gear svg{width:19px;height:19px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
    main{max-width:1080px;margin:34px auto;padding:0 20px}.hero{background:linear-gradient(135deg,var(--dark),var(--navy));color:#fff;border-radius:24px;padding:28px;margin-bottom:22px}
    .hero h1{margin:0 0 7px}.hero p{margin:0;color:#d7def7}.grid{display:grid;grid-template-columns:360px 1fr;gap:20px}.card{background:#fff;border:1px solid var(--line);border-radius:20px;padding:22px}
    h2{margin:0 0 17px;font-size:19px}label{display:block;font-size:12px;font-weight:700;color:#596174;margin:12px 0 6px}
    input,select{width:100%;padding:12px;border:1px solid #cfd5e2;border-radius:11px;font-size:14px;background:#fbfcff}
    button{border:0;border-radius:11px;padding:12px 17px;font-weight:700;cursor:pointer;background:var(--navy);color:#fff}.full{width:100%;margin-top:18px}
    .logout{background:#eef1f7;color:var(--navy)}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:12px 10px;border-bottom:1px solid var(--line);font-size:13px}
    th{color:var(--muted);font-size:11px;text-transform:uppercase}.badge{display:inline-block;padding:5px 9px;border-radius:20px;background:#e9f5e6;color:#3b7131;font-size:11px;font-weight:700}
    .badge.implantador{background:#e9edfb;color:var(--navy)}.notice,.error{padding:12px 15px;border-radius:11px;margin-bottom:15px}.notice{background:#e9f5e6;color:#35682d}.error{background:#fdeaea;color:#9a3030}
    .login{max-width:430px;margin:70px auto}.muted{color:var(--muted);font-size:13px}.user-actions{display:flex;align-items:flex-start;gap:8px;flex-wrap:wrap}.password-reset{position:relative}.password-reset summary{list-style:none;cursor:pointer;border-radius:11px;padding:12px 14px;font-weight:700;color:var(--navy);background:#eef1f7;white-space:nowrap}.password-reset summary::-webkit-details-marker{display:none}.password-reset[open] form{position:absolute;right:0;top:48px;z-index:4;width:260px;padding:14px;background:#fff;border:1px solid var(--line);border-radius:14px;box-shadow:0 16px 35px rgba(23,38,83,.18)}.password-reset form label{margin-top:0}.password-reset form input{margin-bottom:10px}.password-reset form button{width:100%}@media(max-width:800px){.grid{grid-template-columns:1fr}.password-reset[open] form{position:fixed;left:18px;right:18px;top:20%;width:auto}}
    html[data-theme="dark"]{--navy:#9bb0ff;--dark:#101933;--green:#75c361;--bg:#0d1220;--line:#343d51;--muted:#aeb8ce;color-scheme:dark}html[data-theme="dark"] body{color:#eef2ff}html[data-theme="dark"] header,html[data-theme="dark"] .card,html[data-theme="dark"] .password-reset[open] form{background:#171d2b;border-color:var(--line)}html[data-theme="dark"] input,html[data-theme="dark"] select{background:#111725;border-color:#3b465d;color:#eef2ff}html[data-theme="dark"] .logout,html[data-theme="dark"] .password-reset summary{background:#222a3b;color:#dbe3ff}html[data-theme="dark"] th,html[data-theme="dark"] td{border-color:var(--line)}
    </style><script>!function(){var m=localStorage.getItem("reiTheme")||"system",q=matchMedia("(prefers-color-scheme: dark)"),a=function(){var d=m==="dark"||(m==="system"&&q.matches);document.documentElement.dataset.theme=d?"dark":"light"};a();q.addEventListener&&q.addEventListener("change",a)}();</script></head><body>"""
    base_end = "</main></body></html>"
    if users_count() == 0:
        return base_start + """<main class="login"><div class="card"><div class="brand"><img src="/web/assets/logo_dubrasil.png" alt="DuBrasil Soluções"></div>
        <h2 style="margin-top:25px">Criar supervisor inicial</h2><p class="muted">Este primeiro usuário administrará os demais acessos.</p>""" + alert + """
        <form method="post" action="/admin/setup"><label>Nome completo</label><input name="full_name" required minlength="3">
        <label>Usuário</label><input name="username" required minlength="3" autocomplete="username"><label>Senha</label>
        <input type="password" name="password" required minlength="8" autocomplete="new-password"><button class="full">Criar supervisor</button></form></div>""" + base_end
    if not user:
        return base_start + """<main class="login"><div class="card"><div class="brand"><img src="/web/assets/logo_dubrasil.png" alt="DuBrasil Soluções"></div>
        <h2 style="margin-top:25px">Acesso ao R.E.I.</h2><p class="muted">O perfil do usuário define automaticamente a área que será aberta.</p>""" + alert + """<form method="post" action="/login">
        <label>Usuário</label><input name="username" required autocomplete="username"><label>Senha</label>
        <input type="password" name="password" required autocomplete="current-password"><button class="full">Entrar</button></form>
        <script>localStorage.removeItem('reiToken');</script></div>""" + base_end
    if user["role"] != "supervisor":
        return base_start + '<main class="login"><div class="card"><h2>Acesso restrito</h2><p>Somente supervisores podem administrar usuários.</p></div>' + base_end
    with connect() as db:
        users = db.execute("SELECT id,username,full_name,role,active,created_at FROM users ORDER BY full_name").fetchall()
    rows = "".join(
        f"<tr><td><strong>{html.escape(row['full_name'])}</strong><br><span class='muted'>@{html.escape(row['username'])}</span></td>"
        f"<td><span class='badge {row['role']}'>{html.escape(row['role'].title())}</span></td>"
        f"<td>{'Ativo' if row['active'] else 'Inativo'}</td>"
        f"<td><div class='user-actions'><form method='post' action='/admin/users/toggle'><input type='hidden' name='id' value='{row['id']}'>"
        f"<button class='logout'>{'Desativar' if row['active'] else 'Ativar'}</button></form>"
        f"<details class='password-reset'><summary>Alterar senha</summary><form method='post' action='/admin/users/password'>"
        f"<input type='hidden' name='id' value='{row['id']}'><label>Nova senha</label><input type='password' name='new_password' required minlength='8' autocomplete='new-password'>"
        f"<label>Confirmar nova senha</label><input type='password' name='confirmation' required minlength='8' autocomplete='new-password'>"
        f"<button type='submit'>Salvar nova senha</button></form></details></div></td></tr>" for row in users
    )
    return base_start + f"""<header><div class="brand"><img src="/web/assets/logo_dubrasil.png" alt="DuBrasil Soluções"><nav><a class="nav" href="/web">Painel</a><a class="nav active" href="/admin">Usuários</a><a class="nav" href="/admin/items">Itens dos relatórios</a><a class="nav gear" href="/web#settings" title="Configurações da conta" aria-label="Configurações da conta"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06a1.7 1.7 0 0 0-1.88-.34A1.7 1.7 0 0 0 14 20.92V21h-4v-.08A1.7 1.7 0 0 0 9 19.37a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.63 15 1.7 1.7 0 0 0 3.08 14H3v-4h.08A1.7 1.7 0 0 0 4.63 9a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 9 4.63 1.7 1.7 0 0 0 10 3.08V3h4v.08A1.7 1.7 0 0 0 15 4.63a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.37 9c.2.61.77 1.02 1.55 1.02H21v4h-.08A1.7 1.7 0 0 0 19.4 15z"/></svg></a></nav></div>
    <form method="post" action="/admin/logout"><button class="logout">Sair</button></form></header><main>
    <div class="hero"><h1>Gestão de usuários</h1><p>Cadastre supervisores e implantadores que terão acesso ao aplicativo.</p></div>{notice}{alert}
    <div class="grid"><section class="card"><h2>Novo usuário</h2><form method="post" action="/admin/users">
    <label>Nome completo</label><input name="full_name" required minlength="3"><label>Usuário</label><input name="username" required minlength="3">
    <label>Perfil</label><select name="role"><option value="implantador">Implantador</option><option value="supervisor">Supervisor</option></select>
    <label>Senha provisória</label><input type="password" name="password" required minlength="8"><button class="full">Cadastrar usuário</button></form></section>
    <section class="card"><h2>Usuários cadastrados</h2><div style="overflow:auto"><table><thead><tr><th>Usuário</th><th>Perfil</th><th>Status</th><th>Ação</th></tr></thead><tbody>{rows}</tbody></table></div></section></div>""" + base_end


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
    header{position:sticky;top:0;z-index:5;background:rgba(255,255,255,.96);backdrop-filter:blur(10px);border-bottom:1px solid var(--line);padding:10px 4%;display:flex;align-items:center;justify-content:space-between;gap:12px}
    .brand{display:flex;align-items:center;gap:12px}.brand img{width:42px;height:42px;object-fit:contain;display:block}
    nav{display:flex;gap:8px;flex-wrap:wrap}.nav{display:inline-flex;align-items:center;justify-content:center;text-decoration:none;border-radius:999px;padding:9px 13px;font-weight:800;color:var(--navy);background:#eef1f7;font-size:13px}.nav.active{background:var(--navy);color:#fff}.nav.gear{width:38px;padding:9px}.nav.gear svg{width:18px;height:18px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
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
    .field-edit{display:inline-flex}.field-edit summary{list-style:none;cursor:pointer;padding:4px 7px;border-radius:999px;background:#fff;color:var(--navy);font-size:10px;font-weight:900;line-height:1}.field-edit summary::-webkit-details-marker{display:none}.field-edit[open]:before{content:"";position:fixed;inset:0;z-index:20;background:rgba(10,18,38,.58);backdrop-filter:blur(2px)}.field-edit form{display:none}.field-edit[open] form{position:fixed;z-index:21;left:50%;top:50%;transform:translate(-50%,-50%);display:grid;gap:9px;width:min(440px,calc(100vw - 28px));padding:18px;border:1px solid var(--line);border-radius:18px;background:#fff;box-shadow:0 24px 70px rgba(10,18,38,.3);text-align:left}.field-edit form h3{margin:0 0 4px;font-size:18px}.edit-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:4px}.cancel-edit{background:#eef1f7;color:var(--navy)}
    .topic{border:1px solid var(--line);border-radius:15px;padding:12px;margin:9px 0;background:var(--soft)}.topic-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:8px}.topic strong{display:block;color:#172653;font-size:13px}.delete{background:#eef1f7;color:#c0392b;font-size:12px;padding:8px 10px;border-radius:10px}.area-title{display:flex;align-items:center;gap:8px;margin:14px 0 8px;color:var(--navy);font-size:14px}.area-title:before{content:"";width:7px;height:22px;border-radius:999px;background:var(--green)}
    .subblock{padding:8px 0;border-top:1px solid rgba(225,229,238,.75)}.subblock:first-of-type{border-top:0}.source-label{display:inline-flex;margin:0 0 6px;padding:4px 8px;border-radius:999px;background:#fff;color:#66708a;font-size:10px;font-weight:900;text-transform:uppercase;letter-spacing:.06em}
    .empty{display:flex;align-items:center;min-height:44px;padding:10px 12px;border:1px dashed #cfd5e2;border-radius:13px;background:#fbfcff;color:var(--muted);font-size:13px}
    @media(max-width:1100px){.forms-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.lists-grid{grid-template-columns:1fr}}
    @media(max-width:700px){header{align-items:flex-start;flex-direction:column}.hero{display:block}.item-switch,.forms-grid{grid-template-columns:1fr}.two-col{grid-template-columns:1fr}main{padding:0 12px 24px}.card{padding:14px}}
    html[data-theme="dark"]{--navy:#9bb0ff;--dark:#101933;--green:#75c361;--bg:#0d1220;--line:#343d51;--muted:#aeb8ce;--soft:#121827;color-scheme:dark}html[data-theme="dark"] body{color:#eef2ff}html[data-theme="dark"] header,html[data-theme="dark"] .card,html[data-theme="dark"] .item-switch button,html[data-theme="dark"] .source-label,html[data-theme="dark"] .field-edit[open] form{background:#171d2b;border-color:var(--line);color:#eef2ff}html[data-theme="dark"] input,html[data-theme="dark"] select,html[data-theme="dark"] textarea,html[data-theme="dark"] .empty{background:#111725;border-color:#3b465d;color:#eef2ff}html[data-theme="dark"] .form-card h2,html[data-theme="dark"] .list-card h2,html[data-theme="dark"] .topic strong,html[data-theme="dark"] .area-title{color:#dbe3ff}html[data-theme="dark"] .pill,html[data-theme="dark"] .delete,html[data-theme="dark"] .logout,html[data-theme="dark"] .field-edit summary,html[data-theme="dark"] .cancel-edit{background:#222a3b;color:#dbe3ff}
    </style><script>
    !function(){var m=localStorage.getItem("reiTheme")||"system",q=matchMedia("(prefers-color-scheme: dark)"),a=function(){var d=m==="dark"||(m==="system"&&q.matches);document.documentElement.dataset.theme=d?"dark":"light"};a();q.addEventListener&&q.addEventListener("change",a)}();
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
        return base_start + '<main><section class="card"><h2>Acesso restrito</h2><p>Somente supervisores podem gerenciar os itens.</p><p><a class="nav" href="/admin">Voltar</a></p></section>' + base_end
    data = load_schema_items()
    defaults = load_default_schema_items()
    area_options = "".join(f'<option value="{html.escape(key)}">{html.escape(label)}</option>' for key, label in REI_ITEM_AREAS.items())
    rei_topics_by_area: dict[str, list[str]] = {}
    for area in REI_ITEM_AREAS:
        titles: list[str] = []
        if area != "modules":
            for group in defaults["rei"][area] + data["rei"][area]:
                title = str(group.get("title", "")).strip()
                if title and not any(existing.lower() == title.lower() for existing in titles):
                    titles.append(title)
        rei_topics_by_area[area] = titles
    rei_topics_json = json.dumps(rei_topics_by_area, ensure_ascii=False).replace("<", "\\u003c")
    rei_blocks = []
    for area, label in REI_ITEM_AREAS.items():
        custom = data["rei"][area]
        if area == "modules":
            body = (
                render_item_source("Padrão", defaults["rei"]["modules"], "Nenhum módulo padrão cadastrado.", "Caixa de seleção")
                + render_deletable_rei_items("modules", "", custom, "Nenhum módulo personalizado cadastrado.")
            )
        else:
            body = render_rei_area(area, defaults["rei"][area], custom)
        rei_blocks.append(f'<h3 class="area-title">{html.escape(label)}</h3>{body}')
    levantamento_blocks = render_survey_sections(defaults["levantamento"], data["levantamento"])
    type_options = "".join(f'<option value="{html.escape(key)}">{html.escape(label)}</option>' for key, label in SURVEY_FIELD_TYPES.items())
    rei_total = len(data["rei"]["modules"]) + sum(
        len(group.get("items", []))
        for area, groups in data["rei"].items()
        if area != "modules"
        for group in groups
    )
    levantamento_total = sum(len(section.get("fields", [])) for section in data["levantamento"])
    return base_start + f"""<header><div class="brand"><img src="/web/assets/logo_dubrasil.png" alt="DuBrasil Soluções">
    <nav><a class="nav" href="/web">Painel</a><a class="nav" href="/admin">Usuários</a><a class="nav active" href="/admin/items">Itens dos relatórios</a><a class="nav gear" href="/web#settings" title="Configurações da conta" aria-label="Configurações da conta"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06a1.7 1.7 0 0 0-1.88-.34A1.7 1.7 0 0 0 14 20.92V21h-4v-.08A1.7 1.7 0 0 0 9 19.37a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.63 15 1.7 1.7 0 0 0 3.08 14H3v-4h.08A1.7 1.7 0 0 0 4.63 9a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 9 4.63 1.7 1.7 0 0 0 10 3.08V3h4v.08A1.7 1.7 0 0 0 15 4.63a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.37 9c.2.61.77 1.02 1.55 1.02H21v4h-.08A1.7 1.7 0 0 0 19.4 15z"/></svg></a></nav></div>
    <form method="post" action="/admin/logout"><button class="logout">Sair</button></form></header><main>
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
        <div class="two-col"><div class="field-row"><label>Tipo</label><select name="type">{type_options}</select></div><div class="field-row"><label>Campo</label><input name="label" required placeholder="Ex.: Conferir parametrização de comissão"></div></div>
        <div class="field-row"><label>Opções da múltipla escolha</label><textarea name="options" placeholder="Sim, Não, Parcial"></textarea></div>
        <button class="full">Cadastrar item</button></form></section>
      <section class="card form-card is-hidden" data-item-form="levantamento"><h2>Tópico do levantamento</h2><p class="card-subtitle">Crie uma nova etapa para o formulário de levantamento.</p><form method="post" action="/admin/items/survey-topic" class="fields">
        <div class="field-row"><label>Nome do tópico</label><input name="topic" required placeholder="Ex.: Comercial"></div><button class="full">Cadastrar tópico</button></form></section>
      <section class="card form-card is-hidden" data-item-form="levantamento"><h2>Campo do levantamento</h2><p class="card-subtitle">Inclua texto, múltipla escolha, data, foto ou data/hora.</p><form method="post" action="/admin/items/survey-item" class="fields">
        <div class="two-col"><div class="field-row"><label>Tópico</label><input name="topic" required placeholder="Ex.: Financeiro"></div><div class="field-row"><label>Tipo</label><select name="type">{type_options}</select></div></div>
        <div class="field-row"><label>Campo</label><input name="label" required placeholder="Ex.: Utiliza cobrança recorrente?"></div>
        <div class="field-row"><label>Opções da múltipla escolha</label><textarea name="options" placeholder="Sim, Não, Parcial"></textarea></div>
        <button class="full">Cadastrar campo</button></form></section>
    </div>
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
    </div>""" + base_end


def admin_query(kind: str, text: str) -> str:
    return f"/admin?{kind}={quote_plus(text)}"


class LegacyReiHandler(BaseHTTPRequestHandler):
    server_version = "REI-Office/1.0"

    def log_message(self, fmt: str, *args) -> None:
        logging.info("%s - %s", self.address_string(), fmt % args)

    def authorized(self) -> bool:
        return self.headers.get("X-API-Key", "") == CONFIG.get("api_key")

    def send_json(self, status: int, value: dict | list, cookie: str | None = None) -> None:
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
            limit = min(max(int(parse_qs(parsed.query).get("limit", [100])[0]), 1), 1000)
            with connect() as db:
                rows = [dict(row) for row in db.execute(
                    "SELECT id, client, consultant, started_at, ended_at, delivery_status, "
                    "checked_items, completed_at, received_at FROM reports ORDER BY completed_at DESC LIMIT ?",
                    (limit,),
                )]
            self.send_json(200, rows)
            return
        if parsed.path == "/api/bi/reports.csv":
            body = reports_csv()
            self.send_response(200)
            self.send_header("Content-Type", "text/csv; charset=utf-8")
            self.send_header("Content-Disposition", "attachment; filename=rei_reports.csv")
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
            report_id = save_report(payload)
            self.send_json(200, {"status": "saved", "reportId": report_id})
        except (ValueError, json.JSONDecodeError) as error:
            self.send_json(400, {"error": str(error)})
        except Exception:
            logging.exception("Erro ao salvar relatório")
            self.send_json(500, {"error": "erro interno"})


class ReiHandler(BaseHTTPRequestHandler):
    server_version = "REI-Office/2.0"

    def log_message(self, fmt: str, *args) -> None:
        logging.info("%s - %s", self.address_string(), fmt % args)

    def send_json(self, status: int, value: dict | list, cookie: str | None = None) -> None:
        body = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        if cookie:
            self.send_header("Set-Cookie", cookie)
        self.end_headers()
        self.wfile.write(body)

    def send_html(self, content: str, status: int = 200, cookie: str | None = None) -> None:
        body = content.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        if cookie:
            self.send_header("Set-Cookie", cookie)
        self.end_headers()
        self.wfile.write(body)

    def send_static(self, path: str) -> None:
        relative = "index.html" if path in {"/web", "/web/"} else path.removeprefix("/web/")
        target = (WEB_ROOT / relative).resolve()
        root = WEB_ROOT.resolve()
        if target != root and root not in target.parents:
            self.send_json(403, {"error": "acesso negado"})
            return
        if not target.exists() or not target.is_file():
            self.send_json(404, {"error": "arquivo não encontrado"})
            return
        body = target.read_bytes()
        content_type = mimetypes.guess_type(target.name)[0] or "application/octet-stream"
        if content_type.startswith("text/") or target.suffix in {".js", ".css", ".json", ".svg"}:
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
            return {"id": None, "username": "api", "full_name": "Integração BI", "role": "supervisor", "active": 1}
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
            self.send_html(admin_html(
                user, query.get("message", [""])[0], query.get("error", [""])[0]
            ))
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
            self.send_html(admin_items_html(
                user, query.get("message", [""])[0], query.get("error", [""])[0]
            ))
            return
        if parsed.path == "/health":
            self.send_json(200, {"status": "ok", "database": DATABASE.name, "users": users_count()})
            return
        if parsed.path == "/api/auth/me":
            user = self.request_user()
            if not user:
                self.send_json(401, {"error": "não autorizado"})
            else:
                self.send_json(200, {"user": user})
            return
        if parsed.path == "/api/reports":
            user = self.request_user() or self.api_supervisor()
            if not user:
                self.send_json(401, {"error": "não autorizado"})
                return
            query = parse_qs(parsed.query)
            self.send_json(200, list_reports_for_user(
                user,
                int(query.get("limit", [100])[0]),
                query.get("full", ["0"])[0] in {"1", "true", "yes"},
            ))
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
            self.send_json(200, load_schema_items())
            return
        if parsed.path == "/api/bi/reports.csv":
            if not self.api_supervisor():
                self.send_json(403, {"error": "acesso exclusivo para supervisor"})
                return
            body = reports_csv()
            self.send_response(200)
            self.send_header("Content-Type", "text/csv; charset=utf-8")
            self.send_header("Content-Disposition", "attachment; filename=rei_reports.csv")
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
                user_id = create_user(form.get("username", ""), form.get("full_name", ""), form.get("password", ""), "supervisor")
                token = create_session(user_id)
                self.redirect(admin_query("message", "Supervisor criado com sucesso"), f"rei_session={token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000")
            except ValueError as error:
                self.send_html(admin_html(None, error=str(error)), 400)
            return
        if parsed.path in {"/login", "/admin/login"}:
            form = self.read_form()
            user = authenticate(form.get("username", ""), form.get("password", ""))
            if not user:
                self.send_html(admin_html(None, error="Usuário ou senha inválidos"), 401)
                return
            token = create_session(int(user["id"]))
            self.redirect("/web", f"rei_session={token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000")
            return
        if parsed.path == "/admin/logout":
            revoke_session(self.request_token())
            self.redirect("/login", "rei_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0")
            return
        if parsed.path == "/admin/users":
            user = self.request_user()
            if not user or user["role"] != "supervisor":
                self.redirect(admin_query("error", "Acesso negado"))
                return
            try:
                form = self.read_form()
                create_user(form.get("username", ""), form.get("full_name", ""), form.get("password", ""), form.get("role", ""))
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
                self.redirect(admin_query("error", "Você não pode desativar seu próprio usuário"))
                return
            with connect() as db:
                db.execute("UPDATE users SET active=CASE active WHEN 1 THEN 0 ELSE 1 END,updated_at=? WHERE id=?", (datetime.now(timezone.utc).isoformat(), target_id))
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
        if parsed.path in {"/admin/items/rei-topic/delete", "/admin/items/survey-topic/delete"}:
            user = self.request_user()
            if not user or user["role"] != "supervisor":
                self.redirect(schema_redirect("error", "Acesso negado"))
            else:
                self.redirect(schema_redirect("error", "A exclusão de tópicos está bloqueada. Exclua somente os itens cadastrados."))
            return
        if parsed.path in {
            "/admin/items/rei-topic",
            "/admin/items/rei-item",
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
                    add_rei_item(form.get("area", ""), form.get("topic", ""), form.get("label", ""), form.get("type", "text"), form.get("options", ""))
                    message = "Item do R.E.I. cadastrado"
                elif parsed.path == "/admin/items/rei-item/delete":
                    delete_rei_item(form.get("area", ""), form.get("topic", ""), form.get("label", ""))
                    message = "Item do R.E.I. excluído"
                elif parsed.path == "/admin/items/survey-topic":
                    add_survey_topic(form.get("topic", ""))
                    message = "Tópico do levantamento cadastrado"
                elif parsed.path == "/admin/items/survey-item/delete":
                    delete_survey_item(form.get("topic", ""), form.get("key", ""))
                    message = "Campo do levantamento excluído"
                elif parsed.path == "/admin/items/survey-item/edit":
                    update_survey_item(form.get("topic", ""), form.get("key", ""), form.get("label", ""), form.get("type", "text"), form.get("options", ""))
                    message = "Campo do levantamento atualizado"
                else:
                    add_survey_item(form.get("topic", ""), form.get("label", ""), form.get("type", "text"), form.get("options", ""))
                    message = "Campo do levantamento cadastrado"
                self.redirect(schema_redirect("message", message))
            except ValueError as error:
                self.redirect(schema_redirect("error", str(error)))
            return

        if parsed.path == "/api/auth/login":
            try:
                payload = json.loads(self.read_body().decode("utf-8"))
                user = authenticate(str(payload.get("username", "")), str(payload.get("password", "")))
                if not user:
                    self.send_json(401, {"error": "usuário ou senha inválidos"})
                    return
                token = create_session(int(user["id"]))
                self.send_json(200, {"token": token, "user": {
                    "id": user["id"], "username": user["username"], "fullName": user["full_name"], "role": user["role"]
                }}, f"rei_session={token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000")
            except (ValueError, json.JSONDecodeError) as error:
                self.send_json(400, {"error": str(error)})
            return
        if parsed.path == "/api/auth/logout":
            revoke_session(self.request_token())
            self.send_json(200, {"status": "ok"}, "rei_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0")
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
        if parsed.path != "/api/reports":
            self.send_json(404, {"error": "rota não encontrada"})
            return
        user = self.request_user()
        if not user and self.headers.get("X-API-Key", "") != CONFIG.get("api_key"):
            self.send_json(401, {"error": "não autorizado"})
            return
        try:
            payload = json.loads(self.read_body().decode("utf-8"))
            report_id = save_report(payload, user["id"] if user else None)
            self.send_json(200, {"status": "saved", "reportId": report_id})
        except (ValueError, json.JSONDecodeError) as error:
            self.send_json(400, {"error": str(error)})
        except Exception:
            logging.exception("Erro ao salvar relatório")
            self.send_json(500, {"error": "erro interno"})


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    initialize_database()
    host = str(CONFIG.get("host", "0.0.0.0"))
    port = int(CONFIG.get("port", 8765))
    logging.info("Servidor R.E.I. em http://%s:%s", host, port)
    logging.info("Banco central: %s", DATABASE)
    ThreadingHTTPServer((host, port), ReiHandler).serve_forever()


if __name__ == "__main__":
    main()
