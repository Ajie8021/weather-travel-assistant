package com.example.wea_trav_assistant.conflict;

/**
 * 冲突严重级别。
 * INFO：提示性，不影响行程；
 * WARN：建议调整，Agent 应询问用户；
 * HIGH：强烈建议调整，Agent 必须先询问再修改。
 */
public enum ConflictSeverity {
    INFO,
    WARN,
    HIGH
}