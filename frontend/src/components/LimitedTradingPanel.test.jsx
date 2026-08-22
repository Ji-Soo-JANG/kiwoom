import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import LimitedTradingPanel from './LimitedTradingPanel';
import * as api from '../api/kiwoomApi';

vi.mock('../api/kiwoomApi');

describe('LimitedTradingPanel', () => {
  beforeEach(() => {
    api.getAutoTradingControl.mockResolvedValue({
      paperEnabled: false,
      paperStrategy: 'drop-base-breakout-pullback-v1',
      liveEnabled: false,
      liveStrategy: 'drop-base-breakout-pullback-v1',
      availableStrategies: ['drop-base-breakout-pullback-v1'],
      liveBlockers: []
    });
    api.getLatestObservation.mockResolvedValue({
      observedTradingDays: 3,
      minimumTradingDays: 20,
      missedSignals: 1,
      unexpectedSignals: 0,
      agreementRate: 90,
      averagePriceDeviationRate: 0.4
    });
    api.getTradingStrategies.mockResolvedValue([
      {
        versionKey: 'drop-base-breakout-pullback-v1',
        name: '급락-횡보-돌파-눌림',
        status: 'PAPER_ENABLED',
        description: '전략 설명',
        parameters: { baseDays: 60 }
      }
    ]);
    api.getLimitedTradeCandidates.mockResolvedValue([
      {
        id: 1,
        code: '005930',
        reason: '급등 후보',
        referencePrice: 70000,
        suggestedQuantity: 1,
        status: 'PENDING'
      }
    ]);
    api.getTradingPerformance.mockResolvedValue({
      sampleCount: 0,
      averageSlippageRate: 0,
      averageNetReturnRate: 0,
      halted: false
    });
    api.getPaperTradeCycles.mockResolvedValue([]);
    api.getPaperTradeResults.mockResolvedValue([]);
    api.getTradePerformanceSummary.mockResolvedValue({ completedTrades: 0 });
    api.verifyPaperTradingLifecycle.mockResolvedValue({ passed: true });
  });

  it('shows automatic trading controls and candidates', async () => {
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <LimitedTradingPanel />
      </QueryClientProvider>
    );
    expect(await screen.findByText('005930')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: '모의투자 자동매매' })).toHaveAttribute(
      'aria-checked',
      'false'
    );
    expect(screen.getByRole('button', { name: '자동매매 설정 저장' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로컬 주문 흐름 검증' })).toBeInTheDocument();
  });
});
