package org.iskan.mobile;
/** Source choice is separate from the number of pages in the document. */
final class ScanFlow {
 static void configure(ScanDraft.Options o,int action){boolean wasId=o.idScan;o.idScan=action==0;o.multiple=true;o.freeform=action!=0;o.galleryOnly=action==1;o.expected=o.idScan?2:1;if(o.idScan)o.appearance=0;else if(wasId){o.appearance=3;o.strength=0;}o.exportFormat="PDF";o.searchableDefault=true;}
 static boolean needsMore(ScanDraft.Options o,int captured){return !o.freeform&&o.multiple&&captured<o.expected;}
}
