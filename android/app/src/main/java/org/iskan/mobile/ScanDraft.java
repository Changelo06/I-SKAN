package org.iskan.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.AtomicFile;
import androidx.exifinterface.media.ExifInterface;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

final class ScanDraft {
    static final class Page {
        String id = UUID.randomUUID().toString();
        int revision = 1, rotation = 0;
        String original, processed = "", warning = "";
        boolean textUnreadable=false,flipH=false,flipV=false;
        float cropAngle=0;
        float[] crop;
        transient List<OnnxOcr.Line> lines;
        JSONObject json() throws JSONException {
            JSONObject o = new JSONObject().put("id",id).put("revision",revision).put("rotation",rotation)
                .put("flip_h",flipH).put("flip_v",flipV).put("crop_angle",cropAngle).put("text_unreadable",textUnreadable).put("original",original).put("processed",processed).put("warning",warning);
            if(crop!=null){ JSONArray a=new JSONArray();for(float v:crop)a.put(v);o.put("crop",a); }
            return o;
        }
        static Page from(JSONObject o) throws JSONException {
            Page p=new Page();p.id=o.getString("id");p.revision=o.getInt("revision");
            p.rotation=o.optInt("rotation");p.original=o.getString("original");
            p.flipH=o.optBoolean("flip_h");p.flipV=o.optBoolean("flip_v");p.cropAngle=(float)o.optDouble("crop_angle",0);p.textUnreadable=o.optBoolean("text_unreadable");p.processed=o.optString("processed");p.warning=o.optString("warning");
            JSONArray a=o.optJSONArray("crop");if(a!=null){p.crop=new float[8];for(int i=0;i<8;i++)p.crop[i]=(float)a.getDouble(i);}
            return p;
        }
    }
    static final class Options {
        boolean freeform=false,galleryOnly=false;
        boolean multiple=false, idScan=false, auto=true, numbers=false, landscape=false, retain=false;
        int expected=1, appearance=3;
        int cleanerWhitening=100,cleanerShadows=50,cleanerInk=75,cleanerColors=60;
        int cleanerBrightness=0,cleanerExposure=0,cleanerContrast=0,cleanerSharpness=0,cleanerBinaryInk=50;
        String paper="A4", watermark="", cameraId="", exportFormat="PDF";
        boolean searchableDefault=true,idOnePage=false;
        float width=210,height=297,margin=8,strength=0;
        JSONObject json() throws JSONException {
            return new JSONObject().put("freeform",freeform).put("gallery_only",galleryOnly).put("id_one_page",idOnePage).put("multiple",multiple).put("id_scan",idScan).put("document_modes_v2",true).put("expected",expected).put("auto",auto)
                .put("paper",paper).put("width",width).put("height",height).put("landscape",landscape)
                .put("cleaner_brightness",cleanerBrightness).put("cleaner_exposure",cleanerExposure).put("cleaner_contrast",cleanerContrast).put("cleaner_sharpness",cleanerSharpness).put("cleaner_binary_ink",cleanerBinaryInk).put("cleaner_whitening",cleanerWhitening).put("cleaner_shadows",cleanerShadows).put("cleaner_ink",cleanerInk).put("cleaner_colors",cleanerColors).put("appearance",appearance).put("margin",margin).put("strength",strength)
                .put("watermark",watermark).put("numbers",numbers).put("retain",retain).put("camera_id",cameraId).put("export_format",exportFormat).put("searchable_default",searchableDefault);
        }
        static Options from(JSONObject o) {
            Options c=new Options();if(o==null)return c;
            c.cleanerBrightness=DocumentTone.signed(o.optInt("cleaner_brightness",0),50);c.cleanerExposure=DocumentTone.signed(o.optInt("cleaner_exposure",0),100);c.cleanerContrast=DocumentTone.signed(o.optInt("cleaner_contrast",0),50);c.cleanerSharpness=PaperCleanup.clamp(o.optInt("cleaner_sharpness",0));c.cleanerBinaryInk=PaperCleanup.clamp(o.optInt("cleaner_binary_ink",50));
            c.cleanerWhitening=PaperCleanup.clamp(o.optInt("cleaner_whitening",100));c.cleanerShadows=PaperCleanup.clamp(o.optInt("cleaner_shadows",50));c.cleanerInk=PaperCleanup.clamp(o.optInt("cleaner_ink",75));c.cleanerColors=PaperCleanup.clamp(o.optInt("cleaner_colors",60));
            c.freeform=o.optBoolean("freeform");c.galleryOnly=o.optBoolean("gallery_only");c.idOnePage=o.optBoolean("id_one_page");c.idScan=o.optBoolean("id_scan");c.multiple=o.optBoolean("multiple");c.expected=o.optInt("expected",1);c.auto=o.optBoolean("auto",true);
            c.paper=o.optString("paper","A4");c.width=(float)o.optDouble("width",210);c.height=(float)o.optDouble("height",297);
            c.landscape=o.optBoolean("landscape");c.appearance=o.optInt("appearance",3);
            c.margin=(float)o.optDouble("margin",8);c.strength=Math.max(0,Math.min(.65f,(float)o.optDouble("strength",0)));
            c.cameraId=o.optString("camera_id");c.exportFormat=o.optString("export_format","PDF");c.searchableDefault=o.optBoolean("searchable_default",true);c.watermark=o.optString("watermark");c.numbers=o.optBoolean("numbers");c.retain=o.optBoolean("retain");return c;
        }
        float[] size() {
            if(idScan)return new float[]{85.6f,53.98f};
            float w=width,h=height;
            switch(paper){case "A4":w=210;h=297;break;case "A5":w=148;h=210;break;case "Letter":w=215.9f;h=279.4f;break;case "Legal":w=215.9f;h=355.6f;break;}
            float a=Math.min(w,h),b=Math.max(w,h);return landscape?new float[]{b,a}:new float[]{a,b};
        }
        boolean valid(){float[] s=size();return Math.min(s[0],s[1])>=40&&Math.max(s[0],s[1])<=600&&margin>=0&&(idScan||margin*2<Math.min(s[0],s[1]))&&appearance>=0&&appearance<=3&&(!idScan||(multiple&&expected==2))&&expected>=1&&expected<=100;}
    }
    final Context context;
    String id=UUID.randomUUID().toString(), scannedDate="", title="", filename="", documentDate="", format="PDF", stage="home";
    int textPageCount=0;
    boolean textVerified=false, titleEdited=false, exported=false, searchable=true, pdfSearchable=true;
    Options options;
    ArrayList<Page> pages=new ArrayList<>();
    ArrayList<String> outputUris=new ArrayList<>(), outputNames=new ArrayList<>(), outputHashes=new ArrayList<>();
    File folder;
    ScanDraft(Context c,Options config){context=c.getApplicationContext();options=config;format="PDF";searchable=true;folder=new File(context.getFilesDir(),"drafts/"+id);folder.mkdirs();}
    int outputPageCount(){return DocumentLayout.pageCount(options.idScan,options.idOnePage,pages.size());}
    File file(String name){return new File(folder,name);}
    synchronized void save() throws Exception {
        if(exported){clearActive(context,id);return;}
        JSONArray pp=new JSONArray();for(Page p:pages)pp.put(p.json());
        JSONObject o=new JSONObject().put("id",id).put("scanned_date",scannedDate).put("title",title)
            .put("filename",filename).put("document_date",documentDate).put("title_edited",titleEdited)
            .put("text_page_count",textPageCount).put("text_verified",textVerified).put("pdf_searchable",pdfSearchable).put("format",format).put("stage",stage).put("exported",exported).put("searchable",searchable)
            .put("options",options.json()).put("pages",pp)
            .put("output_uris",new JSONArray(outputUris)).put("output_names",new JSONArray(outputNames)).put("output_hashes",new JSONArray(outputHashes));
        AtomicFile a=new AtomicFile(new File(context.getFilesDir(),"current.json"));
        FileOutputStream out=null;
        try{out=a.startWrite();out.write(o.toString().getBytes(StandardCharsets.UTF_8));a.finishWrite(out);}
        catch(Exception e){if(out!=null)a.failWrite(out);throw e;}
    }
    static void clearActive(Context c,String session)throws IOException {
        AtomicFile journal=new AtomicFile(new File(c.getFilesDir(),"current.json"));
        if(!journal.getBaseFile().exists())return;
        try{JSONObject current=new JSONObject(new String(read(journal.getBaseFile()),StandardCharsets.UTF_8));if(session.equals(current.optString("id")))journal.delete();}
        catch(org.json.JSONException e){throw new IOException("DRAFT_CLEAR_FAILED",e);}
    }
    static ScanDraft load(Context c) {
        try {
            File f=new File(c.getFilesDir(),"current.json");if(!f.exists())return null;
            JSONObject o=new JSONObject(new String(read(f),StandardCharsets.UTF_8));
            ScanDraft d=new ScanDraft(c,Options.from(o.getJSONObject("options")));
            File empty=d.folder;d.id=o.getString("id");d.folder=new File(c.getFilesDir(),"drafts/"+d.id);
            if(!empty.equals(d.folder))empty.delete();
            d.scannedDate=o.optString("scanned_date");d.title=o.optString("title");d.filename=o.optString("filename");
            d.documentDate=o.optString("document_date");d.titleEdited=o.optBoolean("title_edited");d.exported=o.optBoolean("exported");
            d.textPageCount=o.optInt("text_page_count");d.textVerified=o.optBoolean("text_verified");d.pdfSearchable=o.optBoolean("pdf_searchable",true);d.searchable=o.optBoolean("searchable",true);d.format=o.optString("format","PDF");d.stage=o.optString("stage","home");
            JSONArray a=o.getJSONArray("pages");for(int i=0;i<a.length();i++)d.pages.add(Page.from(a.getJSONObject(i)));
            for(int i=0;i<o.optJSONArray("output_uris").length();i++)d.outputUris.add(o.getJSONArray("output_uris").getString(i));
            JSONArray n=o.optJSONArray("output_names");if(n!=null)for(int i=0;i<n.length();i++)d.outputNames.add(n.getString(i));
            JSONArray hashes=o.optJSONArray("output_hashes");if(hashes!=null)for(int i=0;i<hashes.length();i++)d.outputHashes.add(hashes.getString(i));
            if("process".equals(d.stage)){d.stage="review";d.save();}
            return d;
        }catch(Exception e){return null;}
    }
    synchronized Page add(Bitmap b,Page replace) throws Exception {
        if(replace==null&&pages.size()>=(options.idScan?2:options.multiple?100:1))throw new IOException("PAGE_LIMIT");
        Page candidate=new Page();
        if(replace!=null){candidate.id=replace.id;candidate.revision=replace.revision+1;}
        candidate.original=candidate.id+"-"+candidate.revision+"-original.jpg";
        writeBitmap(file(candidate.original),b,Bitmap.CompressFormat.JPEG,98);
        Vision.Result q=Vision.analyze(b);candidate.crop=q.corners;candidate.warning=q.message;
        Page p=candidate;
        if(replace==null)pages.add(candidate);
        else {replace.revision=candidate.revision;replace.original=candidate.original;replace.crop=candidate.crop;
              replace.warning=candidate.warning;replace.rotation=0;replace.flipH=false;replace.flipV=false;replace.cropAngle=0;replace.processed="";replace.lines=null;replace.textUnreadable=false;p=replace;}
        if(scannedDate.isEmpty())scannedDate=LocalDate.now(ZoneId.of("Asia/Taipei")).toString();
        stage="review";save();return p;
    }
    void invalidate(Page p){p.revision++;p.lines=null;p.textUnreadable=false;p.warning="";p.processed="";stage="review";}
    void remember(){try{context.getSharedPreferences("settings",0).edit().putString("options",options.json().toString()).apply();}catch(Exception ignored){}}
    static Options remembered(Context c){try{JSONObject json=new JSONObject(c.getSharedPreferences("settings",0).getString("options","{}"));Options o=Options.from(json);if(!json.optBoolean("document_modes_v2")){o.appearance=3;o.strength=0;}return o;}catch(Exception e){return new Options();}}
    static byte[] read(File f) throws IOException {try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    static void writeBitmap(File f,Bitmap bitmap,Bitmap.CompressFormat format,int quality) throws IOException {
        File part=new File(f.getParentFile(),f.getName()+".part");
        try(FileOutputStream out=new FileOutputStream(part)){if(!bitmap.compress(format,quality,out))throw new IOException("IMAGE_ENCODE");out.getFD().sync();}
        if(!part.renameTo(f))throw new IOException("IMAGE_WRITE");
    }
    static Bitmap decode(File f,int max) throws IOException {
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
        if(o.outWidth<=0||o.outHeight<=0||(long)o.outWidth*o.outHeight>80000000L)throw new IOException("INVALID_IMAGE");
        o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>max*1.5)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap b=BitmapFactory.decodeFile(f.getPath(),o);if(b==null)throw new IOException("INVALID_IMAGE");
        ExifInterface exif=new ExifInterface(f);Matrix m=new Matrix();
        int orient=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,1);
        switch(orient){case 2:m.setScale(-1,1);break;case 3:m.postRotate(180);break;case 4:m.setScale(1,-1);break;
        case 5:m.setScale(-1,1);m.postRotate(90);break;case 6:m.postRotate(90);break;case 7:m.setScale(-1,1);m.postRotate(270);break;case 8:m.postRotate(270);break;}
        if(!m.isIdentity()){Bitmap r=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(r!=b)b.recycle();b=r;}
        if(Math.max(b.getWidth(),b.getHeight())>max){float s=max/(float)Math.max(b.getWidth(),b.getHeight());Bitmap r=Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*s),Math.round(b.getHeight()*s),true);b.recycle();b=r;}
        return b;
    }
    static void cleanOld(Context c) {
        File root=new File(c.getFilesDir(),"drafts");File[] folders=root.listFiles();if(folders==null)return;
        ScanDraft current=load(c);
        File journal=new File(c.getFilesDir(),"current.json");
        if(current!=null&&System.currentTimeMillis()-journal.lastModified()>(current.exported&&current.options.retain?30L:7L)*86400000){
            deleteTree(current.folder);journal.delete();current=null;
        }
        for(File f:folders){if(current!=null&&f.equals(current.folder))continue;long days=new File(f,".retention").exists()?30L:7L;if(System.currentTimeMillis()-f.lastModified()>days*86400000)deleteTree(f);}
        File share=new File(c.getCacheDir(),"share");File[] files=share.listFiles();if(files!=null)for(File f:files)if(System.currentTimeMillis()-f.lastModified()>86400000L)f.delete();
    }
    static void deleteTree(File f){File[] children=f.listFiles();if(children!=null)for(File p:children)deleteTree(p);f.delete();}
}

