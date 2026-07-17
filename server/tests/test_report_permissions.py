from __future__ import annotations

import copy
import gc
import json
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import warnings
from http.server import ThreadingHTTPServer
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parents[1]
if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))

import rei_server


class ReportPermissionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.previous_database = rei_server.DATABASE
        rei_server.DATABASE = Path(self.temp_dir.name) / "rei_test.db"
        rei_server.initialize_database()
        self.alice = self.create_actor("alice", "Alice Implantadora", "implantador")
        self.bob = self.create_actor("bob", "Bob Implantador", "implantador")
        self.supervisor = self.create_actor("super", "Supervisora Teste", "supervisor")

    def tearDown(self) -> None:
        rei_server.DATABASE = self.previous_database
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", ResourceWarning)
            gc.collect()
        self.temp_dir.cleanup()

    def create_actor(self, username: str, full_name: str, role: str) -> dict:
        user_id = rei_server.create_user(username, full_name, "senha-segura", role)
        return {
            "id": user_id,
            "username": username,
            "full_name": full_name,
            "role": role,
            "active": 1,
        }

    def payload(
        self,
        report_id: str,
        actor: dict,
        stage: str = "rei",
        delivery_status: str = "",
        assigned: str | None = None,
    ) -> dict:
        fields = {
            "_id": report_id,
            "_stage": stage,
            "_ownerUsername": actor["username"],
            "cliente": f"Cliente {report_id}",
        }
        if delivery_status.startswith("Conclu"):
            fields.update(
                {
                    "empresa": f"Cliente {report_id}",
                    "consultor": actor["full_name"],
                    "inicio": "01/07/2026",
                    "termino": "02/07/2026",
                    "servicosExecutados": "Implantação concluída para teste.",
                    "assinaturaAnalistaImagem": "content://teste/analista",
                    "assinaturaClienteImagem": "content://teste/cliente",
                }
            )
        if assigned is not None:
            fields["_assignedImplantadorUsername"] = assigned
        return {
            "reportId": report_id,
            "completedAt": 1000,
            "report": {
                "fields": fields,
                "checks": [],
                "deliveryStatus": delivery_status,
                "rating": "",
                "attachments": [],
            },
        }

    def assert_rejected(
        self, status: int, code: str, callback
    ) -> rei_server.ReportWriteRejected:
        with self.assertRaises(rei_server.ReportWriteRejected) as context:
            callback()
        self.assertEqual(status, context.exception.status)
        self.assertEqual(code, context.exception.code)
        return context.exception

    def test_implantador_cannot_change_another_implantador_report(self) -> None:
        original = self.payload("owner-1", self.alice)
        rei_server.save_report(original, self.alice)
        changed = copy.deepcopy(original)
        changed["report"]["fields"]["cliente"] = "Alteração indevida"
        self.assert_rejected(
            403, "not_report_owner", lambda: rei_server.save_report(changed, self.bob)
        )

    def test_implantador_cannot_change_supervision_fields(self) -> None:
        original = self.payload(
            "supervision-1", self.alice, delivery_status="Concluído"
        )
        rei_server.save_report(original, self.alice)
        changed = copy.deepcopy(original)
        changed["report"]["fields"]["_supervisionScore"] = "9.0"
        changed["report"]["fields"]["_supervisionReviewedAt"] = "1001"
        self.assert_rejected(
            403, "supervision_only", lambda: rei_server.save_report(changed, self.alice)
        )

    def test_completed_survey_is_read_only_and_cannot_regress(self) -> None:
        pending = self.payload("survey-1", self.alice, stage="levantamento_pendente")
        rei_server.save_report(pending, self.alice)
        completed = copy.deepcopy(pending)
        completed["report"]["fields"]["_stage"] = "rei_pendente"
        completed["report"]["fields"]["_surveyCompletedAt"] = "1001"
        rei_server.save_report(completed, self.alice)

        edited = copy.deepcopy(completed)
        edited["report"]["fields"]["observacao"] = "Não pode editar"
        self.assert_rejected(
            409, "survey_completed", lambda: rei_server.save_report(edited, self.alice)
        )

        regressed = copy.deepcopy(completed)
        regressed["report"]["fields"]["_stage"] = "levantamento_pendente"
        self.assert_rejected(
            409, "state_conflict", lambda: rei_server.save_report(regressed, self.alice)
        )

    def test_completed_survey_answers_cannot_change_when_rei_starts(self) -> None:
        pending = self.payload(
            "survey-transition-1", self.alice, stage="levantamento_pendente"
        )
        pending["report"]["fields"]["financeiroFluxoCaixa"] = "Sim"
        rei_server.save_report(pending, self.alice)
        completed = copy.deepcopy(pending)
        completed["report"]["fields"]["_stage"] = "rei_pendente"
        completed["report"]["fields"]["_surveyCompletedAt"] = "1001"
        rei_server.save_report(completed, self.alice)

        implementation = copy.deepcopy(completed)
        implementation["report"]["fields"]["_stage"] = "rei"
        implementation["report"]["fields"]["financeiroFluxoCaixa"] = "Não"
        self.assert_rejected(
            409,
            "survey_completed",
            lambda: rei_server.save_report(implementation, self.alice),
        )

    def test_only_supervisor_can_evaluate_and_evaluation_is_locked(self) -> None:
        original = self.payload("evaluation-1", self.alice, delivery_status="Concluído")
        rei_server.save_report(original, self.alice)
        evaluation = copy.deepcopy(original)
        evaluation["report"]["fields"].update(
            {
                "_supervisorName": self.supervisor["full_name"],
                "_supervisionScore": "8.5",
                "_supervisionReviewedAt": "1002",
            }
        )
        evaluation["report"]["rating"] = "Bom trabalho"

        self.assert_rejected(
            403,
            "supervision_only",
            lambda: rei_server.save_report(evaluation, self.alice),
        )
        rei_server.save_report(evaluation, self.supervisor)

        replacement = copy.deepcopy(evaluation)
        replacement["report"]["fields"]["_supervisionScore"] = "10.0"
        self.assert_rejected(
            409,
            "evaluation_locked",
            lambda: rei_server.save_report(replacement, self.supervisor),
        )

    def test_supervisor_cannot_evaluate_unfinished_report(self) -> None:
        original = self.payload("evaluation-2", self.alice)
        rei_server.save_report(original, self.alice)
        evaluation = copy.deepcopy(original)
        evaluation["report"]["fields"].update(
            {
                "_supervisorName": self.supervisor["username"],
                "_supervisionScore": "7.0",
                "_supervisionReviewedAt": "1003",
            }
        )
        self.assert_rejected(
            422,
            "invalid_evaluation",
            lambda: rei_server.save_report(evaluation, self.supervisor),
        )

    def test_forward_skip_is_422_and_completed_report_cannot_be_reopened(self) -> None:
        survey = self.payload("transition-1", self.alice, stage="levantamento_pendente")
        rei_server.save_report(survey, self.alice)
        skipped = copy.deepcopy(survey)
        skipped["report"]["fields"]["_stage"] = "rei"
        self.assert_rejected(
            422,
            "invalid_transition",
            lambda: rei_server.save_report(skipped, self.alice),
        )

        concluded = self.payload(
            "transition-2", self.alice, delivery_status="Concluído"
        )
        rei_server.save_report(concluded, self.alice)
        reopened = copy.deepcopy(concluded)
        reopened["report"]["deliveryStatus"] = "Não concluído"
        self.assert_rejected(
            409,
            "report_already_completed",
            lambda: rei_server.save_report(reopened, self.alice),
        )

    def test_legitimate_survey_flow_and_supervisor_assignment_continue_working(
        self,
    ) -> None:
        survey = self.payload(
            "legitimate-1",
            self.supervisor,
            stage="levantamento_pendente",
            assigned=self.alice["username"],
        )
        survey["report"]["fields"]["_createdBy"] = self.supervisor["username"]
        rei_server.save_report(survey, self.supervisor)

        edited = copy.deepcopy(survey)
        edited["report"]["fields"]["contato"] = "Cliente"
        rei_server.save_report(edited, self.alice)

        completed = copy.deepcopy(edited)
        completed["report"]["fields"]["_stage"] = "rei_pendente"
        completed["report"]["fields"]["_surveyCompletedAt"] = "1004"
        rei_server.save_report(completed, self.alice)

        implementation = copy.deepcopy(completed)
        implementation["report"]["fields"]["_stage"] = "rei"
        implementation["report"]["fields"]["inicio"] = "16/07/2026"
        rei_server.save_report(implementation, self.alice)

    def test_supervisor_cannot_change_survey_answers(self) -> None:
        survey = self.payload(
            "supervisor-survey-1",
            self.supervisor,
            stage="levantamento_pendente",
            assigned=self.alice["username"],
        )
        rei_server.save_report(survey, self.supervisor)
        changed = copy.deepcopy(survey)
        changed["report"]["fields"]["respostaTecnica"] = "Alteração indevida"
        self.assert_rejected(
            403,
            "permission_denied",
            lambda: rei_server.save_report(changed, self.supervisor),
        )

    def test_supervisor_must_assign_active_implantador(self) -> None:
        survey = self.payload(
            "assignment-1",
            self.supervisor,
            stage="levantamento_pendente",
            assigned="usuario-inexistente",
        )
        self.assert_rejected(
            422,
            "invalid_assignment",
            lambda: rei_server.save_report(survey, self.supervisor),
        )

    def test_api_key_keeps_existing_trusted_write_access(self) -> None:
        original = self.payload("api-key-1", self.alice, delivery_status="Concluído")
        rei_server.save_report(original, self.alice)
        integration_update = copy.deepcopy(original)
        integration_update["report"]["deliveryStatus"] = ""
        integration_update["report"]["fields"]["_stage"] = "levantamento_pendente"
        rei_server.save_report(integration_update, trusted_api_key=True)

    def test_invalid_content_returns_422(self) -> None:
        invalid = self.payload("invalid-1", self.alice)
        invalid["report"]["checks"] = {"não": "é lista"}
        self.assert_rejected(
            422, "invalid_content", lambda: rei_server.save_report(invalid, self.alice)
        )

    def test_http_api_returns_consistent_permission_json(self) -> None:
        original = self.payload("http-owner-1", self.alice)
        rei_server.save_report(original, self.alice)
        changed = copy.deepcopy(original)
        changed["report"]["fields"]["cliente"] = "Alteração indevida"
        token = rei_server.create_session(self.bob["id"])

        server = ThreadingHTTPServer(("127.0.0.1", 0), rei_server.ReiHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            request = urllib.request.Request(
                f"http://127.0.0.1:{server.server_port}/api/reports",
                data=json.dumps(changed, ensure_ascii=False).encode("utf-8"),
                headers={
                    "Authorization": f"Bearer {token}",
                    "Content-Type": "application/json; charset=utf-8",
                },
                method="POST",
            )
            with self.assertRaises(urllib.error.HTTPError) as context:
                urllib.request.urlopen(request, timeout=5)
            self.assertEqual(403, context.exception.code)
            response = json.loads(context.exception.read().decode("utf-8"))
            self.assertEqual("not_report_owner", response["code"])
            self.assertTrue(response["error"])
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_implantador_limit_is_applied_after_sql_ownership_filter(self) -> None:
        assigned_old = self.payload(
            "assigned-old",
            self.supervisor,
            stage="levantamento_pendente",
            assigned=self.alice["username"],
        )
        assigned_old["completedAt"] = 100
        rei_server.save_report(assigned_old, self.supervisor)

        for index in range(8):
            newer = self.payload(f"bob-new-{index}", self.bob)
            newer["completedAt"] = 10_000 + index
            rei_server.save_report(newer, self.bob)

        alice_full = rei_server.list_reports_for_user(self.alice, limit=1, full=True)
        alice_summary = rei_server.list_reports_for_user(
            self.alice, limit=1, full=False
        )
        supervisor_latest = rei_server.list_reports_for_user(
            self.supervisor, limit=1, full=True
        )

        self.assertEqual(["assigned-old"], [item["id"] for item in alice_full])
        self.assertEqual(["assigned-old"], [item["id"] for item in alice_summary])
        self.assertEqual(["bob-new-7"], [item["id"] for item in supervisor_latest])
        self.assertNotIn("stage", alice_full[0])
        self.assertNotIn("owner_username", alice_full[0])
        with rei_server.connect() as db:
            row = db.execute(
                "SELECT stage, owner_username, assigned_username, updated_by_username FROM reports WHERE id=?",
                ("assigned-old",),
            ).fetchone()
        self.assertEqual("levantamento_pendente", row["stage"])
        self.assertEqual(self.alice["username"], row["assigned_username"])
        self.assertEqual(self.supervisor["username"], row["updated_by_username"])

    def test_initialize_database_backfills_legacy_payload_and_tolerates_invalid_json(
        self,
    ) -> None:
        legacy_payload = self.payload(
            "legacy-1", self.alice, assigned=self.alice["username"]
        )
        with rei_server.connect() as db:
            db.execute(
                """
                INSERT INTO reports(id, client, completed_at, received_at, updated_at, payload_json)
                VALUES(?,?,?,?,?,?)
                """,
                (
                    "legacy-1",
                    "Cliente legado",
                    1,
                    "2026-01-01",
                    "2026-01-01",
                    json.dumps(legacy_payload),
                ),
            )
            db.execute(
                """
                INSERT INTO reports(id, client, completed_at, received_at, updated_at, payload_json)
                VALUES(?,?,?,?,?,?)
                """,
                ("legacy-invalid", "Inválido", 2, "2026-01-01", "2026-01-01", "{"),
            )

        with self.assertLogs(level="WARNING") as logs:
            rei_server.initialize_database()
        self.assertTrue(any("legacy-invalid" in entry for entry in logs.output))
        with rei_server.connect() as db:
            row = db.execute(
                "SELECT stage, owner_username, assigned_username FROM reports WHERE id='legacy-1'"
            ).fetchone()
            indexes = {item[1] for item in db.execute("PRAGMA index_list(reports)")}
        self.assertEqual("rei", row["stage"])
        self.assertEqual(self.alice["username"], row["owner_username"])
        self.assertEqual(self.alice["username"], row["assigned_username"])
        self.assertTrue(
            {
                "idx_reports_stage",
                "idx_reports_owner_username",
                "idx_reports_assigned_username",
            }.issubset(indexes)
        )


if __name__ == "__main__":
    unittest.main()
