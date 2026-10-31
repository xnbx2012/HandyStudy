package com.handymath.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;

import org.json.*;

import java.util.*;

/** Interactive bank-owned knowledge chart; unattempted mastery remains unknown. */
final class KnowledgeChart extends View {
    final BankRepository bank;
    final boolean radar;
    final java.util.List<KnowledgeSystem.Node> nodes;
    final Map<String, Double> latest = new HashMap<>();
    final Paint p = new Paint(3);
    final java.util.List<RectF> hits = new ArrayList<>();
    String tip = "";
    float tx, ty;

    KnowledgeChart(Context c, BankRepository b, JSONArray attempts, boolean radar) {
        super(c);
        bank = b;
        this.radar = radar;
        java.util.List<KnowledgeSystem.Node> roots = b.knowledge.children("");
        nodes =
                roots.size() == 1 && !b.knowledge.children(roots.get(0).id).isEmpty()
                        ? b.knowledge.children(roots.get(0).id)
                        : roots;
        for (int i = 0; i < attempts.length(); i++) {
            JSONObject a = attempts.optJSONObject(i);
            if (bank.id.equals(a.optString("bank")))
                latest.putIfAbsent(a.optString("q"), a.optDouble("score"));
        }
    }

    double[] values(KnowledgeSystem.Node n) {
        double total = 0, score = 0, done = 0;
        for (BankRepository.Question q : bank.questions) {
            boolean match = false;
            for (int j = 0; j < q.knowledgeIds.length(); j++)
                if (bank.knowledge.under(q.knowledgeIds.optString(j), n.id)) match = true;
            if (match) {
                total++;
                if (latest.containsKey(q.id)) {
                    done++;
                    score += latest.get(q.id);
                }
            }
        }
        return new double[] {total, done, done == 0 ? Double.NaN : score / done};
    }

    protected void onDraw(Canvas c) {
        super.onDraw(c);
        hits.clear();
        float d = getResources().getDisplayMetrics().density;
        p.setTextSize(11 * d);
        int n = nodes.size();
        if (n == 0) return;
        if (radar) {
            float cx = getWidth() / 2f,
                    cy = getHeight() / 2f,
                    r = Math.min(getWidth(), getHeight()) * .33f;
            for (int ring = 1; ring <= 5; ring++) {
                Path path = new Path();
                for (int i = 0; i < n; i++) {
                    double a = i * 2 * Math.PI / n - Math.PI / 2;
                    float x = cx + (float) Math.cos(a) * r * ring / 5,
                            y = cy + (float) Math.sin(a) * r * ring / 5;
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xffdce7e4);
                c.drawPath(path, p);
            }
            for (int i = 0; i < n; i++) {
                KnowledgeSystem.Node node = nodes.get(i);
                double a = i * 2 * Math.PI / n - Math.PI / 2;
                float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
                double[] v = values(node);
                p.setColor(0xffdce7e4);
                c.drawLine(cx, cy, x, y, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xff647585);
                String name = node.name;
                if (name.length() > 8) name = name.substring(0, 8) + "…";
                float lx = cx + (float) Math.cos(a) * (r + 18 * d),
                        ly = cy + (float) Math.sin(a) * (r + 18 * d);
                c.drawText(name, lx - p.measureText(name) / 2, ly, p);
                if (!Double.isNaN(v[2])) {
                    p.setColor(0xff197d78);
                    c.drawCircle(
                            cx + (float) Math.cos(a) * r * (float) v[2] / 10,
                            cy + (float) Math.sin(a) * r * (float) v[2] / 10,
                            5 * d,
                            p);
                }
                if (!Double.isNaN(v[2]) && n > 2) {
                    double[] next = values(nodes.get((i + 1) % n));
                    if (!Double.isNaN(next[2])) {
                        double angle = (i + 1) * 2 * Math.PI / n - Math.PI / 2;
                        p.setColor(0xff197d78);
                        p.setStrokeWidth(2 * d);
                        c.drawLine(
                                cx + (float) Math.cos(a) * r * (float) v[2] / 10,
                                cy + (float) Math.sin(a) * r * (float) v[2] / 10,
                                cx + (float) Math.cos(angle) * r * (float) next[2] / 10,
                                cy + (float) Math.sin(angle) * r * (float) next[2] / 10,
                                p);
                    }
                }
                hits.add(new RectF(lx - 45 * d, ly - 20 * d, lx + 45 * d, ly + 15 * d));
            }
        } else {
            float h = getHeight() / (float) n, max = 1;
            for (KnowledgeSystem.Node node : nodes) max = Math.max(max, (float) values(node)[0]);
            for (int i = 0; i < n; i++) {
                float y = i * h;
                double[] v = values(nodes.get(i));
                p.setColor(0xff647585);
                p.setStyle(Paint.Style.FILL);
                String name = nodes.get(i).name;
                if (name.length() > 10) name = name.substring(0, 10) + "…";
                c.drawText(name, 4 * d, y + h * .65f, p);
                p.setColor(0xffb8ddd5);
                c.drawRoundRect(
                        new RectF(
                                getWidth() * .4f,
                                y + h * .2f,
                                getWidth() * .4f + (getWidth() * .5f) * (float) v[0] / max,
                                y + h * .8f),
                        4 * d,
                        4 * d,
                        p);
                hits.add(new RectF(0, y, getWidth(), y + h));
            }
        }
        if (!tip.isEmpty()) {
            p.setTextSize(12 * d);
            float w = Math.min(getWidth() - 8 * d, p.measureText(tip) + 20 * d),
                    x = Math.max(4 * d, Math.min(tx - w / 2, getWidth() - w - 4 * d)),
                    y = Math.max(4 * d, Math.min(ty - 45 * d, getHeight() - 40 * d));
            p.setColor(0xff233446);
            c.drawRoundRect(new RectF(x, y, x + w, y + 34 * d), 9 * d, 9 * d, p);
            p.setColor(Color.WHITE);
            c.drawText(tip, x + 10 * d, y + 22 * d, p);
        }
    }

    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            for (int i = 0; i < hits.size(); i++)
                if ((hits.get(i).contains(e.getX(), e.getY())
                        || radar
                                && i
                                        == ((int)
                                                        Math.round(
                                                                (Math.atan2(
                                                                                        e.getY()
                                                                                                - getHeight()
                                                                                                        / 2f,
                                                                                        e.getX()
                                                                                                - getWidth()
                                                                                                        / 2f)
                                                                                + Math.PI / 2
                                                                                + 2 * Math.PI)
                                                                        * nodes.size()
                                                                        / (2 * Math.PI))
                                                % nodes.size()))) {
                    double[] v = values(nodes.get(i));
                    tip =
                            nodes.get(i).name
                                    + " · "
                                    + (int) v[0]
                                    + "题 · "
                                    + (Double.isNaN(v[2])
                                            ? "未练"
                                            : String.format(Locale.CHINA, "均分%.1f", v[2]));
                    tx = e.getX();
                    ty = e.getY();
                    invalidate();
                    performClick();
                    break;
                }
        }
        return true;
    }

    public boolean performClick() {
        super.performClick();
        return true;
    }
}
