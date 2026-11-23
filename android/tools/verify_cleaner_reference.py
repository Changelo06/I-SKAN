"""Portable OpenCV 4.12 reference experiment, not Android device validation."""
from pathlib import Path
import json, time
import cv2 as cv
import numpy as np
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'output'/'cleaner-research'
OUT.mkdir(parents=True,exist_ok=True)
rng=np.random.default_rng(47)
h,w=1200,900
page=np.full((h,w,3),244,np.uint8)
mask=np.zeros((h,w),np.uint8)
cv.putText(page,'I-SKAN DOCUMENT TEST',(60,100),cv.FONT_HERSHEY_SIMPLEX,1.05,(28,28,28),2,cv.LINE_AA)
for y in range(180,660,60):
 text='Fine print, numbers 0123456789 and lines'
 cv.putText(page,text,(60,y),cv.FONT_HERSHEY_SIMPLEX,.68,(45,45,45),1,cv.LINE_AA)
 cv.putText(mask,text,(60,y),cv.FONT_HERSHEY_SIMPLEX,.68,255,1,cv.LINE_8)
cv.line(page,(80,720),(790,720),(25,25,25),1)
cv.line(mask,(80,720),(790,720),255,1)
cv.putText(page,'Faint pencil: compare before saving',(60,820),cv.FONT_HERSHEY_SIMPLEX,.65,(200,200,200),1,cv.LINE_AA)
cv.circle(page,(680,990),90,(150,85,55),3,cv.LINE_AA)
cv.putText(page,'STAMP',(625,1000),cv.FONT_HERSHEY_SIMPLEX,.65,(150,85,55),2,cv.LINE_AA)
illum=.64+.34*np.linspace(0,1,h)[:,None]+.025*np.sin(np.linspace(0,7,w))[None,:]
source=np.clip(page.astype(float)*illum[:,:,None]+rng.normal(0,.8,(h,w,1)),0,255).astype(np.uint8)
source=cv.GaussianBlur(source,(0,0),.5)
def tone(image,sharp=25,contrast=15):
 ycc=cv.cvtColor(image,cv.COLOR_BGR2YCrCb);y=ycc[:,:,0]
 table=np.clip(np.floor((np.arange(256)-127.5)*2**(contrast/50)+127.5+.5),0,255).astype(np.uint8)
 y=cv.LUT(y,table)
 blur=cv.GaussianBlur(y,(0,0),max(.6,min(1.4,max(y.shape)*.85/1800)))
 detail=y.astype(float)-blur.astype(float)
 delta=np.where(np.abs(detail)>2,np.clip(np.floor(detail*sharp/100+.5),-24,24),0)
 ycc[:,:,0]=np.clip(y.astype(float)+delta,0,255).astype(np.uint8)
 return cv.cvtColor(ycc,cv.COLOR_YCrCb2BGR)
def background(gray):
 scale=min(1,700/max(gray.shape));small=cv.resize(gray,(round(gray.shape[1]*scale),round(gray.shape[0]*scale)),interpolation=cv.INTER_AREA)
 bg=cv.morphologyEx(small,cv.MORPH_CLOSE,cv.getStructuringElement(cv.MORPH_ELLIPSE,(31,31)))
 bg=cv.GaussianBlur(bg,(0,0),7)
 return cv.resize(bg,(gray.shape[1],gray.shape[0]),interpolation=cv.INTER_LINEAR)
def clean_color(image):
 gray=cv.cvtColor(image,cv.COLOR_BGR2GRAY);bg=background(gray).astype(float)
 lo=image.min(2);hi=image.max(2);v=image.astype(np.int32)
 lum=(29*v[:,:,0]+150*v[:,:,1]+77*v[:,:,2])>>8
 eligible=(hi.astype(int)-lo<=24)&(bg>=150)&(lum>=bg-5)
 out=image.copy();out[eligible]=255
 return tone(out,15,0)
def binary(image):
 gray=cv.cvtColor(tone(image),cv.COLOR_BGR2GRAY)
 bg=background(gray);flat=gray.astype(float)*255/np.maximum(bg,80)
 gray=np.clip(np.floor(gray*.35+flat*.65+.5),0,255).astype(np.uint8)
 block=max(21,min(101,round(max(gray.shape)*41/1800)));block+=block%2==0
 return cv.adaptiveThreshold(gray,255,cv.ADAPTIVE_THRESH_GAUSSIAN_C,cv.THRESH_BINARY,int(block),16-60*.14)
start=time.perf_counter();bw=binary(source);elapsed=time.perf_counter()-start
assert set(np.unique(bw))<={0,255}
retained=float((bw[mask>0]==0).mean())
assert retained>.95,retained
color=clean_color(source)
cv.imwrite(str(OUT/'source.png'),source);cv.imwrite(str(OUT/'document-bw.png'),bw);cv.imwrite(str(OUT/'clean-color.png'),color)
thumbs=[]
for label,image in [('Synthetic input',source),('Clean color',color),('Document B&W',cv.cvtColor(bw,cv.COLOR_GRAY2BGR))]:
 thumb=cv.resize(image,(450,600),interpolation=cv.INTER_AREA);tile=np.full((650,470,3),248,np.uint8);tile[40:640,10:460]=thumb;cv.putText(tile,label,(12,27),cv.FONT_HERSHEY_SIMPLEX,.7,(35,35,35),1,cv.LINE_AA);thumbs.append(tile)
cv.imwrite(str(OUT/'comparison.png'),np.hstack(thumbs))
metrics={'runtime':'Python OpenCV '+cv.__version__,'validation':'synthetic portable reference; Android integration tests not executed','fixture_pixels':w*h,'binary_unique_values':np.unique(bw).tolist(),'dark_text_and_rule_retention':retained,'reference_elapsed_seconds':round(elapsed,4)}
(OUT/'metrics.json').write_text(json.dumps(metrics,indent=2)+'\n',encoding='utf-8')
print(json.dumps(metrics,indent=2))
