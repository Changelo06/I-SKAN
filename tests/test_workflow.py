import io
import json
import uuid
from pathlib import Path

import cv2
import fitz
import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw, ImageFont

from iskan.app import create_app
from iskan.exporting import digest, pdf_bytes, raster_page
from iskan.imaging import decode, edges, quality, warp
from iskan.models import Settings
from iskan.store import Store


@pytest.fixture
def client(tmp_path):
    app = create_app(tmp_path / 'working', pairing_code='ABCDEF12')
    with TestClient(app, base_url='http://localhost') as c:
        assert c.post('/api/login', json={}).status_code == 200
        destination = tmp_path / 'saved'
        destination.mkdir()
        assert c.post('/api/storage', json={'path': str(destination)}).status_code == 200
        yield c, app, destination


def photo(title='MEETING MINUTES', color=(255, 255, 255)):
    im = Image.new('RGB', (1200, 1700), color)
    draw = ImageDraw.Draw(im)
    font_path = Path('C:/Windows/Fonts/arial.ttf')
    font = ImageFont.truetype(str(font_path), 58) if font_path.exists() else ImageFont.load_default(size=58)
    body = ImageFont.truetype(str(font_path), 36) if font_path.exists() else ImageFont.load_default(size=36)
    draw.text((100, 110), title, font=font, fill=(20, 20, 20))
    for i, text in enumerate(['2026-10-14', 'Project planning and review', 'Keep the signed original.', 'Next meeting at 10:00 AM']):
        draw.text((100, 270 + i * 100), text, font=body, fill=(30, 30, 30))
    draw.rectangle((90, 730, 1110, 1100), outline=(90, 90, 90), width=3)
    draw.line((90, 920, 1110, 920), fill=(120, 120, 120), width=3)
    draw.ellipse((820, 1240, 1050, 1470), outline=(170, 40, 40), width=7)
    buf = io.BytesIO(); im.save(buf, format='PNG'); return buf.getvalue()


def new(c, destination, mode='single', expected=1, **extra):
    config = Settings(mode=mode, expected=expected, destination=str(destination), strength=0, **extra).model_dump()
    r = c.post('/api/sessions', json=config)
    assert r.status_code == 200
    return r.json()


def upload(c, s, data=None, replace='', revision=0, key=None):
    r = c.post(f'/api/sessions/{s["id"]}/pages', files={'file': ('secret-upload-name.png', data or photo(), 'image/png')},
               data={'replace': replace, 'revision': str(revision), 'capture_id': key or str(uuid.uuid4())})
    assert r.status_code == 200, r.text
    return r.json()


def process(c, app, s):
    r = c.post(f'/api/sessions/{s["id"]}/process')
    assert r.status_code == 200, r.text
    app.state.worker.queue.join()
    s = c.get(f'/api/sessions/{s["id"]}').json()
    assert all(p['status'] == 'ready' for p in s['pages']), s
    return s


def approve(c, s, destination, filename='document', title='Approved title'):
    r = c.post(f'/api/sessions/{s["id"]}/approve', json=dict(revision=s['revision'], title=title,
               filename=filename, document_date='', destination=str(destination)))
    assert r.status_code == 200, r.text
    return r.json()


def fake_ocr(image):
    return [dict(box=[[100, 110], [670, 110], [670, 175], [100, 175]], text='MEETING MINUTES', confidence=.99)]


