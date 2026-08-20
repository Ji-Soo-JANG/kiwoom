import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
    api.getStrategyCandidates.mockResolvedValue({
      candidates: [
        {
          code: '005930',
          name: '삼성전자',
          currentPrice: 75000,
          score: 80,
          qualified: true,
          drawdownRate: -31.2,
          boxRangeRate: 18.4,
          volumeSpikeCount: 3,
          breakoutRate: 8.1,
          pullbackRate: -4.2,
          matchedConditions: ['과거 고점 대비 20% 이상 하락', '돌파선 위 눌림목']
        }
      ],
      scannedCount: 20,
      scope: '당일 급등·급락·거래량 상위 후보군',
      updatedAt: '2026-08-16T03:00:00Z'
    });
    api.getMarketDataStatus.mockResolvedValue({
      stockCount: 2500,
      candleCount: 10000,
      syncedStockCount: 20,
      failedStockCount: 0,
      processedInLastRun: 20,
      succeededInLastRun: 20,
      failedInLastRun: 0,
      running: false,
      checkedAt: '2026-08-16T03:00:00Z'
    });
    api.synchronizeMarketData.mockResolvedValue({});
    api.getFullMarketDataStatus.mockResolvedValue({
      stockCount: 2500,
      candleCount: 10000,
      syncedStockCount: 20,
      failedStockCount: 0,
      latestTradeDate: '2026-08-16',
      processedInLastRun: 20,
      succeededInLastRun: 20,
      failedInLastRun: 0,
      running: false,
      checkedAt: '2026-08-16T03:00:00Z'
    });
    api.synchronizeFullMarketData.mockResolvedValue({});
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
    expect(await screen.findByText('80점')).toBeInTheDocument();
    expect(await screen.findByText(/종목 2,500개/)).toBeInTheDocument();
    expect(await screen.findByText('전체 일봉 일괄 수집')).toBeInTheDocument();
    expect(await screen.findByText(/전체 2,500개 종목/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /80점.*삼성전자/ }));
    expect(onSelectStock).toHaveBeenCalledWith('005930');
  });

  it('박스권 기간 바를 조절하면 새 기간으로 조건 검색을 다시 요청한다', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <MarketDiscovery onSelectStock={vi.fn()} />
      </QueryClientProvider>
    );

    const slider = await screen.findByLabelText('박스권 기준 기간 60거래일');
    fireEvent.change(slider, { target: { value: '90' } });

    await waitFor(() => expect(api.getStrategyCandidates).toHaveBeenCalledWith(90));
    expect(await screen.findByText(/90거래일 동안의 박스권 횡보를 기준으로/)).toBeInTheDocument();
  });
});
