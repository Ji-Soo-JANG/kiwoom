import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import BoxEvaluationWorkbench, { MiniPriceChart } from './BoxEvaluationWorkbench';
import * as api from '../api/kiwoomApi';

vi.mock('../api/kiwoomApi');

describe('BoxEvaluationWorkbench', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getBoxEvaluationBatches.mockResolvedValue([{ id: 1, name: '블라인드 과제' }]);
    api.getBoxEvaluationBatchItems.mockResolvedValue([]);
    api.getBoxEvaluation.mockResolvedValue(null);
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

  it('renders a distinct final period overlay that follows edited boundaries and exposes all zone handles', () => {
    const onPeriodChange = vi.fn();
    const onZonesChange = vi.fn();
    const { rerender } = render(
      <MiniPriceChart
        candles={[
          { tradeDate: '2026-01-01', closePrice: 100 },
          { tradeDate: '2026-02-01', closePrice: 110 },
          { tradeDate: '2026-03-01', closePrice: 120 }
        ]}
        candidates={[{ candidateKey: 'NARROW', startDate: '2026-01-01', endDate: '2026-02-01' }]}
        activeKeys={['NARROW']}
        period={{ startDate: '2026-01-01', endDate: '2026-02-01' }}
        onPeriodChange={onPeriodChange}
        zones={{ lowerMin: 90, lowerMax: 95, upperMin: 115, upperMax: 120 }}
        onZonesChange={onZonesChange}
      />
    );
    const range = screen.getByTestId('final-period-range');
    expect(range).toBeInTheDocument();
    const initialX = range.getAttribute('x');
    rerender(
      <MiniPriceChart
        candles={[
          { tradeDate: '2026-01-01', closePrice: 100 },
          { tradeDate: '2026-02-01', closePrice: 110 },
          { tradeDate: '2026-03-01', closePrice: 120 }
        ]}
        candidates={[{ candidateKey: 'NARROW', startDate: '2026-01-01', endDate: '2026-02-01' }]}
        activeKeys={['NARROW']}
        period={{ startDate: '2026-02-01', endDate: '2026-03-01' }}
        onPeriodChange={onPeriodChange}
        zones={{ lowerMin: 90, lowerMax: 95, upperMin: 115, upperMax: 120 }}
        onZonesChange={onZonesChange}
      />
    );
    expect(screen.getByTestId('final-period-range').getAttribute('x')).not.toBe(initialX);
    expect(screen.getByTestId('lower-support-zone')).toBeInTheDocument();
    expect(screen.getByTestId('upper-resistance-zone')).toBeInTheDocument();
    expect(screen.getByTestId('lower-support-min-handle')).toBeInTheDocument();
    expect(screen.getByTestId('lower-support-max-handle')).toBeInTheDocument();
    expect(screen.getByTestId('upper-resistance-min-handle')).toBeInTheDocument();
    expect(screen.getByTestId('upper-resistance-max-handle')).toBeInTheDocument();
  });

  it('prevents page scrolling with a non-passive wheel listener on the chart only', () => {
    render(
      <MiniPriceChart
        candles={[{ tradeDate: '2026-01-01', closePrice: 100 }, { tradeDate: '2026-02-01', closePrice: 110 }]}
        candidates={[]}
        activeKeys={[]}
        period={{}}
        zones={{}}
      />
    );
    const chart = screen.getByRole('img');
    const wheel = new WheelEvent('wheel', { deltaY: -100, cancelable: true });
    chart.dispatchEvent(wheel);
    expect(wheel.defaultPrevented).toBe(true);
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

  it('renders persisted proposed zones for the active reviewer after BOX selection', async () => {
    api.getBoxFormationEvaluation.mockResolvedValue({
      itemId: 10,
      reviewerId: 'admin',
      formationLabel: null,
      finalStartDate: '2026-02-01',
      finalEndDate: '2026-03-01',
      note: 'persisted formation note',
      confidence: 4,
      proposedLowerSupportMin: 7700,
      proposedLowerSupportMax: 7900,
      proposedUpperResistanceMin: 8600,
      proposedUpperResistanceMax: 8900,
      finalLowerSupportMin: null,
      finalLowerSupportMax: null,
      finalUpperResistanceMin: null,
      finalUpperResistanceMax: null,
      periodDecision: 'ACCEPTED',
      zoneDecision: 'ACCEPTED',
      revision: 1
    });
    api.getBoxEvaluationProgress.mockResolvedValue({ completed: 0, total: 1 });
    render(
      <QueryClientProvider client={new QueryClient()}>
        <BoxEvaluationWorkbench reviewerId="admin" formationMode />
      </QueryClientProvider>
    );
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '1' } });
    fireEvent.click((await screen.findAllByRole('button'))[0]);
    fireEvent.click(await screen.findByLabelText('BOX'));
    expect(await screen.findByText(/Proposed:\s*7700.?7900\s*\/\s*8600.?8900/)).toBeInTheDocument();
    expect(screen.getByTestId('lower-support-zone')).toBeInTheDocument();
    expect(screen.getByTestId('upper-resistance-zone')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2026-02-01')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2026-03-01')).toBeInTheDocument();
    expect(screen.getByLabelText('확신도')).toHaveValue('4');
    expect(screen.getByDisplayValue('persisted formation note')).toBeInTheDocument();
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
    fireEvent.click(screen.getByTestId('completion-previous'));
    expect(await screen.findByText('블라인드 평가 항목 실제 일봉')).toBeInTheDocument();
  });

  it('reopens the latest committed item from persisted batch state after re-entry', async () => {
    api.getBoxEvaluationBatchItems.mockResolvedValue([
      { id: 10, status: 'COMMITTED' },
      { id: 11, status: 'COMMITTED' }
    ]);
    api.getBoxEvaluationItem.mockImplementation((id) => Promise.resolve({
      item: { id, code: '005930', cutoffDate: '2026-08-20' },
      candidates: [{ candidateKey: 'NARROW', startDate: '2026-01-01', endDate: '2026-08-01' }],
      draft: null
    }));
    render(
      <QueryClientProvider client={new QueryClient()}>
        <BoxEvaluationWorkbench reviewerId="admin" formationMode />
      </QueryClientProvider>
    );
    const previous = await screen.findByTestId('previous-evaluation');
    await waitFor(() => expect(previous).toBeEnabled());
    fireEvent.click(previous);
    await waitFor(() => expect(api.getBoxEvaluationItem).toHaveBeenCalledWith(11, 'admin'));
  });

  it('reconfirms a reopened committed BOX through the formation upsert path', async () => {
    api.getBoxEvaluationItem.mockResolvedValue({
      item: { id: 10, code: '005930', cutoffDate: '2026-08-20' },
      candidates: [{ candidateKey: 'NARROW', startDate: '2026-01-01', endDate: '2026-08-01' }],
      draft: null
    });
    api.getBoxEvaluation.mockResolvedValue({
      boundaryDecision: 'CANDIDATE', selectedCandidateKey: 'NARROW',
      finalStartDate: '2026-02-01', finalEndDate: '2026-03-01',
      labelCode: 'VALID_BOX', confidence: 4, reasonCodes: 'STABLE_RANGE', comment: 'old'
    });
    api.getBoxFormationEvaluation.mockResolvedValue({
      formationLabel: 'BOX', finalStartDate: '2026-02-01', finalEndDate: '2026-03-01',
      proposedLowerSupportMin: 90, proposedLowerSupportMax: 95,
      proposedUpperResistanceMin: 115, proposedUpperResistanceMax: 120,
      finalLowerSupportMin: 90, finalLowerSupportMax: 95,
      finalUpperResistanceMin: 115, finalUpperResistanceMax: 120,
      periodDecision: 'ACCEPTED', zoneDecision: 'ACCEPTED', note: 'old', confidence: 4, revision: 1
      , boundaryDecision: 'MANUAL', labelCode: 'VALID_BOX', reasonCodes: 'STABLE_RANGE', comment: 'old comment'
    });
    api.saveBoxFormationEvaluation.mockResolvedValue({ id: 20 });
    render(
      <QueryClientProvider client={new QueryClient()}>
        <BoxEvaluationWorkbench reviewerId="admin" formationMode />
      </QueryClientProvider>
    );
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '1' } });
    fireEvent.click((await screen.findAllByRole('button'))[0]);
    const confirm = await screen.findByRole('button', { name: '평가 내용 확인 및 확정' });
    await waitFor(() => expect(confirm).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Save formation evaluation' }));
    await waitFor(() => expect(api.saveBoxFormationEvaluation).toHaveBeenCalled());
    expect(api.saveBoxEvaluationDraft).not.toHaveBeenCalled();
    api.saveBoxFormationEvaluation.mockClear();
    fireEvent.change(screen.getByLabelText('Formation note'), { target: { value: 'edited persisted note' } });
    fireEvent.click(confirm);
    await waitFor(() => expect(api.saveBoxFormationEvaluation).toHaveBeenCalledWith(10, expect.objectContaining({
      reviewerId: 'admin', formationLabel: 'BOX', finalStartDate: '2026-02-01', finalEndDate: '2026-03-01', note: 'edited persisted note',
      boundaryDecision: 'MANUAL', labelCode: 'VALID_BOX', reasonCodes: 'STABLE_RANGE', comment: 'old comment'
    })));
    expect(api.commitBoxEvaluation).not.toHaveBeenCalled();
    expect(screen.queryByText('확정할 수 없는 평가 항목 상태입니다.')).not.toBeInTheDocument();
  });

  it('enables and saves a COMMITTED UNCERTAIN item after changing classification to BOX', async () => {
    api.getNextBoxEvaluationItem.mockResolvedValueOnce({ id: 68 });
    api.getBoxEvaluationItem.mockResolvedValue({
      item: { id: 68, code: '000050', cutoffDate: '2026-08-21' },
      candidates: [{ candidateKey: 'NARROW', startDate: '2026-05-27', endDate: '2026-08-07' }],
      draft: null
    });
    api.getBoxFormationEvaluation.mockResolvedValue({
      formationLabel: 'UNCERTAIN', finalStartDate: null, finalEndDate: null,
      proposedLowerSupportMin: 7850, proposedLowerSupportMax: 8230,
      proposedUpperResistanceMin: 8700, proposedUpperResistanceMax: 8900,
      finalLowerSupportMin: null, finalLowerSupportMax: null,
      finalUpperResistanceMin: null, finalUpperResistanceMax: null,
      periodDecision: 'ACCEPTED', zoneDecision: 'ACCEPTED', note: 'uncertain', confidence: 3, revision: 1
    });
    api.saveBoxFormationEvaluation.mockResolvedValue({ id: 30 });
    render(<QueryClientProvider client={new QueryClient()}><BoxEvaluationWorkbench reviewerId="admin" formationMode /></QueryClientProvider>);
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '1' } });
    const nextButton = (await screen.findAllByRole('button')).find((button) => button.textContent.includes('다음'));
    await waitFor(() => expect(nextButton).toBeEnabled());
    fireEvent.click(nextButton);
    await waitFor(() => expect(api.getBoxEvaluationItem).toHaveBeenCalledWith(68, 'admin'));
    fireEvent.keyDown(document.body, { key: '1', bubbles: true });
    await waitFor(() => expect(document.querySelectorAll('input[type="date"]').length).toBe(2));
    const dateInputs = Array.from(document.querySelectorAll('input[type="date"]'));
    fireEvent.change(dateInputs[0], { target: { value: '2026-05-27' } });
    fireEvent.change(dateInputs[1], { target: { value: '2026-08-07' } });
    const confirm = await screen.findByRole('button', { name: '평가 내용 확인 및 확정' });
    await waitFor(() => expect(confirm).toBeEnabled());
    fireEvent.click(confirm);
    await waitFor(() => expect(api.saveBoxFormationEvaluation).toHaveBeenCalledWith(68, expect.objectContaining({ formationLabel: 'BOX' })));
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
