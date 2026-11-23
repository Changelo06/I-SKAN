package org.iskan.mobile;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import androidx.appcompat.widget.AppCompatImageView;

/** Native pinch, pan and double-tap inspection of the actual PDF raster. */
final class ZoomImageView extends AppCompatImageView {
 private final Matrix imageMatrix=new Matrix();
 private final ScaleGestureDetector scale;
 private final GestureDetector taps;
 private Bitmap bitmap;
 private float fitted=1,current=1,lastX,lastY;
 ZoomImageView(Context context){
  super(context);setScaleType(ScaleType.MATRIX);setBackgroundColor(0xffdfe5df);
  scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
   @Override public boolean onScale(ScaleGestureDetector d){float next=Math.max(fitted,Math.min(fitted*6,current*d.getScaleFactor()));float ratio=next/current;imageMatrix.postScale(ratio,ratio,d.getFocusX(),d.getFocusY());current=next;setImageMatrix(imageMatrix);return true;}
  });
  taps=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
   @Override public boolean onDown(MotionEvent e){return true;}
   @Override public boolean onDoubleTap(MotionEvent e){if(current>fitted*1.1f)fit();else{imageMatrix.postScale(2.5f,2.5f,e.getX(),e.getY());current=fitted*2.5f;setImageMatrix(imageMatrix);}return true;}
  });
  setContentDescription("Document preview. Pinch to zoom, drag to move, double tap to reset.");
 }
 void image(Bitmap b){bitmap=b;setImageBitmap(b);post(this::fit);}
 private void fit(){if(bitmap==null||getWidth()==0||getHeight()==0)return;fitted=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());current=fitted;imageMatrix.reset();imageMatrix.postScale(fitted,fitted);imageMatrix.postTranslate((getWidth()-bitmap.getWidth()*fitted)/2,(getHeight()-bitmap.getHeight()*fitted)/2);setImageMatrix(imageMatrix);}
 @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);fit();}
 @Override public boolean onTouchEvent(MotionEvent e){
  scale.onTouchEvent(e);taps.onTouchEvent(e);
  if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();}
  else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!scale.isInProgress()){imageMatrix.postTranslate(e.getX()-lastX,e.getY()-lastY);setImageMatrix(imageMatrix);lastX=e.getX();lastY=e.getY();}
  else if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();
  return true;
 }
 @Override public boolean performClick(){super.performClick();return true;}
}

