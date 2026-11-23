"""Synthetic visual/CPU benchmark, never a substitute for physical-paper QA."""
import io
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import cv2
import fitz
import numpy as np
from PIL import Image, ImageDraw, ImageFont

from iskan.exporting import pdf_bytes, raster_page
from iskan.imaging import OCR, clean, decode, encode, quality, warp
from iskan.models import Settings
from iskan.store import Store

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / 'tmp' / 'qa'


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    im = Image.new('RGB', (1654, 2339), '#fafafa')
    d = ImageDraw.Draw(im)
    font = lambda size: ImageFont.truetype('C:/Windows/Fonts/arial.ttf', size)
    d.text((140, 150), 'MEETING MINUTES', font=font(76), fill='#172c23')
    d.text((140, 310), 'Document date: 2026-10-14', font=font(40), fill='#202020')
    lines = ['A local scanner quality check', 'Preserve original marks and page order.',
             'Faint text must remain visible.', 'Save only after reviewing the final document.']
    for i, text in enumerate(lines):
        d.text((140, 440+i*100), text, font=font(39), fill='#777777' if i==2 else '#242424')
    # Real raster test details: table, colored stamp, handwritten-style stroke,
    # highlight and photograph-like color gradient, all must survive cleanup.
    d.rectangle((130, 960, 1490, 1290), outline='#444444', width=3)
    for y in [1070, 1180]: d.line((130,y,1490,y),fill='#777777',width=2)
    d.line((920,960,920,1290),fill='#777777',width=2)
    d.text((165, 995), 'Capture review', font=font(34), fill='#222222')
    d.text((965, 995), 'Verified', font=font(34), fill='#222222')
    d.text((165, 1105), 'Export preview', font=font(34), fill='#222222')
    d.text((965, 1105), 'Check visually', font=font(34), fill='#222222')
    d.ellipse((1170, 1600, 1430, 1860), outline='#af3434', width=10)
    d.text((1210, 1700), 'STAMP', font=font(36), fill='#af3434')
    d.line([(150,1760),(190,1710),(250,1790),(310,1710),(370,1740),(470,1720)],fill='#254a84',width=5)
    d.rectangle((140,1440,730,1510), fill='#fff3a0')
    d.text((150, 1445), 'Highlighted detail', font=font(38), fill='#303030')
    if Path('C:/Windows/Fonts/msjh.ttc').exists():
        d.text((140,2010), '文件掃描測試 — 保留印章與簽名',font=ImageFont.truetype('C:/Windows/Fonts/msjh.ttc',42),fill='#333333')
    im.save(OUTPUT / 'clean-source.png')
    src=cv2.cvtColor(np.array(im),cv2.COLOR_RGB2BGR)
    h,w=src.shape[:2]
    points=np.float32([[340,140],[2040,300],[1900,2670],[160,2480]])
    matrix=cv2.getPerspectiveTransform(np.float32([[0,0],[w-1,0],[w-1,h-1],[0,h-1]]),points)
    background=np.full((2850,2200,3),70,np.uint8)
    warped=cv2.warpPerspective(src,matrix,(2200,2850),borderValue=(70,70,70))
    shadow=np.linspace(.72,1,2200)[None,:,None]
    capture=np.clip(warped*shadow,0,255).astype(np.uint8)
    (OUTPUT / 'angled-capture.png').write_bytes(encode(capture))
    config=Settings(watermark='QUALITY CHECK',numbers=True,strength=.35,destination=str(OUTPUT))
    start=time.perf_counter();q=quality(capture)
    assert q['corners'] is not None
    processed=clean(warp(capture,q['corners']),config,lambda _:None)
    cleaned=time.perf_counter();records=OCR()(processed);recognized=time.perf_counter()
    assert any('MEETING' in r['text'] for r in records)
    store=Store(OUTPUT / 'working');s=store.new(config)
    p=dict(id='qualitycheck',revision=1,status='ready')
    s['pages']=[p];s['scanned_date']='2026-10-14';s['approved']={'title':'Quality check'}
    folder=store.root/s['id'];(folder/'qualitycheck-1-processed.png').write_bytes(encode(processed))
    store.ocr[('qualitycheck',1)]=records
    pdf=pdf_bytes(store,s);(OUTPUT/'quality-check.pdf').write_bytes(pdf)
    (OUTPUT/'export-preview.png').write_bytes(raster_page(pdf,0,dpi=120))
    # Render visible OCR boxes for an alignment inspection, separate from the export.
    with fitz.open(stream=pdf,filetype='pdf') as doc:
        page=doc[0]
        assert 'MEETING' in page.get_text()
        for word in page.get_text('words'):
            page.draw_rect(fitz.Rect(word[:4]), color=(1,0,0),width=.4)
        (OUTPUT/'text-alignment.png').write_bytes(page.get_pixmap(matrix=fitz.Matrix(1.5,1.5)).tobytes('png'))
    store.close()
    result={'fixture':'synthetic angled page with shadow, faint text, color stamp, signature, table and CJK',
            'cleanup_seconds':round(cleaned-start,3),'ocr_seconds':round(recognized-cleaned,3),
            'total_seconds':round(recognized-start,3),'recognized_lines':len(records)}
    (OUTPUT/'benchmark.json').write_text(json.dumps(result,indent=2))
    print('VISUAL_QA_OUTPUT_READY')
    print('CLEANUP_SECONDS='+str(result['cleanup_seconds']))
    print('OCR_SECONDS='+str(result['ocr_seconds']))


if __name__=='__main__':main()