def test_single_real_ocr_searchable_save_and_preview(client, capsys):
    c, app, folder = client
    s = upload(c, new(c, folder, watermark='PRIVATE', numbers=True))
    first_date = s['scanned_date']
    s = process(c, app, s)
    records = app.state.store.ocr[(s['pages'][0]['id'], s['pages'][0]['revision'])]
    assert any('MEETING' in r['text'] for r in records)
    s = approve(c, s, folder)
    preview = c.get(f'/api/sessions/{s["id"]}/preview/0')
    assert preview.status_code == 200
    r = c.post(f'/api/sessions/{s["id"]}/save')
    assert r.status_code == 200, r.text
    target = Path(r.json()['saved'])
    assert target.is_file()
    pdf = target.read_bytes()
    assert preview.content == raster_page(pdf, 0)
    with fitz.open(stream=pdf, filetype='pdf') as doc:
        assert len(doc) == 1
        assert abs(doc[0].rect.width - 210 * 72 / 25.4) < 1
        text = doc[0].get_text()
        assert 'MEETING' in text
        words = [w for w in doc[0].get_text('words') if 'MEETING' in w[4]]
        assert words and 20 < words[0][1] < 100
    assert c.post(f'/api/sessions/{s["id"]}/save').json()['already_saved']
    stats = c.get('/api/stats').json()
    assert stats['logs'] == [{'title': 'Approved title', 'page_count': 1, 'scanned_date': first_date}]
    assert stats['scan_count'] == 1
    assert c.post(f'/api/sessions/{s["id"]}/print').status_code == 200
    assert c.get('/api/stats').json() == stats
    schema = app.state.store.logs.execute('PRAGMA table_info(scan_logs)').fetchall()
    assert [r[1] for r in schema] == ['title', 'page_count', 'scanned_date']
    assert not list(app.state.store.root.rglob('*.txt'))
    assert 'MEETING' not in capsys.readouterr().out


def test_multipage_retake_order_stale_job_and_upload_retry(client):
    c, app, folder = client
    app.state.worker.ocr = fake_ocr
    key = str(uuid.uuid4())
    s = upload(c, new(c, folder, 'multiple', 3), key=key)
    s = upload(c, s, key=key)
    assert len(s['pages']) == 1
    first = s['pages'][0]; scanned = s['scanned_date']
    s = upload(c, s); s = upload(c, s)
    ids = [p['id'] for p in s['pages']]
    s = process(c, app, s)
    s = approve(c, s, folder, title='User edited title')
    s = upload(c, s, photo('REVISED PAGE'), replace=first['id'], revision=first['revision'])
    assert s['approved'] is None and s['metadata']['title'] == 'User edited title'
    assert [p['id'] for p in s['pages']] == ids
    assert s['pages'][0]['revision'] == 2 and s['scanned_date'] == scanned
    assert s['pages'][1]['status'] == 'ready'
    assert (first['id'], 1) not in app.state.store.ocr
    # A late result for the old revision cannot change the replacement.
    app.state.worker.process(s['id'], first['id'], 1, s['settings'])
    assert c.get(f'/api/sessions/{s["id"]}').json()['pages'][0]['status'] == 'captured'
    order = list(reversed(ids))
    s = c.put(f'/api/sessions/{s["id"]}/order', json={'revision': s['revision'], 'pages': order}).json()
    assert [p['id'] for p in s['pages']] == order
    s = process(c, app, s)
    s = c.delete(f'/api/sessions/{s["id"]}/pages/{ids[1]}?revision={s["revision"]}').json()
    assert len(s['pages']) == 2
    assert c.post(f'/api/sessions/{s["id"]}/process').status_code == 409
    config = {**s['settings'], 'expected': 2}
    s = c.put(f'/api/sessions/{s["id"]}/settings?revision={s["revision"]}', json=config).json()
    s = process(c, app, s)
    s = approve(c, s, folder)
    assert c.post(f'/api/sessions/{s["id"]}/save').status_code == 200
    assert c.get('/api/stats').json()['page_count'] == 2


def test_collision_failure_retry_does_not_log(client, monkeypatch):
    c, app, folder = client
    app.state.worker.ocr = fake_ocr
    s = approve(c, process(c, app, upload(c, new(c, folder))), folder)
    (folder / 'document.pdf').write_bytes(b'keep me')
    assert c.post(f'/api/sessions/{s["id"]}/save').status_code == 409
    assert (folder / 'document.pdf').read_bytes() == b'keep me'
    assert c.get('/api/stats').json()['scan_count'] == 0
    s = approve(c, s, folder, 'another')
    import iskan.exporting as exporting
    original = exporting.os.link
    monkeypatch.setattr(exporting.os, 'link', lambda *a: (_ for _ in ()).throw(PermissionError()))
    assert c.post(f'/api/sessions/{s["id"]}/save').status_code == 409
    assert c.get('/api/stats').json()['scan_count'] == 0
    monkeypatch.setattr(exporting.os, 'link', original)
    assert c.post(f'/api/sessions/{s["id"]}/save').status_code == 200
    assert c.post(f'/api/sessions/{s["id"]}/save').status_code == 200
    assert c.get('/api/stats').json()['scan_count'] == 1


