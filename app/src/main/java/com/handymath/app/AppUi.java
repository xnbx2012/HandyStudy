package com.handymath.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

/** Shared visual tokens for native screens, controls and custom sheets. */
final class AppUi {
  static final int BG=0xfff1f5f4, INK=0xff233446, TEAL=0xff197d78, MUTED=0xff647585, PALE=0xffe5f0ee;
  static int dp(Context c,float n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
  static GradientDrawable surface(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
  static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
  static TextView text(Context c,String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
  static TextView heading(Context c,String value){TextView t=text(c,value,19,INK);t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
  static Button button(Context c,String label,boolean primary,Runnable action){
    Button b=new Button(c);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
    b.setTextColor(primary?Color.WHITE:TEAL);b.setMinHeight(dp(c,48));b.setMinimumWidth(0);b.setPadding(dp(c,16),dp(c,8),dp(c,16),dp(c,8));
    b.setBackgroundTintList(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x25197d78),surface(c,primary?TEAL:PALE,14),null));
    b.setOnClickListener(v->action.run());return b;
  }
  static LinearLayout card(Context c){LinearLayout l=column(c);l.setPadding(dp(c,20),dp(c,18),dp(c,20),dp(c,18));l.setBackground(surface(c,Color.WHITE,22));return l;}
  static void message(Context c,String title,String message){
    Dialog d=new Dialog(c);LinearLayout content=card(c);content.addView(heading(c,title));TextView note=text(c,message,15,MUTED);note.setPadding(0,dp(c,14),0,dp(c,20));content.addView(note);
    content.addView(button(c,"知道了",true,d::dismiss));d.setContentView(content);d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setLayout(Math.min(dp(c,480),c.getResources().getDisplayMetrics().widthPixels-dp(c,32)),-2);d.show();d.getWindow().setLayout(Math.min(dp(c,480),c.getResources().getDisplayMetrics().widthPixels-dp(c,32)),-2);
  }
}
