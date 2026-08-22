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
    localStorage.clear();
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
    api.getTradingStrategies.mockResolvedValue([
      {
        versionKey: 'drop-multi-base-current-pullback-v3',
        name: '급락-연속박스-최근회복-현재눌림',
        description: '현재 진행 중인 패턴만 탐지'
      }
    ]);
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

  it('현재 패턴 전략과 장기 박스권 기간을 선택해 조건 검색한다', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <MarketDiscovery onSelectStock={vi.fn()} />
      </QueryClientProvider>
    );

    expect(await screen.findByLabelText('발견 전략')).toHaveValue(
      'drop-multi-base-current-pullback-v3'
    );
    const period = screen.getByLabelText('박스권 기준 기간 60거래일');
    fireEvent.change(period, { target: { value: '240' } });

    await waitFor(() =>
      expect(api.getStrategyCandidates).toHaveBeenCalledWith(
        240,
        '',
        'drop-multi-base-current-pullback-v3'
      )
    );
    expect(await screen.findByText(/60~1,200거래일 구간을 함께 비교/)).toBeInTheDocument();
  });

  it('시장 카드 표시와 순서 및 시장 설정을 브라우저에 저장한다', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { container } = render(
      <QueryClientProvider client={client}>
        <MarketDiscovery onSelectStock={vi.fn()} />
      </QueryClientProvider>
    );

    await screen.findByRole('heading', { name: '급등주' });
    fireEvent.click(screen.getByRole('button', { name: '카드 설정' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '급락주' }));
    fireEvent.click(screen.getByRole('button', { name: '거래량 상위 앞으로 이동' }));
    fireEvent.click(screen.getByRole('button', { name: '거래량 상위 앞으로 이동' }));
    fireEvent.change(screen.getByLabelText('시장'), { target: { value: 'KOSDAQ' } });
    fireEvent.change(screen.getByLabelText('카드별 종목 수'), { target: { value: '5' } });

    await waitFor(() => expect(api.getMarketRankings).toHaveBeenCalledWith('KOSDAQ'));
    expect(screen.queryByRole('heading', { name: '급락주' })).not.toBeInTheDocument();
    expect(
      [...container.querySelectorAll('.ranking-card h3')].map((heading) => heading.textContent)
    ).toEqual(['거래량 상위', '급등주']);
    expect(JSON.parse(localStorage.getItem('kiwoom.marketDiscovery.cards'))).toMatchObject({
      market: 'KOSDAQ',
      itemCount: 5,
      visible: ['gainers', 'mostTraded']
    });
  });
});
