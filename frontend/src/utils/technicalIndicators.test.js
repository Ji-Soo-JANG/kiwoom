import { describe, expect, it } from 'vitest';
import { addTechnicalIndicators, calculateMacd, calculateRsi } from './technicalIndicators';

describe('technicalIndicators', () => {
  const rising = Array.from({ length: 40 }, (_, index) => index + 1);

  it('상승 데이터의 RSI를 100으로 계산한다', () => {
    expect(calculateRsi(rising)[14]).toBe(100);
  });

  it('MACD 초기 구간은 null로 유지한다', () => {
    const result = calculateMacd(rising);
    expect(result.macd[24]).toBeNull();
    expect(result.macd[25]).not.toBeNull();
    expect(result.signal[32]).toBeNull();
    expect(result.signal[33]).not.toBeNull();
  });

  it('일봉 데이터에 지표를 결합한다', () => {
    const result = addTechnicalIndicators(rising.map((closePrice, index) => ({ date: String(index), closePrice })));
    expect(result).toHaveLength(40);
    expect(result[39].rsi).toBe(100);
    expect(result[39].macd).not.toBeNull();
  });
});
