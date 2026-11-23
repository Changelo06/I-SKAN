package org.iskan.mobile;
final class PaperCleanup {
 private PaperCleanup(){}
 static int clamp(int value){return Math.max(0,Math.min(100,value));}
 static boolean isPaper(int b,int g,int r,int background){return isPaper(b,g,r,background,50,75,60);}
 static boolean isPaper(int b,int g,int r,int background,int shadows,int ink,int colors){
  int min=Math.min(b,Math.min(g,r)),max=Math.max(b,Math.max(g,r));
  int luminance=(29*b+150*g+77*r)>>8;
  int floor=220-Math.round(1.4f*clamp(shadows)),tolerance=20-Math.round(.2f*clamp(ink)),chroma=48-Math.round(.4f*clamp(colors));
  return max-min<=chroma&&background>=floor&&luminance>=background-tolerance;
 }
 static int whiten(int channel,int amount){return channel+Math.round((255-channel)*clamp(amount)/100f);}
}
