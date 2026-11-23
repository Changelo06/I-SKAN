package org.iskan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class DocumentToneTest {
 @Test public void neutralToneIsExactIdentity(){for(int value=0;value<256;value++)assertEquals(value,DocumentTone.tone(value,0,0,0));}
 @Test public void brightnessMovesMidtonesBothDirections(){assertTrue(DocumentTone.tone(128,20,0,0)>128);assertTrue(DocumentTone.tone(128,-20,0,0)<128);}
 @Test public void exposureUsesLinearLightStops(){assertEquals(176,DocumentTone.tone(128,0,50,0),1);assertEquals(92,DocumentTone.tone(128,0,-50,0),1);assertEquals(0,DocumentTone.tone(0,0,100,0));}
 @Test public void contrastSeparatesDarkAndLightInk(){assertTrue(DocumentTone.tone(70,0,0,30)<70);assertTrue(DocumentTone.tone(200,0,0,30)>200);}
 @Test public void toneAlwaysStaysMonotonicAndBounded(){for(int brightness:new int[]{-50,0,50})for(int exposure:new int[]{-100,0,100})for(int contrast:new int[]{-50,0,50}){int previous=-1;for(int v=0;v<256;v++){int next=DocumentTone.tone(v,brightness,exposure,contrast);assertTrue(next>=previous&&next>=0&&next<=255);previous=next;}}}
 @Test public void zeroSharpeningAndFlatNoiseAreUnchanged(){for(int value=0;value<256;value++){assertEquals(value,DocumentTone.sharpen(value,100,0));assertEquals(value,DocumentTone.sharpen(value,Math.min(255,value+2),100));}}
 @Test public void sharpeningHasBoundedHalos(){assertEquals(104,DocumentTone.sharpen(80,0,100));assertEquals(176,DocumentTone.sharpen(200,255,100));assertEquals(255,DocumentTone.sharpen(250,0,100));assertEquals(0,DocumentTone.sharpen(2,255,100));}
 @Test public void binaryWindowsFitSmallAndLargePages(){for(int width:new int[]{1,2,3,24,400,1800,3600})for(int height:new int[]{1,2,5,500,3600}){int block=DocumentTone.blockSize(width,height);if(Math.min(width,height)<3)assertEquals(0,block);else assertTrue(block>=3&&block%2==1&&block<=Math.min(width,height)&&block<=101);}}
 @Test public void higherInkRetentionUsesLowerWhiteBias(){assertEquals(16,DocumentTone.thresholdOffset(0),.001);assertEquals(2,DocumentTone.thresholdOffset(100),.001);assertTrue(DocumentTone.thresholdOffset(80)<DocumentTone.thresholdOffset(20));}
}
