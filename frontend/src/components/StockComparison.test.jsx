import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import * as api from '../api/kiwoomApi';
import StockComparison from './StockComparison';

vi.mock('../api/kiwoomApi');

describe('StockComparison', () => {
  it('여러 종목의 수익률·이동평균·거래량 비교를 표시한다', async () => {
    api.getDailyPrices.mockImplementation(async (code) => [
      { date: '20260820', closePrice: code === '005930' ? 70000 : 120000, volume: 100 },
      { date: '20260821', closePrice: code === '005930' ? 71400 : 118800, volume: 200 }
    ]);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <StockComparison
          items={[
            { code: '005930', name: '삼성전자' },
            { code: '000660', name: 'SK하이닉스' }
          ]}
          onRemove={vi.fn()}
          onClear={vi.fn()}
        />
      </QueryClientProvider>
    );

    expect(screen.getByRole('heading', { name: '종목 비교' })).toBeInTheDocument();
    expect(
      await screen.findByRole('heading', { name: '정규화 수익률 · 20일 이동평균' })
    ).toBeInTheDocument();
    await waitFor(() => expect(api.getDailyPrices).toHaveBeenCalledTimes(2));
    expect(screen.getByRole('heading', { name: '거래량' })).toBeInTheDocument();
  });
});
