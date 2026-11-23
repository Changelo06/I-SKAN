import queue
import threading
from pathlib import Path

from .imaging import OCR, clean, decode, encode, suggestions, warp
from .models import Settings
from .exporting import safe_proposal


class Worker:
    def __init__(self, store):
        self.store = store
        self.queue = queue.Queue(maxsize=100)
        self.ocr = OCR()
        self.thread = threading.Thread(target=self.run, daemon=True)
        self.thread.start()

    def enqueue(self, sid, pid, revision, settings):
        self.queue.put_nowait((sid, pid, revision, settings))

    def page(self, sid, pid, revision):
        s = self.store.get(sid)
        p = next((p for p in s['pages'] if p['id'] == pid), None)
        return (s, p) if p and p['revision'] == revision else (None, None)

    def stage(self, sid, pid, revision, label):
        with self.store.lock:
            s, p = self.page(sid, pid, revision)
            if not p:
                return False
            p.update(status='processing', stage=label)
            self.store.put(s)
            return True

    def process(self, sid, pid, revision, config):
        with self.store.lock:
            s, p = self.page(sid, pid, revision)
            if not p:
                return
            folder = self.store.root / sid
            original = folder / p['original']
            rotation, crop = p['rotation'], p['crop']
        stage = lambda label: self.stage(sid, pid, revision, label)
        stage('Correcting page shape')
        image = decode(original.read_bytes())
        image = warp(image, crop)
        if rotation:
            import numpy as np
            image = np.rot90(image, k=-(rotation // 90)).copy()
        image = clean(image, Settings(**config), stage)
        stage('Recognizing text')
        records = self.ocr(image)
        stage('Preparing preview')
        target = folder / f'{pid}-{revision}-processed.png'
        target.write_bytes(encode(image))
        with self.store.lock:
            s, p = self.page(sid, pid, revision)
            if not p:
                target.unlink(missing_ok=True)
                return
            self.store.ocr[(pid, revision)] = records
            p.update(status='ready', stage='Ready for verification',
                     ocr_warning='No readable text found — inspect or retake.' if not records else
                     ('Some text has low confidence — inspect carefully.' if any(r['confidence'] < .7 for r in records) else ''))
            self.store.refresh_suggestions(s)
            self.store.put(s)

    def run(self):
        while True:
            try:
                job = self.queue.get(timeout=3600)
            except queue.Empty:
                with self.store.lock:
                    self.store.cleanup()
                continue
            if job is None:
                self.queue.task_done()
                return
            sid, pid, revision, config = job
            try:
                self.process(sid, pid, revision, config)
            except Exception:
                # Intentionally no exception messages, tracebacks, document paths or text.
                with self.store.lock:
                    try:
                        s, p = self.page(sid, pid, revision)
                        if p:
                            p.update(status='failed', stage='PROCESSING_FAILED — retry or retake')
                            self.store.put(s)
                    except KeyError:
                        pass
            finally:
                self.queue.task_done()

    def close(self):
        self.queue.put(None)
        self.thread.join(timeout=5)
