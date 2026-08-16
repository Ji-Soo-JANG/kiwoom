package com.example.kiwoom.mapper;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.StockPriceResponse;
import com.example.kiwoom.error.KiwoomApiException;
import com.example.kiwoom.error.KiwoomErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class KiwoomResponseMapper {
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TOKEN_EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final ObjectMapper objectMapper;

    public KiwoomResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedAccessToken parseAccessToken(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            verifySuccess(root, "키움 토큰 발급 오류");
            String token = root.path("token").asText();
            String expiry = root.path("expires_dt").asText();
            if (token.isBlank()) throw invalidResponse("토큰 발급 응답에 token이 없습니다");
            if (expiry.isBlank()) throw invalidResponse("토큰 발급 응답에 expires_dt가 없습니다");
            return new ParsedAccessToken(token,
                    LocalDateTime.parse(expiry, TOKEN_EXPIRY_FORMAT).atZone(SEOUL_ZONE).toInstant());
        } catch (JsonProcessingException | DateTimeParseException error) {
            throw invalidResponse("토큰 응답 JSON 파싱 실패: " + error.getMessage());
        }
    }

    public StockPriceResponse parseCurrentPrice(String requestedCode, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            verifySuccess(root, "키움 주가 조회 오류");
            String code = root.path("stk_cd").asText();
            if (code.isBlank()) code = requestedCode;
            return new StockPriceResponse(code, normalizePrice(root.path("cur_prc").asText("0")),
                    normalizeNumber(root.path("pred_pre").asText("0")),
                    normalizeNumber(root.path("flu_rt").asText("0.00")));
        } catch (JsonProcessingException error) {
            throw invalidResponse("주가 응답 JSON 파싱 실패: " + error.getMessage());
        }
    }

    public List<DailyPriceResponse> parseDailyPrices(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            verifySuccess(root, "키움 일봉 조회 오류");
            JsonNode array = root.path("stk_dt_pole_chart_qry");
            if (!array.isArray()) throw invalidResponse("일봉 배열을 찾을 수 없습니다");
            List<DailyPriceResponse> result = new ArrayList<>();
            for (JsonNode item : array) {
                result.add(new DailyPriceResponse(item.path("dt").asText(), parseLong(item.path("open_pric").asText()),
                        parseLong(item.path("high_pric").asText()), parseLong(item.path("low_pric").asText()),
                        parseLong(item.path("cur_prc").asText()), parseLong(item.path("trde_qty").asText())));
            }
            result.sort(Comparator.comparing(DailyPriceResponse::getDate));
            return result;
        } catch (JsonProcessingException error) {
            throw invalidResponse("일봉 응답 JSON 파싱 실패");
        }
    }

    private void verifySuccess(JsonNode root, String message) {
        int code = root.path("return_code").asInt(0);
        if (code != 0) {
            String detail = root.path("return_msg").asText();
            throw KiwoomApiException.fromResponse(code, message + " [" + code + "]: " + detail);
        }
    }

    private KiwoomApiException invalidResponse(String message) {
        return new KiwoomApiException(KiwoomErrorCode.INVALID_RESPONSE, message);
    }

    private String normalizePrice(String value) {
        return value == null || value.isBlank() ? "0" : value.trim().replace(",", "").replace("+", "").replace("-", "");
    }

    private String normalizeNumber(String value) {
        return value == null || value.isBlank() ? "0" : value.trim().replace(",", "");
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Math.abs(Long.parseLong(value.trim().replace(",", "").replace("+", ""))); }
        catch (NumberFormatException error) { return 0; }
    }

    public record ParsedAccessToken(String value, Instant expiresAt) {}
}
