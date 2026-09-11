package com.example.mapchange.config.service;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 配置合法性校验（FR-10.3）：类型/范围/枚举/组合约束（sync-max ≤ local-max、warn < hard-limit）。
 * 单项非法抛 BizException；PUT 整组事务由调用方保证（任一项非法整组拒绝）。
 */
@Component
public class ConfigValidator {

    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$");
    private static final Pattern ABSOLUTE_URL = Pattern.compile("^https?://\\S+$");

    /** 校验单项（类型/范围/枚举） */
    public void validate(String key, String value) {
        ConfigDefaults.ConfigDef def = ConfigDefaults.find(key)
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_INPUT, "未知配置项: " + key));
        if (value == null) {
            throw new BizException(ErrorCode.INVALID_INPUT, "配置值不能为 null: " + key);
        }
        switch (def.type()) {
            case INT -> {
                long v = parseLong(key, value);
                checkRange(key, v, def);
            }
            case DOUBLE -> {
                double v = parseDouble(key, value);
                checkRange(key, v, def);
            }
            case BOOL -> {
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new BizException(ErrorCode.INVALID_INPUT, key + " 须为 true/false");
                }
            }
            case DURATION -> {
                try {
                    Duration.parse(value);
                } catch (DateTimeParseException e) {
                    throw new BizException(ErrorCode.INVALID_INPUT, key + " 须为 ISO-8601 时长（如 PT2H）");
                }
            }
            case URL_OR_EMPTY -> {
                if (!value.isEmpty() && !ABSOLUTE_URL.matcher(value).matches()) {
                    throw new BizException(ErrorCode.INVALID_INPUT,
                            key + " 须为完整 URL（协议+主机+端口+路径，约束 #10）或空串");
                }
            }
            case COLOR -> {
                if (!COLOR.matcher(value).matches()) {
                    throw new BizException(ErrorCode.INVALID_INPUT, key + " 须为 #RRGGBB[AA] 颜色值");
                }
            }
            case JSON -> {
                // 骨架期仅校验非空；JSON 结构校验由使用方负责
                if (value.isBlank()) {
                    throw new BizException(ErrorCode.INVALID_INPUT, key + " 不能为空");
                }
            }
            case STRING -> {
                if (def.allowed() != null && !def.allowed().contains(value)) {
                    throw new BizException(ErrorCode.INVALID_INPUT,
                            key + " 取值须为 " + def.allowed() + " 之一");
                }
            }
        }
    }

    /** 组级组合约束（FR-10.3）：以"修改后"的组值全集校验 */
    public void validateGroup(String group, Map<String, String> mergedValues) {
        if ("inference".equals(group)) {
            long syncMax = Long.parseLong(mergedValues.get("inference.sync-max-input-size"));
            long localMax = Long.parseLong(mergedValues.get("inference.local-max-input-size"));
            if (syncMax > localMax) {
                throw new BizException(ErrorCode.INVALID_INPUT,
                        "组合约束不满足：sync-max-input-size(%d) 须 ≤ local-max-input-size(%d)"
                                .formatted(syncMax, localMax));
            }
        }
        if ("storage".equals(group)) {
            double warn = Double.parseDouble(mergedValues.get("storage.guard.warn-threshold"));
            double hard = Double.parseDouble(mergedValues.get("storage.guard.hard-limit"));
            if (warn >= hard) {
                throw new BizException(ErrorCode.INVALID_INPUT,
                        "组合约束不满足：guard.warn-threshold 须小于 guard.hard-limit");
            }
        }
    }

    private long parseLong(String key, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.INVALID_INPUT, key + " 须为整数");
        }
    }

    private double parseDouble(String key, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.INVALID_INPUT, key + " 须为数值");
        }
    }

    private void checkRange(String key, double v, ConfigDefaults.ConfigDef def) {
        if (def.min() != null && v < def.min() || def.max() != null && v > def.max()) {
            throw new BizException(ErrorCode.INVALID_INPUT,
                    key + " 取值须在 [%s, %s]".formatted(def.min(), def.max()));
        }
    }
}
