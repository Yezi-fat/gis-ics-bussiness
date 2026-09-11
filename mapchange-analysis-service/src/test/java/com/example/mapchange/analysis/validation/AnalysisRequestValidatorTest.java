package com.example.mapchange.analysis.validation;

import com.example.mapchange.analysis.client.ConfigClient;
import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.ElementDto;
import com.example.mapchange.common.core.geo.GeoExtent;
import com.example.mapchange.common.core.geo.TileRange;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** AnalysisRequestValidator 测试（J-021 验收：坐标/多期/要素目录错误码） */
class AnalysisRequestValidatorTest {

    private AnalysisRequestValidator validator;

    @BeforeEach
    void setUp() {
        ConfigClient configClient = Mockito.mock(ConfigClient.class);
        when(configClient.elementCatalog()).thenReturn(ApiResponse.ok(List.of(
                new ElementDto("forest", "森林", "#228B22", 1, true),
                new ElementDto("building", "建筑", "#CD853F", 4, true))));
        RemoteConfigCache configCache = Mockito.mock(RemoteConfigCache.class);
        when(configCache.getOrDefault(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
        validator = new AnalysisRequestValidator(configClient, configCache);
    }

    // ---- 要素目录（FR-6.6） ----

    @Test
    void supportedElementsPass() {
        assertEquals(2, validator.validateElements("forest,building").size());
    }

    @Test
    void unsupportedElementsRejected() {
        BizException e = assertThrows(BizException.class,
                () -> validator.validateElements("forest,water"));
        assertEquals(ErrorCode.UNSUPPORTED_ELEMENTS, e.errorCode());
    }

    @Test
    void emptyElementsRejected() {
        assertThrows(BizException.class, () -> validator.validateElements(""));
    }

    // ---- 坐标一致性（§3.1.5） ----

    @Test
    void tileExtentConsistency() {
        // z=1, x=y=0 → [-180,-90,0,90]
        TileRange tr = validator.parseTileRange("{\"z\":1,\"x_min\":0,\"x_max\":0,\"y_min\":0,\"y_max\":0}");
        GeoExtent ok = validator.parseGeoExtent("[-180,-90,0,90]");
        assertDoesNotThrow(() -> validator.validateExtentConsistency(tr, ok));

        GeoExtent wrong = validator.parseGeoExtent("[-179,-90,0,90]");
        BizException e = assertThrows(BizException.class,
                () -> validator.validateExtentConsistency(tr, wrong));
        assertEquals(ErrorCode.INVALID_INPUT, e.errorCode());
    }

    @Test
    void malformedGeoExtentRejected() {
        assertThrows(BizException.class, () -> validator.parseGeoExtent("not-json"));
        assertThrows(BizException.class, () -> validator.parseGeoExtent(""));
    }

    // ---- 多期（FR-8.1/8.7） ----

    @Test
    void periodOrderChecked() {
        assertDoesNotThrow(() -> validator.validatePeriods("2015,2016,2019", 3));
        BizException e = assertThrows(BizException.class,
                () -> validator.validatePeriods("2016,2015,2019", 3));
        assertEquals(ErrorCode.PERIOD_ORDER_INVALID, e.errorCode());
    }

    @Test
    void periodCountMismatchRejected() {
        assertThrows(BizException.class, () -> validator.validatePeriods("2015,2016", 3));
    }

    // ---- 文件校验（8.3） ----

    @Test
    void nonImageRejected() {
        MockMultipartFile pdf = new MockMultipartFile("image", "a.pdf", "application/pdf",
                "%PDF-1.4 fake".getBytes());
        BizException e = assertThrows(BizException.class,
                () -> validator.validateImages(List.of(pdf)));
        assertEquals(ErrorCode.INVALID_INPUT, e.errorCode());
    }

    @Test
    void emptyImageRejected() {
        MockMultipartFile empty = new MockMultipartFile("image", "a.png", "image/png", new byte[0]);
        assertThrows(BizException.class, () -> validator.validateImages(List.of(empty)));
    }
}
