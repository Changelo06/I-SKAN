package org.iskan.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraCharacteristics;
import android.view.*;
import android.widget.FrameLayout;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.*;
import androidx.camera.core.resolutionselector.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import org.opencv.core.*;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@androidx.annotation.OptIn(markerClass=androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)
final class ScanCamera {
    interface Listener { void quality(Vision.Result result,int width,int height);void captured(File file);void error(String code); }
    final Context context;final LifecycleOwner owner;final Listener listener;
    final ExecutorService analyzer=Executors.newSingleThreadExecutor();
    ProcessCameraProvider provider;Camera camera;ImageCapture capture;PreviewView preview;
    boolean auto=true,running=false,taking=false;
    int flash=ImageCapture.FLASH_MODE_OFF;
    String selectedId="",smoothed="";int repeat=0;
    long lastAnalysis=0,stableSince=0,lastCapture=0;
    byte[] lastFrame,lastCaptured;boolean sceneMoved=true;
    ScanCamera(Context c,LifecycleOwner o,Listener l){context=c;owner=o;listener=l;}
    void start(FrameLayout parent){
        running=true;preview=new PreviewView(context);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        parent.addView(preview,0,new FrameLayout.LayoutParams(-1,-1));
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(context);
        future.addListener(()->{try{provider=future.get();if(running)bind();}catch(Exception e){listener.error("CAMERA_UNAVAILABLE");}},ContextCompat.getMainExecutor(context));
    }
    void bind(){
        try{
            provider.unbindAll();
            CameraSelector selector=selectedId.isEmpty()?CameraSelector.DEFAULT_BACK_CAMERA:new CameraSelector.Builder().addCameraFilter(infos->{
                ArrayList<CameraInfo> out=new ArrayList<>();for(CameraInfo i:infos)if(Camera2CameraInfo.from(i).getCameraId().equals(selectedId))out.add(i);return out;}).build();
            if(!provider.hasCamera(selector))selector=CameraSelector.DEFAULT_FRONT_CAMERA;
            Preview view=new Preview.Builder().build();view.setSurfaceProvider(preview.getSurfaceProvider());
            int rotation=preview.getDisplay()!=null?preview.getDisplay().getRotation():Surface.ROTATION_0;
            capture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setTargetRotation(rotation)
                .setResolutionSelector(new ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build()).build();
            capture.setFlashMode(flash);
            ImageAnalysis analysis=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(new android.util.Size(640,480)).setTargetRotation(rotation).build();
            analysis.setAnalyzer(analyzer,this::analyze);
            camera=provider.bindToLifecycle(owner,selector,view,capture,analysis);
            preview.setOnTouchListener((v,e)->{if(e.getAction()==android.view.MotionEvent.ACTION_UP){FocusMeteringAction focus=new FocusMeteringAction.Builder(preview.getMeteringPointFactory().createPoint(e.getX(),e.getY()),FocusMeteringAction.FLAG_AF|FocusMeteringAction.FLAG_AE).build();camera.getCameraControl().startFocusAndMetering(focus);v.performClick();return true;}return true;});
            stableSince=0;lastFrame=null;repeat=0;
        }catch(Exception e){listener.error("CAMERA_UNAVAILABLE");}
    }
    private void analyze(ImageProxy proxy){
        try{
            long now=System.currentTimeMillis();if(!running||taking||now-lastAnalysis<550)return;lastAnalysis=now;
            int w=proxy.getWidth(),h=proxy.getHeight();ImageProxy.PlaneProxy plane=proxy.getPlanes()[0];ByteBuffer bytes=plane.getBuffer();byte[] data=new byte[w*h];
            int row=plane.getRowStride(),pixel=plane.getPixelStride();
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)data[y*w+x]=bytes.get(y*row+x*pixel);
            Mat original=new Mat(h,w,CvType.CV_8U);original.put(0,0,data);Mat upright=new Mat();
            try{
                int angle=proxy.getImageInfo().getRotationDegrees();
                if(angle==0)original.copyTo(upright);else Core.rotate(original,upright,angle==90?Core.ROTATE_90_CLOCKWISE:angle==180?Core.ROTATE_180:Core.ROTATE_90_COUNTERCLOCKWISE);
                Vision.Result result=Vision.analyzeGray(upright);double motion=lastFrame==null?0:Vision.difference(lastFrame,result.fingerprint);
                lastFrame=result.fingerprint;if(motion>12)sceneMoved=true;
                if(motion>9){result.message="Hold still";result.ready=false;}
                if(result.message.equals(smoothed))repeat++;else{smoothed=result.message;repeat=1;stableSince=0;}
                if(result.ready&&motion<4){if(stableSince==0)stableSince=now;}else stableSince=0;
                int width=upright.cols(),height=upright.rows();
                if(repeat>=2)ContextCompat.getMainExecutor(context).execute(()->{if(running)listener.quality(result,width,height);});
                if(auto&&result.ready&&sceneMoved&&stableSince>0&&now-stableSince>=1800&&now-lastCapture>4500&&Vision.difference(lastCaptured,result.fingerprint)>14){
                    lastCaptured=result.fingerprint;sceneMoved=false;
                    ContextCompat.getMainExecutor(context).execute(this::take);
                }
            }finally{original.release();upright.release();}
        }catch(Exception e){ContextCompat.getMainExecutor(context).execute(()->{if(running)listener.error("QUALITY_CHECK_UNAVAILABLE");});}
        finally{proxy.close();}
    }
    void take(){
        if(!running||taking||capture==null)return;taking=true;lastCapture=System.currentTimeMillis();stableSince=0;lastCaptured=lastFrame;sceneMoved=false;
        try{
            File file=File.createTempFile("capture-",".jpg",context.getCacheDir());
            capture.takePicture(new ImageCapture.OutputFileOptions.Builder(file).build(),ContextCompat.getMainExecutor(context),new ImageCapture.OnImageSavedCallback(){
                public void onImageSaved(ImageCapture.OutputFileResults r){taking=false;if(running)listener.captured(file);else file.delete();}
                public void onError(ImageCaptureException e){taking=false;file.delete();listener.error("CAPTURE_FAILED");}
            });
        }catch(Exception e){taking=false;listener.error("CAPTURE_FAILED");}
    }
    void cycleFlash(){flash=flash==ImageCapture.FLASH_MODE_ON?ImageCapture.FLASH_MODE_OFF:ImageCapture.FLASH_MODE_ON;if(capture!=null)capture.setFlashMode(flash);}
    String flashName(){return flash==ImageCapture.FLASH_MODE_ON?"On":"Off";}
    boolean hasFlash(){return camera!=null&&camera.getCameraInfo().hasFlashUnit();}
    ArrayList<String[]> cameras(){
        ArrayList<String[]> out=new ArrayList<>();if(provider==null)return out;
        for(CameraInfo i:provider.getAvailableCameraInfos()){
            Camera2CameraInfo info=Camera2CameraInfo.from(i);Integer facing=info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING);
            out.add(new String[]{info.getCameraId(),(facing!=null&&facing==CameraCharacteristics.LENS_FACING_FRONT?"Front":"Rear")+" camera "+info.getCameraId()});
        }return out;
    }
    void stop(){running=false;if(preview!=null&&preview.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)preview.getParent()).removeView(preview);if(provider!=null)provider.unbindAll();analyzer.shutdown();taking=false;}
}

