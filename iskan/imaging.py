import io
import re
import threading

import cv2
import numpy as np
from PIL import Image, ImageOps
from pillow_heif import register_heif_opener

register_heif_opener()
Image.MAX_IMAGE_PIXELS = 60_000_000


def decode(data):
    with Image.open(io.BytesIO(data)) as im:
        if im.width * im.height > 60_000_000 or min(im.size) < 100:
            raise ValueError('IMAGE_SIZE')
        im = ImageOps.exif_transpose(im).convert('RGB')
        return cv2.cvtColor(np.array(im), cv2.COLOR_RGB2BGR)


def encode(image, ext='.png', quality=92):
    options = [cv2.IMWRITE_JPEG_QUALITY, quality] if ext == '.jpg' else []
    ok, buf = cv2.imencode(ext, image, options)
    if not ok:
        raise ValueError('IMAGE_ENCODE')
    return buf.tobytes()


def order_points(points):
    # Angle ordering avoids duplicate corners from sum/difference ties.
    p = np.array(points, dtype=np.float32)
    center = p.mean(axis=0)
    p = p[np.argsort(np.arctan2(p[:, 1] - center[1], p[:, 0] - center[0]))]
    p = np.roll(p, -np.argmin(p.sum(axis=1)), axis=0)
    return p


def edges(image):
    h, w = image.shape[:2]
    scale = min(1, 700 / max(h, w))
    small = cv2.resize(image, None, fx=scale, fy=scale)
    gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
    mask = cv2.Canny(cv2.GaussianBlur(gray, (5, 5), 0), 40, 130)
    mask = cv2.dilate(mask, np.ones((3, 3), np.uint8))
    contours, _ = cv2.findContours(mask, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    area = small.shape[0] * small.shape[1]
    for c in sorted(contours, key=cv2.contourArea, reverse=True)[:15]:
        poly = cv2.approxPolyDP(c, .02 * cv2.arcLength(c, True), True)
        if len(poly) == 4 and cv2.isContourConvex(poly) and .18 * area < cv2.contourArea(poly) < .99 * area:
            # A table or boxed heading is also a quadrilateral. Require a paper /
            # background transition; otherwise keep the full photo for manual crop.
            inside = np.zeros_like(gray)
            cv2.fillConvexPoly(inside, poly.reshape(4, 2), 255)
            core = cv2.erode(inside, np.ones((15, 15), np.uint8))
            ring = cv2.subtract(cv2.dilate(inside, np.ones((21, 21), np.uint8)), inside)
            if not np.any(core) or not np.any(ring):
                continue
            if abs(float(np.median(gray[core > 0])) - float(np.median(gray[ring > 0]))) < 18:
                continue
            p = order_points(poly.reshape(4, 2)) / scale
            return (p / np.array([w, h])).tolist()
    return None


def quality(image):
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    h, w = gray.shape
    small = cv2.resize(gray, (min(w, 600), max(1, int(h * min(w, 600) / w))))
    corners = edges(image)
    brightness = float(np.mean(small))
    sharpness = float(cv2.Laplacian(small, cv2.CV_64F).var())
    clipped = bool(corners and any(x < .012 or x > .988 or y < .012 or y > .988 for x, y in corners))
    area = abs(cv2.contourArea(np.array(corners, np.float32))) if corners else 0
    warnings = []
    if brightness < 65:
        warnings.append('Too dark')
    if sharpness < 28:
        warnings.append('Blur risk — hold still')
    if clipped:
        warnings.append('Page may be cut off')
    if corners and area < .35:
        warnings.append('Move closer')
    if not corners:
        warnings.append('No page boundary found — check crop')
    return dict(corners=corners, warnings=warnings, brightness=round(brightness, 1),
                sharpness=round(sharpness, 1))


def warp(image, corners):
    if not corners:
        return image
    h, w = image.shape[:2]
    p = np.array(corners, np.float32) * [w, h]
    p = p.astype(np.float32)
    width = max(np.linalg.norm(p[1] - p[0]), np.linalg.norm(p[2] - p[3]))
    height = max(np.linalg.norm(p[3] - p[0]), np.linalg.norm(p[2] - p[1]))
    if min(width, height) < 40:
        raise ValueError('CROP_TOO_SMALL')
    scale = min(1, 3600 / max(width, height))
    width, height = int(width * scale), int(height * scale)
    dest = np.float32([[0, 0], [width - 1, 0], [width - 1, height - 1], [0, height - 1]])
    return cv2.warpPerspective(image, cv2.getPerspectiveTransform(p, dest), (width, height))


def deskew(image):
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    scale = min(1, 1200 / max(gray.shape))
    small = cv2.resize(gray, None, fx=scale, fy=scale)
    lines = cv2.HoughLinesP(cv2.Canny(small, 60, 150), 1, np.pi / 1800, 70,
                            minLineLength=small.shape[1] * .18, maxLineGap=15)
    if lines is None:
        return image
    angles = [np.degrees(np.arctan2(y2 - y1, x2 - x1)) for [[x1, y1, x2, y2]] in lines]
    angles = [a for a in angles if abs(a) < 5]
    if len(angles) < 4:
        return image
    angle = float(np.median(angles))
    if abs(angle) < .2 or np.std(angles) > 1.8:
        return image
    h, w = image.shape[:2]
    m = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1)
    # Expand the canvas: no text lost at the edges during rotation.
    nw = int(abs(m[0, 0]) * w + abs(m[0, 1]) * h)
    nh = int(abs(m[0, 0]) * h + abs(m[0, 1]) * w)
    m[0, 2] += (nw - w) / 2
    m[1, 2] += (nh - h) / 2
    return cv2.warpAffine(image, m, (nw, nh), borderValue=(255, 255, 255))


