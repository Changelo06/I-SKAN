package org.iskan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class DocumentLayoutTest {
 @Test public void idOutputCountFollowsPrintChoice(){assertEquals(1,DocumentLayout.pageCount(true,true,2));assertEquals(2,DocumentLayout.pageCount(true,false,2));assertEquals(4,DocumentLayout.pageCount(false,true,4));assertEquals(0,DocumentLayout.pageCount(true,true,0));}
 @Test public void bothIdSidesFitOnA4AtActualCardSize(){float[] a=DocumentLayout.idSlot(true,0,2),b=DocumentLayout.idSlot(true,1,2);assertEquals(85.6f,a[2],.001f);assertEquals(53.98f,a[3],.001f);assertEquals(12,a[1]-b[1]-b[3],.001f);assertTrue(b[1]>0);assertTrue(a[1]+a[3]<297);}
 @Test public void separateSidesAreCenteredOnEachA4Page(){float[] a=DocumentLayout.idSlot(false,0,2),b=DocumentLayout.idSlot(false,1,2);assertArrayEquals(a,b,0);assertEquals(105,a[0]+a[2]/2,.001f);assertEquals(148.5f,a[1]+a[3]/2,.001f);}
 @Test public void pdfTextCheckHandlesExtractionWhitespace(){assertEquals("ABC123",Exporter.compactText("A B\nC\u00a0123"));}
}
