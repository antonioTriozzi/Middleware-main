#!/usr/bin/env python3
"""
Simulazione **completa** (default): middleware (``user`` + outbox + flush) → web app ``client``.

  1. Per ogni client: POST ``/api/mobile/v1/users/sync`` al middleware — upsert su ``user``
     (email già presente = solo update, niente outbox).
  2. Se l'email è nuova: ``user_sync_outbox`` e flush verso la web se ``app.web-sync.enabled=true``.
  3. La web riceve ``client-upsert`` e scrive su ``client``.

**Default URL middleware:** ``http://127.0.0.1:8081`` (override con ``--middleware-base-url`` o env ``MIDDLEWARE_BASE_URL``).
Env ``MIDDLEWARE_BASE_URL`` vuota = disabilita il passaggio middleware.

Solo POST diretto alla web (vecchio comportamento): ``--web-only``.

IMPORTANTE — due database MySQL distinti:
  - Middleware (8081): schema tipico ``testtesi``, ``user``, ``user_sync_outbox``.
  - Web (8080): schema tipico ``progetto_tesi``, ``client``.

Con il flusso completo **non** si ripete il POST diretto alla web salvo ``--also-direct-web`` (test / duplicazione).

Chiamata web (diretta): POST {base}/app/api/integration/middleware/client-upsert
Chiamata middleware: POST {mw}/api/mobile/v1/users/sync

Richiede: middleware in esecuzione; web con integrazione abilitata ed edifici (es. id 2 e 3 in BUILDING_IDS).

Uso:
  python simulate_middleware_to_web_client_upsert.py
  python simulate_middleware_to_web_client_upsert.py --web-only
  python simulate_middleware_to_web_client_upsert.py --middleware-base-url http://127.0.0.1:8081
  python simulate_middleware_to_web_client_upsert.py --also-direct-web

Variabili d'ambiente:
  WEB_APP_BASE_URL, MIDDLEWARE_SYNC_TOKEN, MIDDLEWARE_BASE_URL, MOBILE_API_TOKEN
"""

from __future__ import annotations

import argparse
import json
import os
import random
import sys
import urllib.error
import urllib.request
import uuid

# Edifici da alternare (come da tua richiesta: tra 2 e 3)
BUILDING_IDS = (2, 3)

FAKE_FIRST = (
    "Mario",
    "Luigi",
    "Anna",
    "Giulia",
    "Marco",
    "Elena",
    "Paolo",
    "Sara",
    "Luca",
    "Chiara",
)
FAKE_LAST = (
    "Rossi",
    "Bianchi",
    "Verdi",
    "Neri",
    "Ferri",
    "Costa",
    "Lombardi",
    "Fontana",
    "Greco",
    "Marino",
)

# Default allineato a server.port del middleware (application.properties)
DEFAULT_MIDDLEWARE_BASE_URL = "http://127.0.0.1:8081"


def default_middleware_base_url_from_env() -> str:
    """Se MIDDLEWARE_BASE_URL è impostata (anche vuota), usa quella; altrimenti default locale."""
    if "MIDDLEWARE_BASE_URL" in os.environ:
        return os.environ["MIDDLEWARE_BASE_URL"].strip()
    return DEFAULT_MIDDLEWARE_BASE_URL


def fake_clients(count: int) -> list[dict]:
    out = []
    for _ in range(count):
        bid = random.choice(BUILDING_IDS)
        tag = uuid.uuid4().hex[:8]
        email = f"sync.fake.{tag}@middleware-test.local"
        out.append(
            {
                "email": email,
                "nome": random.choice(FAKE_FIRST),
                "cognome": random.choice(FAKE_LAST),
                "idCondominio": bid,
            }
        )
    return out


