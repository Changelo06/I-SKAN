package org.iskan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class CropGeometryTest {
 float[] selection(){return new float[]{.1f,.1f,.9f,.1f,.9f,.9f,.1f,.9f};}
 @Test public void smallCornerAdjustmentsStayExactlyWhereMoved(){float[] original=selection(),moved=CropGeometry.move(original,0,.02f,.01f);assertEquals(.12f,moved[0],.0001f);assertEquals(.11f,moved[1],.0001f);assertEquals(.1f,original[0],0);}
 @Test public void edgeHandleMovesBothEndpoints(){float[] p=CropGeometry.move(selection(),4,0,.05f);assertEquals(.15f,p[1],.0001f);assertEquals(.15f,p[3],.0001f);assertEquals(.9f,p[5],0);}
 @Test public void areaMoveIsBoundedWithoutChangingShape(){float[] p=CropGeometry.move(selection(),8,.8f,.8f);assertEquals(.2f,p[0],.0001f);assertEquals(1,p[4],.0001f);assertEquals(.8f,p[4]-p[0],.0001f);}
 @Test public void crossingCornerIsRejected(){assertArrayEquals(selection(),CropGeometry.move(selection(),0,.9f,.9f),0);}
 @Test public void pixelRectangleAndBoundsAgree(){float[] p=CropGeometry.rectangle(20,30,400,500,1000,2000);assertArrayEquals(new float[]{20,30,400,500},CropGeometry.bounds(p,1000,2000),.001f);}
 @Test public void angledSelectionStaysInsideImage(){float[] p=CropGeometry.rotate(CropGeometry.full(),-12.5f,1200,1800);assertTrue(CropGeometry.valid(p));for(float value:p)assertTrue(value>=0&&value<=1);assertNotEquals(p[1],p[3],.001f);}
 @Test public void clockwiseAndReverseTurnsCancel(){assertEquals(270,CropGeometry.turn(-90));assertEquals(0,CropGeometry.turn(CropGeometry.turn(90)-90));assertEquals(0,CropGeometry.turn(360));}
}
