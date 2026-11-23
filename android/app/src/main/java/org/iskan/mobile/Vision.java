package org.iskan.mobile;

import android.graphics.Bitmap;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;

final class Vision {
    static final class Result {
        float[] corners;
        byte[] fingerprint;
        double brightness, sharpness, coverage, clippedFraction, contrast;
        String message;
        boolean ready;
    }
    static void init(){if(!OpenCVLoader.initLocal())throw new IllegalStateException("VISION_UNAVAILABLE");}
    static Mat bgr(Bitmap bitmap){Mat rgba=new Mat(),out=new Mat();Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,out,Imgproc.COLOR_RGBA2BGR);rgba.release();return out;}
    static Bitmap bitmap(Mat bgr){Mat rgba=new Mat();Imgproc.cvtColor(bgr,rgba,Imgproc.COLOR_BGR2RGBA);Bitmap b=Bitmap.createBitmap(rgba.cols(),rgba.rows(),Bitmap.Config.ARGB_8888);Utils.matToBitmap(rgba,b);rgba.release();return b;}
    static Result analyze(Bitmap bitmap){Mat m=bgr(bitmap),g=new Mat();try{Imgproc.cvtColor(m,g,Imgproc.COLOR_BGR2GRAY);return analyzeGray(g);}finally{m.release();g.release();}}
    static float[] detectCorners(Bitmap bitmap,boolean id){
        if(id){float[] card=cardCorners(bitmap);if(card!=null)return card;}
        return analyze(bitmap).corners;
    }
    // Rounded ID edges often fail the strict four-vertex page contour test.
    static float[] cardCorners(Bitmap bitmap){
        Mat image=bgr(bitmap),gray=new Mat(),edge=new Mat(),hierarchy=new Mat();ArrayList<MatOfPoint> contours=new ArrayList<>();
        try{
            Imgproc.cvtColor(image,gray,Imgproc.COLOR_BGR2GRAY);double scale=Math.min(1,900.0/Math.max(gray.cols(),gray.rows()));Imgproc.resize(gray,gray,new Size(Math.round(gray.cols()*scale),Math.round(gray.rows()*scale)));Imgproc.GaussianBlur(gray,edge,new Size(5,5),0);Imgproc.Canny(edge,edge,35,110);
            Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(5,5));try{Imgproc.morphologyEx(edge,edge,Imgproc.MORPH_CLOSE,kernel);}finally{kernel.release();}
            Imgproc.findContours(edge,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);contours.sort((a,b)->Double.compare(Imgproc.contourArea(b),Imgproc.contourArea(a)));
            for(int i=0;i<Math.min(20,contours.size());i++){
                MatOfPoint c=contours.get(i);MatOfPoint2f curve=new MatOfPoint2f(c.toArray());RotatedRect rect=Imgproc.minAreaRect(curve);curve.release();double area=rect.size.area(),ratio=Math.max(rect.size.width,rect.size.height)/Math.max(1,Math.min(rect.size.width,rect.size.height));
                if(area<gray.total()*.12||area>gray.total()*.98||ratio<1.25||ratio>1.95||Imgproc.contourArea(c)/area<.78)continue;
                Point[] corners=new Point[4];rect.points(corners);corners=order(corners);float[] normalized=new float[8];for(int n=0;n<4;n++){normalized[n*2]=(float)Math.max(0,Math.min(1,corners[n].x/gray.cols()));normalized[n*2+1]=(float)Math.max(0,Math.min(1,corners[n].y/gray.rows()));}if(validCrop(normalized))return normalized;
            }
            return null;
        }finally{for(MatOfPoint c:contours)c.release();image.release();gray.release();edge.release();hierarchy.release();}
    }
    static Result analyzeGray(Mat source){
        Result r=new Result();Mat gray=new Mat(),blur=new Mat(),edge=new Mat(),lap=new Mat(),thumb=new Mat();
        double scale=Math.min(1,700.0/Math.max(source.cols(),source.rows()));
        Imgproc.resize(source,gray,new Size(Math.round(source.cols()*scale),Math.round(source.rows()*scale)));
        try{
            r.brightness=Core.mean(gray).val[0];
            Mat standard=new Mat();double z=Math.min(1,600.0/gray.cols());Imgproc.resize(gray,standard,new Size(Math.round(gray.cols()*z),Math.round(gray.rows()*z)));
            Imgproc.Laplacian(standard,lap,CvType.CV_64F);standard.release();
            MatOfDouble mean=new MatOfDouble(),std=new MatOfDouble();Core.meanStdDev(lap,mean,std);r.sharpness=Math.pow(std.toArray()[0],2);Core.meanStdDev(gray,mean,std);r.contrast=std.toArray()[0];mean.release();std.release();Mat highlights=new Mat();try{Core.inRange(gray,new Scalar(250),new Scalar(255),highlights);r.clippedFraction=Core.countNonZero(highlights)/(double)gray.total();}finally{highlights.release();}
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),0);Imgproc.Canny(blur,edge,40,130);
            Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(3,3));Imgproc.dilate(edge,edge,kernel);kernel.release();
            ArrayList<MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();Imgproc.findContours(edge,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);hierarchy.release();
            contours.sort((a,b)->Double.compare(Imgproc.contourArea(b),Imgproc.contourArea(a)));
            double area=gray.total();
            for(int i=0;i<Math.min(15,contours.size());i++){
                MatOfPoint c=contours.get(i);MatOfPoint2f curve=new MatOfPoint2f(c.toArray()),poly=new MatOfPoint2f();
                Imgproc.approxPolyDP(curve,poly,.02*Imgproc.arcLength(curve,true),true);curve.release();
                Point[] points=poly.toArray();poly.release();if(points.length!=4)continue;
                MatOfPoint polygon=new MatOfPoint(points);double coverage=Imgproc.contourArea(polygon)/area;
                if(!Imgproc.isContourConvex(polygon)||coverage<.18||coverage>.99){polygon.release();continue;}
                Mat mask=Mat.zeros(gray.size(),CvType.CV_8UC1);Imgproc.fillConvexPoly(mask,polygon,new Scalar(255));
                Mat core=new Mat(),dilated=new Mat(),ring=new Mat();
                Mat k1=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(15,15)),k2=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(21,21));
                Imgproc.erode(mask,core,k1);Imgproc.dilate(mask,dilated,k2);Core.subtract(dilated,mask,ring);
                double delta=Math.abs(Core.mean(gray,core).val[0]-Core.mean(gray,ring).val[0]);
                mask.release();core.release();dilated.release();ring.release();k1.release();k2.release();polygon.release();
                if(delta<18)continue;
                Point[] ordered=order(points);r.corners=new float[8];for(int j=0;j<4;j++){r.corners[j*2]=(float)(ordered[j].x/gray.cols());r.corners[j*2+1]=(float)(ordered[j].y/gray.rows());}
                r.coverage=coverage;break;
            }
            for(MatOfPoint c:contours)c.release();
            Imgproc.resize(gray,thumb,new Size(64,48));r.fingerprint=new byte[64*48];thumb.get(0,0,r.fingerprint);
            boolean clipped=false;if(r.corners!=null)for(float x:r.corners)if(x<.012||x>.988)clipped=true;
            if(r.brightness<65)r.message="Too dark";
            else if(LocalFeedback.overexposed(r.brightness,r.clippedFraction,r.contrast))r.message="Too bright";
            else if(r.sharpness<28)r.message="Hold still";
            else if(r.corners==null)r.message="Keep all four corners visible";
            else if(clipped)r.message="Page may be cut off";
            else if(r.coverage<.35)r.message="Move closer";
            else {r.message="Ready";r.ready=true;}
            return r;
        }finally{gray.release();blur.release();edge.release();lap.release();thumb.release();}
    }
    static double difference(byte[] a,byte[] b){if(a==null||b==null||a.length!=b.length)return 255;double sum=0;for(int i=0;i<a.length;i++)sum+=Math.abs((a[i]&255)-(b[i]&255));return sum/a.length;}
    static Point[] order(Point[] input){
        Point[] p=input.clone();double cx=0,cy=0;for(Point x:p){cx+=x.x;cy+=x.y;}final double x=cx/p.length,y=cy/p.length;
        Arrays.sort(p,Comparator.comparingDouble(v->Math.atan2(v.y-y,v.x-x)));
        int first=0;for(int i=1;i<p.length;i++)if(p[i].x+p[i].y<p[first].x+p[first].y)first=i;
        Point[] out=new Point[4];for(int i=0;i<4;i++)out[i]=p[(first+i)%4];return out;
    }
    static boolean validCrop(float[] corners){
        if(corners==null)return true;if(corners.length!=8)return false;
        Point[] pp=new Point[4];for(int i=0;i<4;i++){if(!Float.isFinite(corners[i*2])||!Float.isFinite(corners[i*2+1])||corners[i*2]<0||corners[i*2]>1||corners[i*2+1]<0||corners[i*2+1]>1)return false;pp[i]=new Point(corners[i*2],corners[i*2+1]);}
        MatOfPoint2f f=new MatOfPoint2f(pp);
        double area=Math.abs(Imgproc.contourArea(f));boolean convex=true;
        double sign=0;for(int i=0;i<4;i++){Point a=pp[i],b=pp[(i+1)%4],c=pp[(i+2)%4];double cross=(b.x-a.x)*(c.y-b.y)-(b.y-a.y)*(c.x-b.x);if(Math.abs(cross)<.0001){convex=false;break;}if(sign==0)sign=cross;else if(sign*cross<0)convex=false;}
        f.release();return convex&&area>.015;
    }
    static Mat crop(Mat image,Point[] p,int max){
        double w=Math.max(distance(p[0],p[1]),distance(p[2],p[3])),h=Math.max(distance(p[0],p[3]),distance(p[1],p[2]));
        if(Math.min(w,h)<20)throw new IllegalArgumentException("CROP_TOO_SMALL");
        double s=Math.min(1,max/Math.max(w,h));int width=Math.max(1,(int)(w*s)),height=Math.max(1,(int)(h*s));
        MatOfPoint2f src=new MatOfPoint2f(p),dst=new MatOfPoint2f(new Point(0,0),new Point(width-1,0),new Point(width-1,height-1),new Point(0,height-1));
        Mat matrix=Imgproc.getPerspectiveTransform(src,dst),out=new Mat();
        Imgproc.warpPerspective(image,out,matrix,new Size(width,height),Imgproc.INTER_CUBIC,Core.BORDER_REPLICATE);
        matrix.release();src.release();dst.release();return out;
    }
    static double distance(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
    // Whiten only low-chroma paper near its local background. Dark/faint ink and
    // saturated stamps/photos are kept; this is not a global contrast boost.
    static Mat cleanPaper(Mat image,ScanDraft.Options options){
        if(options.cleanerWhitening==0)return image.clone();
        Mat gray=new Mat(),small=new Mat(),bg=new Mat(),full=new Mat(),out=image.clone();
        try{
            Imgproc.cvtColor(image,gray,Imgproc.COLOR_BGR2GRAY);
            double scale=Math.min(1,700.0/Math.max(image.cols(),image.rows()));
            Imgproc.resize(gray,small,new Size(Math.max(1,Math.round(image.cols()*scale)),Math.max(1,Math.round(image.rows()*scale))),0,0,Imgproc.INTER_AREA);
            Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(31,31));
            try{Imgproc.morphologyEx(small,bg,Imgproc.MORPH_CLOSE,kernel);}finally{kernel.release();}
            Imgproc.GaussianBlur(bg,bg,new Size(0,0),7);
            Imgproc.resize(bg,full,image.size(),0,0,Imgproc.INTER_LINEAR);
            byte[] pixels=new byte[image.cols()*3],paper=new byte[image.cols()];
            for(int row=0;row<image.rows();row++){image.get(row,0,pixels);full.get(row,0,paper);for(int i=0;i<paper.length;i++){int b=pixels[i*3]&255,g=pixels[i*3+1]&255,r=pixels[i*3+2]&255;if(PaperCleanup.isPaper(b,g,r,paper[i]&255,options.cleanerShadows,options.cleanerInk,options.cleanerColors)){pixels[i*3]=(byte)PaperCleanup.whiten(b,options.cleanerWhitening);pixels[i*3+1]=(byte)PaperCleanup.whiten(g,options.cleanerWhitening);pixels[i*3+2]=(byte)PaperCleanup.whiten(r,options.cleanerWhitening);}}out.put(row,0,pixels);}return out;
        }finally{gray.release();small.release();bg.release();full.release();}
    }
    static Bitmap process(Bitmap original,ScanDraft.Page page,ScanDraft.Options options){return process(original,page,options,()->{});}
    static Bitmap process(Bitmap original,ScanDraft.Page page,ScanDraft.Options options,Runnable geometryDone){
        Mat image=bgr(original);
        try{
            if(page.crop!=null){Point[] pp=new Point[4];for(int i=0;i<4;i++)pp[i]=new Point(page.crop[i*2]*image.cols(),page.crop[i*2+1]*image.rows());Mat warped=crop(image,pp,3600);image.release();image=warped;}
            if(page.flipH||page.flipV)Core.flip(image,image,page.flipH&&page.flipV?-1:page.flipH?1:0);
            if(page.rotation!=0){Mat rotated=new Mat();Core.rotate(image,rotated,page.rotation==90?Core.ROTATE_90_CLOCKWISE:page.rotation==180?Core.ROTATE_180:Core.ROTATE_90_COUNTERCLOCKWISE);image.release();image=rotated;}
            geometryDone.run();
            if(options.appearance==3||options.appearance==1){Mat clean=cleanPaper(image,options);image.release();image=clean;}
            if(options.strength>0&&options.appearance!=0&&options.appearance!=3){
                Mat lab=new Mat();Imgproc.cvtColor(image,lab,Imgproc.COLOR_BGR2Lab);ArrayList<Mat> channels=new ArrayList<>();Core.split(lab,channels);
                Mat l=new Mat(),background=new Mat();channels.get(0).convertTo(l,CvType.CV_32F);
                Imgproc.GaussianBlur(l,background,new Size(0,0),Math.max(15,Math.max(image.cols(),image.rows())/35.0));
                float[] lv=new float[(int)l.total()],bg=new float[lv.length];l.get(0,0,lv);background.get(0,0,bg);
                for(int i=0;i<lv.length;i++){double correction=Math.max(.9,Math.min(1.8,225/Math.max(bg[i],60)));lv[i]=(float)Math.min(255,lv[i]*(1+(correction-1)*options.strength));}
                l.put(0,0,lv);l.convertTo(channels.get(0),CvType.CV_8U);Core.merge(channels,lab);Imgproc.cvtColor(lab,image,Imgproc.COLOR_Lab2BGR);
                for(Mat m:channels)m.release();l.release();background.release();lab.release();
            }
            if(options.appearance!=0)DocumentEnhance.toneAndSharpen(image,options);
            if(options.appearance==1||options.appearance==2){Mat g=new Mat();Imgproc.cvtColor(image,g,Imgproc.COLOR_BGR2GRAY);if(options.appearance==2)DocumentEnhance.binary(g,options);Imgproc.cvtColor(g,image,Imgproc.COLOR_GRAY2BGR);g.release();}
            return bitmap(image);
        }finally{image.release();}
    }
}

