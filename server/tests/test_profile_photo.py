import tempfile
import unittest
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import rei_server


class ProfilePhotoTests(unittest.TestCase):
    def setUp(self):
        self.previous_database = rei_server.DATABASE
        self.temp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        rei_server.DATABASE = Path(self.temp.name) / "profile_photo.db"
        rei_server.initialize_database()
        self.user_id = rei_server.create_user("photo.user", "Usuário da Foto", "senha-segura", "implantador")

    def tearDown(self):
        rei_server.DATABASE = self.previous_database
        self.temp.cleanup()

    def test_profile_photo_is_persisted_and_returned(self):
        photo = "data:image/png;base64,iVBORw0KGgo="
        updated = rei_server.update_user_photo(self.user_id, photo)

        self.assertEqual(photo, updated["photoData"])
        self.assertEqual(photo, rei_server.list_users()[0]["photoData"])

    def test_invalid_profile_photo_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Formato de foto inválido"):
            rei_server.update_user_photo(self.user_id, "javascript:alert(1)")


if __name__ == "__main__":
    unittest.main()
