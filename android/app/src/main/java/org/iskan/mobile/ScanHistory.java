package org.iskan.mobile;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import org.json.JSONArray;
import java.util.*;
final class ScanHistory {
 static final class Entry {String title,date,format="",name;int pages;ArrayList<Uri> uris=new ArrayList<>();}
 static boolean receipt(Context context,String session){try(Exporter.Receipts db=new Exporter.Receipts(context);Cursor c=db.getReadableDatabase().rawQuery("SELECT session FROM receipts WHERE session=?",new String[]{session})){return c.moveToFirst();}}
 static List<Entry> read(Context context,int limit){ArrayList<Entry> result=new ArrayList<>();try(Exporter.Receipts db=new Exporter.Receipts(context);Cursor c=db.getReadableDatabase().rawQuery("SELECT l.title,l.scanned_date,l.page_count,o.format,o.names,o.uris,o.page_count FROM scan_logs l LEFT JOIN receipts r ON r.log_rowid=l.rowid LEFT JOIN saved_outputs o ON o.session=r.session ORDER BY l.rowid DESC"+(limit>0?" LIMIT "+limit:""),null)){while(c.moveToNext()){Entry e=new Entry();e.title=c.getString(0);e.date=c.getString(1);e.pages=c.getInt(2);e.name=e.title;if(!c.isNull(3)){e.format=c.getString(3);e.pages=c.getInt(6);try{JSONArray names=new JSONArray(c.getString(4)),uris=new JSONArray(c.getString(5));if(names.length()>0)e.name=names.getString(0);for(int i=0;i<uris.length();i++)e.uris.add(Uri.parse(uris.getString(i)));}catch(Exception ignored){}}result.add(e);}}return result;}
 static void open(Context context,Entry item){if(item.uris.isEmpty())throw new IllegalStateException("LEGACY_ENTRY");String mime=item.format.equals("PDF")?"application/pdf":item.format.equals("PNG")?"image/png":"image/jpeg";Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(item.uris.get(0),mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);context.startActivity(intent);}
}
