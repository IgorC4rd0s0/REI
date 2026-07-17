from __future__ import annotations

import sys
import unittest
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parents[1]
if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))

import rei_server


class AdminHeaderTests(unittest.TestCase):
    def setUp(self) -> None:
        self.user = {
            "username": "supervisor.teste",
            "full_name": "Supervisora <Teste>",
            "role": "supervisor",
        }

    def test_shared_header_matches_web_navigation(self) -> None:
        header = rei_server.admin_header_html(self.user)

        self.assertIn('<header class="topbar">', header)
        self.assertIn('href="/web"', header)
        self.assertIn('href="/admin"', header)
        self.assertIn('href="/admin/items"', header)
        self.assertIn('class="nav gear"', header)
        self.assertIn("Supervisor · Supervisora &lt;Teste&gt;", header)
        self.assertNotIn(">Painel</a>", header)

    def test_legacy_header_is_replaced_once(self) -> None:
        page = "<html><body><header>legado</header><main>conteúdo</main></body></html>"

        result = rei_server.standardize_admin_header(page, self.user)

        self.assertEqual(result.count("<header"), 1)
        self.assertIn('<header class="topbar">', result)
        self.assertNotIn("legado", result)
        self.assertIn("<main>conteúdo</main>", result)


if __name__ == "__main__":
    unittest.main()
