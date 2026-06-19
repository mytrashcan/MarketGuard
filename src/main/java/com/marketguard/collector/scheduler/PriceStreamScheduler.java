package com.marketguard.collector.scheduler;

import com.marketguard.collector.OpenPriceProvider;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.TossApiProperties;
import com.marketguard.dashboard.PriceView;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 실시간 시세 보드용 푸시. 토스는 WebSocket 미지원이라, 서버가 짧은 주기로
 * watch-list 시세를 폴링해서 STOMP 토픽 /topic/prices 로 브라우저에 푸시한다.
 */
@Slf4j
@Component
public class PriceStreamScheduler {

    public static final String TOPIC = "/topic/prices";

    private final TossApiProperties tossProps;
    private final TossMarketDataClient marketDataClient;
    private final OpenPriceProvider openPriceProvider;
    private final SimpMessagingTemplate messagingTemplate;

    public PriceStreamScheduler(TossApiProperties tossProps,
                                TossMarketDataClient marketDataClient,
                                OpenPriceProvider openPriceProvider,
                                SimpMessagingTemplate messagingTemplate) {
        this.tossProps = tossProps;
        this.marketDataClient = marketDataClient;
        this.openPriceProvider = openPriceProvider;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRateString = "${board.push-interval-ms}")
    public void pushBoardPrices() {
        if (tossProps.clientId() == null || tossProps.clientId().isBlank()) {
            return;   // API 키 없으면 푸시할 시세가 없으므로 건너뜀
        }
        List<String> board = tossProps.watchList();
        if (board == null || board.isEmpty()) {
            return;
        }
        try {
            List<PriceView> views = marketDataClient.fetchPrices(board).stream()
                    .map(snapshot -> PriceView.of(
                            snapshot.getStockCode(),
                            snapshot.getPrice(),
                            openPriceProvider.openPrice(snapshot.getStockCode()),
                            snapshot.getCapturedAt()))
                    .toList();
            if (!views.isEmpty()) {
                messagingTemplate.convertAndSend(TOPIC, views);
            }
        } catch (Exception e) {
            log.debug("시세 보드 푸시 실패: {}", e.getMessage());
        }
    }
}
