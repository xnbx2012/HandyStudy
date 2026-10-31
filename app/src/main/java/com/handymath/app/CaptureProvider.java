package com.handymath.app;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.OpenableColumns;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Narrow temporary camera output grant; never exposes the app's private documents. */
public final class CaptureProvider extends ContentProvider {
  static File directory(Context c){File dir=new File(c.getCacheDir(),"photo-captures");dir.mkdirs();return dir;}
  static Uri uri(Context c,String name){return new Uri.Builder().scheme("content").authority(c.getPackageName()+".camera").appendPath("capture").appendPath(name).build();}
  static File file(Context c,Uri uri)throws FileNotFoundException{
    if(uri.getPathSegments().size()!=2||!"capture".equals(uri.getPathSegments().get(0))||!uri.getLastPathSegment().matches("capture-[a-f0-9-]{36}\\.jpg"))throw new FileNotFoundException("Invalid capture path");
    return new File(directory(c),uri.getLastPathSegment());
  }
  @Override public boolean onCreate(){return true;}
  @Override public String getType(Uri uri){return "image/jpeg";}
  @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
    File file=file(getContext(),uri);
    if(!mode.equals("r")&&!mode.equals("w")&&!mode.equals("wt")&&!mode.equals("rw")&&!mode.equals("rwt"))throw new FileNotFoundException("Invalid mode");
    return ParcelFileDescriptor.open(file,ParcelFileDescriptor.parseMode(mode));
  }
  @Override public Cursor query(Uri u,String[]p,String s,String[]a,String sort){
    try {
      File target=file(getContext(),u);
      String[] columns=p==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:p;
      MatrixCursor cursor=new MatrixCursor(columns,1);Object[] row=new Object[columns.length];
      for(int i=0;i<columns.length;i++) {
        if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=target.getName();
        else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=target.length();
      }
      cursor.addRow(row);return cursor;
    } catch(FileNotFoundException e){throw new IllegalArgumentException("Invalid capture path",e);}
  }
  @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
  @Override public int update(Uri u,ContentValues v,String s,String[]a){throw new UnsupportedOperationException();}
  @Override public int delete(Uri u,String s,String[]a){throw new UnsupportedOperationException();}
}
