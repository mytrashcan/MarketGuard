package com.marketguard.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SectorDirectoryTest {

    @Test
    void readsSimpleCodeSectorLines() {
        assertThat(SectorDirectory.parse(List.of("# 주석", "005930,반도체", "000660, 반도체 ", "bad,업종", "035720,")))
                .containsOnlyKeys("005930", "000660")
                .containsEntry("000660", "반도체");
    }

    @Test
    void readsTheKrxSectorExportByHeaderInCp949(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("sectors.csv");
        Files.write(file, String.join("\n",
                "\"종목코드\",\"종목명\",\"시장구분\",\"업종명\"",
                "\"005930\",\"삼성전자\",\"KOSPI\",\"전기전자\"",
                "\"035720\",\"카카오\",\"KOSPI\",\"서비스업\"").getBytes(Charset.forName("MS949")));

        assertThat(SectorDirectory.load(file.toString()))
                .containsEntry("005930", "전기전자")
                .containsEntry("035720", "서비스업");
    }

    @Test
    void disablesSectorAdjustmentWhenTheFileIsMissing(@TempDir Path directory) {
        assertThat(SectorDirectory.load("")).isEmpty();
        assertThat(SectorDirectory.load(directory.resolve("none.csv").toString())).isEmpty();
        assertThat(SectorDirectory.decode("반도체".getBytes(StandardCharsets.UTF_8))).isEqualTo("반도체");
    }
}
