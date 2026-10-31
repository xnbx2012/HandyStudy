package com.handymath.app;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

final class BankTemplate {
    static final String SPEC =
            "## 题库 ZIP 规范\n"
                + "ZIP 根目录放置 `bank.json` 与引用的图片。\n"
                + "- `version` 为 2（兼容旧版 1）；`id` 使用字母、数字、下划线或短横线，同一题库更新时保持不变。\n"
                + "- `subject` 为学科，`title` 为题库名称，`questions` 为题目数组（1–1000 题）。\n"
                + "- 每题包含唯一 `id`、`title`、`topic`，`type` 支持"
                + " `solution`、`choice`、`multi-choice`、`fill`、`reading`、`cloze`、`matching`、`translation`、`writing`、`analysis`。\n"
                + "- 题面使用 `questionMarkdown`（Markdown 字符串，支持 LaTeX）或 `questionImage`（ZIP"
                + " 内相对图片路径），也可同时提供。\n"
                + "- 可提供 `chapter`（章节）、`number`（章内题号），多张题面用 `questionImages` 数组。\n"
                + "- `knowledgeSystem.nodes` 保存完整知识体系，各节点有唯一 `id`、`name`、`parentId`（根节点为空字符串）；每题"
                + " `knowledgeIds` 引用节点。禁止循环或缺失父节点。\n"
                + "- 无官方参考答案时设 `referencePolicy` 为 `independent`，在解析注明来源和评价标准。\n"
                + "- 解析使用 `answerMarkdown` 或 `answerImages` 图片路径数组。\n"
                + "- 选择题填写 `expectedChoice`（A/B/C/D）。\n"
                + "- 可选 `knowledgePoints`、`examPoints` 字符串数组及 `difficulty`（1–5），未提供时由 LLM 分类。\n\n"
                + "公式以 `$$...$$` 包裹。同一行的短公式按行内公式渲染；长公式在两个分隔符内加换行。JSON 中反斜线写作 `\\\\`。同时兼容传统"
                + " `$...$`、`\\\\(...\\\\)`。\n"
                + "图片支持 PNG/JPEG/WebP，最大 5000×8000 且不超过 2000 万像素。解析最多 8 张，ZIP 解压最多 100 MB，禁止绝对路径及"
                + " `..`。模板含 Markdown 示例、图片示例与说明，可直接导入。";

    static void write(OutputStream output) throws Exception {
        JSONObject question =
                new JSONObject()
                        .put("id", "limit-1")
                        .put("title", "基本极限")
                        .put("topic", "极限")
                        .put("type", "solution")
                        .put("chapter", "第一章 极限")
                        .put("number", 1)
                        .put("knowledgeIds", new JSONArray().put("limits"))
                        .put("questionMarkdown", "求极限 $$\\lim_{x\\to0}\\frac{\\sin x}{x}$$，说明依据。")
                        .put("answerMarkdown", "由基本极限 $$\\lim_{x\\to0}\\sin x/x=1$$，答案为 $$1$$。")
                        .put("knowledgePoints", new JSONArray().put("基本极限"))
                        .put("examPoints", new JSONArray().put("等价无穷小"))
                        .put("difficulty", 1);
        android.graphics.Bitmap image =
                android.graphics.Bitmap.createBitmap(
                        800, 200, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(image);
        canvas.drawColor(android.graphics.Color.WHITE);
        android.graphics.Paint p = new android.graphics.Paint(3);
        p.setColor(android.graphics.Color.BLACK);
        p.setTextSize(48);
        canvas.drawText("Find lim(x -> 0) sin(x)/x", 30, 110, p);
        JSONObject imageQuestion =
                new JSONObject(question.toString())
                        .put("id", "image-1")
                        .put("title", "图片题面示例")
                        .put("questionMarkdown", "")
                        .put("questionImage", "question.png");
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            entry(
                    zip,
                    "bank.json",
                    new JSONObject()
                            .put("version", 2)
                            .put("subject", "数学")
                            .put(
                                    "knowledgeSystem",
                                    new JSONObject()
                                            .put(
                                                    "nodes",
                                                    new JSONArray()
                                                            .put(
                                                                    new JSONObject()
                                                                            .put("id", "limits")
                                                                            .put("name", "极限")
                                                                            .put("parentId", ""))))
                            .put("id", "my-math-bank")
                            .put("title", "我的数学题库")
                            .put("questions", new JSONArray().put(question).put(imageQuestion))
                            .toString(2)
                            .getBytes(StandardCharsets.UTF_8));
            entry(zip, "README.md", SPEC.getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("question.png"));
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, zip);
            zip.closeEntry();
        } finally {
            image.recycle();
        }
    }

    static void entry(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }
}
