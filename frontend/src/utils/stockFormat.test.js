import { describe, expect, it } from 'vitest';
import {
  formatDate,
  formatPrice,
  formatShortDate,
  formatSignedNumber,
  getChangeClass
} from './stockFormat';

describe('stockFormat', () => {
  it('가격과 부호 있는 숫자를 표시한다', () => {
    expect(formatPrice('75,000')).toBe('75,000');
    expect(formatPrice('invalid')).toBe('invalid');
    expect(formatSignedNumber('500')).toBe('+500');
    expect(formatSignedNumber('-500')).toBe('-500');
    expect(formatSignedNumber('invalid')).toBe('invalid');
  });

  it('등락 방향에 맞는 클래스를 반환한다', () => {
    expect(getChangeClass(1)).toBe('price-up');
    expect(getChangeClass(-1)).toBe('price-down');
    expect(getChangeClass(0)).toBe('');
  });

  it('키움 날짜를 전체 및 축약 형식으로 표시한다', () => {
    expect(formatDate('20260816')).toBe('2026-08-16');
    expect(formatShortDate('20260816')).toBe('08/16');
    expect(formatDate('invalid')).toBe('invalid');
    expect(formatShortDate(null)).toBeNull();
  });
});