def clean(image, settings, stage):
    stage('Correcting page shape')
    image = deskew(image)
    stage('Balancing lighting')
    strength = settings.strength
    if strength:
        lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
        l = lab[:, :, 0].astype(np.float32)
        background = cv2.GaussianBlur(l, (0, 0), max(15, max(image.shape[:2]) / 35))
        correction = np.clip(225 / np.maximum(background, 60), .9, 1.8)
        lab[:, :, 0] = np.clip(l * (1 + (correction - 1) * strength), 0, 255).astype(np.uint8)
        image = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)
    if settings.appearance in ('grayscale', 'bw'):
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        if settings.appearance == 'bw':
            gray = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                         cv2.THRESH_BINARY, 41, 12)
        image = cv2.cvtColor(gray, cv2.COLOR_GRAY2BGR)
    return image


class OCR:
    def __init__(self):
        self.engine = None
        self.lock = threading.Lock()

    def __call__(self, image):
        with self.lock:
            if self.engine is None:
                from rapidocr_onnxruntime import RapidOCR
                self.engine = RapidOCR(intra_op_num_threads=2, inter_op_num_threads=1,
                                      print_verbose=False)
            result, _ = self.engine(image)
            return [dict(box=np.array(box).tolist(), text=text, confidence=float(conf))
                    for box, text, conf in (result or [])]


def suggestions(records, scanned, settings):
    lines = [r for page in records for r in page if r['confidence'] >= .65]
    first = records[0] if records else []
    candidates = [r for r in first[:12] if 3 <= len(r['text'].strip()) <= 120 and r['confidence'] >= .7]
    if candidates:
        heading = max(candidates, key=lambda r: np.linalg.norm(np.array(r['box'][3]) - r['box'][0]))
        title = heading['text'].strip()
        title_note = 'Candidate heading from page 1 — please verify.'
    else:
        title, title_note = 'Untitled scan', 'No reliable heading found.'
    dates = set()
    for r in lines:
        for match in re.finditer(r'\b(20\d{2})[-/.年](\d{1,2})[-/.月](\d{1,2})日?\b', r['text']):
            try:
                from datetime import date
                dates.add(date(*map(int, match.groups())).isoformat())
            except ValueError:
                pass
    # No automatic document-date selection even for a single candidate.
    date = scanned if settings.date_source == 'scan' or settings.missing_date == 'scan' else ''
    filename = settings.naming.replace('{title}', title).replace('{date}', date or '').strip('_- ')
    return dict(title=title, title_note=title_note, date_candidates=sorted(dates),
                date_note='Dates found in text; choose only if appropriate.' if dates else 'Document date unknown.',
                filename=filename or 'Scan')
