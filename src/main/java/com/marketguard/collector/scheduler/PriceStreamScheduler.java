package com.marketguard.collector.scheduler;

import com.marketguard.collector.BoardDataService;
import com.marketguard.collector.BoardItem;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 실시간 시세 보드 푸시. 토스는 WebSocket 미지원이라, 서버가 짧은 주기로 보드 카드를 조립해
 * (장 마감 시엔 종가 기준으로라도) STOMP 토픽 /topic/prices 로 브라우저에 푸시한다.
 */
@Slf4j
@Component
public class PriceStreamScheduler {

    public static final String TOPIC = "/topic/prices";

    private final BoardDataService boardDataService;
    private final SimpMessagingTemplate messagingTemplate;

    public PriceStreamScheduler(BoardDataService boardDataService,
                                SimpMessagingTemplate messagingTemplate) {
        this.boardDataService = boardDataService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedDelayString = "${board.push-interval-ms}")
    public void pushBoardPrices() {
        try {
            List<BoardItem> items = boardDataService.currentBoard();
            if (!items.isEmpty()) {
                messagingTemplate.convertAndSend(TOPIC, items);
            }
        } catch (Exception e) {
            log.warn("시세 보드 푸시 실패: {}", e.getClass().getSimpleName());
        }
    }
}
