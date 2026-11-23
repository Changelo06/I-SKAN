package org.iskan.mobile;
import android.content.Context;
import android.graphics.*;
import android.view.*;
final class CropView extends View {
 private Bitmap image;private float[] corners,detected,start;private float downX,downY;private int rotation,drag=-1;private boolean flipH,flipV;
 boolean handles=true,grid=false;Runnable onChanged;
 private android.animation.ValueAnimator transformAnimation;private float displayAngle,displayX=1,displayY=1;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private final Matrix toView=new Matrix(),toImage=new Matrix();private final float density;private final Path outline=new Path(),shadePath=new Path(),detectedOutline=new Path(),loupeClip=new Path();private final Matrix loupeZoom=new Matrix();private final float[] drawn=new float[8],detectedDrawn=new float[8];private final DashPathEffect detectedDash;
 CropView(Context c){super(c);density=getResources().getDisplayMetrics().density;detectedDash=new DashPathEffect(new float[]{5*density,5*density},0);setContentDescription("Document crop. Drag corner points, edge handles, or the selected area.");}
 void setPage(Bitmap b,float[] points,int angle){cancelTransformAnimation();image=b;corners=points==null?CropGeometry.full():points.clone();detected=null;rotation=CropGeometry.turn(angle);displayAngle=rotation;invalidate();}
 void selection(float[] p){if(CropGeometry.valid(p)){corners=p.clone();invalidate();}}
 void setDetected(float[] points){detected=CropGeometry.valid(points)?points.clone():null;invalidate();}
 boolean applyDetected(){if(detected==null)return false;corners=detected.clone();handles=true;if(onChanged!=null)onChanged.run();invalidate();return true;}
 float[] crop(){return corners.clone();}
 void rotation(int angle){rotation=CropGeometry.turn(angle);displayAngle=rotation;invalidate();}
 void transform(int angle,boolean horizontal,boolean vertical){cancelTransformAnimation();rotation=CropGeometry.turn(angle);flipH=horizontal;flipV=vertical;displayAngle=rotation;displayX=flipH?-1:1;displayY=flipV?-1:1;invalidate();}
 void cancelTransformAnimation(){if(transformAnimation!=null){transformAnimation.cancel();transformAnimation=null;}displayAngle=rotation;displayX=flipH?-1:1;displayY=flipV?-1:1;invalidate();}
 void animateTransform(int angle,boolean horizontal,boolean vertical,int delta){
  if(!android.animation.ValueAnimator.areAnimatorsEnabled()){transform(angle,horizontal,vertical);return;}
  // Retarget from presentation values so repeated taps do not jump or queue animations.
  if(transformAnimation!=null)transformAnimation.cancel();float fromAngle=displayAngle,fromX=displayX,fromY=displayY;int previous=rotation;rotation=CropGeometry.turn(angle);flipH=horizontal;flipV=vertical;float targetAngle=delta==0?fromAngle:OrientationMotion.target(fromAngle,previous,delta);final float target=targetAngle;float targetX=horizontal?-1:1,targetY=vertical?-1:1;
  transformAnimation=android.animation.ValueAnimator.ofFloat(0,1);transformAnimation.setDuration(260);transformAnimation.setInterpolator(new android.view.animation.DecelerateInterpolator());transformAnimation.addUpdateListener(a->{float t=(float)a.getAnimatedValue();displayAngle=fromAngle+(target-fromAngle)*t;displayX=fromX+(targetX-fromX)*t;displayY=fromY+(targetY-fromY)*t;invalidate();});transformAnimation.start();
 }
 @Override protected void onDetachedFromWindow(){cancelTransformAnimation();super.onDetachedFromWindow();}
 private void matrices(){if(image==null)return;double radians=Math.toRadians(displayAngle);float cos=(float)Math.abs(Math.cos(radians)),sin=(float)Math.abs(Math.sin(radians));float w=image.getWidth()*cos+image.getHeight()*sin,h=image.getWidth()*sin+image.getHeight()*cos,scale=Math.max(.001f,Math.min((getWidth()-32*density)/w,(getHeight()-32*density)/h));toView.reset();toView.postTranslate(-image.getWidth()/2f,-image.getHeight()/2f);toView.postScale(displayX,displayY);toView.postRotate(displayAngle);toView.postScale(scale,scale);toView.postTranslate(getWidth()/2f,getHeight()/2f);toView.invert(toImage);}
 private float[] screen(float[] p){float[] out=p.clone();for(int i=0;i<4;i++){out[i*2]*=image.getWidth();out[i*2+1]*=image.getHeight();}toView.mapPoints(out);return out;}
 private void screenInto(float[] points,float[] out){for(int i=0;i<4;i++){out[i*2]=points[i*2]*image.getWidth();out[i*2+1]=points[i*2+1]*image.getHeight();}toView.mapPoints(out);}
 private void polygonInto(float[] p,Path path){path.reset();path.moveTo(p[0],p[1]);for(int i=1;i<4;i++)path.lineTo(p[i*2],p[i*2+1]);path.close();}
 private Path polygon(float[] p){Path path=new Path();path.moveTo(p[0],p[1]);for(int i=1;i<4;i++)path.lineTo(p[i*2],p[i*2+1]);path.close();return path;}
 @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(0xff30343a);if(image==null)return;matrices();paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);c.drawBitmap(image,toView,paint);if(!handles&&!grid)return;screenInto(corners,drawn);float[] p=drawn;polygonInto(p,outline);
  if(handles){Path shade=shadePath;shade.reset();shade.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW);shade.addPath(outline);shade.setFillType(Path.FillType.EVEN_ODD);paint.setColor(0x6630343a);c.drawPath(shade,paint);}
  if(detected!=null&&handles){paint.setStyle(Paint.Style.STROKE);paint.setColor(0x88f3b48b);paint.setStrokeWidth(density);paint.setPathEffect(detectedDash);screenInto(detected,detectedDrawn);polygonInto(detectedDrawn,detectedOutline);c.drawPath(detectedOutline,paint);paint.setPathEffect(null);}
  if(grid){paint.setStyle(Paint.Style.STROKE);paint.setColor(0x88ffffff);paint.setStrokeWidth(density);for(int i=1;i<3;i++){float t=i/3f;c.drawLine(p[0]+(p[2]-p[0])*t,p[1]+(p[3]-p[1])*t,p[6]+(p[4]-p[6])*t,p[7]+(p[5]-p[7])*t,paint);c.drawLine(p[0]+(p[6]-p[0])*t,p[1]+(p[7]-p[1])*t,p[2]+(p[4]-p[2])*t,p[3]+(p[5]-p[3])*t,paint);}}
  if(!handles)return;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setColor(0xffdc7852);c.drawPath(outline,paint);
  for(int i=0;i<4;i++){paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);c.drawCircle(p[i*2],p[i*2+1],8*density,paint);paint.setColor(0xffdc7852);c.drawCircle(p[i*2],p[i*2+1],5*density,paint);int j=(i+1)%4;float x=(p[i*2]+p[j*2])/2,y=(p[i*2+1]+p[j*2+1])/2;paint.setColor(Color.WHITE);c.drawRoundRect(x-6*density,y-4*density,x+6*density,y+4*density,3*density,3*density,paint);}
  if(drag>=0&&drag<8){float nx=drag<4?corners[drag*2]:(corners[(drag-4)*2]+corners[((drag-3)%4)*2])/2,ny=drag<4?corners[drag*2+1]:(corners[(drag-4)*2+1]+corners[((drag-3)%4)*2+1])/2;float radius=42*density,cx=getWidth()-radius-12*density,cy=radius+12*density;int save=c.save();Path clip=loupeClip;clip.reset();clip.addCircle(cx,cy,radius,Path.Direction.CW);c.clipPath(clip);c.drawColor(Color.DKGRAY);Matrix zoom=loupeZoom;zoom.reset();zoom.postTranslate(-nx*image.getWidth(),-ny*image.getHeight());zoom.postScale(flipH?-1:1,flipV?-1:1);zoom.postRotate(rotation);zoom.postScale(2*density,2*density);zoom.postTranslate(cx,cy);paint.setColor(Color.WHITE);c.drawBitmap(image,zoom,paint);paint.setColor(0xffdc7852);paint.setStrokeWidth(density);c.drawLine(cx-12*density,cy,cx+12*density,cy,paint);c.drawLine(cx,cy-12*density,cx,cy+12*density,paint);c.restoreToCount(save);}
 }
 @Override public boolean onTouchEvent(MotionEvent e){if(image==null||!handles||!isEnabled())return false;matrices();float[] points=screen(corners);int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){drag=-1;float closest=28*density;for(int i=0;i<8;i++){int edge=i-4,j=(edge+1)%4;float x=i<4?points[i*2]:(points[edge*2]+points[j*2])/2,y=i<4?points[i*2+1]:(points[edge*2+1]+points[j*2+1])/2,d=(float)Math.hypot(e.getX()-x,e.getY()-y);if(d<closest){closest=d;drag=i;}}
   if(drag<0){Region region=new Region();region.setPath(polygon(points),new Region(0,0,getWidth(),getHeight()));if(region.contains((int)e.getX(),(int)e.getY()))drag=8;}if(drag<0)return false;float[] point={e.getX(),e.getY()};toImage.mapPoints(point);downX=point[0]/image.getWidth();downY=point[1]/image.getHeight();start=corners.clone();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;}
  if(drag>=0&&action==MotionEvent.ACTION_MOVE){float[] pos={e.getX(),e.getY()};toImage.mapPoints(pos);corners=CropGeometry.move(start,drag,pos[0]/image.getWidth()-downX,pos[1]/image.getHeight()-downY);invalidate();return true;}
  if(drag>=0&&(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)){if(action==MotionEvent.ACTION_CANCEL)corners=start;drag=-1;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(action==MotionEvent.ACTION_UP&&onChanged!=null)onChanged.run();invalidate();performClick();return true;}return false;
 }
 @Override public boolean performClick(){super.performClick();return true;}
}
