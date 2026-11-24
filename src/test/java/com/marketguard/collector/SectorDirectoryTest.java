package com.marketguard.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
    void keepsCommasInsideQuotedFields() {
        assertThat(SectorDirectory.parse(List.of(
                "\"종목코드\",\"종목명\",\"업종명\"",
                "\"005930\",\"삼성전자, 우\",\"운수,창고\"",
                "\"000660\",\"SK \"\"하이닉스\"\"\",\"운수,장비\"")))
                .containsEntry("005930", "운수,창고")
                .containsEntry("000660", "운수,장비");
        assertThat(SectorDirectory.splitCsv("a,\"b,c\",\"d\"\"e\""))
                .containsExactly("a", "b,c", "d\"e");
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
    void readsTheHeaderOfAUtf8FileThatStartsWithAByteOrderMark(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("sectors.csv");
        // 종목명이 두 번째 열이라, 머리글을 놓치면 기본 열(0, 1)로 읽어 종목명을 업종으로 쓰게 된다
        Files.write(file, ("\uFEFF종목코드,종목명,시장구분,업종명\n005930,삼성전자,KOSPI,전기·전자\n")
                .getBytes(StandardCharsets.UTF_8));

        assertThat(SectorDirectory.load(file.toString())).containsExactly(Map.entry("005930", "전기·전자"));
    }

    @Test
    void disablesSectorAdjustmentWhenTheFileIsMissing(@TempDir Path directory) {
        assertThat(SectorDirectory.load("")).isEmpty();
        assertThat(SectorDirectory.load(directory.resolve("none.csv").toString())).isEmpty();
        assertThat(SectorDirectory.decode("반도체".getBytes(StandardCharsets.UTF_8))).isEqualTo("반도체");
    }
}
