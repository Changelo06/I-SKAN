package org.iskan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class LocalFeedbackTest {
 @Test public void cameraPhrasesMatchRequestedWords(){assertEquals("pahimuyong",LocalFeedback.camera("Hold still"));assertEquals("dulom man oi",LocalFeedback.camera("Too dark"));assertEquals("ay kasanag!",LocalFeedback.camera("Too bright"));assertEquals("perpekt!",LocalFeedback.camera("Ready"));assertEquals("Looking for document",LocalFeedback.camera("Looking for document"));}
 @Test public void actionableCornerInstructionsRemainSpecific(){assertEquals("Keep all four corners visible",LocalFeedback.camera("Keep all four corners visible"));assertEquals("Move closer",LocalFeedback.camera("Move closer"));}
 @Test public void ordinaryWhitePaperIsNotAnExposureFailure(){assertFalse(LocalFeedback.overexposed(230,.75,35));assertFalse(LocalFeedback.overexposed(249,.90,30));assertTrue(LocalFeedback.overexposed(254,.99,2));}
}
