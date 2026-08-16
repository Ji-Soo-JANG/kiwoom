import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/kiwoomApi';
import MarketDiscovery from './MarketDiscovery';

vi.mock('../api/kiwoomApi');

const stock = {
  code: '005930',
  name: '삼성전자',
  currentPrice: 75000,
  changeRate: 3.25,
  volume: 1234567
};

describe('MarketDiscovery', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.getMarketRankings.mockResolvedValue({
      gainers: [stock],
      losers: [{ ...stock, code: '000660', name: 'SK하이닉스', changeRate: -2.1 }],
      mostTraded: [{ ...stock, code: '035420', name: 'NAVER' }],
      updatedAt: '2026-08-16T03:00:00Z'
    });
  });

  it('여러 순위 목록을 동시에 출력하고 클릭한 종목을 전달한다', async () => {
    const onSelectStock = vi.fn();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <MarketDiscovery onSelectStock={onSelectStock} />
      </QueryClientProvider>
    );

    expect(await screen.findByRole('heading', { name: '급등주' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '급락주' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '거래량 상위' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /삼성전자/ }));
    expect(onSelectStock).toHaveBeenCalledWith('005930');
  });
});
