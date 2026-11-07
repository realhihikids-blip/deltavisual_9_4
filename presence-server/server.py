#!/usr/bin/env python3
"""
Delta Visuals presence server.

Tells Delta Visuals clients which of the players around them also use the mod, so the logo can be shown next to their nick.
Plain Python 3.8+, standard library only, everything in memory (nothing is written to disk).

    python3 server.py                     # listens on 0.0.0.0:8787
    python3 server.py --port 9000 --ttl 120

Then put the address into  .minecraft/config/delta_visuals/presence.json :
    { "url": "http://YOUR_IP_OR_DOMAIN:8787" }

Protocol (JSON over HTTP):
    POST /sync   {"self": "<uuid>" (optional), "v": "0.9.0", "uuids": ["<uuid>", ...]}
              -> {"online": ["<uuid>", ...]}      subset of "uuids" (and "self") seen in the last --ttl seconds
    GET  /health -> {"ok": true, "users": N}

Only UUIDs are exchanged. There is no login: anyone could claim a UUID, which at worst shows the logo next to a nick,
so treat it as a cosmetic feature. For HTTPS put the server behind any reverse proxy (nginx, Caddy).
"""
import argparse
import json
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 32 * 1024
MAX_UUIDS = 300
MIN_GAP = 5.0          # seconds between two /sync calls from one address

seen = {}              # uuid string -> last time it announced itself
last_call = {}         # address -> last time of /sync
lock = threading.Lock()
TTL = 90.0


def clean(value):
    try:
        return str(uuid.UUID(str(value)))
    except (ValueError, AttributeError, TypeError):
        return None


def purge(now):
    for k in [k for k, t in seen.items() if now - t > TTL]:
        del seen[k]
    for k in [k for k, t in last_call.items() if now - t > 60]:
        del last_call[k]


class Handler(BaseHTTPRequestHandler):
    server_version = "DeltaPresence/1"

    def log_message(self, fmt, *args):      # quiet
        pass

    def reply(self, code, obj):
        data = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.rstrip("/") == "/health":
            with lock:
                purge(time.time())
                self.reply(200, {"ok": True, "users": len(seen)})
        else:
            self.reply(404, {"error": "not found"})

    def do_POST(self):
        if self.path.rstrip("/") != "/sync":
            return self.reply(404, {"error": "not found"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return self.reply(400, {"error": "bad length"})
        if length <= 0 or length > MAX_BODY:
            return self.reply(413, {"error": "bad size"})
        try:
            body = json.loads(self.rfile.read(length).decode("utf-8"))
            if not isinstance(body, dict):
                raise ValueError
        except (ValueError, UnicodeDecodeError):
            return self.reply(400, {"error": "bad json"})

        now = time.time()
        addr = self.client_address[0]
        with lock:
            purge(now)
            if now - last_call.get(addr, 0) < MIN_GAP:
                return self.reply(429, {"error": "slow down"})
            last_call[addr] = now

            me = clean(body.get("self")) if body.get("self") else None
            if me:
                seen[me] = now

            wanted = body.get("uuids", [])
            online = []
            if isinstance(wanted, list):
                for raw in wanted[:MAX_UUIDS]:
                    u = clean(raw)
                    if u and u in seen:
                        online.append(u)
            if me and me not in online:
                online.append(me)
        self.reply(200, {"online": online})


def main():
    global TTL
    ap = argparse.ArgumentParser(description="Delta Visuals presence server")
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8787)
    ap.add_argument("--ttl", type=float, default=90.0, help="seconds a client stays 'online' after its last sync")
    args = ap.parse_args()
    TTL = args.ttl
    srv = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Delta Visuals presence server on {args.host}:{args.port} (ttl {TTL:.0f}s)")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
