package com.example.backend.document.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.backend.document.entity.Document;
import com.example.backend.document.entity.enums.DocumentType;

public final class DocumentFileNameResolver {

    private static final Pattern SUBJECT_NAME_PATTERN = Pattern.compile("^_*([^_]+)");

    private DocumentFileNameResolver() {
    }

    public static String resolveDownloadFileName(Document document) {
        if (document.getType() == DocumentType.WORKLOG || document.getType() == DocumentType.RESEARCH) {
            int year = document.getCreatedAt().getYear();
            int month = document.getCreatedAt().getMonthValue();
            return String.format("%s(%s)_%s_%d년_%d월.pdf",
                    document.getMember().getName(),
                    document.getMember().getUniqueId(),
                    document.getRequestName(),
                    year,
                    month);
        }
        return document.getFileName();
    }

    // resolveSubjectFileName - 제목용
    public static String resolveSubjectFileName(Document document) {
        if (document.getType() == DocumentType.WORKLOG || document.getType() == DocumentType.RESEARCH) {
            return document.getRequestName() + ".pdf";  // 전체 requestName 사용
        }
        return document.getFileName();
    }
}
