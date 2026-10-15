package com.englishhub.modules.cms;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Nhận diện định dạng file qua "magic bytes" ở đầu file thay vì tin đuôi file / Content-Type do trình duyệt gửi
 * (tránh tải lên HTML/SVG đội lốt ảnh rồi được phục vụ công khai dưới /media).
 */
public final class MediaSniffer {

    public enum Kind { AUDIO, VIDEO, IMAGE }

    public record Detected(Kind kind, String extension) {
    }

    private MediaSniffer() {
    }

    public static Optional<Detected> detect(byte[] b) {
        if (b == null || b.length < 12) {
            return Optional.empty();
        }
        if (ascii(b, 0, "ID3") || ((b[0] & 0xFF) == 0xFF && ((b[1] & 0xE0) == 0xE0))) {
            return Optional.of(new Detected(Kind.AUDIO, "mp3"));
        }
        if (ascii(b, 0, "RIFF") && ascii(b, 8, "WAVE")) {
            return Optional.of(new Detected(Kind.AUDIO, "wav"));
        }
        if (ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP")) {
            return Optional.of(new Detected(Kind.IMAGE, "webp"));
        }
        if (ascii(b, 0, "OggS")) {
            return Optional.of(new Detected(Kind.AUDIO, "ogg"));
        }
        if (ascii(b, 4, "ftyp")) {
            // Họ MP4 (ISO BMFF): nhãn "M4A " là audio, còn lại coi là video
            String brand = new String(Arrays.copyOfRange(b, 8, 12), StandardCharsets.US_ASCII);
            return Optional.of(brand.startsWith("M4A") ? new Detected(Kind.AUDIO, "m4a") : new Detected(Kind.VIDEO, "mp4"));
        }
        if ((b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3) {
            return Optional.of(new Detected(Kind.VIDEO, "webm"));
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of(new Detected(Kind.IMAGE, "jpg"));
        }
        if ((b[0] & 0xFF) == 0x89 && ascii(b, 1, "PNG")) {
            return Optional.of(new Detected(Kind.IMAGE, "png"));
        }
        if (ascii(b, 0, "GIF8")) {
            return Optional.of(new Detected(Kind.IMAGE, "gif"));
        }
        return Optional.empty();
    }

    private static boolean ascii(byte[] b, int offset, String text) {
        byte[] t = text.getBytes(StandardCharsets.US_ASCII);
        if (b.length < offset + t.length) {
            return false;
        }
        for (int i = 0; i < t.length; i++) {
            if (b[offset + i] != t[i]) {
                return false;
            }
        }
        return true;
    }
}
