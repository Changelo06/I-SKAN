package org.iskan.mobile;
final class CropGeometry {
 private CropGeometry(){}
 static float[] full(){return new float[]{0,0,1,0,1,1,0,1};}
 static int turn(int angle){return ((angle%360)+360)%360;}
 static boolean valid(float[] p){
  if(p==null||p.length!=8)return false;double sign=0,area=0;
  for(int i=0;i<4;i++){int j=(i+1)%4,k=(i+2)%4;float x=p[i*2],y=p[i*2+1];if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||x>1||y<0||y>1)return false;double cross=(p[j*2]-x)*(p[k*2+1]-p[j*2+1])-(p[j*2+1]-y)*(p[k*2]-p[j*2]);if(Math.abs(cross)<.00001)return false;if(sign==0)sign=cross;else if(sign*cross<0)return false;area+=x*p[j*2+1]-p[j*2]*y;}
  return Math.abs(area)>.03;
 }
 static float[] bounds(float[] p,int w,int h){float minX=1,minY=1,maxX=0,maxY=0;for(int i=0;i<4;i++){minX=Math.min(minX,p[i*2]);maxX=Math.max(maxX,p[i*2]);minY=Math.min(minY,p[i*2+1]);maxY=Math.max(maxY,p[i*2+1]);}return new float[]{minX*w,minY*h,(maxX-minX)*w,(maxY-minY)*h};}
 static float[] move(float[] original,int handle,float dx,float dy){
  float[] p=original.clone();boolean[] selected=new boolean[4];if(handle<4)selected[handle]=true;else if(handle<8){selected[handle-4]=true;selected[(handle-3)%4]=true;}else for(int i=0;i<4;i++)selected[i]=true;
  float lowX=-1,highX=1,lowY=-1,highY=1;for(int i=0;i<4;i++)if(selected[i]){lowX=Math.max(lowX,-p[i*2]);highX=Math.min(highX,1-p[i*2]);lowY=Math.max(lowY,-p[i*2+1]);highY=Math.min(highY,1-p[i*2+1]);}
  dx=Math.max(lowX,Math.min(highX,dx));dy=Math.max(lowY,Math.min(highY,dy));for(int i=0;i<4;i++)if(selected[i]){p[i*2]+=dx;p[i*2+1]+=dy;}return valid(p)?p:original.clone();
 }
 static float[] rectangle(float x,float y,float width,float height,int imageW,int imageH){
  if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(width)||!Float.isFinite(height)||width<2||height<2)throw new IllegalArgumentException("INVALID_CROP");
  width=Math.min(imageW,width);height=Math.min(imageH,height);x=Math.max(0,Math.min(imageW-width,x));y=Math.max(0,Math.min(imageH-height,y));float[] p={x/imageW,y/imageH,(x+width)/imageW,y/imageH,(x+width)/imageW,(y+height)/imageH,x/imageW,(y+height)/imageH};if(!valid(p))throw new IllegalArgumentException("CROP_TOO_SMALL");return p;
 }
 static float[] rotate(float[] p,float degrees,int w,int h){
  float[] q=p.clone();double cx=0,cy=0;for(int i=0;i<4;i++){cx+=p[i*2]*w/4;cy+=p[i*2+1]*h/4;}double cos=Math.cos(Math.toRadians(degrees)),sin=Math.sin(Math.toRadians(degrees)),minX=Double.MAX_VALUE,minY=minX,maxX=-minX,maxY=-minX;
  for(int i=0;i<4;i++){double x=p[i*2]*w-cx,y=p[i*2+1]*h-cy;q[i*2]=(float)(x*cos-y*sin);q[i*2+1]=(float)(x*sin+y*cos);minX=Math.min(minX,q[i*2]);maxX=Math.max(maxX,q[i*2]);minY=Math.min(minY,q[i*2+1]);maxY=Math.max(maxY,q[i*2+1]);}
  double scale=Math.min(1,Math.min(w/(maxX-minX),h/(maxY-minY)));cx=Math.max(-minX*scale,Math.min(w-maxX*scale,cx));cy=Math.max(-minY*scale,Math.min(h-maxY*scale,cy));for(int i=0;i<4;i++){q[i*2]=(float)Math.max(0,Math.min(1,(q[i*2]*scale+cx)/w));q[i*2+1]=(float)Math.max(0,Math.min(1,(q[i*2+1]*scale+cy)/h));}return valid(q)?q:p.clone();
 }
}
