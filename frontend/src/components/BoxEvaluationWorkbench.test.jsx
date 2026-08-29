import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import BoxEvaluationWorkbench from './BoxEvaluationWorkbench';
import * as api from '../api/kiwoomApi';

vi.mock('../api/kiwoomApi');

describe('BoxEvaluationWorkbench', () => {
  beforeEach(() => {
    api.getBoxEvaluationBatches.mockResolvedValue([{ id: 1, name: '블라인드 과제' }]);
    api.getNextBoxEvaluationItem.mockResolvedValue({ id: 10 });
    api.getBoxEvaluationItem.mockResolvedValue({
      item: { id: 10, code: '005930', cutoffDate: '2026-08-20' },
      candidates: [{ candidateKey: 'NARROW', startDate: '2026-01-01', endDate: '2026-08-01' }],
      draft: null
    });
    api.getBoxEvaluationCandles.mockResolvedValue([
      { tradeDate: '2026-01-01', closePrice: 100 },
      { tradeDate: '2026-08-01', closePrice: 110 }
    ]);
  });

  it('loads a blind item and exposes candidate editing without trading controls', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <BoxEvaluationWorkbench reviewerId="admin" />
      </QueryClientProvider>
    );
    fireEvent.change(await screen.findByLabelText('평가 과제'), { target: { value: '1' } });
    const next = screen.getByRole('button', { name: '다음 블라인드 항목' });
    await waitFor(() => expect(next).toBeEnabled());
    fireEvent.click(next);
    expect(await screen.findByText(/이후 가격은 서버에서 차단됨/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '이 후보 선택' }));
    await waitFor(() => expect(screen.getByLabelText('시작 거래일')).toHaveValue('2026-01-01'));
    expect(screen.queryByText('자동매매 ON')).not.toBeInTheDocument();
  });
});
