package com.example.kiwoom.broker.kiwoom.mapper;

import com.example.kiwoom.dto.AccountPortfolioResponse;
import com.example.kiwoom.dto.AccountPosition;
import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketRankingItem;
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
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class KiwoomResponseMapper {
    private static final Pattern SIX_DIGIT_STOCK_CODE = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
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
            verifySuccess(root, "키움 차트 조회 오류");
            JsonNode array = root.path("stk_dt_pole_chart_qry");
            // 일봉 외 기간(주봉·월봉·년봉)은 다른 키를 사용할 수 있으므로
            // 응답 루트에서 첫 번째 배열을 찾아 fallback합니다.
            if (!array.isArray() || array.isEmpty()) {
                array = findFirstArray(root);
            }
            if (array == null || !array.isArray()) {
                throw invalidResponse("차트 배열을 찾을 수 없습니다");
            }
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

    public List<MarketRankingItem> parseRanking(String arrayName, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            verifySuccess(root, "키움 순위 조회 오류");
            JsonNode array = root.path(arrayName);
            if (!array.isArray()) throw invalidResponse("순위 배열을 찾을 수 없습니다: " + arrayName);
            List<MarketRankingItem> result = new ArrayList<>();
            for (JsonNode item : array) {
                String code = normalizeRankingCode(item.path("stk_cd").asText());
                String name = item.path("stk_nm").asText();
                if (!code.matches("\\d{6}") || name.isBlank()) continue;
                result.add(
                        new MarketRankingItem(
                                code,
                                name,
                                absoluteLong(item.path("cur_prc").asText("0")),
                                decimal(item.path("flu_rt").asText("0")),
                                absoluteLong(
                                        firstText(
                                                item,
                                                "trde_qty",
                                                "now_trde_qty",
                                                "acc_trde_qty"))));
            }
            return result.stream().limit(10).toList();
        } catch (JsonProcessingException | NumberFormatException error) {
            throw invalidResponse("순위 응답 JSON 파싱 실패");
        }
    }

    public String parseAccountNumber(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            verifySuccess(root, "키움 계좌번호 조회 오류");
            String accountNumber = root.path("acctNo").asText();
            if (accountNumber.isBlank()) accountNumber = root.path("acct_no").asText();
            if (accountNumber.isBlank()) throw invalidResponse("계좌번호 응답이 없습니다");
            return accountNumber;
        } catch (JsonProcessingException error) {
            throw invalidResponse("계좌번호 응답 JSON 파싱 실패");
        }
    }

    public AccountPortfolioResponse parseAccountPortfolio(
            String accountNumber, String json, Instant updatedAt) {
        try {
            JsonNode root = objectMapper.readTree(json);
            verifySuccess(root, "키움 계좌 평가잔고 조회 오류");
            JsonNode array = root.path("acnt_evlt_remn_indv_tot");
            if (!array.isArray()) {
                // 빈 보유종목: 배열이 없거나 비어있는 경우 빈 리스트 반환
                return new AccountPortfolioResponse(
                        maskAccountNumber(accountNumber),
                        absoluteLong(firstText(root, "tot_pur_amt")),
                        absoluteLong(firstText(root, "tot_evlt_amt")),
                        signedLong(firstText(root, "tot_evlt_pl")),
                        decimal(firstText(root, "tot_prft_rt")),
                        absoluteLong(firstText(root, "prsm_dpst_aset_amt")),
                        List.of(),
                        updatedAt);
            }
            List<AccountPosition> positions = new ArrayList<>();
            for (JsonNode item : array) {
                String code = normalizeRankingCode(firstText(item, "stk_cd", "stk_no"));
                String name = firstText(item, "stk_nm", "stk_name");
                if (!code.matches("\\d{6}") || name.isBlank()) continue;
                long evalAmt = absoluteLong(firstText(item, "evlt_amt"));
                long profitAmt = signedLong(firstText(item, "evltv_prft", "evlt_pl"));
                long totalEval = absoluteLong(firstText(root, "tot_evlt_amt"));
                long totalProfit = signedLong(firstText(root, "tot_evlt_pl"));
                double weight = totalEval > 0 ? (evalAmt * 100.0 / totalEval) : 0;
                double profitContrib = totalProfit != 0 ? (profitAmt * 100.0 / totalProfit) : 0;
                positions.add(
                        new AccountPosition(
                                code,
                                name,
                                absoluteLong(firstText(item, "rmnd_qty", "hold_qty")),
                                absoluteLong(firstText(item, "trde_able_qty", "ord_psbl_qty")),
                                absoluteLong(firstText(item, "pur_pric", "avg_pric")),
                                absoluteLong(firstText(item, "cur_prc")),
                                absoluteLong(firstText(item, "pur_amt")),
                                evalAmt,
                                profitAmt,
                                decimal(firstText(item, "prft_rt", "evltv_prft_rt")),
                                Math.round(weight * 100.0) / 100.0,
                                Math.round(profitContrib * 100.0) / 100.0));
            }
            return new AccountPortfolioResponse(
                    maskAccountNumber(accountNumber),
                    absoluteLong(firstText(root, "tot_pur_amt")),
                    absoluteLong(firstText(root, "tot_evlt_amt")),
                    signedLong(firstText(root, "tot_evlt_pl")),
                    decimal(firstText(root, "tot_prft_rt")),
                    absoluteLong(firstText(root, "prsm_dpst_aset_amt")),
                    List.copyOf(positions),
                    updatedAt);
        } catch (JsonProcessingException | NumberFormatException error) {
            throw invalidResponse("계좌 평가잔고 응답 JSON 파싱 실패");
        }
    }

    /** 계좌번호를 화면에 표시할 때 앞 3자리와 뒤 2자리만 노출하고 나머지를 마스킹합니다. 예: 123-456-78901 → 123-***-**01 */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) return accountNumber;
        String digits = accountNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 6) return accountNumber;
        String prefix = digits.substring(0, Math.min(3, digits.length()));
        String suffix = digits.substring(Math.max(digits.length() - 2, 3));
        return prefix + "-***-**" + suffix;
    }

    /**
     * JSON 노드에서 첫 번째 배열을 재귀적으로 찾습니다. 키움 REST API의 주봉·월봉·년봉 응답은 일봉과 다른 배열 키를 사용할 수 있으므로, 특정 키가 없을 때
     * 이 메서드로 fallback합니다.
     */
    private JsonNode findFirstArray(JsonNode node) {
        if (node.isArray()) return node;
        if (node.isObject()) {
            var fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                JsonNode child = node.path(fieldNames.next());
                if (child.isArray() && !child.isEmpty()) return child;
            }
            fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                JsonNode child = findFirstArray(node.path(fieldNames.next()));
                if (child != null) return child;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText();
            if (!value.isBlank()) return value;
        }
        return "0";
    }

    private String normalizeRankingCode(String value) {
        var matcher = SIX_DIGIT_STOCK_CODE.matcher(value.trim());
        return matcher.find() ? matcher.group(1) : value.trim();
    }

    private long absoluteLong(String value) {
        return Math.abs(Long.parseLong(value.trim().replace(",", "").replace("+", "")));
    }

    private long signedLong(String value) {
        return Long.parseLong(value.trim().replace(",", "").replace("+", ""));
    }

    private double decimal(String value) {
        return Double.parseDouble(value.trim().replace(",", "").replace("+", ""));
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
