from __future__ import annotations

import json
import gc
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


class DeviceHeartbeatTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.previous_database = rei_server.DATABASE
        rei_server.DATABASE = Path(self.temp_dir.name) / "heartbeat_test.db"
        rei_server.initialize_database()
        self.supervisor = self.create_actor(
            "supervisor", "Supervisora Teste", "supervisor"
        )
        self.alice = self.create_actor("alice", "Alice Implantadora", "implantador")
        self.bob = self.create_actor("bob", "Bob Implantador", "implantador")

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

    def heartbeat(self, username: str = "alice") -> dict:
        return {
            "username": username,
            "deviceId": "device-android-001",
            "appVersion": "1.0-test",
            "lastSeen": 1_752_660_000_000,
            "pendingCount": 2,
            "lastError": "Servidor indisponível",
        }

    def test_supervisor_sees_all_and_implantador_only_own_device(self) -> None:
        saved = rei_server.save_device_heartbeat(self.heartbeat(), self.alice)
        self.assertEqual("alice", saved["username"])
        supervisor_devices = rei_server.list_device_heartbeats(self.supervisor)
        self.assertEqual(1, len(supervisor_devices))
        self.assertEqual("Alice Implantadora", supervisor_devices[0]["fullName"])
        self.assertEqual(1, len(rei_server.list_device_heartbeats(self.alice)))
        self.assertEqual([], rei_server.list_device_heartbeats(self.bob))
        self.assertNotIn("token", saved)
        self.assertNotIn("password", saved)

    def test_authenticated_identity_cannot_be_forged(self) -> None:
        with self.assertRaises(rei_server.ReportWriteRejected) as context:
            rei_server.save_device_heartbeat(self.heartbeat(username="bob"), self.alice)
        self.assertEqual(403, context.exception.status)
        self.assertEqual("heartbeat_identity_mismatch", context.exception.code)

    def test_only_latest_device_is_listed_for_each_implantador(self) -> None:
        device_ids = ["device-android-001", "device-android-002", "device-android-003"]
        for index, device_id in enumerate(device_ids):
            payload = self.heartbeat()
            payload["deviceId"] = device_id
            payload["pendingCount"] = index
            rei_server.save_device_heartbeat(payload, self.alice)

        with rei_server.connect() as db:
            for index, device_id in enumerate(device_ids, start=1):
                db.execute(
                    "UPDATE device_heartbeats SET last_seen=? WHERE device_id=?",
                    (f"2026-07-17T17:3{index}:00+00:00", device_id),
                )

        devices = rei_server.list_device_heartbeats(self.supervisor)
        self.assertEqual(1, len(devices))
        self.assertEqual("device-android-003", devices[0]["deviceId"])
        self.assertEqual(2, devices[0]["pendingCount"])
        self.assertEqual("Alice Implantadora", devices[0]["fullName"])

    def test_http_endpoints_filter_devices_and_reject_api_key_only(self) -> None:
        alice_token = rei_server.create_session(self.alice["id"])
        bob_token = rei_server.create_session(self.bob["id"])
        supervisor_token = rei_server.create_session(self.supervisor["id"])
        server = ThreadingHTTPServer(("127.0.0.1", 0), rei_server.ReiHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base_url = f"http://127.0.0.1:{server.server_port}/api/device-heartbeats"
        try:
            post = urllib.request.Request(
                base_url,
                data=json.dumps(self.heartbeat()).encode("utf-8"),
                headers={
                    "Authorization": f"Bearer {alice_token}",
                    "Content-Type": "application/json",
                },
                method="POST",
            )
            with urllib.request.urlopen(post, timeout=5) as response:
                self.assertEqual(200, response.status)

            self.assertEqual([], self.get_devices(base_url, bob_token))
            self.assertEqual(1, len(self.get_devices(base_url, supervisor_token)))

            api_key_request = urllib.request.Request(
                base_url,
                headers={"X-API-Key": str(rei_server.CONFIG.get("api_key", ""))},
            )
            with self.assertRaises(urllib.error.HTTPError) as context:
                urllib.request.urlopen(api_key_request, timeout=5)
            self.assertEqual(401, context.exception.code)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    @staticmethod
    def get_devices(url: str, token: str) -> list[dict]:
        request = urllib.request.Request(
            url, headers={"Authorization": f"Bearer {token}"}
        )
        with urllib.request.urlopen(request, timeout=5) as response:
            return json.loads(response.read().decode("utf-8"))


if __name__ == "__main__":
    unittest.main()
