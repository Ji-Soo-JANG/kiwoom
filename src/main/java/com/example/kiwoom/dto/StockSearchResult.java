package com.example.kiwoom.dto;

public record StockSearchResult(
        String code,
        String name,
        String market,
        StockProductType productType,
        String productTypeLabel) {
    public StockSearchResult(String code, String name, String market) {
        this(code, name, market, StockProductType.classify(name, code));
    }

    private StockSearchResult(
            String code, String name, String market, StockProductType productType) {
        this(code, name, market, productType, productType.label());
    }
}
