package com.example.mapchange.common.core.api;

/**
 * 业务异常：携带统一错误码，由 common-web 的 GlobalExceptionHandler 转换为统一响应包。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String taskId;

    public BizException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BizException(ErrorCode code, String message, String taskId) {
        super(message);
        this.errorCode = code;
        this.taskId = taskId;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String taskId() {
        return taskId;
    }
}
