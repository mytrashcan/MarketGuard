package com.marketguard.collector;

import java.math.BigDecimal;

/**
 * 시세 보드(랭킹 테이블) 1행 DTO.
 * - price: 장중엔 실시간가, 장 마감 등으로 실시간가가 없으면 종가(lastClose)로 대체.
 * - closed: true면 실시간가가 아니라 종가 기준 표시(화면에 '종가' 표시).
 * - changePercent: 전일 종가 대비 등락률(%).
 * - bidVolume/askVolume: 호가 총잔량(매수/매도 비율 바용).
 * (캔들은 우측 상세 패널/차트 탭에서 /api/stocks/{code}/candles 로 별도 조회)
 */
public record BoardItem(
        String code,
        BigDecimal price,
        BigDecimal previousClose,
        Double changePercent,
        long volume,
        long bidVolume,
        long askVolume,
        boolean closed
) {
    public static BoardItem of(String code, BigDecimal livePrice, BoardDataService.Detail detail) {
        BigDecimal prevClose = detail != null ? detail.previousClose() : null;
        long volume = detail != null ? detail.volume() : 0L;
        long bidVolume = detail != null ? detail.bidVolume() : 0L;
        long askVolume = detail != null ? detail.askVolume() : 0L;
        BigDecimal lastClose = detail != null ? detail.lastClose() : null;

        BigDecimal price = livePrice != null ? livePrice : lastClose;
        boolean closed = livePrice == null && price != null;   // 종가로 대체된 경우

        Double changePercent = null;
        if (price != null && prevClose != null && prevClose.signum() != 0) {
            changePercent = price.subtract(prevClose).doubleValue() / prevClose.doubleValue() * 100.0;
        }
        return new BoardItem(code, price, prevClose, changePercent, volume, bidVolume, askVolume, closed);
    }
}
