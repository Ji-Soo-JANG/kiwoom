package com.example.kiwoom.mapper;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.StockPriceResponse;
import com.example.kiwoom.dto.StockSearchResult;
import com.example.kiwoom.error.KiwoomApiException;
import com.example.kiwoom.error.KiwoomErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KiwoomResponseMapper {
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TOKEN_EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
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
            return new ParsedAccessToken(
                    token,
                    LocalDateTime.parse(expiry, TOKEN_EXPIRY_FORMAT)
                            .atZone(SEOUL_ZONE)
                            .toInstant());
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
            return new StockPriceResponse(
                    code,
                    normalizePrice(requiredText(root, "cur_prc")),
                    normalizeNumber(root.path("pred_pre").asText("0"), "pred_pre"),
                    normalizeNumber(root.path("flu_rt").asText("0.00"), "flu_rt"));
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
                result.add(
                        new DailyPriceResponse(
                                requiredText(item, "dt"),
                                parseLong(item, "open_pric"),
                                parseLong(item, "high_pric"),
                                parseLong(item, "low_pric"),
                                parseLong(item, "cur_prc"),
                                parseLong(item, "trde_qty")));
            }
            result.sort(Comparator.comparing(DailyPriceResponse::getDate));
            return result;
        } catch (JsonProcessingException error) {
            throw invalidResponse("일봉 응답 JSON 파싱 실패");
        }
    }

    public List<StockSearchResult> parseStockList(String market, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            verifySuccess(root, "키움 종목 목록 조회 오류");
            JsonNode array = root.path("list");
            if (!array.isArray()) throw invalidResponse("종목 목록 배열을 찾을 수 없습니다");
            List<StockSearchResult> result = new ArrayList<>();
            for (JsonNode item : array) {
                String code = item.path("code").asText();
                String name = item.path("name").asText();
                if (code.matches("\\d{6}") && !name.isBlank()) {
                    result.add(new StockSearchResult(code, name, market));
                }
            }
            return result;
        } catch (JsonProcessingException error) {
            throw invalidResponse("종목 목록 응답 JSON 파싱 실패");
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
        String normalized = value.trim().replace(",", "").replace("+", "").replace("-", "");
        validateNumber(normalized, "cur_prc");
        return normalized;
    }

    private String normalizeNumber(String value, String field) {
        String normalized = value == null || value.isBlank() ? "0" : value.trim().replace(",", "");
        validateNumber(normalized, field);
        return normalized;
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw invalidResponse("필수 응답 필드가 없습니다: " + field);
        return value;
    }

    private long parseLong(JsonNode node, String field) {
        String normalized = requiredText(node, field).trim().replace(",", "").replace("+", "");
        try {
            return Math.abs(Long.parseLong(normalized));
        } catch (NumberFormatException error) {
            throw invalidResponse("숫자 응답 형식이 올바르지 않습니다: " + field);
        }
    }

    private void validateNumber(String value, String field) {
        try {
            new BigDecimal(value);
        } catch (NumberFormatException error) {
            throw invalidResponse("숫자 응답 형식이 올바르지 않습니다: " + field);
        }
    }

    public record ParsedAccessToken(String value, Instant expiresAt) {}
}
