package com.handymath.app;

import java.util.*;

/** Hold the endpoint for 400ms before lifting: snap only sufficiently close basic shapes. */
final class ShapeSnap {
    static boolean apply(InkView.Stroke s) {
        if (s.dots.size() < 8) return false;
        List<InkView.Dot> ds = s.dots;
        InkView.Dot a = ds.get(0), b = ds.get(ds.size() - 1);
        float l = Float.MAX_VALUE, t = l, r = -l, bt = -l;
        double distance = 0;
        for (int i = 0; i < ds.size(); i++) {
            InkView.Dot d = ds.get(i);
            l = Math.min(l, d.x);
            r = Math.max(r, d.x);
            t = Math.min(t, d.y);
            bt = Math.max(bt, d.y);
            if (i > 0) distance += Math.hypot(d.x - ds.get(i - 1).x, d.y - ds.get(i - 1).y);
        }
        double chord = Math.hypot(b.x - a.x, b.y - a.y);
        List<InkView.Dot> out = new ArrayList<>();
        if (chord > 50 && distance < chord * 1.06) {
            out.add(a);
            out.add(b);
        } else if (r - l > 50 && bt - t > 50 && chord < Math.min(r - l, bt - t) * .2) {
            float cx = (l + r) / 2, cy = (t + bt) / 2, rx = (r - l) / 2, ry = (bt - t) / 2;
            double ellipseError = 0, rectangleError = 0;
            for (InkView.Dot d : ds) {
                ellipseError += Math.abs(Math.hypot((d.x - cx) / rx, (d.y - cy) / ry) - 1);
                rectangleError +=
                        Math.min(
                                        Math.min(Math.abs(d.x - l), Math.abs(d.x - r)),
                                        Math.min(Math.abs(d.y - t), Math.abs(d.y - bt)))
                                / Math.min(rx, ry);
            }
            if (ellipseError / ds.size() < .1) {
                for (int i = 0; i <= 96; i++) {
                    double angle = i * Math.PI * 2 / 96;
                    out.add(
                            new InkView.Dot(
                                    cx + (float) Math.cos(angle) * rx,
                                    cy + (float) Math.sin(angle) * ry,
                                    a.pressure));
                }
            } else if (rectangleError / ds.size() < .09) {
                out.add(new InkView.Dot(l, t, a.pressure));
                out.add(new InkView.Dot(r, t, a.pressure));
                out.add(new InkView.Dot(r, bt, a.pressure));
                out.add(new InkView.Dot(l, bt, a.pressure));
                out.add(out.get(0));
            }
        }
        if (!out.isEmpty()) {
            ds.clear();
            ds.addAll(out);
            return true;
        }
        return false;
    }
}
