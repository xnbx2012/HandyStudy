package com.handymath.app;

import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.util.*;

public final class KnowledgeExplorerActivity extends PageActivity {
    BankRepository bank;
    String parent = "";
    JSONArray attempts;

    public void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            bank = BankRepository.loadLocation(this, getIntent().getStringExtra("bankLocation"));
            try (LearningDb db = new LearningDb(this)) {
                attempts = db.attempts(false);
            }
            if (bank.knowledge.children("").size() == 1)
                parent = bank.knowledge.children("").get(0).id;
            show();
        } catch (Exception e) {
            page("知识体系");
            failure(e);
        }
    }

    void show() {
        page("知识体系与弱项");
        String name =
                parent.isEmpty() ? bank.subject + " · " + bank.title : bank.knowledge.path(parent);
        section(name, "点击节点逐层展开；均分按该节点下每题的最新提交，未练保持未知");
        java.util.List<KnowledgeSystem.Node> children = bank.knowledge.children(parent);
        if (!parent.isEmpty())
            action(
                    "‹ 返回上一级",
                    () -> {
                        parent = bank.knowledge.nodes.get(parent).parent;
                        show();
                    });
        panel.addView(
                new MapView(children, name),
                new LinearLayout.LayoutParams(-1, dp(Math.max(220, children.size() * 60))));
        if (!parent.isEmpty()) {
            String selected = parent;
            action(
                    "练习此知识点题库原题",
                    () ->
                            startActivity(
                                    new Intent(this, MainActivity.class)
                                            .putExtra("bankLocation", bank.location())
                                            .putExtra("filterKnowledge", selected)));
            action(
                    "智能生成此知识点加练",
                    () ->
                            startActivity(
                                    new Intent(this, PracticeActivity.class)
                                            .putExtra(
                                                    "topic",
                                                    bank.subject
                                                            + "："
                                                            + bank.knowledge.path(selected))));
        }
        KnowledgeChart model = new KnowledgeChart(this, bank, attempts, true);
        section("本层掌握情况", "多次提交只采用每题最新成绩；题库标签为知识归属，模型诊断作为补充");
        for (KnowledgeSystem.Node n : children) {
            double[] v = model.values(n);
            action(
                    n.name
                            + " · "
                            + (int) v[0]
                            + "题 · 已练"
                            + (int) v[1]
                            + " · "
                            + (Double.isNaN(v[2])
                                    ? "暂无成绩"
                                    : String.format(Locale.CHINA, "均分%.1f", v[2])),
                    () -> {
                        parent = n.id;
                        show();
                    });
        }
    }

    final class MapView extends View {
        final java.util.List<KnowledgeSystem.Node> nodes;
        final String title;
        final Paint p = new Paint(3);
        final java.util.List<RectF> hits = new ArrayList<>();

        MapView(java.util.List<KnowledgeSystem.Node> n, String t) {
            super(KnowledgeExplorerActivity.this);
            nodes = n;
            title = t;
        }

        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density,
                    cx = getWidth() * .2f,
                    cy = getHeight() / 2f;
            hits.clear();
            p.setStrokeWidth(2 * d);
            p.setColor(0xffbadbd3);
            for (int i = 0; i < nodes.size(); i++) {
                float y = (i + .5f) * getHeight() / Math.max(1, nodes.size());
                c.drawLine(cx, cy, getWidth() * .49f, y, p);
            }
            p.setColor(0xff197d78);
            c.drawRoundRect(
                    new RectF(0, cy - 28 * d, getWidth() * .38f, cy + 28 * d), 12 * d, 12 * d, p);
            p.setTextSize(14 * d);
            p.setColor(Color.WHITE);
            c.drawText("当前知识分支", 8 * d, cy + 5 * d, p);
            for (int i = 0; i < nodes.size(); i++) {
                float y = (i + .5f) * getHeight() / nodes.size();
                RectF r = new RectF(getWidth() * .48f, y - 24 * d, getWidth() - 4 * d, y + 24 * d);
                hits.add(r);
                p.setColor(0xffe5f1ed);
                c.drawRoundRect(r, 12 * d, 12 * d, p);
                p.setColor(INK);
                String t = nodes.get(i).name;
                while (p.measureText(t) > r.width() - 20 * d && t.length() > 2)
                    t = t.substring(0, t.length() - 2) + "…";
                c.drawText(t, r.left + 10 * d, y + 5 * d, p);
            }
        }

        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                for (int i = 0; i < hits.size(); i++)
                    if (hits.get(i).contains(e.getX(), e.getY())) {
                        parent = nodes.get(i).id;
                        show();
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
}
