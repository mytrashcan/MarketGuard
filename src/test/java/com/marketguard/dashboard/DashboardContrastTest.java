package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 글자에 쓰는 색 토큰이 두 테마의 모든 배경에서 WCAG AA(4.5:1)를 넘는지 확인한다. */
class DashboardContrastTest {

    private static final double AA_NORMAL_TEXT = 4.5;
    private static final List<String> TEXT_TOKENS =
            List.of("fg", "fg-muted", "fg-subtle", "up", "down", "red", "amber", "green", "iris");
    private static final List<String> BACKGROUNDS = List.of("bg", "bg-subtle", "bg-muted");
    private static final Pattern TOKEN = Pattern.compile("--([a-z-]+):\\s*(#[0-9a-fA-F]{6})\\s*;");

    @Test
    void textTokensMeetAaOnEveryBackgroundInBothThemes() throws IOException {
        String css = Files.readString(Path.of("src/main/resources/static/css/app.css"));
        Map<String, String> light = tokens(css.substring(0, css.indexOf("@media (prefers-color-scheme: dark)")));
        Map<String, String> dark = new LinkedHashMap<>(light);
        dark.putAll(tokens(css.substring(css.indexOf(":root[data-theme=\"dark\"]"),
                css.indexOf("/* ---------- base ---------- */"))));

        List<String> failures = new ArrayList<>();
        check("light", light, failures);
        check("dark", dark, failures);
        assertThat(failures).as("text tokens below %.1f:1", AA_NORMAL_TEXT).isEmpty();
    }

    @Test
    void contrastFormulaMatchesKnownValues() {
        assertThat(contrast("#000000", "#ffffff")).isCloseTo(21.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(contrast("#777777", "#ffffff")).isCloseTo(4.48, org.assertj.core.data.Offset.offset(0.01));
    }

    private static void check(String theme, Map<String, String> tokens, List<String> failures) {
        for (String text : TEXT_TOKENS) {
            for (String background : BACKGROUNDS) {
                double ratio = contrast(tokens.get(text), tokens.get(background));
                if (ratio < AA_NORMAL_TEXT) {
                    failures.add("%s --%s on --%s = %.2f".formatted(theme, text, background, ratio));
                }
            }
        }
    }

    private static Map<String, String> tokens(String block) {
        Map<String, String> tokens = new LinkedHashMap<>();
        Matcher matcher = TOKEN.matcher(block);
        while (matcher.find()) {
            tokens.put(matcher.group(1), matcher.group(2));
        }
        return tokens;
    }

    static double contrast(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        double[] channels = new double[3];
        for (int i = 0; i < 3; i++) {
            double value = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16) / 255.0;
            channels[i] = value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }
}
