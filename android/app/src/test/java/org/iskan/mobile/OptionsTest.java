package org.iskan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class OptionsTest {
 @Test public void paperAndLandscapeStayConsistent(){ScanDraft.Options o=new ScanDraft.Options();assertArrayEquals(new float[]{210,297},o.size(),.001f);o.landscape=true;assertArrayEquals(new float[]{297,210},o.size(),.001f);assertTrue(o.valid());}
 @Test public void invalidPaperMarginsAndCountAreRejected(){ScanDraft.Options o=new ScanDraft.Options();o.paper="Custom";o.width=20;assertFalse(o.valid());o.width=210;o.margin=110;assertFalse(o.valid());o.margin=8;o.expected=101;assertFalse(o.valid());}
 @Test public void filenameCannotTraverseFolders(){assertEquals("..-..-secret",Exporter.baseName("../../secret"));assertEquals("Scan",Exporter.baseName(" ... "));assertFalse(Exporter.baseName("A/B\\C:*?").contains("/"));assertTrue(Exporter.baseName("A".repeat(200)).length()<=100);}
 @Test public void idCardHasPhysicalSizeAndRequiresTwoSides(){ScanDraft.Options o=new ScanDraft.Options();o.idScan=true;o.multiple=true;o.expected=2;o.landscape=true;assertArrayEquals(new float[]{85.6f,53.98f},o.size(),.001f);assertTrue(o.valid());o.expected=1;assertFalse(o.valid());}
 @Test public void defaultsUseConservativeCleanPaperAndSearchablePdf(){ScanDraft.Options o=new ScanDraft.Options();assertEquals(3,o.appearance);assertEquals(0,o.strength,0);assertTrue(o.searchableDefault);assertEquals("PDF",o.exportFormat);}
}