def post_mobile_user_sync(
    middleware_base: str, mobile_token: str, client: dict
) -> tuple[int, str]:
    """POST /api/mobile/v1/users/sync — upsert su tabella user del middleware."""
    url = middleware_base.rstrip("/") + "/api/mobile/v1/users/sync"
    body = json.dumps(
        {
            "email": client["email"],
            "nome": client.get("nome", ""),
            "cognome": client.get("cognome", ""),
            "idCondominio": client.get("idCondominio"),
        },
        ensure_ascii=False,
    ).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "X-Mobile-Api-Token": mobile_token,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.getcode(), resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")
    except urllib.error.URLError as e:
        return -1, f"Connessione fallita ({middleware_base}): {e.reason}"


def post_upsert(base_url: str, token: str, clients: list[dict]) -> tuple[int, str]:
    url = base_url.rstrip("/") + "/app/api/integration/middleware/client-upsert"
    body = json.dumps({"clients": clients}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "X-Middleware-Sync-Token": token,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.getcode(), resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--base-url",
        default=os.environ.get("WEB_APP_BASE_URL", "http://localhost:8080"),
        help="Base URL app web (no trailing slash)",
    )
    p.add_argument(
        "--token",
        default=os.environ.get(
            "MIDDLEWARE_SYNC_TOKEN", "dev-middleware-web-sync-secret"
        ),
        help="Stesso valore di app.middleware-integration.secret sulla web",
    )
    p.add_argument(
        "--count",
        type=int,
        default=4,
        help="Quanti client fittizi inviare in un batch",
    )
    p.add_argument(
        "--print-body",
        action="store_true",
        help="Stampa il JSON inviato oltre alla risposta",
    )
    p.add_argument(
        "--middleware-base-url",
        default=default_middleware_base_url_from_env(),
        help=f"Base URL middleware (default: env MIDDLEWARE_BASE_URL o {DEFAULT_MIDDLEWARE_BASE_URL})",
    )
    p.add_argument(
        "--web-only",
        action="store_true",
        help="Solo POST client-upsert alla web (nessun passaggio dal middleware / testtesi)",
    )
    p.add_argument(
        "--mobile-api-token",
        default=os.environ.get("MOBILE_API_TOKEN", "dev-mobile-api-secret"),
        help="Header X-Mobile-Api-Token (app.mobile-api.secret sul middleware)",
    )
    p.add_argument(
        "--also-direct-web",
        action="store_true",
        help="Dopo il middleware, invia anche un POST client-upsert diretto alla web (duplica l'ultimo step; solo test)",
    )
    args = p.parse_args()

    clients = fake_clients(args.count)
    mw_base = (
        ""
        if args.web_only
        else (args.middleware_base_url or "").strip()
    )
    if not mw_base:
        print(
            "Modalità «solo web» (--web-only o MIDDLEWARE_BASE_URL vuota): "
            "il DB middleware (testtesi.user) non viene usato.\n"
        )
    if args.print_body:
        print("Request body:", json.dumps({"clients": clients}, ensure_ascii=False, indent=2))
        print()

    if mw_base:
        print(
            f"--- Simulazione completa — middleware {mw_base} → DB testtesi → flush → web {args.base_url} ---"
        )
        print(
            "--- Step: POST /api/mobile/v1/users/sync (outbox + flush se utente nuovo) ---"
        )
        failed_mw = False
        for c in clients:
            st, tx = post_mobile_user_sync(mw_base, args.mobile_api_token, c)
            print(f"  {c['email']}: HTTP {st} {tx}")
            if not (200 <= st < 300):
                failed_mw = True
        print()
        if failed_mw:
            return 1
        if not args.also_direct_web:
            print(
                "--- Web: nessun POST diretto (i nuovi utenti sono stati inviati dal flush del middleware). ---"
            )
            print("    Abilita app.web-sync.enabled sul middleware e l'integrazione sulla web.")
            return 0

    status, text = post_upsert(args.base_url, args.token, clients)
    label = (
        "--- Web app → client-upsert (POST diretto)"
        if not mw_base
        else "--- Web app → client-upsert (POST diretto, --also-direct-web) ---"
    )
    print(label)
    print(f"HTTP {status}")
    print(text)
    return 0 if 200 <= status < 300 else 1


if __name__ == "__main__":
    sys.exit(main())
