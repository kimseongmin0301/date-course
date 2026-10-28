package com.datecourse.domain.collector.util;

import java.util.ArrayList;
import java.util.List;

/** CSV 한 줄 나누기: 쉼표로 나누되 큰따옴표 안의 쉼표는 두고, "" 는 " 로 (상가정보 CSV 는 줄 안에 줄바꿈이 없다) */
public final class CsvLine {

    private CsvLine() {
    }

    public static String[] parse(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(ch);
            }
        }
        fields.add(field.toString());
        return fields.toArray(String[]::new);
    }
}
