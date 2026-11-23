package org.iskan.mobile;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
/** Luminance controls keep chroma separate; binary output is an explicit mode. */
final class DocumentEnhance {
 static void toneAndSharpen(Mat bgr,ScanDraft.Options o){
  if(o.cleanerBrightness==0&&o.cleanerExposure==0&&o.cleanerContrast==0&&o.cleanerSharpness==0)return;
  Mat ycc=new Mat(),y=new Mat(),lut=new Mat(1,256,CvType.CV_8UC1),blur=new Mat();
  try{Imgproc.cvtColor(bgr,ycc,Imgproc.COLOR_BGR2YCrCb);Core.extractChannel(ycc,y,0);byte[] table=new byte[256];for(int i=0;i<256;i++)table[i]=(byte)DocumentTone.tone(i,o.cleanerBrightness,o.cleanerExposure,o.cleanerContrast);lut.put(0,0,table);Core.LUT(y,lut,y);
   if(o.cleanerSharpness>0){double radius=Math.max(.6,Math.min(1.4,Math.max(y.cols(),y.rows())*.85/1800));Imgproc.GaussianBlur(y,blur,new Size(0,0),radius);byte[] row=new byte[y.cols()],soft=new byte[y.cols()];for(int r=0;r<y.rows();r++){y.get(r,0,row);blur.get(r,0,soft);for(int x=0;x<row.length;x++)row[x]=(byte)DocumentTone.sharpen(row[x]&255,soft[x]&255,o.cleanerSharpness);y.put(r,0,row);}}
   Core.insertChannel(y,ycc,0);Imgproc.cvtColor(ycc,bgr,Imgproc.COLOR_YCrCb2BGR);
  }finally{ycc.release();y.release();lut.release();blur.release();}
 }
 static void binary(Mat gray,ScanDraft.Options o){
  double amount=PaperCleanup.clamp(o.cleanerShadows)/100.0*PaperCleanup.clamp(o.cleanerWhitening)/100.0;
  if(amount>0){Mat small=new Mat(),background=new Mat(),full=new Mat(),kernel=new Mat();
   try{double scale=Math.min(1,700.0/Math.max(gray.cols(),gray.rows()));Imgproc.resize(gray,small,new Size(Math.max(1,Math.round(gray.cols()*scale)),Math.max(1,Math.round(gray.rows()*scale))),0,0,Imgproc.INTER_AREA);kernel.release();kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(31,31));Imgproc.morphologyEx(small,background,Imgproc.MORPH_CLOSE,kernel);Imgproc.GaussianBlur(background,background,new Size(0,0),7);Imgproc.resize(background,full,gray.size(),0,0,Imgproc.INTER_LINEAR);byte[] pixels=new byte[gray.cols()],paper=new byte[gray.cols()];for(int r=0;r<gray.rows();r++){gray.get(r,0,pixels);full.get(r,0,paper);for(int x=0;x<pixels.length;x++){int value=pixels[x]&255;double flat=value*255.0/Math.max(80,paper[x]&255);pixels[x]=(byte)Math.min(255,Math.round(value*(1-amount)+flat*amount));}gray.put(r,0,pixels);}
   }finally{small.release();background.release();full.release();kernel.release();}}
  int block=DocumentTone.blockSize(gray.cols(),gray.rows());if(block==0)Imgproc.threshold(gray,gray,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);else Imgproc.adaptiveThreshold(gray,gray,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,block,DocumentTone.thresholdOffset(o.cleanerBinaryInk));
 }
}
