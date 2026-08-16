package com.example.kiwoom.service;

import com.example.kiwoom.dto.DailyPriceResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class TechnicalIndicatorService {
    public List<DailyPriceResponse> enrich(List<DailyPriceResponse> prices) {
        double[] closes = prices.stream().mapToDouble(DailyPriceResponse::getClosePrice).toArray();
        Double[] rsi = rsi(closes, 14);
        Double[] fast = ema(closes, 12);
        Double[] slow = ema(closes, 26);
        Double[] macd = new Double[closes.length];
        List<Double> available = new ArrayList<>();
        for (int index = 0; index < closes.length; index++) {
            if (fast[index] != null && slow[index] != null) {
                macd[index] = fast[index] - slow[index];
                available.add(macd[index]);
            }
        }
        Double[] signalValues = ema(available.stream().mapToDouble(Double::doubleValue).toArray(), 9);
        Double[] signal = new Double[closes.length];
        int signalIndex = 0;
        for (int index = 0; index < closes.length; index++) {
            if (macd[index] != null) signal[index] = signalValues[signalIndex++];
            prices.get(index).setIndicators(rsi[index], macd[index], signal[index]);
        }
        return prices;
    }

    private Double[] ema(double[] values, int period) {
        Double[] result = new Double[values.length];
        if (values.length < period) return result;
        double current = Arrays.stream(values, 0, period).average().orElseThrow();
        result[period - 1] = current;
        double multiplier = 2d / (period + 1);
        for (int index = period; index < values.length; index++) {
            current = (values[index] - current) * multiplier + current;
            result[index] = current;
        }
        return result;
    }

    private Double[] rsi(double[] values, int period) {
        Double[] result = new Double[values.length];
        if (values.length <= period) return result;
        double gains = 0;
        double losses = 0;
        for (int index = 1; index <= period; index++) {
            double change = values[index] - values[index - 1];
            gains += Math.max(change, 0);
            losses += Math.max(-change, 0);
        }
        double averageGain = gains / period;
        double averageLoss = losses / period;
        result[period] = rsiValue(averageGain, averageLoss);
        for (int index = period + 1; index < values.length; index++) {
            double change = values[index] - values[index - 1];
            averageGain = (averageGain * (period - 1) + Math.max(change, 0)) / period;
            averageLoss = (averageLoss * (period - 1) + Math.max(-change, 0)) / period;
            result[index] = rsiValue(averageGain, averageLoss);
        }
        return result;
    }

    private double rsiValue(double averageGain, double averageLoss) {
        return averageLoss == 0 ? 100 : 100 - 100 / (1 + averageGain / averageLoss);
    }
}
