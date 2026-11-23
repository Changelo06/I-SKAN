import io
import ipaddress
import os
import re
import secrets
import tempfile
import time
from contextlib import asynccontextmanager
from pathlib import Path
from urllib.parse import urlsplit

import qrcode
from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse, Response
from fastapi.staticfiles import StaticFiles
from starlette.concurrency import run_in_threadpool

from .exporting import pdf_bytes, raster_page, save_document, valid_filename
from .imaging import decode, encode, quality
from .models import Approval, Correction, Settings
from .store import Store, scan_date
from .worker import Worker

BASE = Path(__file__).resolve().parent.parent


def create_app(root=None, pairing_code=None, public_url=None):
    root = Path(root or os.environ.get('ISKAN_DATA', BASE / 'data'))
    store = Store(root)
    worker = Worker(store)
    tokens = {}
    attempts = []
    code = pairing_code or secrets.token_hex(4).upper()
    destination = (BASE / 'output').resolve()
    destination.mkdir(exist_ok=True)
    allowed = store.get_config('destinations', [str(destination)])
    if not store.settings().destination:
        config = store.settings()
        config.destination = str(destination)
        store.config('settings', config.model_dump())

    @asynccontextmanager
    async def lifespan(app):
        yield
        worker.close()
        # Running OCR can take longer than shutdown. It owns its SQLite connection
        # until process termination, so do not close it underneath a live worker.
        if not worker.thread.is_alive():
            store.close()

    app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.store, app.state.worker = store, worker
    app.state.pairing_code = code

    def local(request):
        try:
            return ipaddress.ip_address(request.client.host).is_loopback
        except ValueError:
            return request.client.host == 'testclient'

    def fail(code, status=400):
        raise HTTPException(status, detail=code)

    @app.exception_handler(RequestValidationError)
    async def validation(request, exc):
        return JSONResponse({'detail': 'INVALID_INPUT'}, status_code=422)

    @app.exception_handler(Exception)
    async def unexpected(request, exc):
        return JSONResponse({'detail': 'OPERATION_FAILED'}, status_code=500)

    @app.middleware('http')
    async def guard(request, next_call):
        # No cross-site mutations, no proxy-derived local privileges, no telemetry.
        hosts = {'localhost', '127.0.0.1', '::1'}
        if public_url:
            hosts.add(urlsplit(public_url).hostname)
        if request.url.hostname not in hosts:
            return JSONResponse({'detail': 'HOST_DENIED'}, status_code=403)
        if request.method not in ('GET', 'HEAD', 'OPTIONS'):
            origin = request.headers.get('origin')
            if (origin and urlsplit(origin).netloc != request.url.netloc) or request.headers.get('sec-fetch-site') == 'cross-site':
                return JSONResponse({'detail': 'ORIGIN_DENIED'}, status_code=403)
        try:
            length = int(request.headers.get('content-length', '0'))
        except ValueError:
            length = 36 * 1024 * 1024
        if length > 35 * 1024 * 1024:
            return JSONResponse({'detail': 'UPLOAD_TOO_LARGE'}, status_code=413)
        if request.url.path.startswith('/api/') and request.url.path not in ('/api/login', '/api/health'):
            token = request.cookies.get('iskan')
            if tokens.get(token, 0) < time.time():
                return JSONResponse({'detail': 'PAIRING_REQUIRED'}, status_code=401)
            tokens[token] = time.time() + 24 * 3600
        response = await next_call(request)
        response.headers['Cache-Control'] = 'no-store'
        response.headers['Referrer-Policy'] = 'no-referrer'
        response.headers['X-Content-Type-Options'] = 'nosniff'
        response.headers['Content-Security-Policy'] = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; media-src 'self' blob:; frame-src 'self' blob:; object-src 'none'; base-uri 'self'; frame-ancestors 'self'"
        response.headers['Permissions-Policy'] = 'camera=(self), microphone=()'
        return response

    def session(sid, editable=False):
        try:
            s = store.get(sid)
        except KeyError:
            fail('SESSION_MISSING', 404)
        if editable and s['saved']:
            fail('SAVED_SESSION_LOCKED', 409)
        return s

    def page(s, pid):
        p = next((p for p in s['pages'] if p['id'] == pid), None)
        if not p:
            fail('PAGE_MISSING', 404)
        return p

    def ready(s):
        if not s['pages'] or any(p['status'] != 'ready' for p in s['pages']):
            fail('PROCESS_PAGES_FIRST', 409)

    def check_destination(value):
        path = Path(value).resolve()
        roots = [Path(p).resolve() for p in allowed]
        if not any(path == p or p in path.parents for p in roots) or not path.is_dir():
            fail('DESTINATION_NOT_APPROVED')
        protected = [BASE / 'iskan', BASE / 'static', BASE / '.git', BASE / '.venv', store.root, BASE / 'certs']
        if any(path == p.resolve() or p.resolve() in path.parents for p in protected):
            fail('DESTINATION_PROTECTED')
        return path

    @app.get('/')
    def home():
        return FileResponse(BASE / 'static' / 'index.html')

    @app.get('/api/health')
    def health():
        return {'status': 'ok'}

    @app.post('/api/login')
    async def login(request: Request):
        payload = await request.json()
        now = time.time()
        attempts[:] = [t for t in attempts if now - t < 60]
        if not local(request):
            if len(attempts) >= 10:
                fail('PAIRING_RATE_LIMIT', 429)
            attempts.append(now)
            if not secrets.compare_digest(str(payload.get('code', '')).upper(), code):
                fail('PAIRING_CODE_INCORRECT', 403)
        token = secrets.token_urlsafe(32)
        tokens[token] = now + 24 * 3600
        response = JSONResponse({'paired': True})
        response.set_cookie('iskan', token, httponly=True, samesite='strict', secure=request.url.scheme == 'https', max_age=86400)
        return response

    @app.get('/api/bootstrap')
    def bootstrap(request: Request):
        return dict(settings=store.settings().model_dump(), sessions=store.list(), laptop=local(request),
                    destinations=allowed, public_url=public_url or '',
                    pairing_code=code if local(request) else None,
                    limits={'upload_mb': 32, 'pages': 100},
                    stats=stats())

    @app.get('/api/qr')
    def qr():
        if not public_url:
            fail('SETUP_HTTPS_FIRST', 409)
        stream = io.BytesIO()
        qrcode.make(public_url).save(stream, format='PNG')
        return Response(stream.getvalue(), media_type='image/png')

    @app.get('/api/stats')
    def stats():
        with store.lock:
            rows = store.db.execute('SELECT title,page_count,scanned_date FROM minimal.scan_logs').fetchall()
            return {'scan_count': len(rows), 'page_count': sum(r[1] for r in rows),
                    'logs': [dict(zip(('title', 'page_count', 'scanned_date'), r)) for r in rows]}

    @app.put('/api/settings')
    def settings(config: Settings):
        check_destination(config.destination)
        store.config('settings', config.model_dump())
        return config

    @app.post('/api/storage')
    async def storage(request: Request):
        if not local(request):
            fail('LAPTOP_ONLY', 403)
        payload = await request.json()
        value = payload.get('path')
        if payload.get('browse'):
            def choose():
                import tkinter as tk
                from tkinter import filedialog
                window = tk.Tk()
                window.withdraw()
                window.attributes('-topmost', True)
                try:
                    return filedialog.askdirectory(parent=window, title='I-SKAN output folder')
                finally:
                    window.destroy()
            value = await run_in_threadpool(choose)
        if not value:
            fail('FOLDER_SELECTION_CANCELLED')
        path = Path(value).resolve()
        if not path.is_dir():
            fail('FOLDER_MUST_EXIST')
        # Prevent routing exports into operational secrets or application code.
        protected = [store.root, BASE / 'certs', BASE / 'iskan', BASE / 'static', BASE / '.git', BASE / '.venv']
        if any(path == p.resolve() or p.resolve() in path.parents for p in protected):
            fail('DESTINATION_PROTECTED')
        try:
            fd, probe = tempfile.mkstemp(prefix='.iskan-write-', dir=path)
            os.close(fd)
            Path(probe).unlink()
        except OSError:
            fail('FOLDER_UNWRITABLE')
        if str(path) not in allowed:
            allowed.append(str(path))
            store.config('destinations', allowed)
        config = store.settings()
        config.destination = str(path)
        store.config('settings', config.model_dump())
        return {'destination': str(path), 'destinations': allowed}

    @app.post('/api/sessions')
    def create(config: Settings):
        check_destination(config.destination)
        with store.lock:
            if len(store.list()) >= 50:
                fail('DRAFT_LIMIT_DELETE_OLD_DRAFTS', 409)
            store.config('settings', config.model_dump())
            return store.new(config)

    @app.post('/api/quality')
    async def live_quality(file: UploadFile = File(...)):
        data = await file.read(512 * 1024 + 1)
        await file.close()
        if len(data) > 512 * 1024:
            fail('LIVE_FRAME_TOO_LARGE', 413)
        try:
            image = await run_in_threadpool(decode, data)
            if image.shape[0] * image.shape[1] > 1_000_000:
                fail('LIVE_FRAME_TOO_LARGE', 413)
            return await run_in_threadpool(quality, image)
        except HTTPException:
            raise
        except Exception:
            fail('INVALID_LIVE_FRAME', 422)

    @app.get('/api/sessions/{sid}')
    def get(sid: str):
        return session(sid)

    @app.delete('/api/sessions/{sid}')
    def discard(sid: str):
        import shutil
        with store.lock:
            s = session(sid)
            for p in s['pages']:
                store.ocr.pop((p['id'], p['revision']), None)
            with store.db:
                store.db.execute('DELETE FROM sessions WHERE id=?', (sid,))
            folder = (store.root / s['id']).resolve()
            if folder.parent == store.root:
                shutil.rmtree(folder, ignore_errors=True)
        return {'discarded': True}

    @app.put('/api/sessions/{sid}/settings')
    def session_settings(sid: str, config: Settings, revision: int):
        with store.lock:
            s = session(sid, True)
            if revision != s['revision']:
                fail('STATE_CHANGED_REFRESH', 409)
            if config.mode == 'single' and len(s['pages']) > 1:
                fail('SINGLE_HAS_MULTIPLE_PAGES', 409)
            check_destination(config.destination)
            processing_changed = any(s['settings'][key] != getattr(config, key) for key in ('appearance', 'strength'))
            s['settings'] = config.model_dump()
            store.invalidate(s)
            if processing_changed:
                for p in s['pages']:
                    store.invalidate(s, p)
            store.refresh_suggestions(s)
            store.config('settings', config.model_dump())
            store.put(s)
            return s

    @app.post('/api/sessions/{sid}/pages')
    async def upload(sid: str, file: UploadFile = File(...), replace: str = Form(''),
                     capture_id: str = Form(...), revision: int = Form(0)):
        if not re.fullmatch(r'[a-zA-Z0-9-]{16,64}', capture_id):
            fail('INVALID_CAPTURE_ID')
        data = await file.read(32 * 1024 * 1024 + 1)
        await file.close()
        if len(data) > 32 * 1024 * 1024:
            fail('UPLOAD_TOO_LARGE', 413)
        try:
            image = await run_in_threadpool(decode, data)
            measured = await run_in_threadpool(quality, image)
            original = await run_in_threadpool(encode, image)
        except Exception:
            fail('UNSUPPORTED_OR_INVALID_PHOTO', 422)
        with store.lock:
            s = session(sid, True)
            if capture_id in s.get('capture_ids', []):
                return s
            if replace:
                p = page(s, replace)
                if revision != p['revision']:
                    fail('PAGE_CHANGED_REFRESH', 409)
                store.invalidate(s, p)
            else:
                if len(s['pages']) >= (1 if s['settings']['mode'] == 'single' else 100):
                    fail('PAGE_LIMIT_RETAKE_OR_NEW_SESSION', 409)
                store.invalidate(s)
                p = dict(id=secrets.token_hex(16), revision=1)
                s['pages'].append(p)
            filename = f"{p['id']}-{p['revision']}-original.png"
            (store.root / sid / filename).write_bytes(original)
            p.update(original=filename, crop=measured['corners'], rotation=0, quality=measured,
                     status='captured', stage='Awaiting processing', original_retained=True, ocr_warning='')
            s['scanned_date'] = s['scanned_date'] or scan_date()
            s.setdefault('capture_ids', []).append(capture_id)
            store.put(s)
            return s

    @app.put('/api/sessions/{sid}/pages/{pid}')
    def correct(sid: str, pid: str, correction: Correction):
        with store.lock:
            s = session(sid, True)
            p = page(s, pid)
            if correction.revision != p['revision']:
                fail('PAGE_CHANGED_REFRESH', 409)
            if not p['original_retained']:
                fail('ORIGINAL_REMOVED', 409)
            store.invalidate(s, p)
            p.update(crop=correction.crop, rotation=correction.rotation)
            store.put(s)
            return s

    @app.delete('/api/sessions/{sid}/pages/{pid}')
    def remove(sid: str, pid: str, revision: int):
        with store.lock:
            s = session(sid, True)
            p = page(s, pid)
            if s['revision'] != revision:
                fail('STATE_CHANGED_REFRESH', 409)
            s['pages'].remove(p)
            store.invalidate(s)
            store.ocr.pop((pid, p['revision']), None)
            store.refresh_suggestions(s)
            store.put(s)
            return s

    @app.put('/api/sessions/{sid}/order')
    async def reorder(sid: str, request: Request):
        payload = await request.json()
        with store.lock:
            s = session(sid, True)
            ids = payload.get('pages', [])
            if payload.get('revision') != s['revision']:
                fail('STATE_CHANGED_REFRESH', 409)
            if len(ids) != len(set(ids)) or set(ids) != {p['id'] for p in s['pages']}:
                fail('INVALID_PAGE_ORDER')
            pages = {p['id']: p for p in s['pages']}
            s['pages'] = [pages[pid] for pid in ids]
            store.invalidate(s)
            store.refresh_suggestions(s)
            store.put(s)
            return s

    @app.post('/api/sessions/{sid}/process')
    def process(sid: str):
        with store.lock:
            s = session(sid, True)
            if not s['pages']:
                fail('CAPTURE_PAGES_FIRST')
            if s['settings']['mode'] == 'multiple' and len(s['pages']) != s['settings']['expected']:
                fail('EXPECTED_COUNT_MISMATCH', 409)
            todo = [p for p in s['pages'] if p['status'] in ('captured', 'failed')]
            if worker.queue.qsize() + len(todo) > 100:
                fail('QUEUE_FULL_TRY_LATER', 429)
            for p in todo:
                p.update(status='queued', stage='Waiting in processing queue')
            store.refresh_suggestions(s)
            store.put(s)
            for p in todo:
                worker.enqueue(sid, p['id'], p['revision'], s['settings'])
            return s

    @app.get('/api/sessions/{sid}/pages/{pid}/{kind}')
    def asset(sid: str, pid: str, kind: str):
        with store.lock:
            s = session(sid)
            p = page(s, pid)
            if kind == 'original':
                name = p['original']
            elif kind == 'processed' and p['status'] == 'ready':
                name = f'{pid}-{p["revision"]}-processed.png'
            else:
                fail('ASSET_NOT_READY', 404)
            path = store.root / sid / name
            if not path.is_file():
                fail('ASSET_REMOVED', 404)
            return FileResponse(path, media_type='image/png')

    @app.post('/api/sessions/{sid}/approve')
    def approve(sid: str, approval: Approval):
        with store.lock:
            s = session(sid, True)
            ready(s)
            if approval.revision != s['revision']:
                fail('STATE_CHANGED_REFRESH', 409)
            try:
                valid_filename(approval.filename)
            except ValueError:
                fail('INVALID_FILENAME')
            check_destination(approval.destination)
            s['approved'] = approval.model_dump()
            s['metadata'] = approval.model_dump()
            store.put(s)
            return s

    @app.get('/api/sessions/{sid}/preview/{index}')
    def preview(sid: str, index: int):
        with store.lock:
            s = session(sid)
            ready(s)
            if index < 0 or index >= len(s['pages']):
                fail('PAGE_MISSING', 404)
            # Identical PDF composition is used for previews, saves and printing.
            pdf = pdf_bytes(store, s, searchable=False)
        return Response(raster_page(pdf, index), media_type='image/png')

    @app.get('/api/sessions/{sid}/pdf')
    def document(sid: str):
        with store.lock:
            s = session(sid)
            if s['saved'] and Path(s['saved']).suffix.lower() == '.pdf' and Path(s['saved']).is_file():
                return FileResponse(s['saved'], media_type='application/pdf', content_disposition_type='inline')
            ready(s)
            if not s['approved']:
                fail('APPROVE_DOCUMENT_FIRST', 409)
            pdf = pdf_bytes(store, s, searchable=s['settings']['export'] == 'searchable')
            return Response(pdf, media_type='application/pdf')

    @app.post('/api/sessions/{sid}/save')
    def save(sid: str):
        with store.lock:
            s = session(sid)
            if s['saved']:
                if not Path(s['saved']).exists():
                    fail('SAVED_OUTPUT_MISSING_OR_CHANGED', 409)
                return {'saved': s['saved'], 'already_saved': True}
            ready(s)
            if not s['approved'] or s['approved']['revision'] != s['revision']:
                fail('APPROVE_DOCUMENT_FIRST', 409)
            path = check_destination(s['approved']['destination'])
            try:
                target = save_document(store, s, path)
            except FileExistsError:
                fail('NAME_COLLISION', 409)
            except OSError:
                fail('SAVE_FAILED_CHECK_FOLDER_OR_DISK', 409)
            except ValueError as exc:
                fail(str(exc), 409)
            s['saved'] = str(target)
            if not s['settings']['retain_originals']:
                for original in (store.root / sid).glob('*-original.png'):
                    try:
                        original.unlink(missing_ok=True)
                    except OSError:
                        pass  # A locked original is expired with the working copy.
                for p in s['pages']:
                    p['original_retained'] = (store.root / sid / p['original']).exists()
            store.put(s)
            settings = store.settings()
            settings.destination = str(path)
            store.config('settings', settings.model_dump())
            return {'saved': str(target), 'already_saved': False}

    @app.post('/api/sessions/{sid}/print')
    def prepare_print(sid: str):
        with store.lock:
            s = session(sid)
            if not s['saved']:
                ready(s)
            if not s['approved']:
                fail('APPROVE_DOCUMENT_FIRST', 409)
            s['print_ready'] = True
            store.put(s)
            return {'state': 'Ready to print on laptop', 'url': f'/api/sessions/{sid}/pdf'}

    app.mount('/static', StaticFiles(directory=BASE / 'static'), name='static')
    return app
