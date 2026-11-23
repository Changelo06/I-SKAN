package org.iskan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class PaperCleanupTest {
 @Test public void shadowedNeutralPaperBecomesWhite(){for(int paper=150;paper<=255;paper++)assertTrue(PaperCleanup.isPaper(paper,paper,paper,paper));}
 @Test public void distinguishableFaintInkIsPreserved(){for(int paper=150;paper<=255;paper++)for(int ink=0;ink<paper-5;ink++)assertFalse(PaperCleanup.isPaper(ink,ink,ink,paper));}
 @Test public void coloredStampsAndPhotosArePreserved(){assertFalse(PaperCleanup.isPaper(190,50,20,220));assertFalse(PaperCleanup.isPaper(40,40,200,220));assertFalse(PaperCleanup.isPaper(180,210,240,230));}
 @Test public void ambiguousDarkBackgroundIsUntouched(){assertFalse(PaperCleanup.isPaper(120,120,120,120));}
 @Test public void zeroWhiteningPreservesEveryChannel(){for(int c=0;c<256;c++)assertEquals(c,PaperCleanup.whiten(c,0));assertEquals(255,PaperCleanup.whiten(160,100));assertEquals(208,PaperCleanup.whiten(160,50));}
 @Test public void shadowControlChangesOnlyBackgroundEligibility(){assertFalse(PaperCleanup.isPaper(120,120,120,120,0,75,60));assertTrue(PaperCleanup.isPaper(120,120,120,120,100,75,60));assertFalse(PaperCleanup.isPaper(60,60,60,120,100,75,60));}
 @Test public void strongerInkProtectionKeepsFainterMarks(){assertTrue(PaperCleanup.isPaper(210,210,210,220,50,0,60));assertFalse(PaperCleanup.isPaper(210,210,210,220,50,100,60));}
 @Test public void strongerColorProtectionKeepsSubtleTint(){assertTrue(PaperCleanup.isPaper(210,220,230,220,50,75,0));assertFalse(PaperCleanup.isPaper(210,220,230,220,50,75,100));}

}
