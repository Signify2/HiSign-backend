package com.example.backend.file.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class FileService {

    private final Path signatureStorageLocation;
    private final Path documentStorageLocation;
    private final Path worklogSubjectFilePath;
    private final Path researchSubjectFilePath;  // 신규 추가

    public FileService(@Value("${file.signature-dir}") String signatureDir,
                       @Value("${file.document-dir}") String documentDir,
                       @Value("${file.subjects-worklog-path}") String worklogSubjectPath,
                       @Value("${file.subjects-research-path}") String researchSubjectPath) {
        this.signatureStorageLocation = this.createDirectory(signatureDir);
        this.documentStorageLocation = this.createDirectory(documentDir);
        this.worklogSubjectFilePath = Paths.get(worklogSubjectPath).toAbsolutePath().normalize();
        this.researchSubjectFilePath = Paths.get(researchSubjectPath).toAbsolutePath().normalize();

    }

    public Path createDirectory(String dir) {
        Path directory = Paths.get(dir).toAbsolutePath().normalize();
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
        } catch (IOException e) {
            throw new RuntimeException("디렉토리를 생성할 수 없습니다: " + dir, e);
        }
        return directory;
    }

    public String storeFile(MultipartFile file, String fileType) {
        try {
            // 고유한 파일 이름 생성
            String uniqueFileName = generateUniqueFileName(Objects.requireNonNull(file.getOriginalFilename()));

            // 저장할 경로 설정
            Path targetLocation;
            if ("DOCUMENT".equalsIgnoreCase(fileType)) {
                targetLocation = documentStorageLocation.resolve(uniqueFileName);
            } else if ("SIGNATURE".equalsIgnoreCase(fileType)) {
                targetLocation = signatureStorageLocation.resolve(uniqueFileName);
            } else {
                throw new RuntimeException("파일 저장 타입 오류: " + fileType);
            }

            // 파일 저장
            file.transferTo(targetLocation.toFile());

            return uniqueFileName; // 저장된 파일 경로 반환
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 중 오류 발생: " + file.getOriginalFilename(), e);
        }
    }


    // ── 카테고리 태그 상수 ─────────────────────────────────────────
    // txt 파일에는 "[근무일지]", "[연구참여확약서]" 접두사가 붙어 있습니다.
    // 읽을 때는 태그를 제거하고, 저장할 때는 태그를 다시 붙여 파일 형식을 유지합니다.
    private static final String TAG_WORKLOG  = "[근무일지]";
    private static final String TAG_RESEARCH = "[연구참여확약서]";

    /**
     * 과목 목록 파일을 읽어 카테고리 태그가 제거된 순수 과목명 리스트를 반환합니다.
     * 예) "[근무일지]데이타구조(김동근)" → "데이타구조(김동근)"
     */
    public List<String> readSubjectList(String docType) throws IOException {
        Path targetPath = "research".equals(docType)
                ? researchSubjectFilePath
                : worklogSubjectFilePath;

        if (!Files.exists(targetPath)) return Collections.emptyList();

        String tag = "research".equals(docType) ? TAG_RESEARCH : TAG_WORKLOG;

        return Files.readAllLines(targetPath, StandardCharsets.UTF_8)
                .stream()
                .filter(line -> !line.trim().isEmpty())
                .map(line -> {
                    String trimmed = line.trim();
                    // 태그로 시작하는 줄에서만 태그를 제거
                    return trimmed.startsWith(tag)
                            ? trimmed.substring(tag.length())
                            : trimmed;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 과목명 리스트를 파일에 저장합니다.
     * 프론트에서 태그 없이 전달된 과목명에 카테고리 태그를 다시 붙여 파일 형식을 유지합니다.
     * 예) "데이타구조(김동근)" → "[근무일지]데이타구조(김동근)"
     */
    public void saveSubjectList(String docType, String content) throws IOException {
        Path targetPath = "research".equals(docType)
                ? researchSubjectFilePath
                : worklogSubjectFilePath;

        String tag = "research".equals(docType) ? TAG_RESEARCH : TAG_WORKLOG;

        // 줄 단위로 분리 → 각 줄에 태그 접두사 추가 → 다시 합치기
        String tagged = java.util.Arrays.stream(content.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> {
                    // 이미 태그가 붙어 있으면 중복 추가 방지
                    return line.startsWith(tag) ? line : tag + line;
                })
                .collect(java.util.stream.Collectors.joining("\n"));

        Files.createDirectories(targetPath.getParent());
        Files.write(targetPath,
                tagged.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }


    public void deleteFile(String fileName, String fileType) {
        try {
            // 파일 삭제할 경로 설정
            Path filePath;
            if ("DOCUMENT".equalsIgnoreCase(fileType)) {
                filePath = documentStorageLocation.resolve(fileName);
            } else if ("SIGNATURE".equalsIgnoreCase(fileType)) {
                filePath = signatureStorageLocation.resolve(fileName);
            } else {
                throw new RuntimeException("파일 삭제 타입 오류: " + fileType);
            }

            // 파일 삭제
            Files.deleteIfExists(filePath);
            System.out.println("❌ 파일 삭제 성공: " + filePath);
        } catch (IOException e) {
            throw new RuntimeException("파일 삭제 중 오류 발생: " + fileType + " - " + fileName, e);
        }
    }



    private String generateUniqueFileName(String originalFileName) {
        String onlyFileName = originalFileName.substring(0, originalFileName.lastIndexOf("."));
        String uniqueFileName = onlyFileName + UUID.randomUUID().toString();
        String extension = originalFileName.substring(originalFileName.lastIndexOf("."));

        return uniqueFileName + extension;
    }

    // ✅ 파일 저장 경로 반환
    public Path getSignatureFilePath(String fileName) {
        return signatureStorageLocation.resolve(fileName);
    }

    public Path getDocumentFilePath(String fileName) {
        return documentStorageLocation.resolve(fileName);
    }


}