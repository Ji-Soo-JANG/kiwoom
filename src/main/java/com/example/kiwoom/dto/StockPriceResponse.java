package com.example.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** 키움 API로부터 받은 주가 정보를 담는 데이터 전송 객체 (DTO). */
public class StockPriceResponse {
    @JsonProperty("code")
    private String code;

    @JsonProperty("currentPrice")
    private String currentPrice;

    @JsonProperty("changeAmount")
    private String changeAmount;

    @JsonProperty("changeRate")
    private String changeRate;

    @JsonProperty("fetchedAt")
    private String fetchedAt;

    public StockPriceResponse() {}

    public StockPriceResponse(
            String code, String currentPrice, String changeAmount, String changeRate) {
        this.code = code;
        this.currentPrice = currentPrice;
        this.changeAmount = changeAmount;
        this.changeRate = changeRate;
        this.fetchedAt = Instant.now().toString();
    }

    public String getCode() {
        return code;
    }

    public String getCurrentPrice() {
        return currentPrice;
    }

    public String getChangeAmount() {
        return changeAmount;
    }

    public String getChangeRate() {
        return changeRate;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setCurrentPrice(String currentPrice) {
        this.currentPrice = currentPrice;
    }

    public void setChangeAmount(String changeAmount) {
        this.changeAmount = changeAmount;
    }

    public void setChangeRate(String changeRate) {
        this.changeRate = changeRate;
    }

    public String getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(String fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    @Override
    public String toString() {
        return "StockPriceResponse{"
                + "code='"
                + code
                + '\''
                + ", currentPrice='"
                + currentPrice
                + '\''
                + ", changeAmount='"
                + changeAmount
                + '\''
                + ", changeRate='"
                + changeRate
                + '\''
                + '}';
    }
}
