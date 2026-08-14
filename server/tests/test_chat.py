from __future__ import annotations

import gc
import tempfile
import unittest
import warnings
from pathlib import Path

import sys

SERVER_DIR = Path(__file__).resolve().parents[1]
if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))

import rei_server


class ChatTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.previous_database = rei_server.DATABASE
        rei_server.DATABASE = Path(self.temp_dir.name) / "chat.db"
        rei_server.initialize_database()
        self.alice = self.actor("alice", "Alice Implantadora", "implantador")
        self.bob = self.actor("bob", "Bob Implantador", "implantador")
        self.report_id = "chat-report-1"
        rei_server.save_report({
            "reportId": self.report_id,
            "completedAt": 1,
            "report": {"fields": {"_id": self.report_id, "_stage": "levantamento_pendente", "_ownerUsername": "alice", "_assignedImplantadorUsername": "alice", "cliente": "Cliente Chat", "financeiroContasPagarReceber": "Sim"}, "checks": [], "deliveryStatus": "", "rating": "", "attachments": []},
        }, self.alice)

    def tearDown(self) -> None:
        rei_server.DATABASE = self.previous_database
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", ResourceWarning)
            gc.collect()
        self.temp_dir.cleanup()

    def actor(self, username: str, full_name: str, role: str) -> dict:
        user_id = rei_server.create_user(username, full_name, "senha-segura", role)
        return {"id": user_id, "username": username, "full_name": full_name, "role": role, "active": 1}

    def test_session_message_context_and_idempotency(self) -> None:
        session = rei_server.create_chat_session(self.report_id, self.alice, "erp-levantamento-diagnostico")
        original = rei_server.call_openai_chat
        captured = {}
        rei_server.call_openai_chat = lambda skill, context, messages: (captured.update({"skill": skill, "context": context}) or ({"answer": "Resposta de teste", "questions": [], "facts": [], "pending_items": [], "risks": [], "suggestions": [], "evidence_ids": [], "requires_confirmation": False, "confidence": "high", "skill_code": skill}, "resp-1"))
        try:
            payload = {"sessionId": session["id"], "localIdempotencyKey": "local-1", "content": "Quais pendências existem?"}
            sent = rei_server.send_chat_message(self.report_id, self.alice, payload)
            duplicate = rei_server.send_chat_message(self.report_id, self.alice, payload)
        finally:
            rei_server.call_openai_chat = original
        self.assertEqual("Resposta de teste", sent["response"]["answer"])
        self.assertEqual(sent["messageId"], duplicate["messageId"])
        self.assertEqual("erp-levantamento-diagnostico", captured["skill"])
        self.assertIn("Cliente Chat", captured["context"])
        self.assertEqual(2, len(rei_server.list_chat_messages(self.report_id, self.alice, session["id"])))

    def test_skill_and_report_authorization(self) -> None:
        with self.assertRaises(rei_server.ChatRequestError) as invalid:
            rei_server.create_chat_session(self.report_id, self.alice, "skill-arbitraria")
        self.assertEqual("invalid_skill", invalid.exception.code)
        session = rei_server.create_chat_session(self.report_id, self.alice, "erp-conversao-auditoria")
        with self.assertRaises(rei_server.ChatRequestError) as forbidden:
            rei_server.list_chat_messages(self.report_id, self.bob, session["id"])
        self.assertEqual(403, forbidden.exception.status)


if __name__ == "__main__":
    unittest.main()
