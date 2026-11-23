package org.iskan.mobile;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class TextRecognitionTest {
 @Test public void engineFailureIsNonBlocking(){ScanDraft.Page p=new ScanDraft.Page();TextRecognition.attempt(p,()->{throw new java.io.IOException("OCR unavailable");});assertTrue(p.textUnreadable);assertTrue(p.lines.isEmpty());}
 @Test public void emptyRecognitionIsNonBlocking(){ScanDraft.Page p=new ScanDraft.Page();TextRecognition.attempt(p,Collections::emptyList);assertTrue(p.textUnreadable);assertEquals("",p.warning);}
 @Test public void lowMemoryAndMissingNativeLibraryAreNonBlocking(){ScanDraft.Page p=new ScanDraft.Page();TextRecognition.attempt(p,()->{throw new OutOfMemoryError();});assertTrue(p.textUnreadable);TextRecognition.attempt(p,()->{throw new UnsatisfiedLinkError();});assertTrue(p.textUnreadable);}
 @Test public void readableTextRemainsAvailable(){ScanDraft.Page p=new ScanDraft.Page();p.textUnreadable=true;TextRecognition.attempt(p,()->Collections.singletonList(new OnnxOcr.Line(new float[]{0,0,100,0,100,20,0,20},"Readable",.99)));assertFalse(p.textUnreadable);assertEquals("Readable",p.lines.get(0).text);}
}
