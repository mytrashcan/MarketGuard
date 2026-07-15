package com.marketguard.dashboard;

import com.marketguard.collector.BoardItem;
import com.marketguard.detection.context.RelativeMovement;
import com.marketguard.detection.model.Candle;
import java.util.List;

public record StockContextView(
        String stockCode,
        String stockName,
        BoardItem quote,
        List<Candle> candles,
        RelativeMovement relativeMovement,
        List<String> unavailableContext
) {
}
