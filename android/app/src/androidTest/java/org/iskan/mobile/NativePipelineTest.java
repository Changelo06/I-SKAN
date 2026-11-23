package org.iskan.mobile;
import android.content.*;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NativePipelineTest {
 Context context;ScanDraft draft;Exporter exporter;
 @Before public void setup(){
  Context test=InstrumentationRegistry.getInstrumentation().getContext(),target=InstrumentationRegistry.getInstrumentation().getTargetContext();
  context=new ContextWrapper(test){@Override public Context getApplicationContext(){return this;}@Override public AssetManager getAssets(){return target.getAssets();}};
  Vision.init();exporter=new Exporter(context);
 }
 Bitmap fixture()throws Exception{
  try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("sample.png")){return BitmapFactory.decodeStream(in);}
 }
 int logs(){try(Exporter.Receipts h=new Exporter.Receipts(context);Cursor c=h.getReadableDatabase().rawQuery("SELECT count(*) FROM scan_logs",null)){c.moveToFirst();return c.getInt(0);}}
 @After public void cleanup(){
  if(draft!=null){for(String u:draft.outputUris)try{context.getContentResolver().delete(Uri.parse(u),null,null);}catch(Exception ignored){}
   try(Exporter.Receipts h=new Exporter.Receipts(context)){h.getWritableDatabase().delete("scan_logs","title=?",new String[]{draft.title});h.getWritableDatabase().delete("receipts","session=?",new String[]{draft.id});h.getWritableDatabase().delete("saved_outputs","session=?",new String[]{draft.id});}
   ScanDraft.deleteTree(draft.folder);new File(context.getFilesDir(),"current.json").delete();}
 }
 @Test public void realOnnxPdfAndAtomicPhoneExport()throws Exception{
  int before=logs();ScanDraft.Options options=new ScanDraft.Options();options.strength=0;options.numbers=true;draft=new ScanDraft(context,options);
  Bitmap b=fixture();ScanDraft.Page p=draft.add(b,null);Bitmap cleaned=Vision.process(b,p,options);b.recycle();
  try(OnnxOcr ocr=new OnnxOcr(context)){p.lines=ocr.recognize(cleaned);}
  assertTrue(p.lines.stream().anyMatch(line->line.text.contains("MEETING")));
  p.processed="test-processed.png";ScanDraft.writeBitmap(draft.file(p.processed),cleaned,Bitmap.CompressFormat.PNG,100);cleaned.recycle();
  draft.title="Fixture "+draft.id;draft.filename=draft.title;exporter.compose(draft);
  try(PDDocument pdf=PDDocument.load(draft.file("document.pdf"))){assertEquals(1,pdf.getNumberOfPages());assertTrue(new PDFTextStripper().getText(pdf).contains("MEETING"));}
  Bitmap preview=Exporter.preview(draft,0,1200);assertTrue(preview.getHeight()>preview.getWidth());preview.recycle();
  String raw=new String(ScanDraft.read(new File(context.getFilesDir(),"current.json")),java.nio.charset.StandardCharsets.UTF_8);assertFalse(raw.contains("MEETING MINUTES"));
  exporter.save(draft);assertTrue(exporter.verify(draft));assertEquals(before+1,logs());java.util.ArrayList<String> firstSaved=new java.util.ArrayList<>(draft.outputUris);exporter.save(draft);assertEquals(firstSaved,draft.outputUris);assertEquals(before+1,logs());
  try(Exporter.Receipts h=new Exporter.Receipts(context);Cursor c=h.getReadableDatabase().rawQuery("PRAGMA table_info(scan_logs)",null)){ArrayList<String> names=new ArrayList<>();while(c.moveToNext())names.add(c.getString(1));assertEquals(Arrays.asList("title","page_count","scanned_date"),names);}
  assertFalse(new File(context.getFilesDir(),"current.json").exists());assertNull(ScanDraft.load(context));assertTrue(ScanHistory.receipt(context,draft.id));assertTrue(ScanHistory.read(context,0).stream().anyMatch(entry->entry.name.equals(draft.outputNames.get(0))&&entry.uris.size()==1));
 }
 @Test public void replacementKeepsIdentityOrderAndFirstCaptureDate()throws Exception{
  ScanDraft.Options o=new ScanDraft.Options();o.multiple=true;o.expected=2;draft=new ScanDraft(context,o);Bitmap b=fixture();
  ScanDraft.Page a=draft.add(b,null),second=draft.add(b,null);String id=a.id,date=draft.scannedDate;int rev=a.revision;draft.add(b,a);
  assertEquals(id,draft.pages.get(0).id);assertSame(second,draft.pages.get(1));assertEquals(rev+1,a.revision);assertEquals(date,draft.scannedDate);
  Collections.swap(draft.pages,0,1);draft.save();ScanDraft loaded=ScanDraft.load(context);assertEquals(second.id,loaded.pages.get(0).id);assertNull(loaded.pages.get(0).lines);b.recycle();
 }
 @Test public void incompleteImageSetCannotBeCommittedOnRecovery()throws Exception{
  int before=logs();ScanDraft.Options o=new ScanDraft.Options();o.multiple=true;o.expected=2;draft=new ScanDraft(context,o);Bitmap b=fixture();draft.add(b,null);draft.add(b,null);b.recycle();draft.title="Fixture "+draft.id;draft.format="PNG";
  ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,draft.id+".png");values.put(MediaStore.MediaColumns.MIME_TYPE,"image/png");values.put(MediaStore.MediaColumns.RELATIVE_PATH,Exporter.RELATIVE);values.put(MediaStore.MediaColumns.IS_PENDING,1);
  Uri uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
  try(OutputStream out=context.getContentResolver().openOutputStream(uri)){out.write(new byte[]{1,2,3});}
  draft.outputUris.add(uri.toString());draft.outputNames.add(draft.id+".png");draft.outputHashes.add(Exporter.hash(context.getContentResolver().openInputStream(uri)));draft.save();
  assertFalse(exporter.verify(draft));exporter.recover(draft);assertTrue(draft.outputUris.isEmpty());assertEquals(before,logs());
 }
 @Test public void cleanPaperKeepsInkAndStampWhileWhiteningBackground()throws Exception{
  Bitmap b=Bitmap.createBitmap(600,400,Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(220,220,220));
  Canvas c=new Canvas(b);Paint ink=new Paint();ink.setColor(Color.rgb(190,190,190));c.drawRect(100,100,200,104,ink);
  ink.setColor(Color.rgb(20,50,190));c.drawRect(250,100,280,130,ink);
  ScanDraft.Options o=new ScanDraft.Options();ScanDraft.Page p=new ScanDraft.Page();Bitmap cleaned=Vision.process(b,p,o);
  assertEquals(Color.WHITE,cleaned.getPixel(500,300));assertEquals(b.getPixel(150,102),cleaned.getPixel(150,102));assertEquals(b.getPixel(260,110),cleaned.getPixel(260,110));
  o.appearance=0;o.strength=.65f;Bitmap original=Vision.process(b,p,o);assertTrue(b.sameAs(original));original.recycle();cleaned.recycle();b.recycle();
 }
 @Test public void binaryPdfStaysLosslessAndSelectable()throws Exception{
  ScanDraft.Options o=new ScanDraft.Options();o.appearance=2;draft=new ScanDraft(context,o);draft.title="Fixture "+draft.id;
  Bitmap b=fixture();ScanDraft.Page p=draft.add(b,null);p.crop=null;Bitmap bw=Vision.process(b,p,o);b.recycle();
  int[] pixels=new int[bw.getWidth()*bw.getHeight()];bw.getPixels(pixels,0,bw.getWidth(),0,0,bw.getWidth(),bw.getHeight());
  for(int pixel:pixels)assertTrue(pixel==Color.BLACK||pixel==Color.WHITE);
  p.processed="binary.png";ScanDraft.writeBitmap(draft.file(p.processed),bw,Bitmap.CompressFormat.PNG,100);
  p.lines=Collections.singletonList(new OnnxOcr.Line(new float[]{10,10,300,10,300,50,10,50},"Selectable document",.99));
  exporter.compose(draft);assertTrue(draft.textVerified);assertEquals(1,draft.textPageCount);
  try(PDDocument pdf=PDDocument.load(draft.file("document.pdf"))){
   assertTrue(new PDFTextStripper().getText(pdf).contains("Selectable document"));
   for(com.tom_roush.pdfbox.cos.COSName name:pdf.getPage(0).getResources().getXObjectNames()){
    com.tom_roush.pdfbox.pdmodel.graphics.PDXObject x=pdf.getPage(0).getResources().getXObject(name);
    if(x instanceof com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject)assertTrue(bw.sameAs(((com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject)x).getImage()));
   }
  }
  draft.searchable=false;exporter.compose(draft);assertFalse(draft.textVerified);
  try(PDDocument pdf=PDDocument.load(draft.file("document.pdf"))){assertTrue(new PDFTextStripper().getText(pdf).trim().isEmpty());}
  bw.recycle();
 }
 @Test public void idSidesPersistAndThirdSideIsRejected()throws Exception{
  ScanDraft.Options o=new ScanDraft.Options();o.idScan=true;o.multiple=true;o.expected=2;o.appearance=0;draft=new ScanDraft(context,o);
  Bitmap b=fixture();draft.add(b,null);draft.add(b,null);boolean rejected=false;try{draft.add(b,null);}catch(IOException e){rejected=true;}assertTrue(rejected);
  ScanDraft loaded=ScanDraft.load(context);assertTrue(loaded.options.idScan);assertEquals(2,loaded.pages.size());assertEquals(0,loaded.options.appearance);b.recycle();
 }

 @Test public void detectedCornersOutlineTheWholePageAndBlankHasNoGuess(){
  Bitmap b=Bitmap.createBitmap(800,1000,Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(35,40,38));
  Canvas canvas=new Canvas(b);Paint p=new Paint();p.setColor(Color.WHITE);canvas.drawRect(100,120,700,880,p);
  p.setColor(Color.BLACK);for(int y=210;y<800;y+=45)canvas.drawRect(160,y,620,y+6,p);
  float[] corners=Vision.analyze(b).corners;assertNotNull(corners);assertTrue(Vision.validCrop(corners));
  assertEquals(.125f,corners[0],.03f);assertEquals(.12f,corners[1],.03f);assertEquals(.875f,corners[4],.03f);assertEquals(.88f,corners[5],.03f);
  b.eraseColor(Color.WHITE);assertNull(Vision.analyze(b).corners);b.recycle();
 }
 @Test public void autoCropPreservesRotationAndFailureKeepsManualSelection(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   CropView view=new CropView(InstrumentationRegistry.getInstrumentation().getTargetContext());
   Bitmap b=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888);float[] manual={0,0,1,0,1,1,0,1},detected={.1f,.1f,.9f,.1f,.9f,.9f,.1f,.9f};
   view.setPage(b,manual,90);view.setDetected(detected);assertArrayEquals(manual,view.crop(),0);assertTrue(view.applyDetected());assertArrayEquals(detected,view.crop(),0);
   detected[0]=.3f;assertEquals(.1f,view.crop()[0],0);view.setDetected(null);assertFalse(view.applyDetected());assertEquals(.1f,view.crop()[0],0);b.recycle();
  });
 }

 @Test public void customCleanerSurvivesRestartAndChangesRenderedPage()throws Exception{
  ScanDraft.Options o=new ScanDraft.Options();o.cleanerWhitening=0;o.cleanerShadows=80;o.cleanerInk=95;o.cleanerColors=90;
  draft=new ScanDraft(context,o);Bitmap original=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888);original.eraseColor(Color.rgb(180,180,180));
  ScanDraft.Page p=draft.add(original,null);p.crop=null;draft.save();ScanDraft loaded=ScanDraft.load(context);
  assertEquals(0,loaded.options.cleanerWhitening);assertEquals(80,loaded.options.cleanerShadows);assertEquals(95,loaded.options.cleanerInk);assertEquals(90,loaded.options.cleanerColors);
  Bitmap unchanged=Vision.process(original,p,loaded.options);assertTrue(original.sameAs(unchanged));unchanged.recycle();
  loaded.options.cleanerWhitening=50;Bitmap medium=Vision.process(original,p,loaded.options);assertEquals(Color.rgb(218,218,218),medium.getPixel(200,300));medium.recycle();
  loaded.options.cleanerWhitening=100;Bitmap white=Vision.process(original,p,loaded.options);assertEquals(Color.WHITE,white.getPixel(200,300));white.recycle();original.recycle();
 }

 @Test public void exposureFeedbackRejectsClippedFrameAndDarkFrame(){
  Bitmap b=Bitmap.createBitmap(200,300,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);Vision.Result q=Vision.analyze(b);assertEquals("Too bright",q.message);assertFalse(q.ready);
  b.eraseColor(Color.rgb(30,30,30));q=Vision.analyze(b);assertEquals("Too dark",q.message);assertFalse(q.ready);b.recycle();
 }

 @Test public void idPrintChoicesProduceCorrectPdfAndImageCounts()throws Exception{
  ScanDraft.Options o=new ScanDraft.Options();o.idScan=true;o.multiple=true;o.expected=2;o.idOnePage=true;draft=new ScanDraft(context,o);draft.title="Fixture "+draft.id;
  for(String side:Arrays.asList("FRONT","BACK")){
   Bitmap card=Bitmap.createBitmap(856,540,Bitmap.Config.ARGB_8888);card.eraseColor(Color.WHITE);Canvas c=new Canvas(card);Paint pen=new Paint();pen.setColor(Color.BLACK);pen.setTextSize(50);c.drawText(side,30,100,pen);
   ScanDraft.Page p=draft.add(card,null);p.crop=null;p.processed=side+".png";ScanDraft.writeBitmap(draft.file(p.processed),card,Bitmap.CompressFormat.PNG,100);p.lines=Collections.singletonList(new OnnxOcr.Line(new float[]{30,45,320,45,320,110,30,110},side,.99));card.recycle();
  }
  exporter.compose(draft);assertEquals(1,draft.outputPageCount());try(PDDocument pdf=PDDocument.load(draft.file("document.pdf"))){assertEquals(1,pdf.getNumberOfPages());String text=new PDFTextStripper().getText(pdf);assertTrue(text.contains("FRONT"));assertTrue(text.contains("BACK"));assertEquals(210*72/25.4f,pdf.getPage(0).getMediaBox().getWidth(),.01f);}
  o.idOnePage=false;exporter.compose(draft);try(PDDocument pdf=PDDocument.load(draft.file("document.pdf"))){assertEquals(2,pdf.getNumberOfPages());}
  o.idOnePage=true;exporter.compose(draft);draft.format="PNG";exporter.save(draft);assertEquals(1,draft.outputUris.size());assertTrue(exporter.verify(draft));
 }
 @Test public void roundedIdEdgesCanBeDetected(){
  Bitmap b=Bitmap.createBitmap(900,700,Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(25,30,25));Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);c.drawRoundRect(140,155,760,545,28,28,p);float[] points=Vision.detectCorners(b,true);assertNotNull(points);assertTrue(Vision.validCrop(points));assertEquals(140/900f,points[0],.04f);assertEquals(545/700f,points[5],.04f);b.recycle();
 }

 @Test public void unreadablePageStillExportsBesideReadableText()throws Exception{
  ScanDraft.Options o=new ScanDraft.Options();o.multiple=true;o.expected=2;draft=new ScanDraft(context,o);draft.title="Fixture "+draft.id;
  Bitmap b=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);
  for(int i=0;i<2;i++){ScanDraft.Page p=draft.add(b,null);p.crop=null;p.processed="readability-"+i+".png";ScanDraft.writeBitmap(draft.file(p.processed),b,Bitmap.CompressFormat.PNG,100);
   if(i==0)TextRecognition.attempt(p,()->Collections.singletonList(new OnnxOcr.Line(new float[]{10,10,200,10,200,40,10,40},"Readable",.99)));
   else TextRecognition.attempt(p,()->{throw new java.io.IOException("OCR failed");});
  }b.recycle();exporter.compose(draft);assertTrue(draft.textVerified);assertEquals(1,draft.textPageCount);
  try(PDDocument pdf=PDDocument.load(draft.file("document.pdf"))){assertEquals(2,pdf.getNumberOfPages());PDFTextStripper reader=new PDFTextStripper();reader.setStartPage(1);reader.setEndPage(1);assertTrue(reader.getText(pdf).contains("Readable"));reader.setStartPage(2);reader.setEndPage(2);assertTrue(reader.getText(pdf).trim().isEmpty());assertTrue(pdf.getPage(1).getResources().getXObjectNames().iterator().hasNext());}
  draft.save();assertTrue(ScanDraft.load(context).pages.get(1).textUnreadable);draft.format="PNG";exporter.save(draft);assertEquals(2,draft.outputUris.size());assertTrue(exporter.verify(draft));draft.invalidate(draft.pages.get(1));assertFalse(draft.pages.get(1).textUnreadable);
 }

 @Test public void releasingNearDetectedCornerDoesNotSnapBack(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();CropView view=new CropView(target);Bitmap b=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888);float[] points={.1f,.1f,.9f,.1f,.9f,.9f,.1f,.9f};view.setPage(b,points,0);view.setDetected(points);view.layout(0,0,600,800);
   float density=target.getResources().getDisplayMetrics().density,scale=Math.min((600-32*density)/400,(800-32*density)/600),x=(600-400*scale)/2+40*scale,y=(800-600*scale)/2+60*scale;long now=android.os.SystemClock.uptimeMillis();
   android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,x,y,0),move=android.view.MotionEvent.obtain(now,now+20,2,x+8*scale,y+6*scale,0),up=android.view.MotionEvent.obtain(now,now+40,1,x+8*scale,y+6*scale,0);view.onTouchEvent(down);view.onTouchEvent(move);view.onTouchEvent(up);down.recycle();move.recycle();up.recycle();assertEquals(.12f,view.crop()[0],.001f);assertEquals(.11f,view.crop()[1],.001f);
   float[] saved=view.crop();view.setPage(b,saved,0);view.setDetected(points);assertArrayEquals(saved,view.crop(),0);b.recycle();
  });
 }
 @Test public void flipsAndQuarterTurnMatchProcessedPixelsAndPersist()throws Exception{
  ScanDraft.Options o=new ScanDraft.Options();o.appearance=0;draft=new ScanDraft(context,o);Bitmap b=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint pen=new Paint();int[] colors={Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW};for(int i=0;i<4;i++){pen.setColor(colors[i]);c.drawRect((i%2)*60,(i/2)*40,(i%2+1)*60,(i/2+1)*40,pen);}
  ScanDraft.Page p=draft.add(b,null);p.crop=null;p.flipH=true;p.rotation=90;p.cropAngle=-2.5f;Bitmap result=Vision.process(b,p,o);assertEquals(80,result.getWidth());assertEquals(120,result.getHeight());assertEquals(Color.YELLOW,result.getPixel(5,5));assertEquals(Color.GREEN,result.getPixel(75,5));assertEquals(Color.RED,result.getPixel(75,115));draft.save();ScanDraft.Page restored=ScanDraft.load(context).pages.get(0);assertTrue(restored.flipH);assertEquals(90,restored.rotation);assertEquals(-2.5f,restored.cropAngle,.001f);b.recycle();result.recycle();
 }

 @Test public void rulerRespondsToSwipeAndClampsAngles(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();AngleRuler dial=new AngleRuler(target);float density=target.getResources().getDisplayMetrics().density;dial.layout(0,0,600,Math.round(108*density));float[] applied={0};boolean[] finished={false};dial.listener=(value,done)->{applied[0]=value;finished[0]=done;};long now=android.os.SystemClock.uptimeMillis();float cy=dial.getHeight()-8*density,y=cy-60*density,x=300-45*density;
   android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,300,y,0),move=android.view.MotionEvent.obtain(now,now+20,2,x,y,0),up=android.view.MotionEvent.obtain(now,now+40,1,x,y,0);dial.onTouchEvent(down);dial.onTouchEvent(move);assertFalse(finished[0]);assertEquals(5,applied[0],.1f);dial.onTouchEvent(up);assertTrue(finished[0]);down.recycle();move.recycle();up.recycle();dial.setAngle(90);assertEquals(45,dial.angle(),0);dial.setAngle(-90);assertEquals(-45,dial.angle(),0);
   dial.setAngle(0);down=android.view.MotionEvent.obtain(now,now,0,300,y,0);move=android.view.MotionEvent.obtain(now,now+20,2,x,y,0);android.view.MotionEvent cancel=android.view.MotionEvent.obtain(now,now+40,3,x,y,0);dial.onTouchEvent(down);dial.onTouchEvent(move);dial.onTouchEvent(cancel);assertEquals(0,applied[0],0);assertTrue(finished[0]);down.recycle();move.recycle();cancel.recycle();
   android.os.Bundle progress=new android.os.Bundle();progress.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,12.5f);assertTrue(dial.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),progress));assertEquals(12.5f,applied[0],0);
  });
 }
 @Test public void systemNightResourcesHaveReadableSurfaceText(){
  Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
  for(int mode:new int[]{android.content.res.Configuration.UI_MODE_NIGHT_NO,android.content.res.Configuration.UI_MODE_NIGHT_YES}){
   android.content.res.Configuration config=new android.content.res.Configuration(target.getResources().getConfiguration());config.uiMode=(config.uiMode&~android.content.res.Configuration.UI_MODE_NIGHT_MASK)|mode;Context themed=target.createConfigurationContext(config);int text=themed.getColor(R.color.app_text),surface=themed.getColor(R.color.app_surface);assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(text,surface)>=7);assertEquals(mode==android.content.res.Configuration.UI_MODE_NIGHT_NO,themed.getResources().getBoolean(R.bool.light_system_bars));
  }
 }

 @Test public void freeformGallerySourceAndAddedPagesSurviveRestart()throws Exception{
  ScanDraft.Options o=new ScanDraft.Options();ScanFlow.configure(o,1);draft=new ScanDraft(context,o);Bitmap b=Bitmap.createBitmap(80,120,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);try{for(int i=0;i<3;i++)draft.add(b,null);}finally{b.recycle();}draft.save();ScanDraft loaded=ScanDraft.load(context);assertNotNull(loaded);assertTrue(loaded.options.freeform);assertTrue(loaded.options.galleryOnly);assertEquals(3,loaded.pages.size());assertFalse(ScanFlow.needsMore(loaded.options,loaded.pages.size()));
 }

 @Test public void originalColorBypassesEveryCleanerControl(){Bitmap b=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint pen=new Paint();for(int x=0;x<120;x++){pen.setColor(Color.rgb(x*2,80,200-x));c.drawLine(x,0,x,80,pen);}ScanDraft.Options o=new ScanDraft.Options();o.appearance=0;o.cleanerSharpness=100;o.cleanerBrightness=40;o.cleanerExposure=75;o.cleanerContrast=40;o.cleanerBinaryInk=100;Bitmap result=Vision.process(b,new ScanDraft.Page(),o);assertTrue(b.sameAs(result));result.recycle();b.recycle();}
 @Test public void expandedCleanerPersistsAndBrightensGray()throws Exception{ScanDraft.Options o=new ScanDraft.Options();o.appearance=1;o.cleanerWhitening=0;o.cleanerBrightness=10;o.cleanerExposure=25;o.cleanerContrast=5;o.cleanerSharpness=35;o.cleanerBinaryInk=80;draft=new ScanDraft(context,o);Bitmap b=Bitmap.createBitmap(100,140,Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(110,110,110));ScanDraft.Page p=draft.add(b,null);p.crop=null;draft.save();ScanDraft loaded=ScanDraft.load(context);assertEquals(10,loaded.options.cleanerBrightness);assertEquals(25,loaded.options.cleanerExposure);assertEquals(5,loaded.options.cleanerContrast);assertEquals(35,loaded.options.cleanerSharpness);assertEquals(80,loaded.options.cleanerBinaryInk);Bitmap result=Vision.process(b,p,loaded.options);int pixel=result.getPixel(50,70);assertEquals(Color.red(pixel),Color.green(pixel));assertEquals(Color.green(pixel),Color.blue(pixel));assertTrue(Color.red(pixel)>110);result.recycle();b.recycle();}
 @Test public void documentBinaryKeepsRulesAndWhitesShadowedPaper(){Bitmap b=Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint pen=new Paint();for(int y=0;y<800;y++){int light=140+y/8;pen.setColor(Color.rgb(light,light,light));c.drawRect(0,y,600,y+1,pen);}pen.setColor(Color.rgb(25,25,25));c.drawRect(80,120,520,123,pen);c.drawRect(200,180,203,650,pen);ScanDraft.Options o=new ScanDraft.Options();o.appearance=2;o.cleanerShadows=65;o.cleanerSharpness=25;o.cleanerContrast=15;o.cleanerBinaryInk=60;Bitmap result=Vision.process(b,new ScanDraft.Page(),o);assertEquals(Color.WHITE,result.getPixel(550,60));assertEquals(Color.WHITE,result.getPixel(550,740));assertEquals(Color.BLACK,result.getPixel(250,121));assertEquals(Color.BLACK,result.getPixel(201,300));int[] pixels=new int[600*800];result.getPixels(pixels,0,600,0,0,600,800);for(int pixel:pixels)assertTrue(pixel==Color.BLACK||pixel==Color.WHITE);result.recycle();b.recycle();}

 @Test public void closingOldCompletedSessionCannotClearNewDraft()throws Exception{
  ScanDraft old=new ScanDraft(context,new ScanDraft.Options());old.save();draft=new ScanDraft(context,new ScanDraft.Options());Bitmap b=Bitmap.createBitmap(80,120,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);draft.add(b,null);b.recycle();old.exported=true;old.save();ScanDraft restored=ScanDraft.load(context);assertNotNull(restored);assertEquals(draft.id,restored.id);assertEquals(1,restored.pages.size());ScanDraft.deleteTree(old.folder);
 }
 @Test public void historySchemaUpgradeKeepsLegacyLogFieldsAndRows()throws Exception{
  File file=File.createTempFile("iskan-history-migration-",".db",context.getCacheDir());try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file,null);Exporter.Receipts helper=new Exporter.Receipts(context)){db.execSQL("CREATE TABLE scan_logs(title TEXT NOT NULL,page_count INTEGER NOT NULL,scanned_date TEXT NOT NULL)");db.execSQL("CREATE TABLE receipts(session TEXT PRIMARY KEY)");db.execSQL("INSERT INTO scan_logs VALUES('Legacy fixture',2,'2026-10-15')");db.execSQL("INSERT INTO receipts VALUES('legacy-session')");helper.onUpgrade(db,1,2);try(Cursor c=db.rawQuery("SELECT title,page_count,scanned_date FROM scan_logs",null)){assertTrue(c.moveToFirst());assertEquals("Legacy fixture",c.getString(0));assertEquals(2,c.getInt(1));}try(Cursor c=db.rawQuery("SELECT session,log_rowid FROM receipts",null)){assertTrue(c.moveToFirst());assertEquals("legacy-session",c.getString(0));assertTrue(c.isNull(1));}try(Cursor c=db.rawQuery("SELECT count(*) FROM saved_outputs",null)){assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0));}}finally{android.database.sqlite.SQLiteDatabase.deleteDatabase(file);}
 }

 @Test public void processingSuccessAndFailureHaveDistinctRenderedIndicators(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();ProcessingStage stage=new ProcessingStage(target,"Fixture stage");stage.measure(android.view.View.MeasureSpec.makeMeasureSpec(600,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(80,android.view.View.MeasureSpec.EXACTLY));stage.layout(0,0,600,80);Bitmap image=Bitmap.createBitmap(600,80,Bitmap.Config.ARGB_8888);stage.setText("✓  Fixture completed");stage.draw(new Canvas(image));int[] pixels=new int[600*80];image.getPixels(pixels,0,600,0,0,600,80);boolean green=false;for(int color:pixels)if(Color.green(color)>Color.red(color)*1.5&&Color.green(color)>Color.blue(color)*1.2)green=true;assertTrue(green);image.eraseColor(Color.TRANSPARENT);stage.setText("✕  Fixture failed");stage.draw(new Canvas(image));image.getPixels(pixels,0,600,0,0,600,80);boolean red=false;for(int color:pixels)if(Color.red(color)>Color.green(color)*1.5&&Color.red(color)>Color.blue(color)*1.5)red=true;assertTrue(red);image.recycle();});
 }

}

