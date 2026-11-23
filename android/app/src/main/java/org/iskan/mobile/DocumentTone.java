package org.iskan.mobile;
/** Pure, bounded tone math shared by the native pipeline and local regression tests. */
final class DocumentTone {
 static int signed(int value,int limit){return Math.max(-limit,Math.min(limit,value));}
 static int tone(int value,int brightness,int exposure,int contrast){double gain=Math.pow(2,signed(exposure,100)/50.0),c=Math.pow(2,signed(contrast,50)/50.0);double encoded=value/255.0,linear=encoded<=.04045?encoded/12.92:Math.pow((encoded+.055)/1.055,2.4),lit=linear*gain,exposed=255*(lit<=.0031308?12.92*lit:1.055*Math.pow(lit,1/2.4)-.055),adjusted=(exposed-127.5)*c+127.5+signed(brightness,50)*1.2;return Math.max(0,Math.min(255,(int)Math.round(adjusted)));}
 static int sharpen(int value,int blurred,int amount){int detail=value-blurred;if(Math.abs(detail)<=2)return value;int adjustment=(int)Math.round(detail*PaperCleanup.clamp(amount)/100.0);adjustment=Math.max(-24,Math.min(24,adjustment));return Math.max(0,Math.min(255,value+adjustment));}
 static int blockSize(int width,int height){int minimum=Math.min(width,height);if(minimum<3)return 0;int block=Math.max(21,Math.min(101,Math.round(Math.max(width,height)*41/1800f)));if(block%2==0)block++;int max=minimum%2==0?minimum-1:minimum;return Math.min(block,max);}
 static double thresholdOffset(int ink){return 16-PaperCleanup.clamp(ink)*.14;}
}
