package org.iskan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScanFlowTest {
 @Test public void cameraIsFreeformAfterOneOrSeveralPages(){ScanDraft.Options o=new ScanDraft.Options();ScanFlow.configure(o,2);assertTrue(o.multiple);assertTrue(o.freeform);assertFalse(o.galleryOnly);assertFalse(ScanFlow.needsMore(o,1));assertFalse(ScanFlow.needsMore(o,5));assertTrue(o.valid());}
 @Test public void importUsesGalleryAndDoesNotDemandMorePages(){ScanDraft.Options o=new ScanDraft.Options();ScanFlow.configure(o,1);assertTrue(o.galleryOnly);assertTrue(o.freeform);assertFalse(ScanFlow.needsMore(o,1));assertFalse(ScanFlow.needsMore(o,20));}
 @Test public void idStillRequiresFrontAndBack(){ScanDraft.Options o=new ScanDraft.Options();ScanFlow.configure(o,0);assertTrue(o.idScan);assertFalse(o.freeform);assertFalse(o.galleryOnly);assertTrue(ScanFlow.needsMore(o,1));assertFalse(ScanFlow.needsMore(o,2));assertTrue(o.valid());}
 @Test public void documentAppearanceSurvivesDirectCameraAction(){ScanDraft.Options o=new ScanDraft.Options();o.appearance=1;o.strength=.2f;ScanFlow.configure(o,2);assertEquals(1,o.appearance);assertEquals(.2f,o.strength,0);}
 @Test public void legacyDraftKeepsItsExpectedCount(){ScanDraft.Options o=new ScanDraft.Options();o.multiple=true;o.expected=4;assertTrue(ScanFlow.needsMore(o,2));assertFalse(ScanFlow.needsMore(o,4));}
}
