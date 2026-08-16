import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StockDailyChart from './StockDailyChart';

describe('StockDailyChart', () => {
  it('일봉 데이터가 없으면 차트를 렌더링하지 않는다', () => {
    const { container } = render(<StockDailyChart stockCode="005930" dailyPrices={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('가격·거래량·보조지표 패널과 기간 선택을 제공한다', () => {
    const dailyPrices = Array.from({ length: 80 }, (_, index) => ({
      date: `2026${String(Math.floor(index / 28) + 1).padStart(2, '0')}${String((index % 28) + 1).padStart(2, '0')}`,
      openPrice: 100 + index,
      highPrice: 105 + index,
      lowPrice: 95 + index,
      closePrice: 102 + index,
      volume: 1000 + index
    }));
    render(<StockDailyChart stockCode="005930" dailyPrices={dailyPrices} />);

    expect(screen.getByRole('heading', { name: '005930 일봉 차트' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '거래량' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'RSI(14)' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'MACD' })).toBeInTheDocument();
    const period = screen.getByLabelText('조회 기간');
    expect(period).toHaveValue('60');
    fireEvent.change(period, { target: { value: '30' } });
    expect(period).toHaveValue('30');
    expect(screen.getByText(/범위 선택기를 드래그/)).toBeInTheDocument();
  });
});
