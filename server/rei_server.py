"""Servidor central R.E.I. para a rede local do escritório."""

from __future__ import annotations

import csv
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
            items = [
                str(item).strip()
                for item in (group.get("items") if isinstance(group.get("items"), list) else [])
                if str(item).strip()
            ]
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


def add_rei_item(area: str, topic: str, label: str) -> None:
    area = area if area in REI_ITEM_AREAS else ""
    topic = topic.strip()
    label = label.strip()
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
    if label.lower() not in {item.lower() for item in group["items"]}:
        group["items"].append(label)
    save_schema_items(data)


def add_survey_topic(title: str) -> None:
    title = title.strip()
    if len(title) < 2:
        raise ValueError("Informe um tópico válido")
    data = load_schema_items()
    if not any(section["title"].strip().lower() == title.lower() for section in data["levantamento"]):
        data["levantamento"].append({"title": title, "fields": []})
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
    .brand{display:flex;align-items:center;gap:12px}.brand img{width:46px;height:46px;object-fit:contain;display:block}.login .brand img{width:128px;height:128px}
    nav{display:flex;gap:8px;flex-wrap:wrap}.nav{display:inline-flex;align-items:center;text-decoration:none;border-radius:11px;padding:10px 14px;font-weight:800;color:var(--navy);background:#eef1f7}.nav.active{background:var(--navy);color:#fff}
    main{max-width:1080px;margin:34px auto;padding:0 20px}.hero{background:linear-gradient(135deg,var(--dark),var(--navy));color:#fff;border-radius:24px;padding:28px;margin-bottom:22px}
    .hero h1{margin:0 0 7px}.hero p{margin:0;color:#d7def7}.grid{display:grid;grid-template-columns:360px 1fr;gap:20px}.card{background:#fff;border:1px solid var(--line);border-radius:20px;padding:22px}
    h2{margin:0 0 17px;font-size:19px}label{display:block;font-size:12px;font-weight:700;color:#596174;margin:12px 0 6px}
    input,select{width:100%;padding:12px;border:1px solid #cfd5e2;border-radius:11px;font-size:14px;background:#fbfcff}
    button{border:0;border-radius:11px;padding:12px 17px;font-weight:700;cursor:pointer;background:var(--navy);color:#fff}.full{width:100%;margin-top:18px}
    .logout{background:#eef1f7;color:var(--navy)}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:12px 10px;border-bottom:1px solid var(--line);font-size:13px}
    th{color:var(--muted);font-size:11px;text-transform:uppercase}.badge{display:inline-block;padding:5px 9px;border-radius:20px;background:#e9f5e6;color:#3b7131;font-size:11px;font-weight:700}
    .badge.implantador{background:#e9edfb;color:var(--navy)}.notice,.error{padding:12px 15px;border-radius:11px;margin-bottom:15px}.notice{background:#e9f5e6;color:#35682d}.error{background:#fdeaea;color:#9a3030}
    .login{max-width:430px;margin:70px auto}.muted{color:var(--muted);font-size:13px}@media(max-width:800px){.grid{grid-template-columns:1fr}}
    </style></head><body>"""
    base_end = "</main></body></html>"
    if users_count() == 0:
        return base_start + """<main class="login"><div class="card"><div class="brand"><img src="/web/assets/logo_dubrasil.png" alt="DuBrasil Soluções"></div>
        <h2 style="margin-top:25px">Criar supervisor inicial</h2><p class="muted">Este primeiro usuário administrará os demais acessos.</p>""" + alert + """
        <form method="post" action="/admin/setup"><label>Nome completo</label><input name="full_name" required minlength="3">
        <label>Usuário</label><input name="username" required minlength="3" autocomplete="username"><label>Senha</label>
        <input type="password" name="password" required minlength="8" autocomplete="new-password"><button class="full">Criar supervisor</button></form></div>""" + base_end
    if not user:
        return base_start + """<main class="login"><div class="card"><div class="brand"><img src="/web/assets/logo_dubrasil.png" alt="DuBrasil Soluções"></div>
        <h2 style="margin-top:25px">Acesso administrativo</h2>""" + alert + """<form method="post" action="/admin/login">
        <label>Usuário</label><input name="username" required autocomplete="username"><label>Senha</label>
        <input type="password" name="password" required autocomplete="current-password"><button class="full">Entrar</button></form></div>""" + base_end
    if user["role"] != "supervisor":
        return base_start + '<main class="login"><div class="card"><h2>Acesso restrito</h2><p>Somente supervisores podem administrar usuários.</p></div>' + base_end
    with connect() as db:
        users = db.execute("SELECT id,username,full_name,role,active,created_at FROM users ORDER BY full_name").fetchall()
    rows = "".join(
        f"<tr><td><strong>{html.escape(row['full_name'])}</strong><br><span class='muted'>@{html.escape(row['username'])}</span></td>"
        f"<td><span class='badge {row['role']}'>{html.escape(row['role'].title())}</span></td>"
        f"<td>{'Ativo' if row['active'] else 'Inativo'}</td>"
        f"<td><form method='post' action='/admin/users/toggle'><input type='hidden' name='id' value='{row['id']}'>"
        f"<button class='logout'>{'Desativar' if row['active'] else 'Ativar'}</button></form></td></tr>" for row in users
    )
    return base_start + f"""<header><div class="brand"><img src="/web/assets/logo_dubrasil.png" alt="DuBrasil Soluções"><nav><a class="nav active" href="/admin">Usuários</a><a class="nav" href="/admin/items">Itens dos relatórios</a><a class="nav" href="/web">Abrir sistema</a></nav></div>
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
    :root{--navy:#263a7a;--dark:#172653;--green:#58ad45;--bg:#f4f6fa;--line:#e1e5ee;--muted:#727b90}
    *{box-sizing:border-box}body{margin:0;font-family:Inter,Segoe UI,Arial,sans-serif;background:var(--bg);color:#20283b}
    header{background:#fff;border-bottom:1px solid var(--line);padding:14px 5%;display:flex;align-items:center;justify-content:space-between;gap:14px}
    .brand{display:flex;align-items:center;gap:14px}.brand img{width:48px;height:48px;object-fit:contain;display:block}
    nav{display:flex;gap:8px;flex-wrap:wrap}.nav{display:inline-flex;align-items:center;text-decoration:none;border-radius:11px;padding:10px 14px;font-weight:800;color:var(--navy);background:#eef1f7}.nav.active{background:var(--navy);color:#fff}
    main{max-width:1180px;margin:28px auto;padding:0 20px}.hero{background:linear-gradient(135deg,var(--dark),var(--navy));color:#fff;border-radius:24px;padding:24px;margin-bottom:18px}
    .hero h1{margin:0 0 7px}.hero p{margin:0;color:#d7def7}.grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.wide{grid-column:1/-1}
    .card{background:#fff;border:1px solid var(--line);border-radius:20px;padding:20px}h2{margin:0 0 15px;font-size:18px}h3{margin:18px 0 8px;font-size:14px;color:var(--navy)}
    label{display:block;font-size:12px;font-weight:800;color:#596174;margin:10px 0 6px}input,select,textarea{width:100%;padding:11px;border:1px solid #cfd5e2;border-radius:11px;font-size:14px;background:#fbfcff}
    textarea{min-height:82px;resize:vertical}button{border:0;border-radius:11px;padding:12px 16px;font-weight:800;cursor:pointer;background:var(--navy);color:#fff}.full{width:100%;margin-top:15px}.logout{background:#eef1f7;color:var(--navy)}
    .notice,.error{padding:12px 15px;border-radius:11px;margin-bottom:15px}.notice{background:#e9f5e6;color:#35682d}.error{background:#fdeaea;color:#9a3030}
    .muted{color:var(--muted);font-size:13px}.pill{display:inline-flex;align-items:center;margin:4px 5px 4px 0;padding:7px 10px;border-radius:999px;background:#eef1f7;color:#172653;font-size:12px;font-weight:700}
    .topic{border:1px solid var(--line);border-radius:15px;padding:13px;margin:10px 0;background:#fbfcff}.topic strong{color:#172653}
    @media(max-width:850px){.grid{grid-template-columns:1fr}header{align-items:flex-start;flex-direction:column}}
    </style></head><body>"""
    base_end = "</main></body></html>"
    if not user or user["role"] != "supervisor":
        return base_start + '<main><section class="card"><h2>Acesso restrito</h2><p>Somente supervisores podem gerenciar os itens.</p><p><a class="nav" href="/admin">Voltar</a></p></section>' + base_end
    data = load_schema_items()
    area_options = "".join(f'<option value="{html.escape(key)}">{html.escape(label)}</option>' for key, label in REI_ITEM_AREAS.items())
    rei_blocks = []
    for area, label in REI_ITEM_AREAS.items():
        custom = data["rei"][area]
        if area == "modules":
            body = "".join(f'<span class="pill">{html.escape(item)}</span>' for item in custom) or '<span class="muted">Nenhum módulo personalizado cadastrado.</span>'
        else:
            body = "".join(
                f'<div class="topic"><strong>{html.escape(group["title"])}</strong><div>'
                + ("".join(f'<span class="pill">{html.escape(item)}</span>' for item in group["items"]) or '<span class="muted">Tópico criado, ainda sem itens.</span>')
                + "</div></div>"
                for group in custom
            ) or '<span class="muted">Nenhum item personalizado cadastrado.</span>'
        rei_blocks.append(f'<h3>{html.escape(label)}</h3>{body}')
    levantamento_blocks = "".join(
        f'<div class="topic"><strong>{html.escape(section["title"])}</strong><div>'
        + ("".join(f'<span class="pill">{html.escape(field["label"])} · {html.escape(SURVEY_FIELD_TYPES.get(field["type"], field["type"]))}</span>' for field in section["fields"]) or '<span class="muted">Tópico criado, ainda sem campos.</span>')
        + "</div></div>"
        for section in data["levantamento"]
    ) or '<span class="muted">Nenhum campo personalizado cadastrado.</span>'
    type_options = "".join(f'<option value="{html.escape(key)}">{html.escape(label)}</option>' for key, label in SURVEY_FIELD_TYPES.items())
    return base_start + f"""<header><div class="brand"><img src="/web/assets/logo_dubrasil.png" alt="DuBrasil Soluções">
    <nav><a class="nav" href="/admin">Usuários</a><a class="nav active" href="/admin/items">Itens dos relatórios</a><a class="nav" href="/web">Abrir sistema</a></nav></div>
    <form method="post" action="/admin/logout"><button class="logout">Sair</button></form></header><main>
    <div class="hero"><h1>Gestão de itens e tópicos</h1><p>Cadastre novos campos para o levantamento e novos itens para preenchimento da implantação.</p></div>{notice}{alert}
    <div class="grid">
      <section class="card"><h2>Novo tópico do R.E.I.</h2><form method="post" action="/admin/items/rei-topic">
        <label>Área</label><select name="area">{area_options}</select><label>Nome do tópico</label><input name="topic" required placeholder="Ex.: Cadastros adicionais">
        <button class="full">Cadastrar tópico</button></form></section>
      <section class="card"><h2>Novo item do R.E.I.</h2><form method="post" action="/admin/items/rei-item">
        <label>Área</label><select name="area">{area_options}</select><label>Tópico</label><input name="topic" placeholder="Obrigatório, exceto para Módulos contratados">
        <label>Item a preencher</label><input name="label" required placeholder="Ex.: Conferir parametrização de comissão"><button class="full">Cadastrar item</button></form></section>
      <section class="card"><h2>Novo tópico do levantamento</h2><form method="post" action="/admin/items/survey-topic">
        <label>Nome do tópico</label><input name="topic" required placeholder="Ex.: Comercial"><button class="full">Cadastrar tópico</button></form></section>
      <section class="card"><h2>Novo campo do levantamento</h2><form method="post" action="/admin/items/survey-item">
        <label>Tópico</label><input name="topic" required placeholder="Ex.: Financeiro"><label>Campo</label><input name="label" required placeholder="Ex.: Utiliza cobrança recorrente?">
        <label>Tipo do campo</label><select name="type">{type_options}</select><label>Opções, se for múltipla escolha</label><textarea name="options" placeholder="Sim, Não, Parcial"></textarea>
        <button class="full">Cadastrar campo</button></form></section>
      <section class="card wide"><h2>Itens personalizados do R.E.I.</h2>{''.join(rei_blocks)}</section>
      <section class="card wide"><h2>Campos personalizados do levantamento</h2>{levantamento_blocks}</section>
    </div>""" + base_end


def admin_query(kind: str, text: str) -> str:
    return f"/admin?{kind}={quote_plus(text)}"


class LegacyReiHandler(BaseHTTPRequestHandler):
    server_version = "REI-Office/1.0"

    def log_message(self, fmt: str, *args) -> None:
        logging.info("%s - %s", self.address_string(), fmt % args)

    def authorized(self) -> bool:
        return self.headers.get("X-API-Key", "") == CONFIG.get("api_key")

    def send_json(self, status: int, value: dict | list) -> None:
        body = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
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

    def send_json(self, status: int, value: dict | list) -> None:
        body = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
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
            self.redirect("/web")
            return
        if parsed.path == "/app":
            self.redirect("/web")
            return
        if parsed.path == "/web" or parsed.path.startswith("/web/"):
            self.send_static(parsed.path)
            return
        if parsed.path == "/admin":
            query = parse_qs(parsed.query)
            self.send_html(admin_html(
                self.request_user(), query.get("message", [""])[0], query.get("error", [""])[0]
            ))
            return
        if parsed.path == "/admin/items":
            query = parse_qs(parsed.query)
            self.send_html(admin_items_html(
                self.request_user(), query.get("message", [""])[0], query.get("error", [""])[0]
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
        if parsed.path == "/admin/login":
            form = self.read_form()
            user = authenticate(form.get("username", ""), form.get("password", ""))
            if not user or user["role"] != "supervisor":
                self.send_html(admin_html(None, error="Usuário, senha ou perfil inválido"), 401)
                return
            token = create_session(int(user["id"]))
            self.redirect("/admin", f"rei_session={token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000")
            return
        if parsed.path == "/admin/logout":
            revoke_session(self.request_token())
            self.redirect("/admin", "rei_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0")
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
        if parsed.path in {"/admin/items/rei-topic", "/admin/items/rei-item", "/admin/items/survey-topic", "/admin/items/survey-item"}:
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
                    add_rei_item(form.get("area", ""), form.get("topic", ""), form.get("label", ""))
                    message = "Item do R.E.I. cadastrado"
                elif parsed.path == "/admin/items/survey-topic":
                    add_survey_topic(form.get("topic", ""))
                    message = "Tópico do levantamento cadastrado"
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
                }})
            except (ValueError, json.JSONDecodeError) as error:
                self.send_json(400, {"error": str(error)})
            return
        if parsed.path == "/api/auth/logout":
            revoke_session(self.request_token())
            self.send_json(200, {"status": "ok"})
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
