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

    /**
     * 종목명 문자열에서 상품유형을 추정합니다.
     *
     * <p>분류 우선순위: ETN > 스팩 > 리츠 > ETF 브랜드 > 우선주(우) > 보통주. 종목코드를 추가로 전달하면 코드 패턴(3xxxxx ETF, 5xxxxx
     * ETN 등)으로 종목명 분류와 교차 검증합니다.
     */
    public static StockProductType classify(String name) {
        return classify(name, null);
    }

    /** 종목명과 종목코드를 함께 전달해 분류 정확도를 높입니다. */
    public static StockProductType classify(String name, String code) {
        String normalizedName = name.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        // 1차: 종목명 키워드 매칭
        if (normalizedName.contains("ETN")) return ETN;
        if (normalizedName.contains("스팩") || normalizedName.contains("기업인수목적")) return SPAC;
        if (normalizedName.contains("리츠") || normalizedName.contains("REIT")) return REIT;
        if (ETF_BRANDS.stream().anyMatch(normalizedName::startsWith)) return ETF;
        // 일반적인 ETF 접두사: KODEX, TIGER 등以外에도 "인덱스", "펀드", " leveraged" 등
        if (normalizedName.contains("인덱스") || normalizedName.contains(" leveraged")) return ETF;
        if (normalizedName.matches(".*우(?:B|C|우)?$")) return PREFERRED;
        // 2차: 종목코드 패턴으로 교차 검증
        if (code != null && !code.isBlank()) {
            String normalizedCode = code.trim();
            StockProductType fromCode = classifyByCode(normalizedCode);
            // 코드 분류와 이름 분류가 불일치하면 이름 분류 우선 (이름이 더 정확)
            if (fromCode != STOCK) return fromCode;
        }
        return STOCK;
    }

    /**
     * 한국 상장 종목코드 패턴으로 상품유형을 추정합니다.
     *
     * <p>한국거래소(KRX) 기준:
     *
     * <ul>
     *   <li>ETF: 주로 3xxxxx, 1xxxxx 대역 사용 (물론 일반주식도 1xxxxx 존재)
     *   <li>ETN: 주로 5xxxxx 대역 사용
     *   <li>SPAC: 주로 0#####, 2##### 중 특정 범위
     * </ul>
     *
     * <p>코드만으로는 정확한 분류가 불가능하므로 보조 신호로만 사용합니다.
     */
    private static StockProductType classifyByCode(String code) {
        if (!code.matches("\\d{6}")) return STOCK;
        int prefix = Integer.parseInt(code.substring(0, 2));
        // 50-59: ETN 대역 (KRX ETN 코드 규칙)
        if (prefix >= 50 && prefix <= 59) return ETN;
        return STOCK;
    }
}
