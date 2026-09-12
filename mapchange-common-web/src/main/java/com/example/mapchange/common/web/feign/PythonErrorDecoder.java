package com.example.mapchange.common.web.feign;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.api.ImageUrlExpiredException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 统一 Feign 错误解码（设计 §6.2，J-026）：
 * ① Python 内部错误码 → 对外错误码映射（§6.2 表）；
 * ② Java↔Java 跨服务调用：被调方返回统一错误包（同结构），按 code 透传；
 * ③ 无法解析时按 HTTP 状态映射兜底。
 * 调用超时/熔断由 Resilience4j 转换为 INFERENCE_TIMEOUT / INFERENCE_UNAVAILABLE（不在本类）。
 */
public class PythonErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(PythonErrorDecoder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Python 内部 code → 对外 code（《Python推理计算服务接口文档》§4 全覆盖 + 联调规范 §6 兼容项） */
    private static final Map<String, ErrorCode> PYTHON_CODE_MAPPING = Map.ofEntries(
            Map.entry("UNSUPPORTED_ELEMENT", ErrorCode.UNSUPPORTED_ELEMENTS),
            Map.entry("IMAGE_DECODE_FAILED", ErrorCode.INVALID_INPUT),
            Map.entry("GEO_EXTENT_MISMATCH", ErrorCode.GEO_EXTENT_MISMATCH),
            Map.entry("EXTENT_MISMATCH", ErrorCode.GEO_EXTENT_MISMATCH),   // 远程平台联调规范 §6 旧称兼容
            Map.entry("INPUT_TOO_LARGE", ErrorCode.INPUT_TOO_LARGE),
            Map.entry("ALIGNMENT_FAILED", ErrorCode.ALIGNMENT_FAILED),
            Map.entry("LOCATION_UNRESOLVED", ErrorCode.LOCATION_UNRESOLVED),
            Map.entry("INFERENCE_FAILED", ErrorCode.INFERENCE_UNAVAILABLE),
            Map.entry("MODEL_NOT_READY", ErrorCode.INFERENCE_UNAVAILABLE),
            Map.entry("NLU_UNAVAILABLE", ErrorCode.NLU_UNAVAILABLE),
            Map.entry("INTERNAL_ERROR", ErrorCode.INTERNAL_ERROR)
    );

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = readBody(response);
        if (body != null) {
            try {
                JsonNode node = MAPPER.readTree(body);
                JsonNode codeNode = node.get("code");
                if (codeNode == null && node.has("detail")) {
                    // FastAPI 请求校验错误（422，非业务错误包）——属请求组装缺陷，映射 INVALID_INPUT
                    return new BizException(ErrorCode.INVALID_INPUT,
                            "下游请求校验失败: " + node.path("detail").toString());
                }
                if (codeNode != null && codeNode.isTextual()) {
                    String code = codeNode.asText();
                    String message = node.path("message").asText(code);
                    String taskId = node.path("task_id").isTextual() ? node.path("task_id").asText() : null;
                    // J-02/A-3②：IMAGE_DECODE_FAILED 且存储端 403（URL 过期）→ 编排层重签重试一次
                    if ("IMAGE_DECODE_FAILED".equals(code) && message.contains("403")) {
                        return new ImageUrlExpiredException(message, taskId);
                    }
                    ErrorCode mapped = PYTHON_CODE_MAPPING.get(code);
                    if (mapped != null) {
                        return new BizException(mapped, message, taskId);
                    }
                    // Java↔Java 透传：code 本身即对外错误码
                    try {
                        return new BizException(ErrorCode.valueOf(code), message, taskId);
                    } catch (IllegalArgumentException ignored) {
                        // 未知 code，落到状态码兜底
                    }
                }
            } catch (Exception e) {
                log.warn("error body 解析失败，按 HTTP 状态兜底: {}", e.getMessage());
            }
        }
        return new BizException(statusToCode(response.status()),
                "下游调用失败: HTTP " + response.status() + " " + methodKey);
    }

    static ErrorCode statusToCode(int status) {
        return switch (status) {
            case 400 -> ErrorCode.INVALID_INPUT;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.INVALID_INPUT;
            case 409 -> ErrorCode.TASK_NOT_CANCELLABLE;
            case 422 -> ErrorCode.ALIGNMENT_FAILED;
            case 429 -> ErrorCode.RATE_LIMITED;
            case 502 -> ErrorCode.INFERENCE_UNAVAILABLE;
            case 503 -> ErrorCode.NLU_UNAVAILABLE;
            case 504 -> ErrorCode.INFERENCE_TIMEOUT;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    private String readBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try {
            return new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
