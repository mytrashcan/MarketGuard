package com.marketguard.collector;

import com.marketguard.detection.model.Candle;
import java.math.BigDecimal;
import java.util.List;

/**
 * 시세 보드 카드 1개(종목 블록) DTO.
 * - price: 장중엔 실시간가, 장 마감 등으로 실시간가가 없으면 종가(lastClose)로 대체.
 * - closed: true면 실시간가가 아니라 종가 기준으로 표시 중(화면에 '종가' 표시).
 * - changePercent: 전일 종가 대비 등락률(%) — 증권사 표준 표기와 일치.
 */
public record BoardItem(
        String code,
        BigDecimal price,
        BigDecimal previousClose,
        Double changePercent,
        long volume,
        boolean closed,
        List<Candle> candles
) {
    public static BoardItem of(String code, BigDecimal livePrice, BoardDataService.Detail detail) {
        BigDecimal prevClose = detail != null ? detail.previousClose() : null;
        long volume = detail != null ? detail.volume() : 0L;
        List<Candle> candles = detail != null ? detail.candles() : List.of();
        BigDecimal lastClose = detail != null ? detail.lastClose() : null;

        BigDecimal price = livePrice != null ? livePrice : lastClose;
        boolean closed = livePrice == null && price != null;   // 종가로 대체된 경우

        Double changePercent = null;
        if (price != null && prevClose != null && prevClose.signum() != 0) {
            changePercent = price.subtract(prevClose).doubleValue() / prevClose.doubleValue() * 100.0;
        }
        return new BoardItem(code, price, prevClose, changePercent, volume, closed, candles);
    }
}
