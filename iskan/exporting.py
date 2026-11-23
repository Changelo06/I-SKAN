import hashlib
import io
import os
import re
import shutil
import tempfile
from pathlib import Path

import cv2
import fitz
import numpy as np
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.pdfgen import canvas

from .models import Settings

pdfmetrics.registerFont(UnicodeCIDFont('MSung-Light'))


def valid_filename(name):
    # Validate; never silently change the approved filename.
    if not name.strip() or name != name.strip() or re.search(r'[<>:"/\\|?*\x00-\x1f]', name) or name.endswith(('.', ' ')):
        raise ValueError('INVALID_FILENAME')
    if name.split('.')[0].upper() in {'CON', 'PRN', 'AUX', 'NUL', *(f'COM{i}' for i in range(1, 10)), *(f'LPT{i}' for i in range(1, 10))}:
        raise ValueError('INVALID_FILENAME')
    if name in ('.', '..'):
        raise ValueError('INVALID_FILENAME')
    return name


def safe_proposal(name):
    name = re.sub(r'[<>:"/\\|?*\x00-\x1f]', '-', name).strip(' .')[:130]
    try:
        return valid_filename(name)
    except ValueError:
        return 'Scan'


def pdf_bytes(store, s, searchable=True):
    settings = Settings(**s['settings'])
    output = io.BytesIO()
    c = canvas.Canvas(output, pageCompression=1, invariant=1)
    c.setTitle(s['approved']['title'] if s['approved'] else s.get('suggestions', {}).get('title', 'Scan'))
    c.setAuthor('')
    for i, page in enumerate(s['pages']):
        path = store.root / s['id'] / f"{page['id']}-{page['revision']}-processed.png"
        image = cv2.imdecode(np.frombuffer(path.read_bytes(), dtype=np.uint8), cv2.IMREAD_COLOR)
        ih, iw = image.shape[:2]
        pw, ph = [v * 72 / 25.4 for v in settings.dimensions()]
        margin = settings.margin * 72 / 25.4
        if settings.layout == 'preserve':
            # Keep crop ratio; selected paper width anchors the physical output size.
            ph = (pw - margin * 2) * ih / iw + margin * 2
        c.setPageSize((pw, ph))
        scale = min((pw - 2 * margin) / iw, (ph - 2 * margin) / ih)
        x, y = (pw - iw * scale) / 2, (ph - ih * scale) / 2
        ok, jpg = cv2.imencode('.jpg', image, [cv2.IMWRITE_JPEG_QUALITY, settings.quality])
        if not ok:
            raise ValueError('IMAGE_ENCODE')
        c.drawImage(ImageReader(io.BytesIO(jpg.tobytes())), x, y, iw * scale, ih * scale)
        if searchable:
            records = store.ocr.get((page['id'], page['revision']))
            if records is None:
                raise ValueError('OCR_REQUIRED')
            for r in records:
                if r['confidence'] < .5 or not r['text'].strip():
                    continue
                p = np.array(r['box'])
                # Follow OCR baseline angle and length, in the processed image coordinates.
                left = (p[0] + p[3]) / 2
                right = (p[1] + p[2]) / 2
                height = (np.linalg.norm(p[3] - p[0]) + np.linalg.norm(p[2] - p[1])) / 2
                font_size = max(1, height * scale * .85)
                width = np.linalg.norm(right - left) * scale
                font = 'MSung-Light' if any(ord(ch) > 255 for ch in r['text']) else 'Helvetica'
                c.saveState()
                c.translate(x + left[0] * scale, y + (ih - left[1]) * scale)
                angle = -np.degrees(np.arctan2(right[1] - left[1], right[0] - left[0]))
                c.rotate(float(angle))
                t = c.beginText(0, -font_size * .32)
                t.setFont(font, font_size)
                t.setTextRenderMode(3)
                natural = pdfmetrics.stringWidth(r['text'], font, font_size)
                if natural:
                    t.setHorizScale(width / natural * 100)
                t.textOut(r['text'])
                c.drawText(t)
                c.restoreState()
        if settings.watermark:
            c.saveState()
            c.setFillAlpha(settings.watermark_opacity)
            c.setFillColorRGB(.25, .25, .25)
            font = 'MSung-Light' if any(ord(ch) > 255 for ch in settings.watermark) else 'Helvetica'
            size = min(settings.watermark_size, (pw - 24) / max(1, pdfmetrics.stringWidth(settings.watermark, font, 1)))
            c.setFont(font, size)
            wy = ph / 2 if settings.watermark_position == 'center' else (ph - size - 12 if settings.watermark_position == 'top' else 18)
            c.drawCentredString(pw / 2, wy, settings.watermark)
            c.restoreState()
        if settings.numbers:
            c.setFont('Helvetica', 10)
            c.setFillColorRGB(.2, .2, .2)
            text = str(settings.number_start + i)
            if settings.number_position == 'left':
                c.drawString(12, 10, text)
            elif settings.number_position == 'right':
                c.drawRightString(pw - 12, 10, text)
            else:
                c.drawCentredString(pw / 2, 10, text)
        c.showPage()
    c.save()
    return output.getvalue()


