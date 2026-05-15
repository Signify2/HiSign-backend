package com.example.backend.document.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.backend.document.entity.Document;

public final class DocumentFileNameResolver {

    private static final Pattern SUBJECT_NAME_PATTERN = Pattern.compile("^_*([^_]+)");

    private DocumentFileNameResolver() {
    }

    public static String resolveDownloadFileName(Document document) {
        if (document.getType() == 1) {
            String subjectName = "Unknown";
            Matcher matcher = SUBJECT_NAME_PATTERN.matcher(document.getRequestName());
            if (matcher.find()) {
                subjectName = matcher.group(1);
            }
            return String.format("%s(%s)_%s.pdf",
                    document.getMember().getName(),
                    document.getMember().getUniqueId(),
                    subjectName);
        }
        return document.getFileName();
    }
}
