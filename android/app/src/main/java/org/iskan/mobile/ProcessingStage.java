package org.iskan.mobile;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.view.*;
import android.widget.*;
/** Real work drives states. Success draws a short check animation; running uses a native ring. */
final class ProcessingStage extends LinearLayout {
 private final Indicator mark;private final TextView label;private final ProgressBar spinner;private char state=' ';
 ProcessingStage(Context context,String title){super(context);setGravity(Gravity.CENTER_VERTICAL);setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);float density=getResources().getDisplayMetrics().density;FrameLayout indicator=new FrameLayout(context);addView(indicator,new LayoutParams((int)(38*density),-1));mark=new Indicator(context);indicator.addView(mark,new FrameLayout.LayoutParams((int)(24*density),(int)(24*density),Gravity.CENTER));spinner=new ProgressBar(context);spinner.setIndeterminateTintList(ColorStateList.valueOf(0xffF3B48B));indicator.addView(spinner,new FrameLayout.LayoutParams((int)(24*density),(int)(24*density),Gravity.CENTER));label=new TextView(context);label.setTextSize(14);label.setTextColor(0xfff3f1ed);addView(label,new LayoutParams(0,-2,1));setText("○  "+title);}
 void setText(String value){char next=value.charAt(0);boolean running=next=='◉',good=next=='✓',bad=next=='✕';spinner.setVisibility(running?VISIBLE:GONE);mark.setVisibility(running?GONE:VISIBLE);mark.setState(next,next!=state);state=next;label.setText(value.substring(1).trim());setContentDescription((running?"In progress: ":good?"Completed: ":bad?"Failed: ":"Pending: ")+label.getText());}
 private static final class Indicator extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path check=new Path(),segment=new Path();private final PathMeasure measure=new PathMeasure();private ValueAnimator animation;private char state;private float progress=1;
  Indicator(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  void setState(char value,boolean changed){state=value;if(animation!=null)animation.cancel();progress=1;if(changed&&value=='✓'&&ValueAnimator.areAnimatorsEnabled()){progress=0;animation=ValueAnimator.ofFloat(0,1);animation.setDuration(220);animation.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animation.start();}invalidate();}
  @Override protected void onDraw(Canvas canvas){float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-1;paint.setStyle(Paint.Style.FILL);if(state=='✓'||state=='✕'){paint.setColor(state=='✓'?0xff248548:0xffb83d3d);canvas.drawCircle(cx,cy,r,paint);paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(r*.16f);paint.setStrokeCap(Paint.Cap.ROUND);if(state=='✓'){check.reset();check.moveTo(cx-r*.45f,cy);check.lineTo(cx-r*.1f,cy+r*.32f);check.lineTo(cx+r*.48f,cy-r*.35f);measure.setPath(check,false);segment.reset();measure.getSegment(0,measure.getLength()*progress,segment,true);canvas.drawPath(segment,paint);}else{canvas.drawLine(cx-r*.32f,cy-r*.32f,cx+r*.32f,cy+r*.32f,paint);canvas.drawLine(cx+r*.32f,cy-r*.32f,cx-r*.32f,cy+r*.32f,paint);}}else{paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(r*.15f);paint.setColor(0xff92989f);canvas.drawCircle(cx,cy,r*.85f,paint);}}
  @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
 }
}
