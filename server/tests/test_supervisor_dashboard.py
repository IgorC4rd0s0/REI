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
from datetime import datetime, timedelta, timezone
from http.server import ThreadingHTTPServer
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parents[1]
if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))

import rei_server


class SupervisorDashboardTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.previous_database = rei_server.DATABASE
        rei_server.DATABASE = Path(self.temp_dir.name) / "dashboard_test.db"
        rei_server.initialize_database()
        self.supervisor = self.actor("super", "Supervisora Teste", "supervisor")
        self.alice = self.actor("alice", "Alice Implantadora", "implantador")
        self.bob = self.actor("bob", "Bob Implantador", "implantador")
        self.seed_reports()

    def tearDown(self) -> None:
        rei_server.DATABASE = self.previous_database
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", ResourceWarning)
            gc.collect()
        self.temp_dir.cleanup()

    def actor(self, username: str, full_name: str, role: str) -> dict:
        user_id = rei_server.create_user(username, full_name, "senha-segura", role)
        return {
            "id": user_id,
            "username": username,
            "full_name": full_name,
            "role": role,
            "active": 1,
        }

    def report(
        self,
        report_id: str,
        actor: dict,
        status: str = "",
        start_days: int = 5,
        end_days: int = 0,
    ) -> dict:
        now = datetime.now(timezone.utc)
        fields = {
            "_id": report_id,
            "_stage": "rei",
            "_ownerUsername": actor["username"],
            "_assignedImplantadorUsername": actor["username"],
            "cliente": f"Cliente {report_id}",
            "inicio": (now - timedelta(days=start_days)).strftime("%d/%m/%Y"),
            "termino": (now + timedelta(days=end_days)).strftime("%d/%m/%Y"),
        }
        if status.startswith("Conclu"):
            fields.update(
                {
                    "empresa": f"Cliente {report_id}",
                    "consultor": actor["full_name"],
                    "servicosExecutados": "Implantação concluída para teste.",
                    "assinaturaAnalistaImagem": "content://teste/analista",
                    "assinaturaClienteImagem": "content://teste/cliente",
                }
            )
        return {
            "reportId": report_id,
            "completedAt": int(now.timestamp() * 1000),
            "report": {
                "fields": fields,
                "checks": [],
                "deliveryStatus": status,
                "rating": "",
                "attachments": [],
            },
        }

    def seed_reports(self) -> None:
        overdue = self.report("alice-overdue", self.alice, end_days=-2)
        overdue["report"]["fields"]["pendencias"] = "Aguardando retorno do cliente"
        rei_server.save_report(overdue, self.alice)

        concluded_pending_evaluation = self.report(
            "alice-concluded", self.alice, "Concluído", start_days=3
        )
        rei_server.save_report(concluded_pending_evaluation, self.alice)

        concluded_bob = self.report(
            "bob-evaluated", self.bob, "Concluído", start_days=1
        )
        rei_server.save_report(concluded_bob, self.bob)
        evaluated = copy.deepcopy(concluded_bob)
        evaluated["report"]["fields"].update(
            {
                "_supervisorName": self.supervisor["full_name"],
                "_supervisionScore": "8.0",
                "_supervisionReviewedAt": str(
                    int(datetime.now(timezone.utc).timestamp() * 1000)
                ),
            }
        )
        rei_server.save_report(evaluated, self.supervisor)

        cancelled = self.report("bob-cancelled", self.bob, "Cancelado", start_days=2)
        rei_server.save_report(cancelled, self.bob)

        with rei_server.connect() as db:
            db.execute(
                "UPDATE reports SET updated_at=? WHERE id='alice-overdue'",
                ((datetime.now(timezone.utc) - timedelta(days=12)).isoformat(),),
            )
        rei_server.save_device_heartbeat(
            {
                "username": "alice",
                "deviceId": "alice-device-001",
                "appVersion": "1.0",
                "lastSeen": int(datetime.now(timezone.utc).timestamp() * 1000),
                "pendingCount": 2,
                "lastError": "Falha de rede",
            },
            self.alice,
        )

    def test_metrics_follow_operational_rules_and_exclude_cancelled(self) -> None:
        dashboard = rei_server.supervisor_dashboard(
            {"period": ["all"], "staleDays": ["7"]}
        )
        indicators = dashboard["indicators"]
        self.assertEqual(1, indicators["overdue"])
        self.assertEqual(1, indicators["stale"])
        self.assertEqual(1, indicators["blockers"])
        self.assertEqual(1, indicators["pendingEvaluations"])
        self.assertEqual(2, indicators["concludedMonth"])
        self.assertEqual(8.0, indicators["averageScore"])
        self.assertEqual(1, indicators["syncErrors"])
        self.assertNotIn("payload_json", json.dumps(dashboard))
        alice = next(
            item for item in dashboard["workload"] if item["username"] == "alice"
        )
        self.assertEqual(1, alice["overdue"])
        self.assertEqual(1, alice["pendingEvaluations"])
        self.assertEqual(2, alice["pendingSync"])

    def test_implantador_and_stage_filters_are_applied(self) -> None:
        dashboard = rei_server.supervisor_dashboard(
            {
                "period": ["all"],
                "implantador": ["alice"],
                "stage": ["rei"],
                "overdue": ["1"],
            }
        )
        self.assertEqual(1, dashboard["indicators"]["total"])
        self.assertEqual(
            ["alice-overdue"], [item["id"] for item in dashboard["lists"]["overdue"]]
        )
        self.assertEqual(
            ["alice"], [item["username"] for item in dashboard["workload"]]
        )

    def test_endpoint_is_exclusive_to_authenticated_supervisor(self) -> None:
        supervisor_token = rei_server.create_session(self.supervisor["id"])
        alice_token = rei_server.create_session(self.alice["id"])
        server = ThreadingHTTPServer(("127.0.0.1", 0), rei_server.ReiHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        url = (
            f"http://127.0.0.1:{server.server_port}/api/dashboard/supervisor?period=all"
        )
        try:
            request = urllib.request.Request(
                url, headers={"Authorization": f"Bearer {supervisor_token}"}
            )
            with urllib.request.urlopen(request, timeout=5) as response:
                body = json.loads(response.read().decode("utf-8"))
            self.assertIn("indicators", body)

            denied = urllib.request.Request(
                url, headers={"Authorization": f"Bearer {alice_token}"}
            )
            with self.assertRaises(urllib.error.HTTPError) as context:
                urllib.request.urlopen(denied, timeout=5)
            self.assertEqual(403, context.exception.code)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)


if __name__ == "__main__":
    unittest.main()
