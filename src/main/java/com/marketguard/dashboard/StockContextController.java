package com.marketguard.dashboard;

import com.marketguard.collector.BoardDataService;
import com.marketguard.collector.BoardItem;
import com.marketguard.collector.StockReferenceService;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.detection.context.RelativeMovement;
import com.marketguard.detection.model.Candle;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/stocks")
public class StockContextController {

    private final BoardDataService boardDataService;
    private final StockReferenceService stockReferenceService;
    private final TossMarketDataClient marketDataClient;

    public StockContextController(BoardDataService boardDataService,
                                  StockReferenceService stockReferenceService,
                                  TossMarketDataClient marketDataClient) {
        this.boardDataService = boardDataService;
        this.stockReferenceService = stockReferenceService;
        this.marketDataClient = marketDataClient;
    }

    @GetMapping("/{code}/context")
    public StockContextView context(
            @PathVariable @Pattern(regexp = "\\d{6}") String code,
            @RequestParam(defaultValue = "60") @Min(2) @Max(200) int count) {
        BoardItem quote = boardDataService.currentBoard().stream()
                .filter(item -> item.code().equals(code)).findFirst().orElse(null);
        List<Candle> candles = marketDataClient.fetchCandles(code, "1m", count);
        return new StockContextView(code, stockReferenceService.nameOf(code), quote, candles,
                RelativeMovement.unavailable(
                        "업종 분류와 시장지수 시계열이 현재 수집 데이터에 없어 상대 움직임은 계산하지 않습니다."),
                List.of("관련 공시", "뉴스", "신규 상장", "주식분할", "배당락", "업종 동반 움직임"));
    }
}
