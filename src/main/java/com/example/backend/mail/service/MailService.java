package com.example.backend.mail.service;

import com.example.backend.auth.util.EncryptionUtil;
import com.example.backend.document.entity.Document;
import com.example.backend.document.support.DocumentFileNameResolver;
import com.example.backend.mail.support.MailSenderResolver;
import com.example.backend.mail.template.MailTemplateRenderer;
import com.example.backend.mail.template.MailTemplateRenderer.CompletedSignatureTemplate;
import com.example.backend.mail.template.MailTemplateRenderer.RejectedSignatureTemplate;
import com.example.backend.mail.template.MailTemplateRenderer.SignatureRequestTemplate;
import com.example.backend.signatureRequest.entity.SignatureRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private static final DateTimeFormatter EXPIRATION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E) a h시 mm분");

    @Value("${custom.host.client}")
    private String client;
    @Value("${spring.mail.username}")
    private String emailAdress;
    private final JavaMailSender mailSender;
    private final EncryptionUtil encryptionUtil;
    private final MailSenderResolver mailSenderResolver;

    private void sendSignatureRequestEmail(
            SignatureRequest request,
            String senderName,
            String password,
            String signersText
    ) throws Exception {
        Document document = request.getDocument();
        String requesterName = resolveRequesterName(document, senderName);
        String senderDisplayName = mailSenderResolver.resolveDisplayName(requesterName);
        String recipientEmail = request.getSignerEmail();
        String encryptedToken = encryptionUtil.encryptUUID(request.getToken());
        String signatureUrl = client + "/hisign/checkEmail?token=" + encryptedToken;
        String formattedDeadline = request.getExpiredAt().format(EXPIRATION_FORMATTER);
        String accessPassword = shouldIncludePassword(password) ? password : null;

        String documentName = safeText(DocumentFileNameResolver.resolveDownloadFileName(document));

        SignatureRequestTemplate template = new SignatureRequestTemplate(
                requesterName,
                documentName,
                document.getDescription(),
                formattedDeadline,
                signatureUrl,
                accessPassword,
                signersText
        );

        sendEmail(
                recipientEmail,
                senderDisplayName,
                "[HISign] " + requesterName + " 님으로부터 [" + documentName + "] 서명 요청입니다.",
                MailTemplateRenderer.renderSignatureRequest(template)
        );
    }

    public void sendSignatureRequestEmails(String senderName, String requestName, List<SignatureRequest> requests, String password) throws Exception {
        String signersText = formatSigners(requests);
        for (SignatureRequest request : requests) {
            sendSignatureRequestEmail(request, senderName, password, signersText);
        }
    }

    public void sendSignatureRequestEmailsWithoutPassword(String senderName, String requestName, List<SignatureRequest> requests) throws Exception {
        sendSignatureRequestEmails(senderName, requestName, requests, "NONE");
    }

    public void sendCompletedSignatureMail(String recipientEmail, Document document, byte[] pdfData) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setHeader("Message-ID", "<" + UUID.randomUUID() + "@hisign.domain>");
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String requesterName = resolveRequesterName(document, null);
            String documentName = safeText(DocumentFileNameResolver.resolveDownloadFileName(document));
            boolean attachmentIncluded = document.getType() != 1;

            helper.setTo(recipientEmail);
            helper.setSubject("[HISign] " + requesterName + " 님의 [" + documentName + "] 모든 서명이 완료되었습니다.");
            setFromWithDisplayName(helper, requesterName);

            CompletedSignatureTemplate template = new CompletedSignatureTemplate(
                    requesterName,
                    documentName,
                    attachmentIncluded
            );
            helper.setText(MailTemplateRenderer.renderCompletedSignature(template), true);

            if (attachmentIncluded) {
                helper.addAttachment(documentName, new ByteArrayResource(pdfData));
            } else {
                log.info("타입 1 문서: PDF 첨부 생략");
            }

            mailSender.send(message);
        } catch (MailSendException e) {
            log.error("이메일 전송 실패 (SMTP 문제): {}", e.getMessage(), e);
            throw new RuntimeException("이메일 전송 중 SMTP 오류 발생", e);
        } catch (MessagingException e) {
            log.error("이메일 메시지 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("이메일 메시지 생성 실패", e);
        }
    }

    public void sendRejectedSignatureMail(String recipientEmail, Document document, String rejectorName, String reason) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setHeader("Message-ID", "<" + UUID.randomUUID() + "@hisign.domain>");

            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String senderDisplayName = mailSenderResolver.resolveDisplayName(rejectorName);
            String documentName = safeText(DocumentFileNameResolver.resolveDownloadFileName(document));
            String rejectReason = StringUtils.hasText(reason) ? reason : "사유 없음";

            helper.setTo(recipientEmail);
            helper.setSubject("[HISign] " + senderDisplayName + " 님으로부터 [" + documentName + "] 서명 요청이 반려되었습니다.");
            setFromWithDisplayName(helper, senderDisplayName);

            RejectedSignatureTemplate template = new RejectedSignatureTemplate(
                    documentName,
                    senderDisplayName,
                    rejectReason
            );
            helper.setText(MailTemplateRenderer.renderRejectedSignature(template), true);
            mailSender.send(message);

        } catch (MailSendException e) {
            log.error("반려 이메일 전송 실패 (SMTP 문제): {}", e.getMessage(), e);
            throw new RuntimeException("이메일 전송 중 SMTP 오류 발생", e);
        } catch (MessagingException e) {
            log.error("반려 이메일 메시지 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("이메일 메시지 생성 실패", e);
        }
    }

    private void sendEmail(String to, String senderDisplayName, String subject, String emailContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            setFromWithDisplayName(helper, senderDisplayName);
            helper.setText(emailContent, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("이메일 전송 실패: " + e.getMessage(), e);
        }
    }

    private void setFromWithDisplayName(MimeMessageHelper helper, String senderDisplayName) throws MessagingException {
        try {
            helper.setFrom(emailAdress, senderDisplayName);
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("발신자 이름 인코딩에 실패했습니다.", e);
        }
    }

    private String resolveRequesterName(Document document, String fallbackName) {
        if (document != null && document.getMember() != null && StringUtils.hasText(document.getMember().getName())) {
            return document.getMember().getName().trim();
        }

        if (StringUtils.hasText(fallbackName)) {
            return fallbackName.trim();
        }

        return "HISign";
    }

    private boolean shouldIncludePassword(String password) {
        return StringUtils.hasText(password) && !"NONE".equals(password);
    }

    private String formatSigners(List<SignatureRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return "-";
        }

        return requests.stream()
                .map(request -> escapeForHtml(request.getSignerName()))
                .collect(Collectors.joining("<br>"));
    }

    private String escapeForHtml(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }

        return value.trim()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String safeText(String input) {
        return input != null ? input : "";
    }
}
