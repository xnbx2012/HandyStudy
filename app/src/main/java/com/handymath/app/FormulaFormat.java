package com.handymath.app;

import java.io.IOException;
import java.util.regex.*;

final class FormulaFormat {
    static String preview(String value) {
        int open = value.lastIndexOf("$$");
        if (open >= 0 && value.split("\\$\\$", -1).length % 2 == 0)
            return value.substring(0, open) + "（公式生成中…）";
        return value;
    }

    static String checked(String value) throws IOException {
        Pattern spans =
                Pattern.compile(
                        "\\$\\$[\\s\\S]*?\\$\\$|\\\\\\([\\s\\S]*?\\\\\\)|\\\\\\[[\\s\\S]*?\\\\\\]|\\$[^$\\n"
                            + "]+\\$");
        Matcher m = spans.matcher(value);
        StringBuffer normalized = new StringBuffer();
        StringBuilder outside = new StringBuilder();
        int end = 0;
        while (m.find()) {
            outside.append(value, end, m.start());
            String raw = m.group();
            String formula =
                    raw.startsWith("$$")
                            ? raw
                            : raw.startsWith("\\")
                                    ? "$$" + raw.substring(2, raw.length() - 2) + "$$"
                                    : "$$" + raw.substring(1, raw.length() - 1) + "$$";
            m.appendReplacement(normalized, Matcher.quoteReplacement(formula));
            end = m.end();
        }
        m.appendTail(normalized);
        outside.append(value.substring(end));
        if (Pattern.compile(
                        "\\\\[a-zA-Z]+|\\$|[∫∑ΣΠ√∞≤≥≠≈→αβγδεθλμπσφω²³⁴⁵⁶⁷⁸⁹₀₁₂₃]|(?<![A-Za-z0-9])(?:[A-Za-z]|[0-9]+)\\s*(?:[/^_=]|[+*-]\\s*[(A-Za-z0-9])")
                .matcher(outside)
                .find()) throw new IOException("模型公式未使用完整的 $$ 分隔符，请重试");
        return normalized.toString();
    }
}
