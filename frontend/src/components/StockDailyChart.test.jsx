import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import StockDailyChart from './StockDailyChart';
import * as api from '../api/kiwoomApi';

vi.mock('../api/kiwoomApi');

const dailyPrices = Array.from({ length: 80 }, (_, index) => ({
  date: `2026${String(Math.floor(index / 28) + 1).padStart(2, '0')}${String((index % 28) + 1).padStart(2, '0')}`,
  openPrice: 100 + index,
  highPrice: 105 + index,
  lowPrice: 95 + index,
  closePrice: 102 + index,
  volume: 1000 + index
}));

describe('StockDailyChart', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.getDailyPrices.mockResolvedValue(dailyPrices);
  });

  const renderChart = (stockCode = '005930') => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
      <QueryClientProvider client={client}>
        <StockDailyChart stockCode={stockCode} />
      </QueryClientProvider>
    );
  };

  it('종목이 없으면 차트를 렌더링하지 않는다', () => {
    const { container } = renderChart(null);
    expect(container).toBeEmptyDOMElement();
  });

  it('가격·거래량·보조지표 패널과 기간 선택을 제공한다', async () => {
    renderChart('005930');

    expect(await screen.findByRole('heading', { name: '005930 일봉 차트' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '거래량' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'RSI(14)' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'MACD' })).toBeInTheDocument();
    const period = screen.getByLabelText('조회 기간');
    expect(period).toHaveValue('60');
    fireEvent.change(period, { target: { value: '30' } });
    expect(period).toHaveValue('30');
    expect(screen.getByText(/범위 선택기를 드래그/)).toBeInTheDocument();
  });

  it('차트 종류를 바꾸면 해당 주기로 다시 조회한다', async () => {
    renderChart('005930');

    const timeframe = await screen.findByLabelText('차트 종류');
    expect(timeframe).toHaveValue('day');
    fireEvent.change(timeframe, { target: { value: 'week' } });

    await waitFor(() => expect(api.getDailyPrices).toHaveBeenCalledWith('005930', 'week'));
    expect(await screen.findByRole('heading', { name: '005930 주봉 차트' })).toBeInTheDocument();
  });
});
