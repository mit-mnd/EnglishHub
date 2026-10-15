package com.englishhub.common.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void quotesSpecialCharactersAndKeepsVietnamese() {
        String csv = new CsvWriter().row(List.of("Email", "Họ tên")).row(Arrays.asList("a@b.com", "Trần, \"Bình\"", null, 12)).build();
        assertThat(csv).startsWith(CsvWriter.BOM)
                .isEqualTo(CsvWriter.BOM + "Email,Họ tên\r\na@b.com,\"Trần, \"\"Bình\"\"\",,12\r\n");
    }

    @Test
    void neutralizesFormulaInjection() {
        assertThat(CsvWriter.escape("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(CsvWriter.escape("+84 912")).isEqualTo("'+84 912");
        assertThat(CsvWriter.escape("@cmd")).isEqualTo("'@cmd");
        assertThat(CsvWriter.escape(-5)).isEqualTo("-5");   // số âm thật không bị đổi
    }
}
