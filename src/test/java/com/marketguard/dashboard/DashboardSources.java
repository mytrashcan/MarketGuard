package com.marketguard.dashboard;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 대시보드 정적 소스를 읽는 테스트 도우미. */
final class DashboardSources {

    static final Path STATIC_ROOT = Path.of("src/main/resources/static");

    private DashboardSources() {
    }

    static String indexHtml() {
        return read(STATIC_ROOT.resolve("index.html"));
    }

    static String script(String name) {
        return read(STATIC_ROOT.resolve("js").resolve(name));
    }

    /** index.html과 모든 JS 모듈을 합친 내용. */
    static String all() {
        try (Stream<Path> files = Files.list(STATIC_ROOT.resolve("js"))) {
            return indexHtml() + files.filter(path -> path.toString().endsWith(".js")).sorted()
                    .map(DashboardSources::read).collect(Collectors.joining("\n"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
