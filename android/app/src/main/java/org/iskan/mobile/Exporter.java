package org.iskan.mobile;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.*;
import com.tom_roush.pdfbox.pdmodel.graphics.image.*;
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode;
import com.tom_roush.pdfbox.util.Matrix;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

final class Exporter {
    private final Context context;
    Exporter(Context c){context=c.getApplicationContext();PDFBoxResourceLoader.init(context);}
    static final String RELATIVE="Download/I-SKAN/";
    static String baseName(String title){
        String name=title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","-").trim().replaceAll("[. ]+$","");
        if(name.isEmpty())name="Scan";if(name.length()>100)name=name.substring(0,100);
        return name;
    }
    void compose(ScanDraft draft) throws Exception {
        if(draft.pages.isEmpty())throw new IOException("NO_PAGES");
        File target=draft.file("document.pdf"),part=draft.file("document.pdf.part");
        ArrayList<ArrayList<String>> expectedText=new ArrayList<>();
        try(PDDocument doc=new PDDocument();InputStream fontStream=context.getAssets().open("fonts/NotoSansSC-Regular.ttf")){
            PDType0Font font=PDType0Font.load(doc,fontStream,true);
            float[] size=draft.options.size();float pw=size[0]*72/25.4f,ph=size[1]*72/25.4f,margin=(draft.options.idScan?0:draft.options.margin)*72/25.4f;
            doc.getDocumentInformation().setTitle(draft.title.isEmpty()?"Scan":draft.title);
            if(draft.options.idScan){pw=210*72/25.4f;ph=297*72/25.4f;}
            for(int output=0;output<draft.outputPageCount();output++){
                ArrayList<String> pageText=new ArrayList<>();expectedText.add(pageText);
                PDPage page=new PDPage(new PDRectangle(pw,ph));doc.addPage(page);
                try(PDPageContentStream stream=new PDPageContentStream(doc,page)){
                    int first=draft.options.idScan&&draft.options.idOnePage?0:output;
                    int last=draft.options.idScan&&draft.options.idOnePage?draft.pages.size():first+1;
                    for(int index=first;index<last;index++){
                        ScanDraft.Page p=draft.pages.get(index);if(p.processed.isEmpty())throw new IOException("PAGE_NOT_PROCESSED");
                        Bitmap image=ScanDraft.decode(draft.file(p.processed),3600);
                        try{
                            float bx=margin,by=margin,bw=pw-2*margin,bh=ph-2*margin;
                            if(draft.options.idScan){float[] slot=DocumentLayout.idSlot(draft.options.idOnePage,index,draft.pages.size());bx=slot[0]*72/25.4f;by=slot[1]*72/25.4f;bw=slot[2]*72/25.4f;bh=slot[3]*72/25.4f;}
                            float scale=Math.min(bw/image.getWidth(),bh/image.getHeight());float iw=image.getWidth()*scale,ih=image.getHeight()*scale,x=bx+(bw-iw)/2,y=by+(bh-ih)/2;
                            stream.drawImage(LosslessFactory.createFromImage(doc,image),x,y,iw,ih);
                            if(draft.searchable&&!p.textUnreadable&&p.lines!=null){
                                int beforeText=pageText.size();
                                for(OnnxOcr.Line line:p.lines){
                                    String text=supported(font,line.text);if(text.isEmpty())continue;
                                    float[] b=line.box;float lx=(b[0]+b[6])/2,ly=(b[1]+b[7])/2,rx=(b[2]+b[4])/2,ry=(b[3]+b[5])/2;
                                    float boxHeight=(float)((Math.hypot(b[6]-b[0],b[7]-b[1])+Math.hypot(b[4]-b[2],b[5]-b[3]))/2);
                                    float fs=Math.max(1,boxHeight*scale*.85f),width=(float)Math.hypot(rx-lx,ry-ly)*scale,natural=font.getStringWidth(text)/1000*fs;if(natural<=0)continue;
                                    stream.beginText();stream.setFont(font,fs);stream.setRenderingMode(RenderingMode.NEITHER);stream.setHorizontalScaling(width/natural*100);stream.setTextMatrix(Matrix.getRotateInstance(-Math.atan2(ry-ly,rx-lx),x+lx*scale,y+ih-ly*scale-fs*.32f));stream.showText(text);stream.endText();pageText.add(text);
                                }
                                if(pageText.size()==beforeText)p.textUnreadable=true;
                            }
                        }finally{image.recycle();}
                    }
                    stream.setRenderingMode(RenderingMode.FILL);stream.setHorizontalScaling(100);
                    if(!draft.options.idScan&&!draft.options.watermark.isEmpty()){
                        String wm=supported(font,draft.options.watermark);float fs=Math.min(24,(pw-30)/Math.max(1,font.getStringWidth(wm)/1000));stream.setNonStrokingColor(190,190,190);stream.beginText();stream.setFont(font,fs);stream.newLineAtOffset((pw-font.getStringWidth(wm)/1000*fs)/2,ph/2);stream.showText(wm);stream.endText();
                    }
                    if(!draft.options.idScan&&draft.options.numbers){String n=String.valueOf(output+1);stream.setNonStrokingColor(70,70,70);stream.beginText();stream.setFont(font,9);stream.newLineAtOffset((pw-font.getStringWidth(n)/1000*9)/2,10);stream.showText(n);stream.endText();}
                }
            }
            // Verify the actual text layer survives PDF encoding before committing.
            doc.save(part);
        }
        int verifiedPages=0;boolean retryWithoutFailedText=false;
        if(draft.searchable){
            try(PDDocument check=PDDocument.load(part)){
                com.tom_roush.pdfbox.text.PDFTextStripper reader=new com.tom_roush.pdfbox.text.PDFTextStripper();
                reader.setSuppressDuplicateOverlappingText(false);
                for(int i=0;i<draft.outputPageCount();i++){
                    reader.setStartPage(i+1);reader.setEndPage(i+1);
                    String extracted=reader.getText(check).trim();
                    String compact=compactText(extracted);
                    for(String expected:expectedText.get(i))if(!compact.contains(compactText(expected))){
                        int first=draft.options.idScan&&draft.options.idOnePage?0:i,last=draft.options.idScan&&draft.options.idOnePage?draft.pages.size():i+1;
                        for(int source=first;source<last;source++){ScanDraft.Page p=draft.pages.get(source);p.textUnreadable=true;p.lines=Collections.emptyList();p.warning="";}retryWithoutFailedText=true;break;
                    }
                    if(!expectedText.get(i).isEmpty())verifiedPages++;
                }
            }
        }
        if(retryWithoutFailedText){compose(draft);return;}
        if(!part.renameTo(target))throw new IOException("PDF_COMMIT_FAILED");
        draft.outputUris.clear();draft.outputNames.clear();draft.outputHashes.clear();draft.exported=false;
        draft.pdfSearchable=draft.searchable;draft.textVerified=verifiedPages>0;draft.textPageCount=verifiedPages;
    }

