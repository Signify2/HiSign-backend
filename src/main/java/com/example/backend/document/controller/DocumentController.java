package com.example.backend.document.controller;

import com.example.backend.auth.dto.AuthDto;
import com.example.backend.document.dto.UploadRequestDTO;
import com.example.backend.document.entity.Document;
import com.example.backend.document.service.DocumentService;
import com.example.backend.file.service.FileService;
import com.example.backend.mail.service.MailService;
import com.example.backend.member.entity.Member;
import com.example.backend.member.service.MemberService;
import com.example.backend.signature.DTO.SignatureDTO;
import com.example.backend.signature.entity.Signature;
import com.example.backend.signature.service.SignatureService;
import com.example.backend.signatureRequest.service.SignatureRequestService;
import com.example.backend.pdf.service.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final FileService fileService;
    private final DocumentService documentService;
    private final MemberService memberService;
    private final SignatureRequestService signatureRequestService;
    private final SignatureService signatureService;
    private final PdfService pdfService;
    private final MailService mailService;


    @PostMapping(value = "/full-upload", consumes = {"multipart/form-data"})
    @Transactional
    public  ResponseEntity<Map<String, Object>> fullUpload(
            @RequestParam("file") MultipartFile file,
            @RequestPart("dto") UploadRequestDTO dto
    ) {
        log.info("📥 fullUpload 요청 수신 - uniqueId: {}", dto.getUniqueId());
        log.info("📦 파일 이름: {}", file.getOriginalFilename());
        if(dto.getIsSelfIncluded()) log.debug("본인 서명 포함 작업");
        else log.debug("본인 서명 미 포함 작업");
        try {
            // 1. 파일 저장
            String storedFileName = fileService.storeFile(file, "DOCUMENT");

            // 2. 업로드한 사용자 조회
            Member owner = memberService.findByUniqueId(dto.getUniqueId());

            // 3. 문서 생성 및 저장
            Document document = new Document();
            document.setRequestName(dto.getRequestName());
            document.setFileName(file.getOriginalFilename());
            document.setSavedFileName(storedFileName);
            if(dto.getIsSelfIncluded()) document.setStatus(8);
            else document.setStatus(0);
            document.setIsRejectable(dto.getIsRejectable());
            document.setDescription(dto.getDescription());
            document.setType(dto.getType());
            document.setCreatedAt(LocalDateTime.now());
            document.setUpdatedAt(LocalDateTime.now());
            document.setMember(owner);

            documentService.save(document);

            // 4. 타입에 따라 분기
            if (document.getType() == 1) {
                // 타입 1 → 검토 요청만 (메일 ❌)
                if(!dto.getIsSelfIncluded()) documentService.requestCheckingById(document.getId());
                signatureRequestService.saveSignatureRequestAndFields(document, dto.getSigners(), dto.getPassword(), dto.getExpirationDateTime());
            } else {
                // 타입 1이 아닐 경우 → 저장 + 메일 발송
                signatureRequestService.saveRequestsAndSendMail(document, dto.getSigners(), dto.getPassword(), dto.getMemberName(), dto.getExpirationDateTime());
            }

            // ✅ 문서 ID 포함한 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("message", "문서 업로드 및 서명 요청이 성공적으로 처리되었습니다.");
            response.put("documentId", document.getId());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("사용자 조회 실패: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            log.error("❌ fullUpload 실패", e);
            throw new RuntimeException("fullUpload 처리 중 오류 발생: " + e.getMessage(), e);
        }
    }
    // 요청한 문서 리스트
    @GetMapping("/requested-documents")
    public List<Map<String, Object>> getRequestedDocuments(@RequestParam(value = "searchQuery", required = false) String searchQuery) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthDto)) {
            throw new IllegalStateException("사용자의 인증 정보가 유효하지 않습니다.");
        }

        AuthDto authDto = (AuthDto) authentication.getPrincipal();
        String uniqueId = authDto.getUniqueId();

        if (uniqueId == null) {
            throw new IllegalStateException("사용자의 고유 ID를 찾을 수 없습니다.");
        }

        log.debug("요청한 문서 리스트 요청 - UniqueId: {}", uniqueId);

        List<Map<String, Object>> documents = documentService.getDocumentsByUniqueId(uniqueId);

        if (searchQuery != null && !searchQuery.isEmpty()) {
            documents = documents.stream()
                    .filter(doc -> doc.get("requestName").toString().toLowerCase().contains(searchQuery.toLowerCase()))
                    .collect(Collectors.toList());
        }
        return documents;
    }


    @GetMapping("/received-with-requester")
    public List<Map<String, Object>> getReceivedDocuments(@RequestParam(value = "searchQuery", required = false) String searchQuery) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof AuthDto)) {
            throw new IllegalStateException("사용자의 이메일 정보를 찾을 수 없습니다.");
        }

        String email = ((AuthDto) principal).getEmail();

        log.debug("[DEBUG] 요청받은 문서 리스트 요청 - 이메일: {}", email);

        List<Map<String, Object>> documents = documentService.getDocumentsWithRequesterInfoBySignerEmail(email);

        if (searchQuery != null && !searchQuery.isEmpty()) {
            documents = documents.stream()
                    .filter(doc -> doc.get("requestName").toString().toLowerCase().contains(searchQuery.toLowerCase()))
                    .collect(Collectors.toList());
        }

        return documents;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> getDocument(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));

        Resource resource = documentService.loadFileAsResource(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 읽어올 수 없습니다."));

        try {
            String encodedFileName = URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8.toString())
                    .replace("+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName);

            return ResponseEntity.ok()
                    .headers(headers)
                    .header("Content-Type", "application/pdf")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/{id}/signed-preview")
    public ResponseEntity<byte[]> previewSignedDocument(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));

        try {
            List<Signature> signatures = signatureService.getSignaturesForDocument(id);
            byte[] pdfData = pdfService.generateReviewDocument(id, signatures);

            String encodedFileName = URLEncoder.encode(document.getFileName(), String.valueOf(StandardCharsets.UTF_8))
                    .replace("+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.inline()
                    .filename(encodedFileName, StandardCharsets.UTF_8)
                    .build());

            return new ResponseEntity<>(pdfData, headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "서명 포함 문서 미리보기 실패", e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDocument(@PathVariable Long id, @RequestParam("viewType") String viewType) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthDto)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("인증 정보가 없습니다.");
        }

        AuthDto authDto = (AuthDto) authentication.getPrincipal();
        String uniqueId = authDto.getUniqueId();
        boolean deleted = documentService.deleteDocumentById(id,uniqueId,viewType);
        if (deleted) {
            return ResponseEntity.ok("문서 및 관련 서명 요청이 성공적으로 삭제되었습니다.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("해당 문서를 찾을 수 없습니다.");
        }
    }

    //서명용 문서 불러오기
    @GetMapping("/sign/{id}")
    public ResponseEntity<Resource> getDocumentForSigning(@PathVariable Long id) throws UnsupportedEncodingException {
        Resource resource = documentService.loadFileAsResource(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 읽어올 수 없습니다."));

        // 문서의 원본 파일명 조회
        String originalFileName = documentService.getOriginalFileName(id);
        String encodedFileName = URLEncoder.encode(Objects.requireNonNull(originalFileName), String.valueOf(StandardCharsets.UTF_8))
                .replace("+", "%20"); // 공백 변환

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName);
        headers.add(HttpHeaders.CONTENT_TYPE, "application/pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }


    @GetMapping("/info/{id}")
    public ResponseEntity<Map<String, Object>> getDocumentInfo(@PathVariable Long id) {
        Map<String, Object> documentInfo = documentService.getDocumentInfo(id);
        return ResponseEntity.ok(documentInfo);
    }

    @GetMapping("/admin_document")
    public ResponseEntity<List<Map<String, Object>>> getAdminDocuments(@RequestParam(value = "searchQuery", required = false) String searchQuery) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthDto)) {
            throw new IllegalStateException("사용자의 인증 정보가 유효하지 않습니다.");
        }

        AuthDto authDto = (AuthDto) authentication.getPrincipal();
        String uniqueId = authDto.getUniqueId();

        List<Map<String, Object>> documents = documentService.getAllAdminDocuments(uniqueId);

        if (searchQuery != null && !searchQuery.isEmpty()) {
            documents = documents.stream()
                    .filter(doc -> doc.get("requestName").toString().toLowerCase().contains(searchQuery.toLowerCase()))
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/request-check/{id}")
    public ResponseEntity<String> getDocumentForRequestCheck(@PathVariable Long id) {
        boolean requested = documentService.requestCheckingById(id);
        if (requested) {
            return ResponseEntity.ok("문서에 대한 검토가 성공적으로 요청되었습니다.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("해당 문서를 찾을 수 없습니다.");
        }
    }

    @PutMapping("/{documentId}/reject")
    public ResponseEntity<?> rejectDocumentReview(
            @PathVariable Long documentId,
            @RequestBody  Map<String, String> body) {
        Document document = documentService.getDocumentById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));
        String reason = body.get("reason");

        mailService.sendRejectedSignatureMail(document.getMember().getEmail(), document, "관리자", reason);
        documentService.rejectDocument(documentId, reason);

        return ResponseEntity.ok("문서가 성공적으로 반려 처리되었습니다.");
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));
        try{
            List<Signature> signatures = signatureService.getSignaturesForDocument(id);

            // ✅ 5. PDF 생성
            byte[] pdfData = pdfService.generateSignedDocument(id, signatures);

            String fileName= "Unknown";
            if(document.getType() == 1 ) {
                // 1. 변수를 조건문 밖에서 미리 선언 (초기값 설정)
                String subjectName = "Unknown";
                Pattern pattern = Pattern.compile("^_*([^_]+)");
                Matcher matcher = pattern.matcher(document.getRequestName());
                // 2. 조건문 안에서 값을 할당
                if (matcher.find()) {
                    subjectName = matcher.group(1);
                }
                fileName = String.format("%s(%s)_%s.pdf",
                        document.getMember().getName(),
                        document.getMember().getUniqueId(),
                        subjectName);
            } else {
                fileName = document.getFileName();
            }

            String encodedFileName = URLEncoder.encode(fileName, "UTF-8")
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);

            return new ResponseEntity<>(pdfData, headers, HttpStatus.OK);
        }catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "서명된 문서 다운로드 실패", e);
        }
    }

    @PostMapping("/download/zip")
    public ResponseEntity<byte[]> downloadDocumentsAsZip(@RequestBody List<Long> documentIds) {
        try {
            ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
            ZipOutputStream zipOut = new ZipOutputStream(zipBaos);
            Map<String, Integer> fileNameCount = new HashMap<>();

            for (Long id : documentIds) {
                Document document = documentService.getDocumentById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));

                List<Signature> signatures = signatureService.getSignaturesForDocument(id);
                byte[] pdfData = pdfService.generateSignedDocument(id, signatures);

                String baseName  = document.getRequestName();
                if (!baseName .toLowerCase().endsWith(".pdf")) {
                    baseName  += ".pdf";
                }

                // 중복 처리
                String finalName = baseName;
                if (fileNameCount.containsKey(baseName)) {
                    int count = fileNameCount.get(baseName) + 1;
                    fileNameCount.put(baseName, count);

                    // 파일명 확장자 분리 후 (1), (2) 붙이기
                    int dotIndex = baseName.lastIndexOf('.');
                    String nameOnly = baseName.substring(0, dotIndex);
                    String ext = baseName.substring(dotIndex);

                    finalName = nameOnly + " (" + count + ")" + ext;
                } else {
                    fileNameCount.put(baseName, 0); // 첫 등장 기록
                }

                // ZIP에 엔트리 추가
                zipOut.putNextEntry(new ZipEntry(finalName));
                zipOut.write(pdfData);
                zipOut.closeEntry();
            }

            zipOut.close();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition
                    .attachment()
                    .filename("documents.zip")
                    .build());

            return new ResponseEntity<>(zipBaos.toByteArray(), headers, HttpStatus.OK);

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ZIP 압축 중 오류 발생", e);
        }
    }

}


