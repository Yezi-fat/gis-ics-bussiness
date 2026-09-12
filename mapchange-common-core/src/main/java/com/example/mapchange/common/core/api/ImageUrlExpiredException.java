package com.example.mapchange.common.core.api;

/**
 * 影像预签名 URL 过期/失效（Python IMAGE_DECODE_FAILED 且 message 携带存储端 HTTP 403，J-02/A-3②）：
 * 编排层捕获后重签 URL 并重试一次；仍失败则按 errorCode=INVALID_INPUT 语义登记。
 */
public class ImageUrlExpiredException extends BizException {

    public ImageUrlExpiredException(String message, String taskId) {
        super(ErrorCode.INVALID_INPUT, message, taskId);
    }
}
