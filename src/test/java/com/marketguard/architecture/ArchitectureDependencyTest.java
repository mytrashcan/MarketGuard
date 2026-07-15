package com.marketguard.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitectureDependencyTest {

    private static final Path DETECTION_CORE = Path.of("src/main/java/com/marketguard/detection");
    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "import org.springframework.",
            "import jakarta.persistence.",
            "import com.marketguard.domain.",
            "import com.marketguard.collector.",
            "import com.marketguard.dashboard.");

    @Test
    void detectionCoreDoesNotDependOnFrameworksOrOuterLayers() throws IOException {
        try (var files = Files.walk(DETECTION_CORE)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::containsForbiddenImport)
                    .toList();

            assertThat(violations)
                    .as("detection dependencies must point inward")
                    .isEmpty();
        }
    }

    private boolean containsForbiddenImport(Path path) {
        try {
            String source = Files.readString(path);
            return FORBIDDEN_IMPORTS.stream().anyMatch(source::contains);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect " + path, exception);
        }
    }
}
