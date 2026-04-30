"""
Simulazione "tempo reale": invia ripetutamente il batch consumi (JSON → POST /api/consumi).
I valori vengono leggermente modificati a ogni ciclo così app e DB riflettono aggiornamenti periodici.

Default dev: middleware su server.port=8081 (vedi application.properties).
"""
import json
import random
import time
from pathlib import Path

import requests


def bump_value(item: dict) -> dict:
    """
    Modifica value in modo coerente:
    - power: random walk
    - active_energy/energy: incrementale
    - temperature: oscillazione
    - switch: 0/1
    """
    measure = str(item.get("measure", "")).lower()
    unit = str(item.get("unit", "")).lower()
    v = item.get("value")
    try:
        v = float(v) if v is not None else 0.0
    except Exception:
        v = 0.0

    if "power" in measure or unit in ("w", "kw"):
        v = max(0.0, v + random.uniform(-200, 200))
    elif "energy" in measure or unit in ("kwh", "wh"):
        v = max(0.0, v + random.uniform(0.1, 2.0))
    elif "temperature" in measure or unit in ("c", "°c"):
        v = min(90.0, max(-10.0, v + random.uniform(-0.5, 0.5)))
    elif "switch" in measure:
        v = random.choice([0, 1])
    elif unit == "%":
        v = min(100.0, max(0.0, v + random.uniform(-5, 5)))
    else:
        v = v + random.uniform(-1, 1)

    # Keep integer for switch-like
    if "switch" in measure:
        item["value"] = int(v)
    else:
        item["value"] = round(v, 3)
    return item


def main():
    base_url = "http://localhost:8081"
    username = "admin"
    password = "AdminPass123!"
    interval_sec = 30

    payload_path = Path(__file__).resolve().parent / "consumi_template.json"
    if not payload_path.exists():
        raise SystemExit(f"Missing template file: {payload_path}")

    token_resp = requests.post(
        f"{base_url}/auth/login",
        params={"username": username, "password": password},
        timeout=10,
    )
    token_resp.raise_for_status()
    token = token_resp.json().get("token")
    if not token:
        raise SystemExit(f"Login failed, response: {token_resp.text}")

    headers = {"Authorization": f"Bearer {token}"}

    with payload_path.open("r", encoding="utf-8") as f:
        items = json.load(f)
    if not isinstance(items, list):
        raise SystemExit("Template must be a JSON array.")

    print(f"Starting push loop every {interval_sec}s → {base_url}/api/consumi")
    while True:
        batch = [bump_value(dict(it)) for it in items]
        r = requests.post(f"{base_url}/api/consumi", json=batch, headers=headers, timeout=10)
        print(time.strftime("%H:%M:%S"), r.status_code, r.text)
        time.sleep(interval_sec)


if __name__ == "__main__":
    main()

