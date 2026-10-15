package com.englishhub.integration.ai.speech;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

/** Đọc thông tin cơ bản từ header WAV (RIFF) để kiểm tra định dạng và tính thời lượng phía server. */
public final class WavInfo {

    private WavInfo() {
    }

    /** Thời lượng (ms) nếu là file WAV PCM hợp lệ; rỗng nếu không phải WAV. */
    public static OptionalLong durationMs(byte[] bytes) {
        if (bytes.length < 44 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WAVE")) {
            return OptionalLong.empty();
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int byteRate = 0;
        int pos = 12;
        // Duyệt các chunk: "fmt " chứa byte rate, "data" chứa dữ liệu âm thanh
        while (pos + 8 <= bytes.length) {
            String id = new String(bytes, pos, 4, StandardCharsets.US_ASCII);
            int size = buf.getInt(pos + 4);
            if (size < 0) {
                return OptionalLong.empty();
            }
            if (id.equals("fmt ") && pos + 20 <= bytes.length) {
                byteRate = buf.getInt(pos + 16);
            } else if (id.equals("data")) {
                int dataSize = Math.min(size, bytes.length - pos - 8);
                return byteRate > 0 ? OptionalLong.of(dataSize * 1000L / byteRate) : OptionalLong.empty();
            }
            pos += 8 + size + (size & 1);    // chunk được đệm cho chẵn byte
        }
        return OptionalLong.empty();
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII).equals(expected);
    }
}
