import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  approveLimitedTrade,
  getLimitedTradeCandidates,
  getTradingPerformance,
  rejectLimitedTrade
} from '../api/kiwoomApi';
import { EmptyState, ErrorState, LoadingState } from './AsyncState';

export default function LimitedTradingPanel() {
  const queryClient = useQueryClient();
  const candidates = useQuery({
    queryKey: ['limited-trade-candidates'],
    queryFn: getLimitedTradeCandidates
  });
  const performance = useQuery({
    queryKey: ['trading-performance'],
    queryFn: getTradingPerformance
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['limited-trade-candidates'] });
  const approve = useMutation({ mutationFn: approveLimitedTrade, onSuccess: refresh });
  const reject = useMutation({ mutationFn: rejectLimitedTrade, onSuccess: refresh });

  if (candidates.isLoading || performance.isLoading) {
    return <LoadingState>제한 매매 상태를 불러오는 중입니다.</LoadingState>;
  }
  const error = candidates.error || performance.error || approve.error || reject.error;
  if (error) return <ErrorState>{error.message}</ErrorState>;

  const status = performance.data;
  return (
    <section aria-labelledby="limited-trading-title">
      <h2 id="limited-trading-title">제한 매매 승인</h2>
      <p className="subtitle">
        실제 주문이 아닌 PAPER 주문만 승인합니다. 주문당 10만원, 한 종목, 하루 2회로 제한됩니다.
      </p>
      <div className={status?.halted ? 'error' : 'empty-state'} role="status">
        표본 {status?.sampleCount ?? 0}건 · 평균 슬리피지{' '}
        {Number(status?.averageSlippageRate ?? 0).toFixed(4)} · 평균 순수익률{' '}
        {Number(status?.averageNetReturnRate ?? 0).toFixed(4)}
        {status?.halted && ` · 주문 중단: ${status.haltReason}`}
      </div>
      {!candidates.data?.length ? (
        <EmptyState>승인을 기다리는 후보가 없습니다.</EmptyState>
      ) : (
        <ul className="result-list" aria-label="매매 후보 목록">
          {candidates.data.map((candidate) => (
            <li key={candidate.id}>
              <strong>{candidate.code}</strong> · {candidate.reason}
              <div>
                기준가 {Number(candidate.referencePrice).toLocaleString()}원 ·{' '}
                {candidate.suggestedQuantity}주 · {candidate.status}
              </div>
              {candidate.status === 'PENDING' && (
                <div className="button-group">
                  <button type="button" onClick={() => approve.mutate(candidate.id)}>
                    PAPER 주문 승인
                  </button>
                  <button type="button" onClick={() => reject.mutate(candidate.id)}>
                    거절
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
