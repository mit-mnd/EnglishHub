package com.englishhub.integration.ai.speech;

import com.englishhub.integration.ai.speech.PronunciationScorer.PronunciationResult;
import com.englishhub.integration.ai.speech.PronunciationScorer.SpeechInput;
import com.englishhub.integration.ai.speech.WordAligner.Alignment;
import com.englishhub.integration.ai.speech.WordAligner.Status;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechScoringTest {

    private final MockPronunciationScorer scorer = new MockPronunciationScorer();

    @Test
    void alignerMarksCorrectMispronouncedAndOmittedWords() {
        Alignment a = WordAligner.align("Hello, my name is Lan. I am a student.", "hello my name is land i am student");

        assertThat(a.words()).extracting(WordAligner.WordResult::status).containsExactly(
                Status.CORRECT, Status.CORRECT, Status.CORRECT, Status.CORRECT, Status.MISPRONOUNCED,
                Status.CORRECT, Status.CORRECT, Status.OMITTED, Status.CORRECT);
        assertThat(a.words().get(4).word()).isEqualTo("Lan.");   // giữ nguyên từ hiển thị
    }

    @Test
    void contractionsMatchExpandedForm() {
        Alignment a = WordAligner.align("I'm from Vietnam.", "I am from Vietnam");
        assertThat(a.words()).extracting(WordAligner.WordResult::status).containsOnly(Status.CORRECT);
        assertThat(a.inserted()).isZero();
    }

    @Test
    void mockScoresPerfectReadingHigh() {
        PronunciationResult r = scorer.score(new SpeechInput(new byte[0], "audio/wav", 2500,
                "Nice to meet you.", "nice to meet you"));
        assertThat(r.estimated()).isFalse();
        assertThat(r.accuracyScore()).isEqualTo(100.0);
        assertThat(r.completenessScore()).isEqualTo(100.0);
        assertThat(r.pronunciationScore()).isGreaterThanOrEqualTo(90);
    }

    @Test
    void mockPenalizesMissingWords() {
        PronunciationResult r = scorer.score(new SpeechInput(new byte[0], "audio/wav", 2500,
                "I am from Vietnam and I live in Hanoi.", "I am from Vietnam"));
        assertThat(r.completenessScore()).isLessThan(60);
        assertThat(r.words()).anyMatch(w -> w.word().equals("Hanoi.") && w.errorType().equals("OMISSION"));
    }

    @Test
    void mockWithoutTranscriptIsEstimatedAndCapped() {
        PronunciationResult r = scorer.score(new SpeechInput(new byte[0], "audio/wav", 2200,
                "Nice to meet you.", null));
        assertThat(r.estimated()).isTrue();
        assertThat(r.accuracyScore()).isNull();
        assertThat(r.pronunciationScore()).isLessThanOrEqualTo(80);
    }

    @Test
    void wavInfoReadsDurationFromHeader() {
        assertThat(WavInfo.durationMs(wav(16000, 1500))).hasValue(1500);
        assertThat(WavInfo.durationMs("not a wav file at all, definitely not one".getBytes())).isEmpty();
    }

    /** WAV PCM 16-bit mono toàn im lặng, dài {@code ms}. */
    static byte[] wav(int sampleRate, int ms) {
        int dataSize = sampleRate * 2 * ms / 1000;
        ByteBuffer b = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataSize).put("WAVE".getBytes()).put("fmt ".getBytes())
                .putInt(16).putShort((short) 1).putShort((short) 1).putInt(sampleRate).putInt(sampleRate * 2)
                .putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(dataSize);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(b.array());
        return out.toByteArray();
    }
}
