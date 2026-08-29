package com.example.kiwoom.research.boxevaluation.candidate;

import com.example.kiwoom.dto.StoredDailyCandle;
import com.example.kiwoom.research.boxevaluation.dto.BoxCandidateGenerationResult;
import com.example.kiwoom.research.boxevaluation.model.BoxCandidate;
import com.example.kiwoom.research.boxevaluation.model.BoxCandidateFeatures;
import com.example.kiwoom.research.boxevaluation.model.BoxCandidateParameters;
import com.example.kiwoom.research.boxevaluation.model.BoxCandidateType;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure, deterministic candidate generation for the box-evaluation research workbench. */
public final class BoxCandidateGenerator {

    public BoxCandidateGenerationResult generate(
            List<StoredDailyCandle> source, LocalDate cutoff, BoxCandidateParameters parameters) {
        if (source == null || cutoff == null || parameters == null)
            throw new IllegalArgumentException("source, cutoff and parameters are required");

        List<StoredDailyCandle> candles = normalize(source, cutoff);
        String code = candles.isEmpty() ? null : candles.getFirst().code();
        if (candles.size() < parameters.minimumSampleDays()) {
            return new BoxCandidateGenerationResult(
                    code,
                    cutoff,
                    candles.isEmpty() ? null : candles.getLast().tradeDate(),
                    "INSUFFICIENT",
                    List.of(),
                    List.of("통계 계산에 필요한 cutoff 이하 일봉이 부족합니다."));
        }

        int anchor = findRecoveryAnchor(candles, parameters);
        int boxEnd = anchor < candles.size() ? anchor - 1 : candles.size() - 1;
        if (boxEnd + 1 < parameters.minimumSampleDays()) {
            return new BoxCandidateGenerationResult(
                    code,
                    cutoff,
                    candles.getLast().tradeDate(),
                    "INSUFFICIENT",
                    List.of(),
                    List.of("회복 앵커 이전의 안정 구간 표본이 부족합니다."));
        }

        int initialStart = boxEnd - parameters.minimumSampleDays() + 1;
        int narrowStart =
                extendBackward(
                        candles, initialStart, boxEnd, parameters.narrowDailyDeviationRate());
        int expandedStart =
                extendBackward(
                        candles, narrowStart, boxEnd, parameters.expandedDailyDeviationRate());
        int connectedStart = findConnectedStart(candles, expandedStart, boxEnd, parameters);

        String anchorEvidence =
                anchor < candles.size()
                        ? "회복 변화점 " + candles.get(anchor).tradeDate() + " 직전까지 평가"
                        : "명확한 회복 변화점이 없어 기준일 종가까지 평가";
        List<BoxCandidate> candidates =
                List.of(
                        candidate(
                                BoxCandidateType.NARROW,
                                candles,
                                narrowStart,
                                boxEnd,
                                parameters,
                                List.of(anchorEvidence, "엄격한 일별 이탈 한도로 역방향 확장")),
                        candidate(
                                BoxCandidateType.EXPANDED,
                                candles,
                                expandedStart,
                                boxEnd,
                                parameters,
                                List.of(anchorEvidence, "완화된 일별 이탈 한도로 역방향 확장")),
                        candidate(
                                BoxCandidateType.CONNECTED,
                                candles,
                                connectedStart,
                                boxEnd,
                                parameters,
                                List.of(
                                        anchorEvidence,
                                        connectedStart < expandedStart
                                                ? "짧은 단절 앞의 유사 가격 상태를 연결"
                                                : "연결 가능한 앞선 안정 구간 없음")));

        return new BoxCandidateGenerationResult(
                code,
                cutoff,
                candles.getLast().tradeDate(),
                "READY",
                candidates,
                List.of("후보는 평가 자료이며 매수 신호가 아닙니다."));
    }

    public BoxCandidateGenerationResult generate(List<StoredDailyCandle> source, LocalDate cutoff) {
        return generate(source, cutoff, BoxCandidateParameters.defaults());
    }

    private List<StoredDailyCandle> normalize(List<StoredDailyCandle> source, LocalDate cutoff) {
        List<StoredDailyCandle> candles =
                source.stream()
                        .filter(candle -> candle != null && !candle.tradeDate().isAfter(cutoff))
                        .sorted(Comparator.comparing(StoredDailyCandle::tradeDate))
                        .toList();
        Set<LocalDate> dates = new HashSet<>();
        String code = null;
        for (StoredDailyCandle candle : candles) {
            if (code == null) code = candle.code();
            if (!code.equals(candle.code()))
                throw new IllegalArgumentException("all candles must have the same code");
            if (!dates.add(candle.tradeDate()))
                throw new IllegalArgumentException("duplicate trade date: " + candle.tradeDate());
            if (candle.closePrice() <= 0 || candle.volume() < 0)
                throw new IllegalArgumentException("invalid candle at " + candle.tradeDate());
        }
        return candles;
    }

