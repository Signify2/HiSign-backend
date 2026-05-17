package com.example.backend.mail.support;

public final class MailServiceName {

    /** Display name used in mail subjects, body, and sender fallback */
    public static final String SERVICE_NAME = "HiSign";

    private MailServiceName() {}

    public static String subjectPrefix() {
        return "[" + SERVICE_NAME + "] ";
    }
}
