import { describe, expect, it } from 'vitest';
import {
  addTechnicalIndicators,
  calculateMacd,
  calculateRsi,
  calculateSma
} from './technicalIndicators';

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

  it('단순 이동평균을 계산한다', () => {
    expect(calculateSma([1, 2, 3, 4, 5, 6], 5)).toEqual([null, null, null, null, 3, 4]);
  });

  it('일봉 데이터에 지표를 결합한다', () => {
    const result = addTechnicalIndicators(
      rising.map((closePrice, index) => ({ date: String(index), closePrice }))
    );
    expect(result).toHaveLength(40);
    expect(result[39].rsi).toBe(100);
    expect(result[39].macd).not.toBeNull();
    expect(result[39].ma5).toBe(38);
    expect(result[19].ma20).toBe(10.5);
  });
});
