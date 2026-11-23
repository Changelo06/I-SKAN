package org.iskan.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import ai.onnxruntime.*;
import org.json.JSONArray;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class OnnxOcr implements AutoCloseable {
    static final class Line {
        final float[] box;
        final String text;
        final double confidence;
        Line(float[] b,String t,double c){box=b;text=t;confidence=c;}
    }
    private final OrtEnvironment env;
    private final OrtSession detector,recognizer,classifier;
    private final ArrayList<String> alphabet=new ArrayList<>();
    OnnxOcr(Context context) throws Exception {
        env=OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR,"ISKAN");
        try(OrtSession.SessionOptions o=new OrtSession.SessionOptions()){
            o.setIntraOpNumThreads(2);o.setInterOpNumThreads(1);o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            detector=env.createSession(asset(context,"ocr/ch_PP-OCRv4_det_infer.onnx"),o);
            recognizer=env.createSession(asset(context,"ocr/ch_PP-OCRv4_rec_infer.onnx"),o);
            classifier=env.createSession(asset(context,"ocr/ch_ppocr_mobile_v2.0_cls_infer.onnx"),o);
        }
        JSONArray a=new JSONArray(new String(asset(context,"ocr/characters.json"),StandardCharsets.UTF_8));
        for(int i=0;i<a.length();i++)alphabet.add(a.getString(i));
    }
    static byte[] asset(Context c,String name) throws IOException {try(InputStream in=c.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    private OnnxTensor normalized(Mat image,int height,int width,boolean pad) throws OrtException {
        int resized=pad?Math.min(width,(int)Math.ceil(height*image.cols()/(double)image.rows())):width;
        Mat scaled=new Mat();Imgproc.resize(image,scaled,new Size(resized,height));byte[] bytes=new byte[height*resized*3];scaled.get(0,0,bytes);scaled.release();
        float[] data=new float[3*height*width];
        for(int y=0;y<height;y++)for(int x=0;x<resized;x++)for(int channel=0;channel<3;channel++)data[channel*height*width+y*width+x]=((bytes[(y*resized+x)*3+channel]&255)/255f-.5f)/.5f;
        return OnnxTensor.createTensor(env,FloatBuffer.wrap(data),new long[]{1,3,height,width});
    }
    private OrtSession.Result run(OrtSession s,OnnxTensor input) throws OrtException {return s.run(Collections.singletonMap(s.getInputNames().iterator().next(),input));}
    List<Line> recognize(Bitmap bitmap) throws Exception {
        Mat image=Vision.bgr(bitmap);
        try{
            int h=image.rows(),w=image.cols();double scale=Math.min(1.6,1280.0/Math.max(w,h));
            int dh=Math.max(32,Math.round((float)(h*scale/32))*32),dw=Math.max(32,Math.round((float)(w*scale/32))*32);
            float[][] values;
            try(OnnxTensor in=normalized(image,dh,dw,false);OrtSession.Result out=run(detector,in)){values=((float[][][][])out.get(0).getValue())[0][0];}
            int mh=values.length,mw=values[0].length;Mat prob=new Mat(mh,mw,CvType.CV_32F),mask=new Mat(mh,mw,CvType.CV_8U);
            float[] flat=new float[mh*mw];byte[] bit=new byte[mh*mw];
            for(int y=0;y<mh;y++)for(int x=0;x<mw;x++){flat[y*mw+x]=values[y][x];bit[y*mw+x]=(byte)(values[y][x]>.3?255:0);}
            prob.put(0,0,flat);mask.put(0,0,bit);
            Mat k=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(2,2));Imgproc.dilate(mask,mask,k);k.release();
            ArrayList<MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();Imgproc.findContours(mask,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);hierarchy.release();mask.release();
            ArrayList<Point[]> boxes=new ArrayList<>();
            for(int i=0;i<Math.min(1000,contours.size());i++){
                MatOfPoint c=contours.get(i);if(Imgproc.contourArea(c)<8)continue;
                MatOfPoint2f curve=new MatOfPoint2f(c.toArray());RotatedRect rect=Imgproc.minAreaRect(curve);curve.release();
                if(Math.min(rect.size.width,rect.size.height)<3)continue;
                Mat region=Mat.zeros(prob.size(),CvType.CV_8U);Imgproc.drawContours(region,Collections.singletonList(c),0,new Scalar(255),-1);
                double score=Core.mean(prob,region).val[0];region.release();if(score<.5)continue;
                double expand=rect.size.width*rect.size.height*1.6/(2*(rect.size.width+rect.size.height));
                rect.size=new Size(rect.size.width+2*expand,rect.size.height+2*expand);
                Point[] points=new Point[4];rect.points(points);points=Vision.order(points);
                for(Point p:points){p.x=Math.max(0,Math.min(w-1,p.x*w/mw));p.y=Math.max(0,Math.min(h-1,p.y*h/mh));}
                if(Vision.distance(points[0],points[1])>5&&Vision.distance(points[0],points[3])>5)boxes.add(points);
            }
            for(MatOfPoint c:contours)c.release();prob.release();
            boxes.sort(Comparator.comparingDouble((Point[] a)->a[0].y).thenComparingDouble(a->a[0].x));
            ArrayList<Line> lines=new ArrayList<>();
            for(Point[] box:boxes){
                Mat line=Vision.crop(image,box,2200);
                try{
                    if(line.rows()>line.cols()*1.5){Mat rotated=new Mat();Core.rotate(line,rotated,Core.ROTATE_90_COUNTERCLOCKWISE);line.release();line=rotated;}
                    try(OnnxTensor in=normalized(line,48,192,true);OrtSession.Result out=run(classifier,in)){
                        float[] scores=((float[][])out.get(0).getValue())[0];
                        if(scores[1]>.9&&scores[1]>scores[0])Core.rotate(line,line,Core.ROTATE_180);
                    }
                    int width=Math.max(320,Math.min(2200,(int)Math.ceil(48.0*line.cols()/line.rows())));
                    float[][] prediction;
                    try(OnnxTensor in=normalized(line,48,width,true);OrtSession.Result out=run(recognizer,in)){prediction=((float[][][])out.get(0).getValue())[0];}
                    StringBuilder text=new StringBuilder();int previous=-1,count=0;double confidence=0;
                    for(float[] step:prediction){
                        int best=0;for(int j=1;j<step.length;j++)if(step[j]>step[best])best=j;
                        if(best!=0&&best!=previous&&best<alphabet.size()){text.append(alphabet.get(best));confidence+=step[best];count++;}
                        previous=best;
                    }
                    String value=text.toString().trim();
                    if(count>0&&confidence/count>=.5&&!value.isEmpty()){float[] coords=new float[8];for(int j=0;j<4;j++){coords[j*2]=(float)box[j].x;coords[j*2+1]=(float)box[j].y;}lines.add(new Line(coords,value,confidence/count));}
                }finally{line.release();}
            }
            return lines;
        }finally{image.release();}
    }
    @Override public void close() throws Exception {detector.close();recognizer.close();classifier.close();}
}

