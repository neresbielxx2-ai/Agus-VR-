#!/usr/bin/env python3
"""
Relay de exemplo: Agus VR (TCP) → HTTP(S) para o Roblox Studio.

O Roblox só consome HTTP(S) via HttpService; o Agus VR exporta JSON por
TCP. Este relay faz a ponte:

  celular (porta 28097)  →  relay (este script)  →  Studio (HttpService)

Uso:
  python3 relay_example.py <ip-do-celular> [--save sessao.jsonl] [--port 8080]

Depois exponha o relay via HTTPS (ex.: ngrok http 8080) e coloque a URL
em RELAY_URL dentro de AgusVRBridge.server.lua.

Requer apenas Python 3.8+ (sem dependências externas).
"""
import json
import socket
import sys
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

LATEST = {"packet": None}
LOCK = threading.Lock()
SAVE_FILE = None


def tcp_client(host: str, port: int = 28097):
    global SAVE_FILE
    while True:
        try:
            with socket.create_connection((host, port), timeout=10) as s:
                print(f"[relay] conectado ao Agus VR em {host}:{port}")
                buf = b""
                while True:
                    data = s.recv(65536)
                    if not data:
                        break
                    buf += data
                    while b"\n" in buf:
                        line, buf = buf.split(b"\n", 1)
                        if not line.strip():
                            continue
                        try:
                            pkt = json.loads(line)
                        except Exception:
                            continue
                        with LOCK:
                            LATEST["packet"] = line.decode()
                        if SAVE_FILE:
                            with open(SAVE_FILE, "a", encoding="utf-8") as f:
                                f.write(line.decode() + "\n")
        except Exception as e:
            print(f"[relay] conexão caiu ({e}); tentando de novo em 2s…")
        import time
        time.sleep(2)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/packet":
            with LOCK:
                pkt = LATEST["packet"]
            if pkt:
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(pkt.encode())
            else:
                self.send_response(404)
                self.end_headers()
        else:
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"Agus VR relay OK\n")

    def log_message(self, *a):  # silencioso
        pass


def main():
    global SAVE_FILE
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(1)
    host = args[0]
    port = 8080
    if "--save" in args:
        SAVE_FILE = args[args.index("--save") + 1]
    if "--port" in args:
        port = int(args[args.index("--port") + 1])

    t = threading.Thread(target=tcp_client, args=(host,), daemon=True)
    t.start()
    print(f"[relay] HTTP em 0.0.0.0:{port}/packet — use ngrok/túnel HTTPS p/ o Studio")
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()
