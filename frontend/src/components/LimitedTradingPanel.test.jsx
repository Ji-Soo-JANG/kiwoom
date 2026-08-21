import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import LimitedTradingPanel from './LimitedTradingPanel';
import * as api from '../api/kiwoomApi';

vi.mock('../api/kiwoomApi');

describe('LimitedTradingPanel', () => {
  beforeEach(() => {
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
    api.verifyPaperTradingLifecycle.mockResolvedValue({ passed: true });
  });

  it('shows pending candidates and explicit paper approval', async () => {
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <LimitedTradingPanel />
      </QueryClientProvider>
    );
    expect(await screen.findByText('005930')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'PAPER 주문 승인' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로컬 주문 흐름 검증' })).toBeInTheDocument();
  });
});
