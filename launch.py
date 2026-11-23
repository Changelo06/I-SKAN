"""Local launcher: deliberately disables server access/error content logging."""
import argparse
import json
import logging
import socket
import sys
import webbrowser
from pathlib import Path

import uvicorn

from iskan.app import create_app

BASE = Path(__file__).resolve().parent


def lan_ip():
    try:
        # UDP routing lookup does not transmit a document or establish a connection.
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(('192.0.2.1', 9))
            return s.getsockname()[0]
    except OSError:
        return '127.0.0.1'


def main():
    parser = argparse.ArgumentParser(description='I-SKAN private local document scanner')
    parser.add_argument('--port', type=int, default=8443)
    parser.add_argument('--local', action='store_true', help='HTTP on loopback only; upload/laptop testing')
    parser.add_argument('--http-lan', action='store_true', help='Explicit HTTP LAN photo-upload fallback, no live phone camera')
    parser.add_argument('--no-browser', action='store_true')
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535:
        parser.error('Port must be 1024–65535.')
    config_path = BASE / 'certs' / 'config.json'
    secure = config_path.exists() and not args.local and not args.http_lan
    if not secure and not args.local and not args.http_lan:
        print('HTTPS_SETUP_REQUIRED: run setup-https.ps1 first, or use start.ps1 -Local for laptop-only use.')
        return 1
    address = json.loads(config_path.read_text())['ip'] if secure else lan_ip()
    scheme = 'https' if secure else 'http'
    public_url = f'{scheme}://{address}:{args.port}' if secure or args.http_lan else None
    logging.disable(logging.CRITICAL)
    app = create_app(public_url=public_url)
    print(f'I-SKAN laptop: {scheme}://localhost:{args.port}')
    if public_url:
        print(f'Phone connection: {public_url}')
        print(f'Pairing code: {app.state.pairing_code}')
    if args.http_lan:
        print('HTTP_FALLBACK: private Wi-Fi photo upload only; use HTTPS for phone camera access.')
    print('Keep the laptop awake. Press Ctrl+C to stop. Draft captures are retained for recovery.')
    if not args.no_browser:
        # Open after listener startup rather than racing the server.
        import threading
        threading.Timer(1.5, lambda: webbrowser.open(f'{scheme}://localhost:{args.port}')).start()
    try:
        uvicorn.run(app, host='127.0.0.1' if args.local else '0.0.0.0', port=args.port,
                    ssl_keyfile=str(BASE / 'certs' / 'server-key.pem') if secure else None,
                    ssl_certfile=str(BASE / 'certs' / 'server.pem') if secure else None,
                    access_log=False, log_config=None, log_level='critical', proxy_headers=False)
    except Exception:
        print('SERVER_START_FAILED: check the selected port and HTTPS certificate setup.')
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