@pytest.mark.parametrize('fmt', ['pdf', 'jpg', 'png'])
def test_export_formats_and_print_only(client, fmt):
    c, app, folder = client
    app.state.worker.ocr = fake_ocr
    s = approve(c, process(c, app, upload(c, new(c, folder, export=fmt))), folder, fmt)
    assert c.post(f'/api/sessions/{s["id"]}/print').status_code == 200
    assert c.get('/api/stats').json()['scan_count'] == 0
    response = c.post(f'/api/sessions/{s["id"]}/save')
    assert response.status_code == 200
    target = Path(response.json()['saved'])
    if fmt == 'pdf':
        with fitz.open(target) as doc:
            assert not doc[0].get_text().strip()
    else:
        assert len(list(target.glob(f'*.{fmt}'))) == 1
        with Image.open(target / f'001.{fmt}') as im:
            assert im.width > 1500
    assert c.get('/api/stats').json()['page_count'] == 1


def test_restart_preserves_capture_and_minimal_log(client):
    c, app, folder = client
    app.state.worker.ocr = fake_ocr
    s = process(c, app, upload(c, new(c, folder)))
    store2 = Store(app.state.store.root)
    try:
        recovered = store2.get(s['id'])
        assert recovered['pages'][0]['status'] == 'captured'
        assert (store2.root / s['id'] / recovered['pages'][0]['original']).is_file()
        assert not store2.ocr
        assert recovered['scanned_date'] == s['scanned_date']
    finally:
        store2.close()


def test_geometry_quality_and_crop_validation():
    image = np.full((1000, 1000, 3), 40, np.uint8)
    points = np.int32([[220, 100], [830, 190], [750, 930], [110, 800]])
    cv2.fillConvexPoly(image, points, (245, 245, 245))
    corners = edges(image)
    assert corners is not None
    result = warp(image, corners)
    assert result.shape[0] > 600 and result.shape[1] > 500
    assert 'Too dark' in quality(np.full((500, 500, 3), 20, np.uint8))['warnings']
    with pytest.raises(ValueError):
        Settings(paper='Custom', width=5, height=10)


def test_access_validation_and_content_restraint(client):
    c, app, folder = client
    unauth = TestClient(app, base_url='http://localhost')
    assert unauth.get('/api/bootstrap').status_code == 401
    assert c.post('/api/login', json={}, headers={'Origin': 'https://attacker.example'}).status_code == 403
    assert c.get('/api/bootstrap', headers={'Host': 'attacker.example'}).status_code == 403
    invalid = c.post('/api/sessions', json={'expected': -1, 'destination': 'SECRET_PATH_SENTINEL'})
    assert invalid.status_code == 422
    assert 'SECRET_PATH_SENTINEL' not in invalid.text
    s = upload(c, new(c, folder))
    assert c.post(f'/api/sessions/{s["id"]}/save').status_code == 409
    p = s['pages'][0]
    invalid = c.put(f'/api/sessions/{s["id"]}/pages/{p["id"]}', json={'revision': p['revision'], 'crop': [[0,0],[1,1],[1,0],[0,1]]})
    assert invalid.status_code == 422


def test_crash_after_publish_reconciles_without_duplicate(client, monkeypatch):
    c, app, folder = client
    app.state.worker.ocr = fake_ocr
    s = approve(c, process(c, app, upload(c, new(c, folder))), folder, 'recover')
    original = app.state.store.commit_save
    monkeypatch.setattr(app.state.store, 'commit_save', lambda *a: (_ for _ in ()).throw(OSError()))
    assert c.post(f'/api/sessions/{s["id"]}/save').status_code == 409
    assert (folder / 'recover.pdf').exists()
    assert c.get('/api/stats').json()['scan_count'] == 0
    monkeypatch.setattr(app.state.store, 'commit_save', original)
    restarted = Store(app.state.store.root)
    try:
        recovered = restarted.get(s['id'])
        assert recovered['saved'] and recovered['approved']
        assert recovered['pages'][0]['status'] == 'ready'
    finally:
        restarted.close()
    assert c.post(f'/api/sessions/{s["id"]}/save').status_code == 200
    app.state.store.recover_saves()
    assert c.get('/api/stats').json()['scan_count'] == 1