    static String compactText(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).replaceAll("[\\s\\p{Z}]+", "");}
    static String supported(PDFont font,String input){
        StringBuilder out=new StringBuilder();
        for(int offset=0;offset<input.length();){int cp=input.codePointAt(offset);offset+=Character.charCount(cp);
            if(Character.isISOControl(cp))continue;String ch=new String(Character.toChars(cp));
            try{font.getStringWidth(ch);out.append(ch);}catch(Exception ignored){}
        }
        return out.toString();
    }
    static Bitmap preview(ScanDraft d,int index,int longest) throws IOException {
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(d.file("document.pdf"),ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd);PdfRenderer.Page p=renderer.openPage(index)){
            float scale=longest/(float)Math.max(p.getWidth(),p.getHeight());
            Bitmap b=Bitmap.createBitmap(Math.max(1,Math.round(p.getWidth()*scale)),Math.max(1,Math.round(p.getHeight()*scale)),Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return b;
        }
    }
    synchronized void save(ScanDraft d) throws Exception {
        if(!d.outputUris.isEmpty()&&verify(d)){
            publish(d);record(d);d.exported=true;d.save();return;
        }
        if(d.exported)throw new IOException("SAVED_FILE_UNAVAILABLE");
        d.outputUris.clear();d.outputNames.clear();d.outputHashes.clear();d.exported=false;
        if(d.format.equals("PDF")){
            File approved=d.file("approved.pdf");
            try(PDDocument doc=PDDocument.load(d.file("document.pdf"))){
                doc.getDocumentInformation().setTitle(d.title);doc.save(approved);
            }
            if(!approved.renameTo(d.file("document.pdf")))throw new IOException("PDF_APPROVAL_FAILED");
        }
        String base=baseName(d.filename.isEmpty()?d.title:d.filename);
        if(base.toLowerCase(Locale.ROOT).endsWith(".pdf"))base=base.substring(0,base.length()-4);
        int count=d.format.equals("PDF")?1:d.outputPageCount();
        boolean published=false;
        try{
            for(int i=0;i<count;i++){
                File payload;
                String ext=d.format.toLowerCase(Locale.ROOT),name;
                if(d.format.equals("PDF")){payload=d.file("document.pdf");name=base+".pdf";}
                else{
                    payload=d.file("export-"+i+"."+ext);Bitmap b=preview(d,i,3300);
                    try{ScanDraft.writeBitmap(payload,b,d.format.equals("JPG")?Bitmap.CompressFormat.JPEG:Bitmap.CompressFormat.PNG,94);}finally{b.recycle();}
                    name=base+(count>1?String.format(Locale.ROOT,"_%03d",i+1):"")+"."+ext;
                }
                ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,unique(name));
                values.put(MediaStore.MediaColumns.MIME_TYPE,d.format.equals("PDF")?"application/pdf":d.format.equals("JPG")?"image/jpeg":"image/png");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,RELATIVE);values.put(MediaStore.MediaColumns.IS_PENDING,1);
                Uri uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("SAVE_FAILED");
                d.outputUris.add(uri.toString());d.outputNames.add(displayName(uri,name));d.outputHashes.add(hash(new FileInputStream(payload)));d.save();
                try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(uri,"w");FileInputStream in=new FileInputStream(payload)){
                    if(fd==null)throw new IOException("SAVE_FAILED");
                    try(FileOutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(fd)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();out.getFD().sync();}
                }
            }
            publish(d);published=true;record(d);d.exported=true;d.save();
            if(d.options.retain){
                try(FileOutputStream policy=new FileOutputStream(d.file(".retention"))){policy.write("30".getBytes(java.nio.charset.StandardCharsets.US_ASCII));}catch(IOException ignored){}
            }
            if(!d.options.retain){
                File[] files=d.folder.listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith("-original.jpg"))f.delete();
            }
        }catch(Exception e){
            if(published){try{d.save();}catch(Exception ignored){}throw e;}
            // Roll back only items created by this export. No content/URI enters a log.
            for(String uri:d.outputUris)try{context.getContentResolver().delete(Uri.parse(uri),null,null);}catch(Exception ignored){}
            d.outputUris.clear();d.outputNames.clear();d.outputHashes.clear();d.exported=false;d.save();throw e;
        }
    }
    boolean verify(ScanDraft d){
        if(d.outputUris.isEmpty()||d.outputUris.size()!=d.outputHashes.size()||d.outputUris.size()!=(d.format.equals("PDF")?1:d.outputPageCount()))return false;
        try{for(int i=0;i<d.outputUris.size();i++)if(!hash(context.getContentResolver().openInputStream(Uri.parse(d.outputUris.get(i)))).equals(d.outputHashes.get(i)))return false;return true;}catch(Exception e){return false;}
    }
    void recover(ScanDraft d) throws Exception{
        if(d.outputUris.isEmpty())return;
        if(verify(d)){publish(d);record(d);d.exported=true;d.save();}
        else if(!d.exported){
            for(String u:d.outputUris)try{context.getContentResolver().delete(Uri.parse(u),null,null);}catch(Exception ignored){}
            d.outputUris.clear();d.outputNames.clear();d.outputHashes.clear();d.save();
        }
    }
    private void publish(ScanDraft d) throws IOException {
        ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.IS_PENDING,0);
        for(String u:d.outputUris)if(context.getContentResolver().update(Uri.parse(u),v,null,null)!=1)throw new IOException("PUBLISH_FAILED");
    }
    String displayName(Uri uri,String fallback){try(Cursor c=context.getContentResolver().query(uri,new String[]{MediaStore.MediaColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}return fallback;}
    String unique(String name){
        String stem=name,ext="";int dot=name.lastIndexOf('.');if(dot>0){stem=name.substring(0,dot);ext=name.substring(dot);}
        for(int i=0;i<10000;i++){String candidate=i==0?name:stem+" ("+i+")"+ext;
            try(Cursor c=context.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.DISPLAY_NAME+"=? AND "+MediaStore.MediaColumns.RELATIVE_PATH+"=?",new String[]{candidate,RELATIVE},null)){if(c==null||!c.moveToFirst())return candidate;}
        }
        throw new IllegalStateException("TOO_MANY_COLLISIONS");
    }
    static String hash(InputStream input) throws Exception {
        if(input==null)throw new IOException("OUTPUT_MISSING");
        try(InputStream in=input){MessageDigest h=MessageDigest.getInstance("SHA-256");byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)h.update(b,0,n);StringBuilder out=new StringBuilder();for(byte x:h.digest())out.append(String.format(Locale.ROOT,"%02x",x&255));return out.toString();}
    }
    private void record(ScanDraft d){
        try(Receipts helper=new Receipts(context)){
            SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();
            try{
                try(Cursor c=db.rawQuery("SELECT session FROM receipts WHERE session=?",new String[]{d.id})){
                    if(!c.moveToFirst()){
                        ContentValues log=new ContentValues();log.put("title",d.title);log.put("page_count",d.pages.size());log.put("scanned_date",d.scannedDate);
                        long rowId=db.insertOrThrow("scan_logs",null,log);
                        ContentValues receipt=new ContentValues();receipt.put("session",d.id);receipt.put("log_rowid",rowId);db.insertOrThrow("receipts",null,receipt);
                    }
                }
                ContentValues output=new ContentValues();output.put("session",d.id);output.put("format",d.format);output.put("names",new org.json.JSONArray(d.outputNames).toString());output.put("uris",new org.json.JSONArray(d.outputUris).toString());output.put("page_count",d.outputPageCount());db.insertWithOnConflict("saved_outputs",null,output,SQLiteDatabase.CONFLICT_REPLACE);
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }
    }
    static final class Receipts extends SQLiteOpenHelper {
        Receipts(Context c){super(c,"scan_receipts.sqlite",null,2);}
        public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE scan_logs(title TEXT NOT NULL,page_count INTEGER NOT NULL,scanned_date TEXT NOT NULL)");db.execSQL("CREATE TABLE receipts(session TEXT PRIMARY KEY,log_rowid INTEGER)");createOutputs(db);}
        private void createOutputs(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS saved_outputs(session TEXT PRIMARY KEY,format TEXT NOT NULL,names TEXT NOT NULL,uris TEXT NOT NULL,page_count INTEGER NOT NULL)");}
        public void onUpgrade(SQLiteDatabase db,int old,int version){if(old<2){db.execSQL("ALTER TABLE receipts ADD COLUMN log_rowid INTEGER");createOutputs(db);}}
    }
    void share(ScanDraft d){
        ArrayList<Uri> uris=new ArrayList<>();for(String u:d.outputUris)uris.add(Uri.parse(u));
        Intent intent=new Intent(uris.size()>1?Intent.ACTION_SEND_MULTIPLE:Intent.ACTION_SEND);
        intent.setType(d.format.equals("PDF")?"application/pdf":d.format.equals("JPG")?"image/jpeg":"image/png");
        if(uris.size()>1)intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);else intent.putExtra(Intent.EXTRA_STREAM,uris.get(0));
        ClipData clip=ClipData.newUri(context.getContentResolver(),"Scan",uris.get(0));for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));
        intent.setClipData(clip);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser=Intent.createChooser(intent,"Share document");chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(chooser);
    }
    void open(ScanDraft d){
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(d.outputUris.get(0)),d.format.equals("PDF")?"application/pdf":d.format.equals("JPG")?"image/jpeg":"image/png")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}

