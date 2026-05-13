package com.example.backend.mail.template;

import org.springframework.util.StringUtils;

public final class MailTemplateRenderer {

    private static final String COMMON_STYLES =
            "  body { margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f4f8fb; }"
                    + "  .container { max-width: 600px; margin: auto; background-color: #ffffff; border-radius: 10px; padding: 20px; box-shadow: 0px 4px 10px rgba(0,0,0,0.1); }"
                    + "  .btn { background-color: #0366d6; color: #ffffff; text-decoration: none; padding: 12px 20px; border-radius: 5px; font-size: 18px; display: inline-block; margin-top: 20px; }"
                    + "  .info-table { width: 100%; border-collapse: collapse; margin: 16px 0; background-color: #fcfdff; }"
                    + "  .info-table th, .info-table td { border: 1px solid #eef2f8; padding: 10px 12px; font-size: 15px; text-align: left; vertical-align: top; }"
                    + "  .info-table th { width: 120px; background-color: #f8fbff; color: #5b8fc7; font-weight: 600; }"
                    + "  .info-table td { color: #444; background-color: #ffffff; }"
                    + "  .info-table .expiry-row th { color: #c74b4b; font-weight: 700; }"
                    + "  .info-table .expiry-row td { color: #b42318; font-weight: 700; }"
                    + "  .notice-block { background-color: #fff6e5; padding: 15px; border-radius: 5px; border-left: 5px solid #ff9900; margin: 15px 0; }"
                    + "  .reason-block { background-color: #ffe5e5; padding: 15px; border-radius: 5px; border-left: 5px solid #ff4d4d; margin: 15px 0; }"
                    + "  @media (max-width: 600px) {"
                    + "    .container { padding: 10px; }"
                    + "    .btn { font-size: 16px; padding: 10px 16px; }"
                    + "    .info-table th, .info-table td { font-size: 14px; }"
                    + "  }";

    private MailTemplateRenderer() {
    }

    public static String renderSignatureRequest(SignatureRequestTemplate template) {
        String passwordBlock = "";
        if (StringUtils.hasText(template.getAccessPassword())) {
            passwordBlock =
                    "<div class='notice-block'>"
                            + "<p style='font-size:16px; font-weight:bold; color:#ff9900; margin:0 0 8px 0;'>접근 비밀번호</p>"
                            + "<p style='font-size:18px; font-weight:bold; color:#333; margin:0; text-align:center;'>"
                            + escapeHtml(template.getAccessPassword())
                            + "</p>"
                            + "</div>";
        }

        return wrapHtml(
                "HISign 전자 서명 요청",
                "#0366d6",
                "<p style='font-size:16px; color:#333;'>안녕하세요, 사랑 · 겸손 · 봉사 정신의 한동대학교 전자 서명 서비스 <b>HISign</b>입니다.</p>"
                        + "<p style='font-size:16px; color:#333;'><b>"
                        + escapeHtml(template.getSenderName())
                        + "</b>님으로부터 서명 요청이 도착했습니다. 아래 정보를 확인한 뒤 서명을 진행해 주세요.</p>"
                        + renderInfoTable(
                                infoRow("요청자", template.getSenderName()),
                                infoRowHtml("서명자", template.getSigners()),
                                infoRow("문서명", template.getDocumentName()),
                                infoRow("요청 내용", template.getRequestDescription()),
                                emphasizedInfoRow("만료 일시", template.getExpiredAt())
                        )
                        + passwordBlock
                        + "<div style='text-align:center;'>"
                        + "<a href='"
                        + escapeHtmlAttribute(template.getSignatureUrl())
                        + "' class='btn' style='color: #ffffff;'>서명하기</a>"
                        + "</div>"
        );
    }

    public static String renderCompletedSignature(CompletedSignatureTemplate template) {
        String attachmentMessage = template.isAttachmentIncluded()
                ? "<p style='font-size:16px; color:#333;'>완료된 서명 문서가 첨부되어 있으니 확인해 주세요.</p>"
                : "<p style='font-size:16px; color:#333;'>모든 서명이 완료된 문서가 정상적으로 처리되었습니다.</p>";

        return wrapHtml(
                "HISign 서명 완료 안내",
                "#0366d6",
                "<p style='font-size:16px; color:#333;'>안녕하세요, 사랑 · 겸손 · 봉사 정신의 한동대학교 전자 서명 서비스 <b>HISign</b>입니다.</p>"
                        + renderInfoTable(
                                infoRow("요청자", template.getRequesterName()),
                                infoRow("문서명", template.getDocumentName()),
                                infoRow("처리 상태", "모든 서명 완료")
                        )
                        + attachmentMessage
                        + "<p style='font-size:16px; color:#333;'>이용해 주셔서 감사합니다.</p>"
        );
    }

