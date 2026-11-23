package com.marketguard.collector;

import com.marketguard.application.port.SectorLookup;
import com.marketguard.config.OutcomeProperties;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * outcome.sectors-file에서 종목별 업종을 읽는다. 파일이 없으면 업종 보정을 하지 않는다.
 *
 * <p>형식: 한 줄에 "종목코드,업종". KRX 정보데이터시스템의 업종분류 CSV를 그대로 넣어도 되도록
 * 머리글에 "종목코드"와 "업종명"이 있으면 그 열을 쓰고, UTF-8로 읽히지 않으면 CP949로 다시 읽는다.
 */
@Slf4j
@Component
public class SectorDirectory implements SectorLookup {

    private static final Pattern CODE = Pattern.compile("\\d{6}");
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final Map<String, String> sectors;

    public SectorDirectory(OutcomeProperties properties) {
        this.sectors = load(properties.sectorsFile());
    }

    @Override
    public Optional<String> sectorOf(String stockCode) {
        return Optional.ofNullable(sectors.get(stockCode));
    }

    public int size() {
        return sectors.size();
    }

    static Map<String, String> load(String file) {
        if (file == null || file.isBlank()) {
            return Map.of();
        }
        Path path = Path.of(file.trim());
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) {
                log.warn("outcome.sectors-file은 10 MiB 이하의 일반 파일이어야 합니다: {} → 업종 보정 안 함", path);
                return Map.of();
            }
            Map<String, String> parsed = parse(decode(Files.readAllBytes(path)).lines().toList());
            log.info("업종 파일 사용: {} ({}종목)", path, parsed.size());
            return parsed;
        } catch (IOException exception) {
            log.warn("업종 파일 로드 실패({}): {} → 업종 보정 안 함", path, exception.getMessage());
            return Map.of();
        }
    }

    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(bytes, Charset.forName("MS949"));
        }
    }

    static Map<String, String> parse(List<String> lines) {
        int codeColumn = 0;
        int sectorColumn = 1;
        Map<String, String> result = new HashMap<>();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            List<String> cells = Arrays.stream(trimmed.split(",", -1))
                    .map(cell -> cell.strip().replaceAll("^\"|\"$", "").strip())
                    .toList();
            int codeHeader = cells.indexOf("종목코드");
            int sectorHeader = cells.indexOf("업종명");
            if (codeHeader >= 0 && sectorHeader >= 0) {
                codeColumn = codeHeader;
                sectorColumn = sectorHeader;
                continue;
            }
            if (cells.size() <= Math.max(codeColumn, sectorColumn)) {
                continue;
            }
            String code = cells.get(codeColumn);
            String sector = cells.get(sectorColumn);
            if (CODE.matcher(code).matches() && !sector.isEmpty()) {
                result.put(code, sector);
            }
        }
        return Map.copyOf(result);
    }
}
