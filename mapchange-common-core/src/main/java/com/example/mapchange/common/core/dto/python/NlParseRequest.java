package com.example.mapchange.common.core.dto.python;

import com.example.mapchange.common.core.dto.ElementDto;

import java.util.List;

/** 自然语言解析请求（FR-9；注入当前要素目录供 Python 做要素映射，设计 §5.3） */
public record NlParseRequest(String text, List<ElementDto> elementCatalog) {
}
