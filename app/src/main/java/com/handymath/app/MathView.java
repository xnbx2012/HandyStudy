package com.handymath.app;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.webkit.*;
import org.json.JSONObject;

/** Sanitized Markdown and KaTeX served exclusively from packaged local assets. */
final class MathView extends WebView {
  private String markdown = "";
  private boolean ready = false, streaming = false, disposed = false;

  @android.annotation.SuppressLint("SetJavaScriptEnabled")
  MathView(Context c) {
    super(c);
    setBackgroundColor(Color.TRANSPARENT);
    getSettings().setJavaScriptEnabled(true);
    getSettings().setAllowFileAccess(false);
    getSettings().setAllowContentAccess(false);
    getSettings().setBlockNetworkLoads(true);
    addJavascriptInterface(
        new Object() {
          @JavascriptInterface
          public void height(double h) {
            post(
                () -> {
                  if (disposed) return;
                  ViewGroup.LayoutParams p = getLayoutParams();
                  if (p != null) {
                    int height =
                        (int)
                            Math.ceil(
                                Math.min(20000, h) * getResources().getDisplayMetrics().density);
                    if (Math.abs(p.height - height) > 2) {
                      p.height = height;
                      setLayoutParams(p);
                    }
                  }
                });
          }
        },
        "MathHeight");
    setWebViewClient(
        new WebViewClient() {
          @Override
          public void onPageFinished(WebView v, String url) {
            if (disposed) return;
            ready = true;
            render();
          }

          @Override
          public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            return true;
          }

          @Override
          public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
            String url = request.getUrl().toString(), prefix = "https://math.local/render/";
            if (!url.startsWith(prefix)) return empty();
            String path = url.substring(prefix.length());
            if (path.contains("..")) return empty();
            try {
              String mime =
                  path.endsWith(".js")
                      ? "application/javascript"
                      : path.endsWith(".css")
                          ? "text/css"
                          : path.endsWith(".woff2")
                              ? "font/woff2"
                              : path.endsWith(".woff")
                                  ? "font/woff"
                                  : path.endsWith(".ttf") ? "font/ttf" : "text/html";
              return new WebResourceResponse(mime, "UTF-8", c.getAssets().open("render/" + path));
            } catch (Exception e) {
              return empty();
            }
          }

          private WebResourceResponse empty() {
            return new WebResourceResponse(
                "text/plain", "UTF-8", new java.io.ByteArrayInputStream(new byte[0]));
          }
        });
    loadUrl("https://math.local/render/math.html");
  }

  void setMarkdownStreaming(String value, boolean on) {
    streaming = on;
    markdown = value;
    if (ready) render();
  }

  void setMarkdown(String value) {
    streaming = false;
    markdown = value;
    if (ready) render();
  }

  static void releaseTree(android.view.View view) {
    if (view instanceof MathView) {
      MathView math = (MathView) view;
      if (math.disposed) return;
      math.disposed = true;
      if (math.getParent() instanceof ViewGroup) ((ViewGroup) math.getParent()).removeView(math);
      math.stopLoading(); math.destroy();
    } else if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = group.getChildCount() - 1; i >= 0; i--) releaseTree(group.getChildAt(i));
    }
  }

  private void render() {
    if (disposed) return;
    evaluateJavascript("setMarkdown(" + JSONObject.quote(markdown) + "," + streaming + ")", null);
  }
}
