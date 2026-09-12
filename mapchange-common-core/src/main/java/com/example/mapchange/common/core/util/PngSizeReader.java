package com.example.mapchange.common.core.util;

/** PNG 头解析（只读 IHDR 尺寸，用于由 geo_extent + 像素尺寸推导 geo_transform；不做像素计算） */
public final class PngSizeReader {

    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private PngSizeReader() {
    }

    /** 读取 PNG 尺寸，返回 [width, height]；非 PNG 或头不完整返回 null（调用方降级处理） */
    public static int[] readSize(byte[] data) {
        if (data == null || data.length < 24) {
            return null;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                return null;
            }
        }
        // IHDR 起始于第 8 字节：4 字节长度 + "IHDR" + width(4) + height(4)
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') {
            return null;
        }
        int width = readInt32(data, 16);
        int height = readInt32(data, 20);
        return width > 0 && height > 0 ? new int[]{width, height} : null;
    }

    private static int readInt32(byte[] d, int off) {
        return ((d[off] & 0xFF) << 24) | ((d[off + 1] & 0xFF) << 16)
                | ((d[off + 2] & 0xFF) << 8) | (d[off + 3] & 0xFF);
    }
}
