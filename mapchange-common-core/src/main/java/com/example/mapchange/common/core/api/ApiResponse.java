package com.example.mapchange.common.core.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应包（评审 R-10②）：taskId 仅错误/任务场景有值，其余为 null。
 * JSON 序列化统一 snake_case（Jackson PropertyNamingStrategies.SNAKE_CASE 全局配置），
 * 与设计 §6.1 错误包字段（code/message/task_id/trace_id）一致。
 * traceId 由 web 层（GlobalExceptionHandler / TaskIdMdcFilter 所在服务）填充，core 保持零依赖。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, String taskId, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", null, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(code.name(), message, null, null, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, String taskId) {
        return new ApiResponse<>(code.name(), message, taskId, null, null);
    }
}
