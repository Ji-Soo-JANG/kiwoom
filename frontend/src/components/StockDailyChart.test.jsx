import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StockDailyChart from './StockDailyChart';

describe('StockDailyChart', () => {
  it('일봉 데이터가 없으면 차트를 렌더링하지 않는다', () => {
    const { container } = render(<StockDailyChart stockCode="005930" dailyPrices={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});
