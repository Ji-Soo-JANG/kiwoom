import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import BoxEvaluationWorkbench from './BoxEvaluationWorkbench';
import * as api from '../api/kiwoomApi';

vi.mock('../api/kiwoomApi');

describe('BoxEvaluationWorkbench', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
    api.commitBoxEvaluation.mockResolvedValue({ id: 100 });
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
    expect(screen.getByText('블라인드 평가 항목 실제 일봉')).toBeInTheDocument();
    expect(screen.queryByText(/005930 실제 일봉/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '미래 결과 공개' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '이 후보 선택' }));
    await waitFor(() => expect(screen.getByLabelText('시작 거래일')).toHaveValue('2026-01-01'));
    expect(screen.queryByText('자동매매 ON')).not.toBeInTheDocument();
  });

  it('moves to the next blind item after a successful commit and clears item state', async () => {
    api.getNextBoxEvaluationItem
      .mockResolvedValueOnce({ id: 10 })
      .mockResolvedValueOnce({ id: 11 });
    api.getBoxEvaluationItem.mockImplementation((id) =>
      Promise.resolve({
        item: { id, code: id === 10 ? '005930' : '000660', cutoffDate: '2026-08-20' },
        candidates:
          id === 10
            ? [{ candidateKey: 'NARROW', startDate: '2026-01-01', endDate: '2026-08-01' }]
            : [{ candidateKey: 'NEXT', startDate: '2025-01-01', endDate: '2026-07-01' }],
        draft: null
      })
    );

    render(
      <QueryClientProvider client={new QueryClient()}>
        <BoxEvaluationWorkbench reviewerId="admin" />
      </QueryClientProvider>
    );
    const nextButton = await screen.findByRole('button', { name: '다음 블라인드 항목' });
    await waitFor(() => expect(nextButton).toBeEnabled());
    fireEvent.click(nextButton);
    fireEvent.click(await screen.findByRole('button', { name: '이 후보 선택' }));
    fireEvent.change(screen.getByLabelText('평가'), { target: { value: 'VALID_BOX' } });
    fireEvent.click(screen.getByLabelText('STABLE_RANGE'));
    fireEvent.change(screen.getByLabelText('확신도'), { target: { value: '4' } });
    fireEvent.change(screen.getByLabelText('설명'), { target: { value: '이전 항목 메모' } });
    fireEvent.click(screen.getByRole('button', { name: '평가 내용 확인 및 확정' }));

    await waitFor(() => expect(api.getBoxEvaluationItem).toHaveBeenLastCalledWith(11, 'admin'));
    expect(await screen.findByText(/다음 블라인드 항목을 불러왔습니다/)).toBeInTheDocument();
    expect(screen.getByText('블라인드 평가 항목 실제 일봉')).toBeInTheDocument();
    expect(screen.getByLabelText('평가')).toHaveValue('');
    expect(screen.getByLabelText('확신도')).toHaveValue('');
    expect(screen.getByLabelText('설명')).toHaveValue('');
    expect(screen.getByLabelText('STABLE_RANGE')).not.toBeChecked();
  });

  it('keeps the current item when commit fails', async () => {
    api.commitBoxEvaluation.mockRejectedValue(new Error('확정 실패'));

    render(
      <QueryClientProvider client={new QueryClient()}>
        <BoxEvaluationWorkbench reviewerId="admin" />
      </QueryClientProvider>
    );
    const nextButton = await screen.findByRole('button', { name: '다음 블라인드 항목' });
    await waitFor(() => expect(nextButton).toBeEnabled());
    fireEvent.click(nextButton);
    fireEvent.click(await screen.findByRole('button', { name: '이 후보 선택' }));
    fireEvent.change(await screen.findByLabelText('평가'), {
      target: { value: 'VALID_BOX' }
    });
    fireEvent.click(screen.getByLabelText('STABLE_RANGE'));
    fireEvent.change(screen.getByLabelText('확신도'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: '평가 내용 확인 및 확정' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('확정 실패');
    expect(screen.getByText('블라인드 평가 항목 실제 일봉')).toBeInTheDocument();
    expect(api.getNextBoxEvaluationItem).toHaveBeenCalledTimes(1);
  });

  it('shows completion and clears the prior item when no blind item remains', async () => {
    api.getNextBoxEvaluationItem.mockResolvedValueOnce({ id: 10 }).mockResolvedValueOnce(null);

    render(
      <QueryClientProvider client={new QueryClient()}>
        <BoxEvaluationWorkbench reviewerId="admin" />
      </QueryClientProvider>
    );
    const nextButton = await screen.findByRole('button', { name: '다음 블라인드 항목' });
    await waitFor(() => expect(nextButton).toBeEnabled());
    fireEvent.click(nextButton);
    fireEvent.click(await screen.findByRole('button', { name: '이 후보 선택' }));
    fireEvent.change(await screen.findByLabelText('평가'), {
      target: { value: 'VALID_BOX' }
    });
    fireEvent.click(screen.getByLabelText('STABLE_RANGE'));
    fireEvent.change(screen.getByLabelText('확신도'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: '평가 내용 확인 및 확정' }));

    expect(await screen.findByText(/모든 블라인드 항목을 완료했습니다/)).toBeInTheDocument();
    expect(screen.queryByText('블라인드 평가 항목 실제 일봉')).not.toBeInTheDocument();
  });

  it('requires deliberate confidence and explanation for partial evaluations', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <BoxEvaluationWorkbench reviewerId="admin" />
      </QueryClientProvider>
    );
    const nextButton = await screen.findByRole('button', { name: '다음 블라인드 항목' });
    await waitFor(() => expect(nextButton).toBeEnabled());
    fireEvent.click(nextButton);
    const commitButton = await screen.findByRole('button', {
      name: '평가 내용 확인 및 확정'
    });
    fireEvent.change(screen.getByLabelText('평가'), { target: { value: 'PARTIAL_BOX' } });
    fireEvent.click(screen.getByLabelText('BOUNDARY_AMBIGUOUS'));
    expect(commitButton).toBeDisabled();
    fireEvent.change(screen.getByLabelText('확신도'), { target: { value: '2' } });
    expect(commitButton).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/설명/), { target: { value: '종료 경계가 모호함' } });
    expect(commitButton).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '이 후보 선택' }));
    expect(commitButton).toBeEnabled();
  });

  it('requires an explicit no-candidate decision for a negative label', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <BoxEvaluationWorkbench reviewerId="admin" />
      </QueryClientProvider>
    );
    const nextButton = await screen.findByRole('button', { name: '다음 블라인드 항목' });
    await waitFor(() => expect(nextButton).toBeEnabled());
    fireEvent.click(nextButton);
    const commitButton = await screen.findByRole('button', {
      name: '평가 내용 확인 및 확정'
    });
    fireEvent.change(screen.getByLabelText('평가'), { target: { value: 'NOT_BOX' } });
    fireEvent.change(screen.getByLabelText('확신도'), { target: { value: '4' } });
    fireEvent.click(screen.getByLabelText('BOUNDARY_AMBIGUOUS'));
    expect(commitButton).toBeDisabled();
    fireEvent.click(screen.getByLabelText('적합 후보 없음'));
    expect(commitButton).toBeEnabled();
  });
});
