package com.handymath.app;

import android.app.*;
import android.content.*;

import java.util.*;

final class StudyFilter {
    String type = "", knowledge = "";

    boolean matches(BankRepository bank, BankRepository.Question q) {
        if (!type.isEmpty() && !type.equals(q.type)) return false;
        if (knowledge.isEmpty()) return true;
        if (knowledge.startsWith("topic:")) return knowledge.substring(6).equals(q.topic);
        for (int i = 0; i < q.knowledgeIds.length(); i++)
            if (bank.knowledge.under(q.knowledgeIds.optString(i), knowledge)) return true;
        return false;
    }

    static StudyFilter load(Context c, String bank) {
        SharedPreferences p = c.getSharedPreferences("filter-" + bank, 0);
        StudyFilter f = new StudyFilter();
        f.type = p.getString("type", "");
        f.knowledge = p.getString("knowledge", "");
        return f;
    }

    void save(Context c, String bank) {
        c.getSharedPreferences("filter-" + bank, 0)
                .edit()
                .putString("type", type)
                .putString("knowledge", knowledge)
                .apply();
    }

    static String typeName(String type) {
        return switch (type) {
            case "choice" -> "单项选择题";
            case "multi-choice" -> "多项选择题";
            case "fill" -> "填空题";
            case "solution" -> "解答题";
            case "reading" -> "阅读理解（材料组）";
            case "cloze" -> "完形填空（材料组）";
            case "matching" -> "新题型（材料组）";
            case "translation" -> "翻译";
            case "writing" -> "写作";
            case "analysis" -> "材料分析题";
            default -> type.equals("auto") ? "综合题" : type;
        };
    }

    interface Selection {
        void selected(String id);
    }

    static void pickKnowledge(
            Activity activity, BankRepository bank, String parent, Selection choice) {
        if (bank.knowledge.nodes.isEmpty()) {
            List<String> names = new ArrayList<>();
            names.add("全部知识点");
            for (BankRepository.Question q : bank.questions)
                if (!names.contains(q.topic)) names.add(q.topic);
            new AlertDialog.Builder(activity)
                    .setTitle("知识点")
                    .setItems(
                            names.toArray(new String[0]),
                            (d, i) -> choice.selected(i == 0 ? "" : "topic:" + names.get(i)))
                    .show();
            return;
        }
        List<KnowledgeSystem.Node> children = bank.knowledge.children(parent);
        List<String> names = new ArrayList<>();
        names.add(parent.isEmpty() ? "全部知识点" : "选择本级全部");
        for (KnowledgeSystem.Node n : children)
            names.add(n.name + (bank.knowledge.children(n.id).isEmpty() ? "" : "  ›"));
        new AlertDialog.Builder(activity)
                .setTitle(parent.isEmpty() ? "知识体系" : bank.knowledge.path(parent))
                .setItems(
                        names.toArray(new String[0]),
                        (d, i) -> {
                            if (i == 0) choice.selected(parent);
                            else {
                                KnowledgeSystem.Node node = children.get(i - 1);
                                if (bank.knowledge.children(node.id).isEmpty())
                                    choice.selected(node.id);
                                else pickKnowledge(activity, bank, node.id, choice);
                            }
                        })
                .setNegativeButton("取消", null)
                .setNeutralButton(
                        parent.isEmpty() ? "关闭" : "上一级",
                        (d, w) -> {
                            if (!parent.isEmpty())
                                pickKnowledge(
                                        activity,
                                        bank,
                                        bank.knowledge.nodes.get(parent).parent,
                                        choice);
                        })
                .show();
    }
}
