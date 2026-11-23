package org.iskan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class PipelineProblemTest {
 @Test public void ocrFailureNamesPageStepAndRecovery(){String message=PipelineProblem.explain(new Exception(),"Reading text",2);assertTrue(message.contains("Page 3"));assertTrue(message.contains("Reading text"));assertTrue(message.contains("export without selectable text"));}
 @Test public void unknownTechnicalDetailsDoNotLeakDocumentContent(){String message=PipelineProblem.explain(new Exception("secret receipt content"),"Cleaning paper",0);assertFalse(message.contains("secret"));assertTrue(message.contains("Original color"));}
 @Test public void pdfFailureIsAttributedToDocument(){String message=PipelineProblem.explain(new java.io.IOException("PDF_TEXT_LAYER_MISSING"),"Building PDF",3);assertTrue(message.startsWith("All pages"));assertTrue(message.contains("text layer"));}
 @Test public void memoryAndInvalidCropOfferSpecificActions(){assertTrue(PipelineProblem.explain(new OutOfMemoryError(),"Opening photo",0).contains("smaller image"));assertTrue(PipelineProblem.explain(new Exception("CROP_TOO_SMALL"),"Correcting crop",0).contains("four corners"));}
}