    public static String renderRejectedSignature(RejectedSignatureTemplate template) {
        return wrapHtml(
                "HISign 서명 반려 안내",
                "#d9534f",
                "<p style='font-size:16px; color:#333;'>안녕하세요, <b>HISign</b> 전자 서명 서비스입니다.</p>"
                        + renderInfoTable(
                                infoRow("문서명", template.getDocumentName()),
                                infoRow("반려자", template.getRejectorName())
                        )
                        + "<div class='reason-block'>"
                        + "<p style='font-size:16px; font-weight:bold; color:#d9534f; margin:0 0 8px 0;'>반려 사유</p>"
                        + "<p style='font-size:16px; color:#333; margin:0;'>"
                        + escapeHtml(template.getRejectReason())
                        + "</p>"
                        + "</div>"
        );
    }

    private static String wrapHtml(String title, String titleColor, String bodyContent) {
        return "<!DOCTYPE html>"
                + "<html lang='ko'>"
                + "<head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<style>"
                + COMMON_STYLES
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class='container'>"
                + "<h2 style='color:"
                + titleColor
                + "; text-align:center;'>"
                + escapeHtml(title)
                + "</h2>"
                + bodyContent
                + "<p style='font-size:14px; color:#666; text-align:center;'>※ 본 메일은 자동 발송되었으며 회신이 불가능합니다.</p>"
                + "</div>"
                + "</body>"
                + "</html>";
    }

    private static String emphasizedInfoRow(String label, String value) {
        return "<tr class='expiry-row'><th>"
                + escapeHtml(label)
                + "</th><td>"
                + escapeHtml(value)
                + "</td></tr>";
    }

    private static String renderInfoTable(String... rows) {
        StringBuilder table = new StringBuilder();
        table.append("<table class='info-table'>");
        for (String row : rows) {
            table.append(row);
        }
        table.append("</table>");
        return table.toString();
    }

    private static String infoRow(String label, String value) {
        return "<tr><th>"
                + escapeHtml(label)
                + "</th><td>"
                + escapeHtml(value)
                + "</td></tr>";
    }

    private static String infoRowHtml(String label, String htmlValue) {
        return "<tr><th>"
                + escapeHtml(label)
                + "</th><td>"
                + (StringUtils.hasText(htmlValue) ? htmlValue : "-")
                + "</td></tr>";
    }

    private static String escapeHtml(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String escapeHtmlAttribute(String value) {
        return escapeHtml(value);
    }

    public static final class SignatureRequestTemplate {
        private final String senderName;
        private final String documentName;
        private final String requestDescription;
        private final String expiredAt;
        private final String signatureUrl;
        private final String accessPassword;
        private final String signers;

        public SignatureRequestTemplate(
                String senderName,
                String documentName,
                String requestDescription,
                String expiredAt,
                String signatureUrl,
                String accessPassword,
                String signers
        ) {
            this.senderName = senderName;
            this.documentName = documentName;
            this.requestDescription = requestDescription;
            this.expiredAt = expiredAt;
            this.signatureUrl = signatureUrl;
            this.accessPassword = accessPassword;
            this.signers = signers;
        }

        public String getSenderName() {
            return senderName;
        }

        public String getDocumentName() {
            return documentName;
        }

        public String getRequestDescription() {
            return requestDescription;
        }

        public String getExpiredAt() {
            return expiredAt;
        }

        public String getSignatureUrl() {
            return signatureUrl;
        }

        public String getAccessPassword() {
            return accessPassword;
        }

        public String getSigners() {
            return signers;
        }
    }

    public static final class CompletedSignatureTemplate {
        private final String requesterName;
        private final String documentName;
        private final boolean attachmentIncluded;

        public CompletedSignatureTemplate(
                String requesterName,
                String documentName,
                boolean attachmentIncluded
        ) {
            this.requesterName = requesterName;
            this.documentName = documentName;
            this.attachmentIncluded = attachmentIncluded;
        }

        public String getRequesterName() {
            return requesterName;
        }

        public String getDocumentName() {
            return documentName;
        }

        public boolean isAttachmentIncluded() {
            return attachmentIncluded;
        }
    }

    public static final class RejectedSignatureTemplate {
        private final String documentName;
        private final String rejectorName;
        private final String rejectReason;

        public RejectedSignatureTemplate(
                String documentName,
                String rejectorName,
                String rejectReason
        ) {
            this.documentName = documentName;
            this.rejectorName = rejectorName;
            this.rejectReason = rejectReason;
        }

        public String getDocumentName() {
            return documentName;
        }

        public String getRejectorName() {
            return rejectorName;
        }

        public String getRejectReason() {
            return rejectReason;
        }
    }
}
