package com.handymath.app;

import android.content.*;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Offline source pages and listening audio, separate from the student's exercise images. */
public final class BankMaterialsActivity extends PageActivity {
  BankRepository bank;
  JSONObject document;
  int index = 0;
  ImageView image;
  TextView status;
  LinearLayout body;
  android.graphics.Bitmap bitmap;
  MediaPlayer player;
  File audio;

  public void onCreate(Bundle state) {
    super.onCreate(state);
    page("题库资料");
    try {
      bank = BankRepository.loadLocation(this, getIntent().getStringExtra("bankLocation"));
      label("原书页面保留完整内容；资料中的答案不是你的作答。听力可在此播放，模型检查仍需要你写下作答内容。");
      Spinner documents = new Spinner(this);
      List<String> titles = new ArrayList<>();
      for (int i = 0; i < bank.materials.length(); i++)
        titles.add(bank.materials.getJSONObject(i).getString("title"));
      documents.setAdapter(
          new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, titles));
      panel.addView(documents);
      body = new LinearLayout(this);
      body.setOrientation(LinearLayout.VERTICAL);
      panel.addView(body);
      documents.setOnItemSelectedListener(
          new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) {}

            public void onItemSelected(AdapterView<?> p, android.view.View v, int n, long id) {
              try {
                stop();
                document = bank.materials.getJSONObject(n);
                index = 0;
                show();
              } catch (Exception e) {
                failure(e);
              }
            }
          });
    } catch (Exception e) {
      failure(e);
    }
  }

  void show() throws Exception {
    body.removeAllViews();
    panel = body;
    if (document.optString("type").equals("audio")) {
      label(document.getString("title"));
      status = label("听力音频 · 离开此页停止播放");
      action(
          "播放 / 暂停",
          () -> {
            try {
              if (player == null) {
                audio = File.createTempFile("listening-", ".ogg", getCacheDir());
                try (InputStream in = bank.open(document.getString("path"));
                    OutputStream out = new FileOutputStream(audio)) {
                  byte[] bytes = new byte[8192];
                  int n;
                  while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
                }
                player = new MediaPlayer();
                player.setDataSource(audio.getAbsolutePath());
                player.prepare();
              }
              if (player.isPlaying()) {
                player.pause();
                status.setText("已暂停");
              } else {
                player.start();
                status.setText("正在播放");
              }
            } catch (Exception e) {
              failure(e);
            }
          });
      action(
          "从头播放",
          () -> {
            if (player != null) {
              player.seekTo(0);
              player.start();
            }
          });
      return;
    }
    JSONArray pages = document.getJSONArray("pages");
    status = label("原文件第 " + (index + 1) + " / " + pages.length() + " 页");
    LinearLayout nav = new LinearLayout(this);
    body.addView(nav);
    Button prev = new Button(this);
    prev.setText("上一页");
    prev.setEnabled(index > 0);
    prev.setOnClickListener(v -> move(index - 1));
    nav.addView(prev);
    EditText jump = new EditText(this);
    jump.setSingleLine();
    jump.setHint("页码");
    jump.setInputType(2);
    nav.addView(jump, new LinearLayout.LayoutParams(0, -2, 1));
    Button go = new Button(this);
    go.setText("跳转");
    go.setOnClickListener(
        v -> {
          try {
            move(Integer.parseInt(jump.getText().toString()) - 1);
          } catch (Exception e) {
            Toast.makeText(this, "请输入有效页码", Toast.LENGTH_SHORT).show();
          }
        });
    nav.addView(go);
    Button next = new Button(this);
    next.setText("下一页");
    next.setEnabled(index + 1 < pages.length());
    next.setOnClickListener(v -> move(index + 1));
    nav.addView(next);
    image = new ImageView(this);
    image.setAdjustViewBounds(true);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    body.addView(image, new LinearLayout.LayoutParams(-1, -2));
    if (bitmap != null) {
      bitmap.recycle();
      bitmap = null;
    }
    bitmap = bank.image(pages.getString(index));
    image.setImageBitmap(bitmap);
    image.setContentDescription("原书资料第" + (index + 1) + "页，点击放大");
    image.setOnClickListener(
        v ->
            startActivity(
                new Intent(this, ImageViewerActivity.class)
                    .putExtra("bank", bank.location())
                    .putExtra("image", pages.optString(index))));
  }

  void move(int page) {
    if (page < 0 || page >= document.optJSONArray("pages").length()) {
      Toast.makeText(this, "页码超出范围", Toast.LENGTH_SHORT).show();
      return;
    }
    index = page;
    try {
      show();
    } catch (Exception e) {
      failure(e);
    }
  }

  void stop() {
    if (player != null) {
      player.release();
      player = null;
    }
    if (audio != null) {
      audio.delete();
      audio = null;
    }
  }

  protected void onStop() {
    stop();
    super.onStop();
  }

  protected void onDestroy() {
    stop();
    if (bitmap != null) bitmap.recycle();
    super.onDestroy();
  }
}
