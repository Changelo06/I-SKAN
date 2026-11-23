package org.iskan.mobile;
import java.util.*;
final class TextRecognition {
 interface Reader {List<OnnxOcr.Line> read()throws Exception;}
 private TextRecognition(){}
 static void attempt(ScanDraft.Page page,Reader reader){
  try{List<OnnxOcr.Line> lines=reader.read();page.lines=lines==null?Collections.emptyList():lines;page.textUnreadable=page.lines.isEmpty();}
  catch(Exception|OutOfMemoryError|LinkageError failure){page.lines=Collections.emptyList();page.textUnreadable=true;}
  if(page.textUnreadable)page.warning="";
 }
}
