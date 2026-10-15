package com.englishhub.common.util;

import java.util.List;

/**
 * Ghi CSV đơn giản (RFC 4180) cho file xuất báo cáo.
 * <ul>
 *   <li>Có BOM UTF-8 để Excel hiển thị đúng tiếng Việt.</li>
 *   <li>Chống "CSV injection": ô bắt đầu bằng = + - @ (hoặc tab/CR) được thêm dấu ' để Excel không chạy như công thức.</li>
 * </ul>
 */
public final class CsvWriter {

    public static final String BOM = "﻿";

    private final StringBuilder out = new StringBuilder(BOM);

    public CsvWriter row(List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(cells.get(i)));
        }
        out.append("\r\n");
        return this;
    }

    public String build() {
        return out.toString();
    }

    static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (!s.isEmpty() && !(value instanceof Number) && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
