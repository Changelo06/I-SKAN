package org.iskan.mobile;
final class DocumentLayout {
 private DocumentLayout(){}
 static int pageCount(boolean id,boolean onePage,int sources){return id&&onePage&&sources>0?1:sources;}
 // Actual ID-1 size in mm, centered on A4. Two sides stack with a 12 mm gap.
 static float[] idSlot(boolean onePage,int side,int sides){
  float width=85.6f,height=53.98f,x=(210-width)/2,y=(297-height)/2;
  if(onePage&&sides>1)y=side==0?297/2f+6:297/2f-6-height;
  return new float[]{x,y,width,height};
 }
}
