package org.iskan.mobile;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import static org.junit.Assert.*;
/** Captures the actual Android window. Guarded to the isolated preview application only. */
@RunWith(AndroidJUnit4.class)
public class AppScreenshotsTest {
 final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
 void await(ActivityScenario<MainActivity> scenario,Predicate<MainActivity> ready,long timeout)throws Exception{long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){AtomicBoolean done=new AtomicBoolean();scenario.onActivity(a->done.set(ready.test(a)));if(done.get())return;SystemClock.sleep(120);}throw new AssertionError("App did not reach expected screenshot state");}
 void capture(String name)throws Exception{if(!name.equals("05-processing")&&!name.equals("07-saved"))SystemClock.sleep(4200);instrument.waitForIdleSync();Bitmap bitmap=instrument.getUiAutomation().takeScreenshot();assertNotNull("Android screenshot unavailable",bitmap);File folder=instrument.getTargetContext().getExternalFilesDir("screenshots");assertNotNull(folder);assertTrue(folder.exists()||folder.mkdirs());File file=new File(folder,name+".png");try(FileOutputStream out=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}System.out.println("I-SKAN_SCREENSHOT "+file.getAbsolutePath());}
 ListView list(View view){if(view instanceof ListView)return (ListView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){ListView found=list(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
 @Test public void captureRealNativeScreensWithSampleDocument()throws Exception{
  Context target=instrument.getTargetContext();org.junit.Assume.assumeTrue("Screenshot capture requires isolated preview app",target.getPackageName().endsWith(".preview"));
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   await(scenario,a->a.screen.equals("home")&&!a.busy,60000);SystemClock.sleep(300);capture("00-home-empty");
   if(InstrumentationRegistry.getArguments().getString("captureCamera","false").equals("true")){try(android.os.ParcelFileDescriptor grant=instrument.getUiAutomation().executeShellCommand("pm grant "+target.getPackageName()+" android.permission.CAMERA")){try(java.io.FileInputStream granted=new java.io.FileInputStream(grant.getFileDescriptor())){byte[] buffer=new byte[256];while(granted.read(buffer)!=-1){}}}scenario.onActivity(a->a.startHomeAction(2));await(scenario,a->a.screen.equals("capture")&&a.camera!=null,30000);SystemClock.sleep(1800);capture("09-camera");scenario.onActivity(a->a.getOnBackPressedDispatcher().onBackPressed());await(scenario,a->a.screen.equals("home")&&!a.busy,30000);}
   AtomicReference<MainActivity> owner=new AtomicReference<>();scenario.onActivity(owner::set);MainActivity activity=owner.get();
   ScanDraft demo=activity.work.submit(()->{ScanDraft.Options o=new ScanDraft.Options();ScanFlow.configure(o,2);o.appearance=3;ScanDraft d=new ScanDraft(target,o);d.title="I-SKAN sample document";d.filename="I-SKAN sample document";d.titleEdited=true;try(InputStream in=instrument.getContext().getAssets().open("sample.png")){Bitmap b=BitmapFactory.decodeStream(in);assertNotNull(b);try{for(int i=0;i<2;i++)d.add(b,null);}finally{b.recycle();}}d.save();return d;}).get(60,TimeUnit.SECONDS);
   scenario.onActivity(a->{a.options=demo.options;a.draft=demo;a.pageIndex=0;a.showReview(false);});await(scenario,a->a.screen.equals("review")&&!a.busy,60000);SystemClock.sleep(300);capture("02-review");
   scenario.onActivity(MainActivity::manualCrop);SystemClock.sleep(300);capture("03-crop");scenario.onActivity(a->{a.finishInlineEdit(false);a.cleanerSettings();});await(scenario,a->a.screen.equals("cleaner")&&a.cleanerEditor!=null&&a.cleanerEditor.resultBitmap!=null,60000);SystemClock.sleep(250);capture("04-cleaner");
   scenario.onActivity(a->a.cleanerEditor.cancel());await(scenario,a->a.screen.equals("review")&&!a.busy,60000);scenario.onActivity(MainActivity::startProcessing);SystemClock.sleep(350);AtomicBoolean processing=new AtomicBoolean();scenario.onActivity(a->processing.set(a.screen.equals("process")));if(processing.get())capture("05-processing");
   await(scenario,a->a.screen.equals("export")&&!a.busy&&a.previewBitmap!=null,180000);SystemClock.sleep(300);capture("06-export");scenario.onActivity(a->a.saveOutput(false));await(scenario,a->a.screen.equals("export")&&!a.busy&&a.draft.exported,120000);SystemClock.sleep(250);capture("07-saved");
   scenario.onActivity(MainActivity::exitSavedSession);await(scenario,a->a.screen.equals("home")&&!a.busy,30000);activity.work.submit(()->{}).get(30,TimeUnit.SECONDS);instrument.waitForIdleSync();SystemClock.sleep(300);capture("01-home");scenario.onActivity(MainActivity::showLog);await(scenario,a->{ListView view=list(a.body);return a.screen.equals("history")&&view!=null&&view.getAdapter()!=null&&view.getAdapter().getCount()>0;},30000);SystemClock.sleep(250);capture("08-history");
  }
 }
}
