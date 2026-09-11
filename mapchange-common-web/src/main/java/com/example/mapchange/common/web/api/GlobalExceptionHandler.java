package com.example.mapchange.common.web.api;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理（设计 §6.1；依赖 spring-webmvc，仅 5 个 Servlet 业务服务注册，评审 S-02）。
 * gateway（WebFlux）不适用本 Handler，其错误响应由 GatewayErrorAttributes 整形。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：按 ErrorCode 默认 HTTP 状态输出统一响应包 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        log.warn("biz error: code={}, message={}", e.errorCode(), e.getMessage());
        return ResponseEntity.status(e.errorCode().defaultHttpStatus())
                .body(withTrace(ApiResponse.error(e.errorCode(), e.getMessage())));
    }

    /** 参数缺失/绑定错误 → INVALID_INPUT */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(withTrace(ApiResponse.error(ErrorCode.INVALID_INPUT, "参数不合法: " + e.getMessage())));
    }

    /** 上传超硬限制 → INPUT_TOO_LARGE（评审 R-04） */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(withTrace(ApiResponse.error(ErrorCode.INPUT_TOO_LARGE, "上传大小超过硬限制")));
    }

    /** 兜底 INTERNAL_ERROR */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(withTrace(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "内部错误")));
    }

    private ApiResponse<Void> withTrace(ApiResponse<Void> body) {
        String traceId = MDC.get("trace_id");
        return traceId == null ? body
                : new ApiResponse<>(body.code(), body.message(), body.taskId(), body.data(), traceId);
    }
}
