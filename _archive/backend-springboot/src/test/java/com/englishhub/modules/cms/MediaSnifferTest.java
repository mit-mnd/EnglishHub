package com.englishhub.modules.cms;

import com.englishhub.modules.cms.MediaSniffer.Detected;
import com.englishhub.modules.cms.MediaSniffer.Kind;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MediaSnifferTest {

    private static byte[] header(String ascii, int... prefix) {
        byte[] out = Arrays.copyOf(new byte[0], 32);
        for (int i = 0; i < prefix.length; i++) {
            out[i] = (byte) prefix[i];
        }
        byte[] text = ascii.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(text, 0, out, prefix.length, text.length);
        return out;
    }

    @Test
    void detectsAudioVideoAndImages() {
        assertThat(MediaSniffer.detect(header("ID3"))).contains(new Detected(Kind.AUDIO, "mp3"));
        assertThat(MediaSniffer.detect(header("RIFF\0\0\0\0WAVEfmt "))).contains(new Detected(Kind.AUDIO, "wav"));
        assertThat(MediaSniffer.detect(header("RIFF\0\0\0\0WEBPVP8 "))).contains(new Detected(Kind.IMAGE, "webp"));
        assertThat(MediaSniffer.detect(header("\0\0\0 ftypM4A "))).contains(new Detected(Kind.AUDIO, "m4a"));
        assertThat(MediaSniffer.detect(header("\0\0\0 ftypisom"))).contains(new Detected(Kind.VIDEO, "mp4"));
        assertThat(MediaSniffer.detect(header("", 0xFF, 0xD8, 0xFF, 0xE0))).contains(new Detected(Kind.IMAGE, "jpg"));
        assertThat(MediaSniffer.detect(header("PNG\r\n", 0x89))).contains(new Detected(Kind.IMAGE, "png"));
    }

    @Test
    void rejectsHtmlSvgAndTinyFiles() {
        assertThat(MediaSniffer.detect("<svg xmlns=\"http://www.w3.org/2000/svg\">".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(MediaSniffer.detect("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(MediaSniffer.detect(new byte[]{1, 2, 3})).isEmpty();
    }
}
