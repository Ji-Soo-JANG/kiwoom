package com.example.kiwoom.dto;

import java.util.List;
import java.util.Locale;

public enum StockProductType {
    STOCK("주식", List.of("주식", "보통주")),
    PREFERRED("우선주", List.of("우선주", "우선", "우")),
    ETF("ETF", List.of("etf", "상장지수펀드")),
    ETN("ETN", List.of("etn", "상장지수증권")),
    REIT("리츠", List.of("리츠", "reit", "부동산투자회사")),
    SPAC("스팩", List.of("스팩", "spac", "기업인수목적"));

    private static final List<String> ETF_BRANDS =
            List.of(
                    "KODEX",
                    "TIGER",
                    "RISE",
                    "ACE",
                    "SOL",
                    "HANARO",
                    "KOSEF",
                    "ARIRANG",
                    "PLUS",
                    "TIMEFOLIO");

    private final String label;
    private final List<String> searchTerms;

    StockProductType(String label, List<String> searchTerms) {
        this.label = label;
        this.searchTerms = searchTerms;
    }

    public String label() {
        return label;
    }

    public boolean matchesKeyword(String keyword) {
        return searchTerms.stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .anyMatch(term -> term.contains(keyword) || keyword.contains(term));
    }

    public static StockProductType classify(String name) {
        String normalized = name.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        if (normalized.contains("ETN")) return ETN;
        if (normalized.contains("스팩") || normalized.contains("기업인수목적")) return SPAC;
        if (normalized.contains("리츠") || normalized.contains("REIT")) return REIT;
        if (ETF_BRANDS.stream().anyMatch(normalized::startsWith)) return ETF;
        if (normalized.matches(".*우(?:B|C|우)?$")) return PREFERRED;
        return STOCK;
    }
}
