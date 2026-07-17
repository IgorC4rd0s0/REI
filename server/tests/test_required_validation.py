from __future__ import annotations

import copy
import gc
import json
import sqlite3
import sys
import tempfile
import unittest
import warnings
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parents[1]
if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))

import rei_server


class RequiredValidationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.previous_database = rei_server.DATABASE
        rei_server.DATABASE = Path(self.temp_dir.name) / "rei_required.db"
        rei_server.initialize_database()
        self.schema = rei_server.load_effective_schema_items()

    def tearDown(self) -> None:
        rei_server.DATABASE = self.previous_database
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", ResourceWarning)
            gc.collect()
        self.temp_dir.cleanup()

    def item(self, label: str) -> dict:
        for area in ("modules", "technical", "stock", "finance", "fiscal", "supervision"):
            value = self.schema["rei"].get(area, [])
            if area == "modules":
                candidates = value
            else:
                candidates = [item for group in value for item in group.get("items", [])]
            for item in candidates:
                if item.get("label") == label:
                    return item
        self.fail(f"Item não encontrado no esquema: {label}")

    def report(self, modules: tuple[str, ...] = ()) -> dict:
        module_keys = [self.item(label)["key"] for label in modules]
        return {
            "fields": {"empresa": "Cliente Teste", "cliente": "Cliente Teste"},
            "checks": module_keys,
            "deliveryStatus": "",
            "rating": "",
            "attachments": [],
        }

    def valid_rei_report(self) -> dict:
        report = self.report()
        report["fields"].update(
            {
                "_stage": "rei",
                "consultor": "Implantador Teste",
                "inicio": "01/07/2026",
                "termino": "02/07/2026",
                "servicosExecutados": "Configuração e treinamento concluídos.",
                "assinaturaAnalistaImagem": "content://assinatura/analista",
                "assinaturaClienteImagem": "content://assinatura/cliente",
            }
        )
        report["deliveryStatus"] = "Concluído"
        return report

    def errors(self, report: dict, phase: str = "rei_completion") -> list[dict]:
        return rei_server.validate_required_requirements(report, self.schema, phase)

    def missing_labels(self, report: dict, phase: str = "rei_completion") -> set[str]:
        return {item["label"] for item in self.errors(report, phase)}

    def test_nfe_requires_certificate_installation(self) -> None:
        self.assertIn(
            "Instalação do certificado no TGA",
            self.missing_labels(self.report(("Nota Fiscal Eletrônica",))),
        )

    def test_nfse_requires_certificate_installation(self) -> None:
        self.assertIn(
            "Instalação do certificado no TGA",
            self.missing_labels(self.report(("Nota Fiscal Eletrônica de Serviço",))),
        )

    def test_nfce_requires_certificate_installation(self) -> None:
        self.assertIn(
            "Instalação do certificado no TGA",
            self.missing_labels(self.report(("Emissão de NFC-e",))),
        )

    def test_certificate_is_not_required_without_fiscal_modules(self) -> None:
        self.assertNotIn(
            "Instalação do certificado no TGA", self.missing_labels(self.report())
        )

    def test_a1_requires_database_insertion_but_a3_does_not(self) -> None:
        a1 = self.report(("Nota Fiscal Eletrônica",))
        a1["fields"]["estoqueCertificado"] = "A1"
        a3 = copy.deepcopy(a1)
        a3["fields"]["estoqueCertificado"] = "A3"
        label = "Certificado A1 inserido no banco de dados"
        self.assertIn(label, self.missing_labels(a1))
        self.assertNotIn(label, self.missing_labels(a3))

    def test_incomplete_draft_can_be_saved(self) -> None:
        payload = {"reportId": "draft-1", "report": self.report()}
        self.assertEqual("draft-1", rei_server.save_report(payload, trusted_api_key=True))

    def test_incomplete_completion_returns_422(self) -> None:
        draft = self.report()
        rei_server.save_report(
            {"reportId": "incomplete-1", "report": draft}, trusted_api_key=True
        )
        concluded = copy.deepcopy(draft)
        concluded["deliveryStatus"] = "Concluído"
        with self.assertRaises(rei_server.ReportWriteRejected) as context:
            rei_server.save_report(
                {"reportId": "incomplete-1", "report": concluded},
                trusted_api_key=True,
            )
        self.assertEqual(422, context.exception.status)
        self.assertEqual("required_items_missing", context.exception.code)
        self.assertGreater(len(context.exception.details), 1)

    def test_valid_completion_is_saved_with_snapshot(self) -> None:
        draft = self.report()
        rei_server.save_report(
            {"reportId": "valid-1", "report": draft}, trusted_api_key=True
        )
        valid = self.valid_rei_report()
        rei_server.save_report(
            {"reportId": "valid-1", "report": valid}, trusted_api_key=True
        )
        with sqlite3.connect(rei_server.DATABASE) as connection:
            stored = json.loads(
                connection.execute(
                    "SELECT payload_json FROM reports WHERE id = ?", ("valid-1",)
                ).fetchone()[0]
            )["report"]
        snapshot = json.loads(stored["fields"]["_requiredValidationSnapshot"])
        self.assertEqual("rei_completion", snapshot["phase"])
        self.assertIn("cliente", snapshot["requiredKeys"])

    def test_supervision_items_do_not_block_rei_completion(self) -> None:
        schema = copy.deepcopy(self.schema)
        item = schema["rei"]["supervision"][0]["items"][0]
        item["requiredMode"] = "always"
        item["requiredWhen"] = None
        self.assertEqual(
            [],
            [
                error
                for error in rei_server.validate_required_requirements(
                    self.valid_rei_report(), schema, "rei_completion"
                )
                if error["key"] == item["key"]
            ],
        )

    def test_required_supervision_item_blocks_evaluation(self) -> None:
        schema = copy.deepcopy(self.schema)
        item = schema["rei"]["supervision"][0]["items"][0]
        item["requiredMode"] = "always"
        item["requiredWhen"] = None
        errors = rei_server.validate_required_requirements(
            self.valid_rei_report(), schema, "supervision_submission"
        )
        self.assertIn(item["key"], {error["key"] for error in errors})

    def test_renamed_label_keeps_stable_and_legacy_checks(self) -> None:
        original = self.item("Configurar backup")
        renamed = copy.deepcopy(original)
        renamed["label"] = "Configurar cópia de segurança"
        renamed["requiredMode"] = "always"
        legacy = f"tecnico::Configuração e cadastros::{original['label']}"
        renamed["legacyKeys"] = list(dict.fromkeys([*renamed.get("legacyKeys", []), legacy]))
        schema = copy.deepcopy(self.schema)
        group = next(
            group
            for group in schema["rei"]["technical"]
            if group["title"] == "Configuração e cadastros"
        )
        group["items"] = [renamed if item["key"] == original["key"] else item for item in group["items"]]
        report = self.valid_rei_report()
        report["checks"] = [legacy]
        errors = rei_server.validate_required_requirements(report, schema, "rei_completion")
        self.assertNotIn(original["key"], {error["key"] for error in errors})

    def test_invalid_rule_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            rei_server.normalize_required_when(
                {
                    "match": "any",
                    "conditions": [
                        {"source": "python", "key": "x", "operator": "eval"}
                    ],
                },
                strict=True,
            )

    def test_any_and_all_conditions(self) -> None:
        nfe = self.item("Nota Fiscal Eletrônica")["key"]
        finance = self.item("Financeiro")["key"]
        report = self.report(("Nota Fiscal Eletrônica",))
        any_rule = rei_server.required_rule(
            "any",
            rei_server.condition("module", nfe, "checked"),
            rei_server.condition("module", finance, "checked"),
        )
        all_rule = copy.deepcopy(any_rule)
        all_rule["match"] = "all"
        self.assertTrue(rei_server.evaluate_required_when(report, any_rule, self.schema))
        self.assertFalse(rei_server.evaluate_required_when(report, all_rule, self.schema))

    def test_repeated_sync_does_not_duplicate_checks(self) -> None:
        report = self.report(("Financeiro",))
        key = report["checks"][0]
        report["checks"] = [key, key, key]
        payload = {"reportId": "dedupe-1", "report": report}
        rei_server.save_report(payload, trusted_api_key=True)
        rei_server.save_report(payload, trusted_api_key=True)
        with sqlite3.connect(rei_server.DATABASE) as connection:
            checks = json.loads(
                connection.execute(
                    "SELECT payload_json FROM reports WHERE id = ?", ("dedupe-1",)
                ).fetchone()[0]
            )["report"]["checks"]
        self.assertEqual([key], checks)

    def test_legacy_plain_text_check_is_read(self) -> None:
        item = self.item("Configurar backup")
        schema = copy.deepcopy(self.schema)
        target = next(
            candidate
            for group in schema["rei"]["technical"]
            for candidate in group["items"]
            if candidate["key"] == item["key"]
        )
        target["requiredMode"] = "always"
        legacy = f"tecnico::Configuração e cadastros::{item['label']}"
        report = self.valid_rei_report()
        report["checks"] = [legacy]
        errors = rei_server.validate_required_requirements(report, schema, "rei_completion")
        self.assertNotIn(item["key"], {error["key"] for error in errors})


if __name__ == "__main__":
    unittest.main()
