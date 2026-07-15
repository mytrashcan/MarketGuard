package com.marketguard.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class RestClientConfigTest {

    private HttpServer server;
    private ExecutorService executor;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void enforcesReadTimeout() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(2_000);
                exchange.sendResponseHeaders(200, 0);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        TossHttpProperties properties = new TossHttpProperties(
                Duration.ofSeconds(1), Duration.ofMillis(100), 1,
                Duration.ofMillis(1), Duration.ofMillis(10), 0.0, Duration.ofSeconds(1));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RestClient client = new RestClientConfig().baseBuilder(properties)
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .requestInterceptor(new TossHttpMetricsInterceptor(registry, "test"))
                .build();

        assertThatThrownBy(() -> client.get().uri("/slow").retrieve().toBodilessEntity())
                .isInstanceOf(ResourceAccessException.class);
        org.assertj.core.api.Assertions.assertThat(
                registry.get("marketguard.toss.http.duration")
                        .tag("client", "test")
                        .tag("endpoint", "other")
                        .tag("outcome", "io_error")
                        .timer().count())
                .isEqualTo(1);
    }
}
