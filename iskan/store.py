"""Operational state is separate from the deliberately minimal scan log."""
import json
import secrets
import shutil
import sqlite3
import threading
import time
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from .models import Settings


def scan_date():
    return datetime.now(ZoneInfo('Asia/Taipei')).date().isoformat()


class Store:
    def __init__(self, root: Path):
        self.root = root.resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        self.db = sqlite3.connect(self.root / 'operational.sqlite', check_same_thread=False)
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.executescript('''
        CREATE TABLE IF NOT EXISTS sessions(id TEXT PRIMARY KEY, state TEXT NOT NULL, updated REAL NOT NULL);
        CREATE TABLE IF NOT EXISTS config(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS saves(session TEXT PRIMARY KEY, target TEXT NOT NULL, title TEXT NOT NULL,
           pages INTEGER NOT NULL, scanned TEXT NOT NULL, committed INTEGER NOT NULL DEFAULT 0, digest TEXT NOT NULL);
        ''')
        # Separate DB: each scan_logs row has exactly the three specified fields.
        self.logs = sqlite3.connect(self.root / 'scan_logs.sqlite', check_same_thread=False)
        self.logs.execute('CREATE TABLE IF NOT EXISTS scan_logs(title TEXT NOT NULL, page_count INTEGER NOT NULL, scanned_date TEXT NOT NULL)')
        # ATTACH permits save-state/log insertion in one SQLite transaction (DELETE journals).
        self.db.execute('PRAGMA journal_mode=DELETE')
        self.db.execute('ATTACH DATABASE ? AS minimal', (str(self.root / 'scan_logs.sqlite'),))
        self.db.commit()
        self.ocr = {}  # OCR lives only in memory; restart requires recognition again.
        self.cleanup()
        self.recover_saves()
        for s in self.list():
            if s.get('saved'):
                continue
            dirty = False
            for p in s['pages']:
                if p['status'] in ('queued', 'processing', 'ready'):
                    p['status'] = 'captured'
                    p['stage'] = 'Process again after restart'
                    dirty = True
            if dirty:
                s['approved'] = None
                self.put(s)

    def get_config(self, key, fallback=None):
        with self.lock:
            row = self.db.execute('SELECT value FROM config WHERE key=?', (key,)).fetchone()
            return json.loads(row[0]) if row else fallback

    def config(self, key, value):
        with self.lock, self.db:
            self.db.execute('INSERT OR REPLACE INTO config VALUES (?,?)', (key, json.dumps(value)))

    def settings(self):
        return Settings(**self.get_config('settings', {}))

    def get(self, sid):
        with self.lock:
            row = self.db.execute('SELECT state FROM sessions WHERE id=?', (sid,)).fetchone()
            if not row:
                raise KeyError('SESSION_MISSING')
            return json.loads(row[0])

    def put(self, state):
        with self.lock, self.db:
            self.db.execute('INSERT OR REPLACE INTO sessions VALUES (?,?,?)',
                            (state['id'], json.dumps(state), time.time()))

    def list(self):
        with self.lock:
            return [json.loads(r[0]) for r in self.db.execute('SELECT state FROM sessions ORDER BY updated DESC')]

    def new(self, settings):
        sid = secrets.token_hex(16)
        s = dict(id=sid, revision=1, settings=settings.model_dump(), pages=[], scanned_date=None,
                 approved=None, metadata=None, suggestions={}, saved=None, print_ready=False)
        (self.root / sid).mkdir()
        self.put(s)
        return s

    def invalidate(self, s, page=None):
        s['revision'] += 1
        s['approved'] = None
        s['suggestions'] = {}
        s['print_ready'] = False
        if page:
            self.ocr.pop((page['id'], page['revision']), None)
            page['revision'] += 1
            page['status'] = 'captured'
            page['stage'] = 'Awaiting processing'

    def refresh_suggestions(self, s):
        if s['pages'] and all(p['status'] == 'ready' for p in s['pages']):
            from .imaging import suggestions
            from .exporting import safe_proposal
            records = [self.ocr.get((p['id'], p['revision']), []) for p in s['pages']]
            s['suggestions'] = suggestions(records, s['scanned_date'], Settings(**s['settings']))
            s['suggestions']['filename'] = safe_proposal(s['suggestions']['filename'])

    def commit_save(self, sid):
        with self.lock, self.db:
            row = self.db.execute('SELECT title,pages,scanned,committed FROM saves WHERE session=?', (sid,)).fetchone()
            if row and not row[3]:
                self.db.execute('INSERT INTO minimal.scan_logs VALUES (?,?,?)', row[:3])
                self.db.execute('UPDATE saves SET committed=1 WHERE session=?', (sid,))

    def recover_saves(self):
        # A completed atomic rename followed by a crash is reconciled on launch.
        from .exporting import digest
        for sid, target, committed, checksum in self.db.execute('SELECT session,target,committed,digest FROM saves').fetchall():
            matches = False
            try:
                matches = Path(target).exists() and digest(Path(target)) == checksum
            except OSError:
                pass
            if not committed and matches:
                self.commit_save(sid)
            if matches:
                try:
                    s = self.get(sid)
                    s['saved'] = target
                    self.put(s)
                except KeyError:
                    pass

    def cleanup(self):
        # Drafts expire after 7 days. Retained saved originals expire after 30 days.
        cutoff = time.time()
        for sid, raw, updated in self.db.execute('SELECT * FROM sessions').fetchall():
            s = json.loads(raw)
            days = 30 if s.get('saved') and s['settings'].get('retain_originals') else 7
            if cutoff - updated > days * 86400:
                for p in s['pages']:
                    self.ocr.pop((p['id'], p['revision']), None)
                folder = (self.root / sid).resolve()
                if folder.parent == self.root:
                    shutil.rmtree(folder, ignore_errors=True)
                with self.db:
                    self.db.execute('DELETE FROM sessions WHERE id=?', (sid,))

    def close(self):
        self.db.close()
        self.logs.close()