def raster_page(pdf, index, dpi=120, ext='png', quality=92):
    with fitz.open(stream=pdf, filetype='pdf') as document:
        page = document[index]
        scale = min(dpi / 72, 6000 / max(page.rect.width, page.rect.height))
        pix = page.get_pixmap(matrix=fitz.Matrix(scale, scale), alpha=False)
        return pix.tobytes('jpg', jpg_quality=quality) if ext == 'jpg' else pix.tobytes('png')


def digest(path):
    h = hashlib.sha256()
    if path.is_dir():
        for item in sorted(path.iterdir()):
            if not item.is_file():
                raise ValueError('SAVE_LAYOUT')
            h.update(item.name.encode())
            h.update(item.read_bytes())
    else:
        h.update(path.read_bytes())
    return h.hexdigest()


def save_document(store, s, destination):
    approval = s['approved']
    settings = Settings(**s['settings'])
    name = valid_filename(approval['filename'])
    pdf = pdf_bytes(store, s, searchable=settings.export == 'searchable')
    ext = '.pdf' if settings.export in ('searchable', 'pdf') else ''
    if ext and name.lower().endswith(ext):
        name = name[:-len(ext)]
    target = destination / (name + ext)
    existing = store.db.execute('SELECT target,committed,digest FROM saves WHERE session=?', (s['id'],)).fetchone()
    if existing:
        old = Path(existing[0])
        if old.exists() and digest(old) == existing[2]:
            store.commit_save(s['id'])
            return old
        if existing[1]:
            raise ValueError('SAVED_OUTPUT_MISSING_OR_CHANGED')
    if target.exists():
        raise FileExistsError('NAME_COLLISION')
    temp = Path(tempfile.mkdtemp(prefix='.iskan-', dir=destination))
    try:
        payload = temp / 'payload'
        if ext:
            with payload.open('wb') as f:
                f.write(pdf)
                f.flush()
                os.fsync(f.fileno())
        else:
            payload.mkdir()
            for i in range(len(s['pages'])):
                fpath = payload / f'{i + 1:03d}.{settings.export}'
                fpath.write_bytes(raster_page(pdf, i, dpi=240, ext=settings.export, quality=settings.quality))
        checksum = digest(payload)
        with store.db:
            store.db.execute('INSERT OR REPLACE INTO saves(session,target,title,pages,scanned,committed,digest) VALUES (?,?,?,?,?,0,?)',
                             (s['id'], str(target), approval['title'], len(s['pages']), s['scanned_date'], checksum))
        if ext:
            # Hard-link publishes the fully written file atomically, refusing overwrite.
            os.link(payload, target)
        else:
            # Windows rename refuses an existing target; on POSIX protect with mkdir then
            # move files and only consider the complete checksum committed on recovery.
            if os.name == 'nt':
                os.rename(payload, target)
            else:
                target.mkdir()
                try:
                    for item in payload.iterdir():
                        os.rename(item, target / item.name)
                except Exception:
                    shutil.rmtree(target)
                    raise
        store.commit_save(s['id'])
        return target
    finally:
        shutil.rmtree(temp, ignore_errors=True)