    private int findRecoveryAnchor(
            List<StoredDailyCandle> candles, BoxCandidateParameters parameters) {
        int first =
                Math.max(
                        parameters.minimumSampleDays(),
                        candles.size() - parameters.anchorLookbackDays());
        for (int index = first; index < candles.size(); index++) {
            int from = index - parameters.minimumSampleDays();
            long baseline = medianClose(candles.subList(from, index));
            if (baseline > 0
                    && candles.get(index).closePrice()
                            >= baseline * (1 + parameters.recoveryAnchorRate())) return index;
        }
        return candles.size();
    }

    private int extendBackward(
            List<StoredDailyCandle> candles, int start, int end, double deviationRate) {
        int result = start;
        while (result > 0) {
            long median = medianClose(candles.subList(result, end + 1));
            long close = candles.get(result - 1).closePrice();
            if (relativeDifference(close, median) > deviationRate) break;
            result--;
        }
        return result;
    }

    private int findConnectedStart(
            List<StoredDailyCandle> candles,
            int expandedStart,
            int boxEnd,
            BoxCandidateParameters parameters) {
        if (expandedStart == 0) return expandedStart;
        long currentMedian = medianClose(candles.subList(expandedStart, boxEnd + 1));
        int cursor = expandedStart - 1;
        int gap = 0;
        while (cursor >= 0
                && gap < parameters.maximumConnectionGapDays()
                && relativeDifference(candles.get(cursor).closePrice(), currentMedian)
                        > parameters.expandedDailyDeviationRate()) {
            cursor--;
            gap++;
        }
        if (gap == 0 || cursor + 1 < parameters.minimumSampleDays()) return expandedStart;

        int earlierEnd = cursor;
        int earlierStart = earlierEnd - parameters.minimumSampleDays() + 1;
        long earlierMedian = medianClose(candles.subList(earlierStart, earlierEnd + 1));
        if (relativeDifference(earlierMedian, currentMedian)
                > parameters.connectedMedianDifferenceRate()) return expandedStart;

        return extendBackward(
                candles, earlierStart, earlierEnd, parameters.expandedDailyDeviationRate());
    }

    private BoxCandidate candidate(
            BoxCandidateType type,
            List<StoredDailyCandle> candles,
            int start,
            int end,
            BoxCandidateParameters parameters,
            List<String> evidence) {
        List<StoredDailyCandle> range = candles.subList(start, end + 1);
        return new BoxCandidate(
                type,
                range.getFirst().tradeDate(),
                range.getLast().tradeDate(),
                features(range, parameters),
                evidence);
    }

    private BoxCandidateFeatures features(
            List<StoredDailyCandle> candles, BoxCandidateParameters parameters) {
        List<Long> closes = candles.stream().map(StoredDailyCandle::closePrice).sorted().toList();
        long medianClose = percentile(closes, 0.50);
        long lower = percentile(closes, 0.10);
        long upper = percentile(closes, 0.90);
        List<Long> volumes = candles.stream().map(StoredDailyCandle::volume).sorted().toList();
        long medianVolume = percentile(volumes, 0.50);
        int spikes = 0;
        double maximumMultiple = 0;
        for (StoredDailyCandle candle : candles) {
            double multiple = medianVolume == 0 ? 0 : (double) candle.volume() / medianVolume;
            maximumMultiple = Math.max(maximumMultiple, multiple);
            if (multiple >= parameters.volumeSpikeMultiple()) spikes++;
        }
        double rangeRate = medianClose == 0 ? 0 : (double) (upper - lower) / medianClose;
        double slope =
                candles.size() < 2
                        ? 0
                        : ((double) candles.getLast().closePrice() / candles.getFirst().closePrice()
                                        - 1)
                                / (candles.size() - 1);
        return new BoxCandidateFeatures(
                candles.size(),
                medianClose,
                lower,
                upper,
                round(rangeRate),
                round(slope),
                spikes,
                round(maximumMultiple));
    }

    private long medianClose(List<StoredDailyCandle> candles) {
        return percentile(
                candles.stream().map(StoredDailyCandle::closePrice).sorted().toList(), 0.50);
    }

    private long percentile(List<Long> values, double percentile) {
        int index = Math.min(values.size() - 1, (int) Math.floor((values.size() - 1) * percentile));
        return values.get(index);
    }

    private double relativeDifference(long value, long reference) {
        return reference == 0 ? Double.POSITIVE_INFINITY : Math.abs((double) value / reference - 1);
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
