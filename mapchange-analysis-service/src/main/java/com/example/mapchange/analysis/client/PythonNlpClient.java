package com.example.mapchange.analysis.client;

import com.example.mapchange.common.core.dto.python.NlParseRequest;
import com.example.mapchange.common.core.dto.python.NlParseResponse;
import com.example.mapchange.common.core.dto.python.PythonHealthResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Python nlp-service 客户端（配置地址直连；/nlp/* 属内部契约；FR-9 转发，Java 不做解析逻辑） */
@FeignClient(name = "python-nlp", url = "${python.nlp.url}", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface PythonNlpClient {

    @PostMapping("/nlp/parse")
    NlParseResponse parse(@RequestBody NlParseRequest req);

    @GetMapping("/health")
    PythonHealthResponse health();
}
