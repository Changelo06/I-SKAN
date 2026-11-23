package org.iskan.mobile;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** A single outlined chat balloon; the tail points left toward Bondi. */
final class SpeechBubble extends Drawable {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private final float density;private final int fill,stroke;
 SpeechBubble(float density,int fill,int stroke){this.density=density;this.fill=fill;this.stroke=stroke;}
 @Override protected void onBoundsChange(Rect bounds){float x=bounds.left+11*density,y=bounds.top+density,right=bounds.right-density,bottom=bounds.bottom-density,r=Math.min(16*density,(bottom-y)/3),mid=(y+bottom)/2;path.reset();path.moveTo(x+r,y);path.lineTo(right-r,y);path.quadTo(right,y,right,y+r);path.lineTo(right,bottom-r);path.quadTo(right,bottom,right-r,bottom);path.lineTo(x+r,bottom);path.quadTo(x,bottom,x,bottom-r);path.lineTo(x,mid+8*density);path.quadTo(bounds.left+3*density,mid+9*density,bounds.left+density,mid+15*density);path.quadTo(bounds.left+3*density,mid+2*density,x,mid-6*density);path.lineTo(x,y+r);path.quadTo(x,y,x+r,y);path.close();}
 @Override public void draw(Canvas canvas){paint.setStyle(Paint.Style.FILL);paint.setColor(fill);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density);paint.setColor(stroke);canvas.drawPath(path,paint);}
 @Override public void setAlpha(int value){paint.setAlpha(value);invalidateSelf();}
 @Override public void setColorFilter(ColorFilter value){paint.setColorFilter(value);invalidateSelf();}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
