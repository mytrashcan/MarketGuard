package com.marketguard.collector;

import com.marketguard.config.TossApiProperties;
import com.marketguard.config.ScanProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 이상거래 스캔 대상 종목 유니버스(전 종목 감시 대상).
 * 우선순위: ① 외부 파일(scan.symbols-file) → ② classpath:symbols.txt → ③ watch-list 폴백.
 * 외부 파일은 KRX 전종목 CSV를 그대로 넣어도 되도록, 각 줄에서 6자리 종목코드만 추출한다
 * (인코딩 영향 없게 ISO-8859-1로 읽음 — 숫자는 보존됨).
 * 토스 가격 API가 한 번에 최대 200개라 chunk()로 분할 호출을 돕는다.
 */
@Slf4j
@Component
public class SymbolUniverse {

    public static final int MAX_PER_REQUEST = 200;
    private static final String CLASSPATH_RESOURCE = "classpath:symbols.txt";
    private static final Pattern CODE = Pattern.compile("\\d{6}");

    private final List<String> symbols;

    public SymbolUniverse(TossApiProperties props,
                          ResourceLoader resourceLoader,
                          ScanProperties scanProperties) {
        this.symbols = load(resourceLoader, scanProperties.symbolsFile(), props.watchList());
        log.info("이상거래 스캔 유니버스 {}종목 로드됨", symbols.size());
    }

    public List<String> all() {
        return symbols;
    }

    private static List<String> load(ResourceLoader loader, String externalFile, List<String> fallback) {
        // ① 외부 파일 (KRX 전종목 CSV 등)
        if (externalFile != null && !externalFile.isBlank()) {
            Path path = Path.of(externalFile.trim());
            if (Files.exists(path)) {
                try {
                    // 인코딩 무관(CP949/UTF-8) — 코드는 ASCII 숫자라 ISO-8859-1로 읽어도 보존됨
                    if (!Files.isRegularFile(path) || Files.size(path) > 10 * 1024 * 1024) {
                        throw new IOException("symbols file must be a regular file no larger than 10 MiB");
                    }
                    List<String> codes;
                    try (Stream<String> lines = Files.lines(path, StandardCharsets.ISO_8859_1)) {
                        codes = parse(lines);
                    }
                    if (!codes.isEmpty()) {
                        log.info("외부 종목 파일 사용: {} ({}종목)", path, codes.size());
                        return codes;
                    }
                } catch (IOException e) {
                    log.warn("외부 종목 파일 로드 실패({}), classpath로 폴백: {}", path, e.getMessage());
                }
            } else {
                log.warn("scan.symbols-file 경로가 없음: {} → classpath로 폴백", path);
            }
        }
        // ② classpath:symbols.txt
        Resource resource = loader.getResource(CLASSPATH_RESOURCE);
        if (resource.exists()) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String> codes = parse(reader.lines());
                if (!codes.isEmpty()) {
                    return codes;
                }
            } catch (IOException e) {
                log.warn("symbols.txt 로드 실패, watch-list로 폴백: {}", e.getMessage());
            }
        }
        // ③ watch-list 폴백
        return fallback == null ? List.of() : fallback;
    }

    /** 각 줄에서 6자리 종목코드를 추출(주석/헤더/CSV 모두 허용). 첫 6자리 필드를 코드로 본다. */
    private static List<String> parse(Stream<String> lines) {
        return lines.map(SymbolUniverse::extractCode)
                .filter(code -> code != null)
                .distinct()
                .toList();
    }

    private static String extractCode(String rawLine) {
        int hash = rawLine.indexOf('#');
        String line = (hash >= 0 ? rawLine.substring(0, hash) : rawLine).trim();
        if (line.isEmpty()) {
            return null;
        }
        for (String field : line.split(",")) {
            String f = field.trim().replace("\"", "").replace("'", "");
            if (CODE.matcher(f).matches()) {
                return f;
            }
        }
        return null;
    }

    /** 리스트를 size 단위로 분할. */
    public static <T> List<List<T>> chunk(List<T> source, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < source.size(); i += size) {
            chunks.add(source.subList(i, Math.min(i + size, source.size())));
        }
        return chunks;
    }
}
