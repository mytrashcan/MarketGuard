package com.marketguard.collector;

import com.marketguard.config.TossApiProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 이상거래 스캔 대상 종목 유니버스.
 * classpath:symbols.txt 에서 종목코드를 읽고(주석/빈 줄·끝 주석 제거), 없으면 watch-list로 폴백한다.
 * 토스 가격 API가 한 번에 최대 200개라 chunk()로 분할 호출을 돕는다.
 */
@Slf4j
@Component
public class SymbolUniverse {

    public static final int MAX_PER_REQUEST = 200;
    private static final String RESOURCE = "classpath:symbols.txt";

    private final List<String> symbols;

    public SymbolUniverse(TossApiProperties props, ResourceLoader resourceLoader) {
        this.symbols = load(resourceLoader, props.watchList());
        log.info("이상거래 스캔 유니버스 {}종목 로드됨", symbols.size());
    }

    public List<String> all() {
        return symbols;
    }

    private static List<String> load(ResourceLoader loader, List<String> fallback) {
        Resource resource = loader.getResource(RESOURCE);
        if (resource.exists()) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String> codes = reader.lines()
                        .map(SymbolUniverse::stripComment)
                        .filter(line -> !line.isEmpty())
                        .distinct()
                        .toList();
                if (!codes.isEmpty()) {
                    return codes;
                }
            } catch (IOException e) {
                log.warn("symbols.txt 로드 실패, watch-list로 폴백: {}", e.getMessage());
            }
        }
        return fallback == null ? List.of() : fallback;
    }

    /** '#' 이후(주석)와 공백 제거 후 종목코드만 남긴다. */
    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        String code = (hash >= 0 ? line.substring(0, hash) : line).trim();
        return code;
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
