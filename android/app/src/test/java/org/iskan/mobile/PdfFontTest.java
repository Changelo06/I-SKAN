package org.iskan.mobile;
import java.io.*;
import com.tom_roush.fontbox.ttf.TTFParser;
import com.tom_roush.fontbox.ttf.TrueTypeFont;
import org.junit.Test;
import static org.junit.Assert.*;
public class PdfFontTest {
 @Test public void bundledFontSupportsChineseEnglishAndEmbedding() throws Exception {
  try(TrueTypeFont font=new TTFParser().parse(new File("src/main/assets/fonts/NotoSansSC-Regular.ttf"))){
   assertTrue(font.getUnicodeCmapLookup().getGlyphId('M')>0);
   assertTrue(font.getUnicodeCmapLookup().getGlyphId('文')>0);
   assertTrue(font.getUnicodeCmapLookup().getGlyphId('掃')>0);
   assertTrue(font.getUnicodeCmapLookup().getGlyphId('描')>0);
   assertEquals(0,font.getOS2Windows().getFsType());
  }
 }
}

