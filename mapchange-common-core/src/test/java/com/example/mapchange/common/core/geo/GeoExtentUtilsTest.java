package com.example.mapchange.common.core.geo;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * GeoExtentUtils 冻结公式单测（评审 T-03 例外：纯函数随骨架直接实现）。
 * span(z) = 360 / 2^z；expectedExtent 按设计 §3.1.5 线性公式。
 */
class GeoExtentUtilsTest {

    private static final TileMatrixParam DEFAULT = TileMatrixParam.defaults();

    @ParameterizedTest
    @CsvSource({
            "1, 180.0",
            "2, 90.0",
            "4, 22.5",
            "16, 0.0054931640625"
    })
    void spanMatchesFrozenFormula(int z, double expected) {
        assertEquals(expected, GeoExtentUtils.span(z, DEFAULT), 1e-12);
    }

    @Test
    void expectedExtentMatchesFrozenFormula() {
        // z=1 时 span=180°：tile x=[0,0], y=[0,0] → [-180, -90, 0, 90]
        GeoExtent extent = GeoExtentUtils.expectedExtent(new TileRange(1, 0, 0, 0, 0), DEFAULT);
        assertEquals(-180.0, extent.minx(), 1e-9);
        assertEquals(-90.0, extent.miny(), 1e-9);
        assertEquals(0.0, extent.maxx(), 1e-9);
        assertEquals(90.0, extent.maxy(), 1e-9);
    }

    @Test
    void expectedExtentRespectsCustomOrigin() {
        TileMatrixParam param = new TileMatrixParam(new double[]{0.0, 0.0}, 360.0, 1);
        GeoExtent extent = GeoExtentUtils.expectedExtent(new TileRange(1, 0, 0, 0, 0), param);
        assertEquals(0.0, extent.minx(), 1e-9);
        assertEquals(-180.0, extent.miny(), 1e-9);
        assertEquals(180.0, extent.maxx(), 1e-9);
        assertEquals(0.0, extent.maxy(), 1e-9);
    }

    // ---- J-021 校验方法 ----

    @Test
    void validateConsistencyPassWithinTolerance() {
        TileRange range = new TileRange(1, 0, 0, 0, 0);
        GeoExtent exact = new GeoExtent(-180.0, -90.0, 0.0, 90.0);
        assertDoesNotThrow(() -> GeoExtentUtils.validateConsistency(range, exact, DEFAULT));
        GeoExtent tiny = new GeoExtent(-180.0 + 5e-10, -90.0, 0.0, 90.0);
        assertDoesNotThrow(() -> GeoExtentUtils.validateConsistency(range, tiny, DEFAULT));
    }

    @Test
    void validateConsistencyRejectsMismatch() {
        TileRange range = new TileRange(1, 0, 0, 0, 0);
        GeoExtent wrong = new GeoExtent(-179.0, -90.0, 0.0, 90.0);
        BizException e = assertThrows(BizException.class,
                () -> GeoExtentUtils.validateConsistency(range, wrong, DEFAULT));
        assertEquals(ErrorCode.INVALID_INPUT, e.errorCode());
    }

    @Test
    void validateSameExtentPassAndReject() {
        GeoExtent a = new GeoExtent(104.01, 30.69, 104.07, 30.73);
        assertDoesNotThrow(() -> GeoExtentUtils.validateSameExtent(List.of(a, a, a)));
        BizException e = assertThrows(BizException.class, () ->
                GeoExtentUtils.validateSameExtent(List.of(a, new GeoExtent(104.02, 30.69, 104.07, 30.73))));
        assertEquals(ErrorCode.GEO_EXTENT_MISMATCH, e.errorCode());
    }
}
