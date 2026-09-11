package com.example.mapchange.config.service;

import com.example.mapchange.common.core.api.BizException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** ConfigValidator 参数化测试（全局要求 #4：正常 + 异常分支） */
class ConfigValidatorTest {

    private final ConfigValidator validator = new ConfigValidator();

    @ParameterizedTest
    @ValueSource(strings = {"auto", "remote", "local-gpu", "local-cpu"})
    void providerEnumAccepted(String v) {
        assertDoesNotThrow(() -> validator.validate("inference.provider", v));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gpu", "AUTO", "", "remote2"})
    void providerEnumRejected(String v) {
        assertThrows(BizException.class, () -> validator.validate("inference.provider", v));
    }

    @ParameterizedTest
    @ValueSource(strings = {"256", "2048", "16384"})
    void intInRangeAccepted(String v) {
        assertDoesNotThrow(() -> validator.validate("inference.local-max-input-size", v));
    }

    @ParameterizedTest
    @ValueSource(strings = {"255", "16385", "abc", "1.5"})
    void intOutOfRangeOrMalformedRejected(String v) {
        assertThrows(BizException.class, () -> validator.validate("inference.local-max-input-size", v));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "http://oss.internal:9000/bucket", "https://a.b.c:443/p/x"})
    void urlOrEmptyAccepted(String v) {
        assertDoesNotThrow(() -> validator.validate("inference.remote.endpoint", v));
    }

    @ParameterizedTest
    @ValueSource(strings = {"oss.internal", "ftp://x", "http://"})
    void invalidUrlRejected(String v) {
        assertThrows(BizException.class, () -> validator.validate("inference.remote.endpoint", v));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT2H", "PT10M", "PT30S"})
    void durationAccepted(String v) {
        assertDoesNotThrow(() -> validator.validate("storage.sign-url-ttl", v));
    }

    @ParameterizedTest
    @ValueSource(strings = {"#00FF00", "#F0F8FF", "#228B22FF"})
    void colorAccepted(String v) {
        assertDoesNotThrow(() -> validator.validate("feature.diff-colors.added", v));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00FF00", "#FFF", "red"})
    void colorRejected(String v) {
        assertThrows(BizException.class, () -> validator.validate("feature.diff-colors.added", v));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void boolAccepted(String v) {
        assertDoesNotThrow(() -> validator.validate("features.nlp.enabled", v));
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "1", "TRUE"})
    void boolRejected(String v) {
        assertThrows(BizException.class, () -> validator.validate("features.nlp.enabled", v));
    }

    @org.junit.jupiter.api.Test
    void unknownKeyRejected() {
        assertThrows(BizException.class, () -> validator.validate("no.such.key", "1"));
    }

    @org.junit.jupiter.api.Test
    void groupCombinationSyncLeLocal() {
        Map<String, String> ok = new HashMap<>();
        ok.put("inference.sync-max-input-size", "1024");
        ok.put("inference.local-max-input-size", "2048");
        assertDoesNotThrow(() -> validator.validateGroup("inference", ok));

        Map<String, String> bad = new HashMap<>();
        bad.put("inference.sync-max-input-size", "4096");
        bad.put("inference.local-max-input-size", "2048");
        assertThrows(BizException.class, () -> validator.validateGroup("inference", bad));
    }

    @org.junit.jupiter.api.Test
    void groupCombinationWarnLtHard() {
        Map<String, String> bad = new HashMap<>();
        bad.put("storage.guard.warn-threshold", "0.9");
        bad.put("storage.guard.hard-limit", "0.9");
        assertThrows(BizException.class, () -> validator.validateGroup("storage", bad));
    }
}
