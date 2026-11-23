package org.iskan.mobile;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;
/** A directly manipulated shallow curved straightening ruler; no per-frame bitmap allocation. */
final class AngleRuler extends View {
 interface Listener{void changed(float degrees,boolean finished);}
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final float density;
 private float value,downX,start;
 private boolean dragging;
 int ink=0xff243b48,muted=0xff8c9ba3,accent=0xffdc7852;
 Listener listener;
 AngleRuler(Context context){super(context);density=getResources().getDisplayMetrics().density;setFocusable(true);setContentDescription("Straighten angle. Drag the curved dial left or right, minus 45 to plus 45 degrees.");}
 void setAngle(float degrees){if(!Float.isFinite(degrees))return;value=Math.max(-45,Math.min(45,degrees));invalidate();}
 float angle(){return value;}
 private void change(float degrees,boolean finished){setAngle(Math.round(degrees*10)/10f);if(listener!=null)listener.changed(value,finished);}
 protected void onDraw(Canvas canvas){
  super.onDraw(canvas);
  float center=getWidth()/2f;
  float halfWidth=center-14*density;
  if(halfWidth<=0)return;
  float baseline=19*density;
  float rise=14*density;
  p.setTextAlign(Paint.Align.CENTER);
  p.setTextSize(10*density);
  p.setStrokeCap(Paint.Cap.ROUND);
  for(int degree=-45;degree<=45;degree++){
   float offset=(degree-value)*9*density;
   if(Math.abs(offset)>halfWidth)continue;
   float position=offset/halfWidth;
   float x=center+offset;
   float y=baseline+rise*position*position;
   boolean major=degree%5==0;
   p.setColor(degree==0?accent:major?ink:muted);
   p.setStrokeWidth(density);
   canvas.drawLine(x,y,x-position*3*density,y+(major?12:6)*density,p);
   if(degree%10==0)canvas.drawText(degree+"\u00B0",x,y+27*density,p);
  }
  p.setColor(accent);
  p.setStrokeWidth(2*density);
  canvas.drawLine(center,5*density,center,baseline+12*density,p);
 }
 public boolean onTouchEvent(MotionEvent e){if(!isEnabled())return false;switch(e.getActionMasked()){
  case MotionEvent.ACTION_DOWN:downX=e.getX();start=value;dragging=true;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;
  case MotionEvent.ACTION_MOVE:if(dragging){change(start+(downX-e.getX())/(9*density),false);return true;}break;
  case MotionEvent.ACTION_UP:if(dragging){dragging=false;change(start+(downX-e.getX())/(9*density),true);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}break;
  case MotionEvent.ACTION_CANCEL:dragging=false;change(start,true);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);return true;
 }return false;}
 public boolean performClick(){super.performClick();return true;}
 public boolean onKeyDown(int key,KeyEvent event){if(isEnabled()&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){change(value+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1),true);return true;}return super.onKeyDown(key,event);}
 public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.SeekBar");info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,-45,45,value));info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);}
 public boolean performAccessibilityAction(int action,Bundle args){if(!isEnabled())return super.performAccessibilityAction(action,args);if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){float requested=args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,Float.NaN);if(!Float.isFinite(requested))return false;change(requested,true);return true;}if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){change(value+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1),true);announceForAccessibility(String.format(Locale.ROOT,"%.1f degrees",value));return true;}return super.performAccessibilityAction(action,args);}
}
