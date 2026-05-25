package com.example.backend.document.entity.enums;

/**
 * BASIC   : 일반 작업 (basicTask) — 관리자 목록 미노출, 서명 완료 시 메일 발송
 * WORKLOG : TA 근무일지           — 관리자 목록 노출, 서명 완료 시 메일 미발송
 * RESEARCH: 연구참여확약서         — 관리자 목록 노출, 서명 완료 시 메일 미발송
 */
public enum DocumentType {
    BASIC,
    WORKLOG,
    RESEARCH
}