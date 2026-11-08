#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Torrent TV contributors
"""Private synthetic torrent/webseed for cache tests; no public swarm or media."""
import argparse
import hashlib
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PIECE = 1024 * 1024
SIZE = 256 * PIECE


def encode(value):
    if isinstance(value, int):
        return b"i" + str(value).encode() + b"e"
    if isinstance(value, str):
        value = value.encode()
    if isinstance(value, bytes):
        return str(len(value)).encode() + b":" + value
    if isinstance(value, list):
        return b"l" + b"".join(encode(item) for item in value) + b"e"
    return b"d" + b"".join(encode(k) + encode(v) for k, v in sorted(value.items())) + b"e"


def handler_for(torrent):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path == "/fixture.torrent":
                self.send_response(200)
                self.send_header("Content-Length", str(len(torrent)))
                self.end_headers()
                self.wfile.write(torrent)
                return
            if self.path != "/content/test.mp4":
                self.send_error(404)
                return
            start, end = 0, SIZE - 1
            header = self.headers.get("Range")
            if header:
                match = re.fullmatch(r"bytes=(\d+)-(\d*)", header)
                if not match:
                    self.send_error(416)
                    return
                start = int(match[1])
                end = min(SIZE - 1, int(match[2]) if match[2] else SIZE - 1)
            if start > end or start >= SIZE:
                self.send_error(416)
                return
            self.send_response(206 if header else 200)
            self.send_header("Content-Length", str(end - start + 1))
            self.send_header("Accept-Ranges", "bytes")
            if header:
                self.send_header("Content-Range", f"bytes {start}-{end}/{SIZE}")
            self.end_headers()
            try:
                while start <= end:
                    length = min(65536, end - start + 1, PIECE - start % PIECE)
                    self.wfile.write(bytes([(start // PIECE) % 251]) * length)
                    start += length
            except (BrokenPipeError, ConnectionResetError):
                pass

        def log_message(self, *_args):
            pass

    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1", help="Bind address; use the PC LAN IPv4 address for TV tests")
    parser.add_argument("--port", type=int, default=18761)
    parser.add_argument("--advertise-host", help="Reachable IPv4/hostname when binding a wildcard address")
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error("port must be between 1 and 65535")
    advertised = args.advertise_host or args.host
    if advertised in ("0.0.0.0", "::") or ":" in advertised:
        parser.error("supply a reachable IPv4 address or hostname using --advertise-host")
    torrent = encode({
        "info": {
            "name": "test.mp4", "length": SIZE, "piece length": PIECE,
            "pieces": b"".join(hashlib.sha1(bytes([i % 251]) * PIECE).digest() for i in range(256)),
            "private": 1,
        },
        "url-list": [f"http://{advertised}:{args.port}/content/"],
    })
    server = ThreadingHTTPServer((args.host, args.port), handler_for(torrent))
    print(f"Fixture ready: http://{advertised}:{args.port}/fixture.torrent", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
