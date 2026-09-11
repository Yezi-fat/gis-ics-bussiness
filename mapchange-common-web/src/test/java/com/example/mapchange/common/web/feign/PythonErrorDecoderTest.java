package com.example.mapchange.common.web.feign;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PythonErrorDecoder 测试（J-026：§6.2 映射表七行全覆盖 + 状态兜底） */
class PythonErrorDecoderTest {

    private final PythonErrorDecoder decoder = new PythonErrorDecoder();

    private Response response(int status, String body) {
        return Response.builder()
                .status(status)
                .request(Request.create(Request.HttpMethod.GET, "/x", Map.of(), null, null, null))
                .body(body == null ? null : body.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @ParameterizedTest
    @CsvSource({
            "UNSUPPORTED_ELEMENT,UNSUPPORTED_ELEMENTS",
            "IMAGE_DECODE_FAILED,INVALID_INPUT",
            "EXTENT_MISMATCH,GEO_EXTENT_MISMATCH",
            "MODEL_NOT_READY,INFERENCE_UNAVAILABLE",
            "INFERENCE_FAILED,INFERENCE_UNAVAILABLE",
            "NLU_UNAVAILABLE,NLU_UNAVAILABLE"
    })
    void pythonCodeMapping(String pythonCode, String expected) {
        String body = "{\"code\":\"%s\",\"message\":\"boom\"}".formatted(pythonCode);
        BizException e = (BizException) decoder.decode("m", response(400, body));
        assertEquals(ErrorCode.valueOf(expected), e.errorCode());
        assertEquals("boom", e.getMessage());
    }

    @Test
    void javaUnifiedPackagePassThrough() {
        // Java 服务返回的统一错误包（code 本身即对外错误码）按 code 透传
        String body = "{\"code\":\"GEO_EXTENT_MISMATCH\",\"message\":\"范围不一致\",\"task_id\":\"t-1\"}";
        BizException e = (BizException) decoder.decode("m", response(400, body));
        assertEquals(ErrorCode.GEO_EXTENT_MISMATCH, e.errorCode());
        assertEquals("t-1", e.taskId());
    }

    @Test
    void unknownCodeFallsBackToHttpStatus() {
        BizException e = (BizException) decoder.decode("m", response(504, "{\"code\":\"XXX\"}"));
        assertEquals(ErrorCode.INFERENCE_TIMEOUT, e.errorCode());
    }

    @Test
    void emptyBodyFallsBackToHttpStatus() {
        assertEquals(ErrorCode.INTERNAL_ERROR,
                ((BizException) decoder.decode("m", response(500, null))).errorCode());
        assertEquals(ErrorCode.RATE_LIMITED,
                ((BizException) decoder.decode("m", response(429, null))).errorCode());
        assertEquals(ErrorCode.UNAUTHORIZED,
                ((BizException) decoder.decode("m", response(401, null))).errorCode());
    }
}
