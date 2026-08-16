package com.example.kiwoom.error;

public class KiwoomApiException extends RuntimeException {
    private final KiwoomErrorCode errorCode;
    private final Integer upstreamCode;

    public KiwoomApiException(KiwoomErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public KiwoomApiException(KiwoomErrorCode errorCode, String message, Integer upstreamCode) {
        super(message);
        this.errorCode = errorCode;
        this.upstreamCode = upstreamCode;
    }

    public KiwoomErrorCode errorCode() {
        return errorCode;
    }

    public Integer upstreamCode() {
        return upstreamCode;
    }

    public static KiwoomApiException fromResponse(int upstreamCode, String message) {
        String normalized = message == null ? "" : message.toLowerCase();
        KiwoomErrorCode code;
        if (containsAny(normalized, "인증", "토큰", "auth"))
            code = KiwoomErrorCode.AUTHENTICATION_FAILED;
        else if (containsAny(normalized, "호출 제한", "요청 제한", "초과", "rate limit"))
            code = KiwoomErrorCode.RATE_LIMITED;
        else if (containsAny(normalized, "종목 없음", "없는 종목", "종목코드 오류", "not found"))
            code = KiwoomErrorCode.STOCK_NOT_FOUND;
        else if (containsAny(normalized, "장 운영", "장운영", "장 마감", "거래 시간", "market closed"))
            code = KiwoomErrorCode.MARKET_CLOSED;
        else code = KiwoomErrorCode.UPSTREAM_UNAVAILABLE;
        return new KiwoomApiException(
                code,
                message == null || message.isBlank() ? "키움 API 요청에 실패했습니다" : message,
                upstreamCode);
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }
}