def test_failed_page_retry_keeps_successful_pages(client):
    c, app, folder = client
    calls = []
    def sometimes_fail(image):
        calls.append(1)
        if len(calls) == 2:
            raise RuntimeError('DOCUMENT_CONTENT_SENTINEL')
        return fake_ocr(image)
    app.state.worker.ocr = sometimes_fail
    s = upload(c, upload(c, new(c, folder, 'multiple', 2)))
    assert c.post(f'/api/sessions/{s["id"]}/process').status_code == 200
    app.state.worker.queue.join()
    s = c.get(f'/api/sessions/{s["id"]}').json()
    assert [p['status'] for p in s['pages']] == ['ready', 'failed']
    assert 'DOCUMENT_CONTENT_SENTINEL' not in json.dumps(s)
    s = process(c, app, s)
    assert len(calls) == 3
    assert len(s['pages']) == 2


def test_saved_restart_is_printable_without_persistent_ocr(client):
    c, app, folder = client
    app.state.worker.ocr = fake_ocr
    s = approve(c, process(c, app, upload(c, new(c, folder))), folder)
    assert c.post(f'/api/sessions/{s["id"]}/save').status_code == 200
    store2 = Store(app.state.store.root)
    try:
        recovered = store2.get(s['id'])
        assert recovered['saved']
        assert recovered['approved']
        assert recovered['pages'][0]['status'] == 'ready'
        assert not recovered['pages'][0]['original_retained']
        assert not store2.ocr
    finally:
        store2.close()


def test_remote_pairing_and_laptop_folder_restriction(tmp_path):
    app = create_app(tmp_path / 'working', pairing_code='ABCDEF12', public_url='https://192.168.1.25:8443')
    with TestClient(app, base_url='https://192.168.1.25:8443', client=('192.168.1.40', 4000)) as remote:
        assert remote.get('/api/bootstrap').status_code == 401
        assert remote.post('/api/login', json={}).status_code == 403
        assert remote.post('/api/login', json={'code':'ABCDEF12'}).status_code == 200
        cookie = remote.cookies.get('iskan')
        assert cookie
        data = remote.get('/api/bootstrap').json()
        assert not data['laptop'] and data['pairing_code'] is None
        assert remote.post('/api/storage', json={'path':str(tmp_path)}).status_code == 403
        assert remote.get('/api/qr').status_code == 200


def test_crop_and_rotation_change_only_one_page(client):
    c, app, folder = client
    app.state.worker.ocr = fake_ocr
    s = process(c, app, upload(c, upload(c, new(c, folder, 'multiple', 2))))
    p = s['pages'][0]
    revision = s['pages'][1]['revision']
    s = c.put(f'/api/sessions/{s["id"]}/pages/{p["id"]}', json={'revision':p['revision'],
              'crop':[[.05,.05],[.95,.05],[.95,.95],[.05,.95]],'rotation':90}).json()
    assert s['pages'][0]['status'] == 'captured'
    assert s['pages'][1]['status'] == 'ready' and s['pages'][1]['revision'] == revision
    s = process(c, app, s)
    image = decode(c.get(f'/api/sessions/{s["id"]}/pages/{p["id"]}/processed').content)
    assert image.shape[1] > image.shape[0]


def test_title_suggestions_follow_new_first_page(client):
    c, app, folder = client
    index = []
    def different_headings(image):
        index.append(1)
        r = fake_ocr(image)
        r[0]['text'] = 'FIRST TITLE' if len(index)==1 else 'SECOND TITLE'
        return r
    app.state.worker.ocr = different_headings
    s = process(c, app, upload(c, upload(c, new(c, folder, 'multiple', 2))))
    assert s['suggestions']['title'] == 'FIRST TITLE'
    s = c.put(f'/api/sessions/{s["id"]}/order', json={'revision':s['revision'],
              'pages':[p['id'] for p in reversed(s['pages'])]}).json()
    assert s['suggestions']['title'] == 'SECOND TITLE'
    assert s['suggestions']['date_candidates'] == []
