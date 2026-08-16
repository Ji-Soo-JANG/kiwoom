package com.example.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 키움 API의 일봉 데이터를 담는 DTO입니다.
 */
public class DailyPriceResponse {

    @JsonProperty("date")
    private String date;

    @JsonProperty("openPrice")
    private long openPrice;

    @JsonProperty("highPrice")
    private long highPrice;

    @JsonProperty("lowPrice")
    private long lowPrice;

    @JsonProperty("closePrice")
    private long closePrice;

    @JsonProperty("volume")
    private long volume;

    private Double rsi;
    private Double macd;
    private Double signal;

    public DailyPriceResponse() {
    }

    public DailyPriceResponse(
            String date,
            long openPrice,
            long highPrice,
            long lowPrice,
            long closePrice,
            long volume
    ) {
        this.date = date;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    public String getDate() {
        return date;
    }

    public long getOpenPrice() {
        return openPrice;
    }

    public long getHighPrice() {
        return highPrice;
    }

    public long getLowPrice() {
        return lowPrice;
    }

    public long getClosePrice() {
        return closePrice;
    }

    public long getVolume() {
        return volume;
    }

    public Double getRsi() { return rsi; }
    public Double getMacd() { return macd; }
    public Double getSignal() { return signal; }

    public void setIndicators(Double rsi, Double macd, Double signal) {
        this.rsi = rsi;
        this.macd = macd;
        this.signal = signal;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setOpenPrice(long openPrice) {
        this.openPrice = openPrice;
    }

    public void setHighPrice(long highPrice) {
        this.highPrice = highPrice;
    }

    public void setLowPrice(long lowPrice) {
        this.lowPrice = lowPrice;
    }

    public void setClosePrice(long closePrice) {
        this.closePrice = closePrice;
    }

    public void setVolume(long volume) {
        this.volume = volume;
    }

    @Override
    public String toString() {
        return "DailyPriceResponse{" +
                "date='" + date + '\'' +
                ", openPrice=" + openPrice +
                ", highPrice=" + highPrice +
                ", lowPrice=" + lowPrice +
                ", closePrice=" + closePrice +
                ", volume=" + volume +
                '}';
    }
}
