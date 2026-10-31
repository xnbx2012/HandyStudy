package com.handymath.app;

import android.app.Activity;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public final class ImageViewerActivity extends Activity {
    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xff182630);
        try {
            String location = getIntent().getStringExtra("bank");
            BankRepository bank = BankRepository.loadLocation(this, location);
            frame.addView(
                    new ZoomImage(bank.image(getIntent().getStringExtra("image"))),
                    new FrameLayout.LayoutParams(-1, -1));
        } catch (Exception e) {
            finish();
            return;
        }
        Button back = new Button(this);
        back.setText("‹ 返回");
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams p =
                new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        p.topMargin = 60;
        p.leftMargin = 20;
        frame.addView(back, p);
        setContentView(frame);
    }

    private final class ZoomImage extends View {
        final Bitmap bitmap;
        final Paint paint = new Paint(3);
        float scale = 1, x = 0, y = 0, lastX, lastY;
        final ScaleGestureDetector pinch;
        final GestureDetector gestures;

        ZoomImage(Bitmap b) {
            super(ImageViewerActivity.this);
            bitmap = b;
            pinch =
                    new ScaleGestureDetector(
                            getContext(),
                            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                @Override
                                public boolean onScale(ScaleGestureDetector d) {
                                    float wx = (d.getFocusX() - x) / scale,
                                            wy = (d.getFocusY() - y) / scale;
                                    scale = Math.max(.1f, Math.min(12, scale * d.getScaleFactor()));
                                    x = d.getFocusX() - wx * scale;
                                    y = d.getFocusY() - wy * scale;
                                    invalidate();
                                    return true;
                                }
                            });
            gestures =
                    new GestureDetector(
                            getContext(),
                            new GestureDetector.SimpleOnGestureListener() {
                                @Override
                                public boolean onDown(MotionEvent e) {
                                    return true;
                                }

                                @Override
                                public boolean onDoubleTap(MotionEvent e) {
                                    if (scale > fit() * 1.1f) reset();
                                    else {
                                        float wx = (e.getX() - x) / scale,
                                                wy = (e.getY() - y) / scale;
                                        scale = fit() * 2.5f;
                                        x = e.getX() - wx * scale;
                                        y = e.getY() - wy * scale;
                                        invalidate();
                                    }
                                    return true;
                                }
                            });
        }

        float fit() {
            return Math.min(
                    getWidth() / (float) bitmap.getWidth(),
                    getHeight() / (float) bitmap.getHeight());
        }

        void reset() {
            scale = fit();
            x = (getWidth() - bitmap.getWidth() * scale) / 2;
            y = (getHeight() - bitmap.getHeight() * scale) / 2;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            reset();
        }

        @Override
        protected void onDraw(Canvas c) {
            c.save();
            c.translate(x, y);
            c.scale(scale, scale);
            c.drawBitmap(bitmap, 0, 0, paint);
            c.restore();
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            pinch.onTouchEvent(e);
            gestures.onTouchEvent(e);
            float cx = 0, cy = 0;
            int pointers = 0;
            for (int i = 0; i < e.getPointerCount(); i++) {
                if (e.getActionMasked() == MotionEvent.ACTION_POINTER_UP && i == e.getActionIndex())
                    continue;
                cx += e.getX(i);
                cy += e.getY(i);
                pointers++;
            }
            cx /= Math.max(1, pointers);
            cy /= Math.max(1, pointers);
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                x += cx - lastX;
                y += cy - lastY;
                invalidate();
            }
            lastX = cx;
            lastY = cy;
            return true;
        }
    }
}
