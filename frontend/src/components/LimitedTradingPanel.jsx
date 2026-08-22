import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  getAutoTradingControl,
  getLimitedTradeCandidates,
  getLatestObservation,
  getPaperTradeCycles,
  getPaperTradeResults,
  getTradePerformanceSummary,
  getTradingPerformance,
  updateAutoTradingControl,
  verifyPaperTradingLifecycle
} from '../api/kiwoomApi';
import { EmptyState, ErrorState, LoadingState } from './AsyncState';

export default function LimitedTradingPanel() {
  const queryClient = useQueryClient();
  const [controlForm, setControlForm] = useState(null);
  const control = useQuery({ queryKey: ['auto-trading-control'], queryFn: getAutoTradingControl });
  const observation = useQuery({ queryKey: ['latest-observation'], queryFn: getLatestObservation });
  const candidates = useQuery({
    queryKey: ['limited-trade-candidates'],
    queryFn: getLimitedTradeCandidates
  });
  const performance = useQuery({
    queryKey: ['trading-performance'],
    queryFn: getTradingPerformance
  });
  const cycles = useQuery({ queryKey: ['paper-trade-cycles'], queryFn: getPaperTradeCycles });
  const results = useQuery({ queryKey: ['paper-trade-results'], queryFn: getPaperTradeResults });
  const summary = useQuery({
    queryKey: ['trade-performance-summary'],
    queryFn: getTradePerformanceSummary
  });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['limited-trade-candidates'] });
    queryClient.invalidateQueries({ queryKey: ['paper-trade-cycles'] });
    queryClient.invalidateQueries({ queryKey: ['paper-trade-results'] });
    queryClient.invalidateQueries({ queryKey: ['trade-performance-summary'] });
  };
  const saveControl = useMutation({
    mutationFn: updateAutoTradingControl,
    onSuccess: (data) => {
      queryClient.setQueryData(['auto-trading-control'], data);
      setControlForm(data);
      refresh();
    }
  });
  const verification = useMutation({ mutationFn: verifyPaperTradingLifecycle });

  const currentControl = controlForm ?? control.data;

  if (
    control.isLoading ||
    observation.isLoading ||
    candidates.isLoading ||
    performance.isLoading ||
    cycles.isLoading ||
    results.isLoading ||
    summary.isLoading
  ) {
    return <LoadingState>제한 매매 상태를 불러오는 중입니다.</LoadingState>;
  }
  const error =
    control.error ||
    observation.error ||
    candidates.error ||
    performance.error ||
    cycles.error ||
    results.error ||
    summary.error ||
    saveControl.error;
  if (error) return <ErrorState>{error.message}</ErrorState>;

  const status = performance.data;
  return (
    <section aria-labelledby="limited-trading-title">
      <h2 id="limited-trading-title">자동매매 제어</h2>
      <p className="subtitle">
        PAPER는 신호 발생부터 청산까지 자동 처리합니다. 실투자는 어댑터가 없어 ON 상태에서도 주문이
        전송되지 않습니다.
      </p>
      {currentControl && (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            saveControl.mutate({
              paperEnabled: currentControl.paperEnabled,
              paperStrategy: currentControl.paperStrategy,
              liveEnabled: currentControl.liveEnabled,
              liveStrategy: currentControl.liveStrategy,
              liveConfirmation: currentControl.liveEnabled ? 'ENABLE_BLOCKED_LIVE_AUTOMATION' : ''
            });
          }}
        >
          <label>
            <input
              type="checkbox"
              checked={currentControl.paperEnabled}
              onChange={(e) =>
                setControlForm({ ...currentControl, paperEnabled: e.target.checked })
              }
            />
            모의투자 자동매매
          </label>
          <select
            aria-label="모의투자 전략"
            value={currentControl.paperStrategy}
            onChange={(e) => setControlForm({ ...currentControl, paperStrategy: e.target.value })}
          >
            {currentControl.availableStrategies.map((strategy) => (
              <option key={strategy}>{strategy}</option>
            ))}
          </select>
          <label>
            <input
              type="checkbox"
              checked={currentControl.liveEnabled}
              onChange={(e) => setControlForm({ ...currentControl, liveEnabled: e.target.checked })}
            />
            실투자 자동매매 요청
          </label>
          <select
            aria-label="실투자 전략"
            value={currentControl.liveStrategy}
            onChange={(e) => setControlForm({ ...currentControl, liveStrategy: e.target.value })}
          >
            {currentControl.availableStrategies.map((strategy) => (
              <option key={strategy}>{strategy}</option>
            ))}
          </select>
          <button type="submit" disabled={saveControl.isPending}>
            {saveControl.isPending ? '저장 중...' : '자동매매 설정 저장'}
          </button>
        </form>
      )}
      {control.data?.liveEnabled && (
        <div className="error" role="status">
          실투자 주문 차단: {control.data.liveBlockers.join(' ')}
        </div>
      )}
      <div className="empty-state" role="status">
        장중 관찰 {observation.data?.observedTradingDays ?? 0}/
        {observation.data?.minimumTradingDays ?? 20}거래일 · 누락 신호{' '}
        {observation.data?.missedSignals ?? 0}건 · 예상 밖 신호{' '}
        {observation.data?.unexpectedSignals ?? 0}건 · 일치율{' '}
        {Number(observation.data?.agreementRate ?? 0).toFixed(1)}% · 가격 편차{' '}
        {Number(observation.data?.averagePriceDeviationRate ?? 0).toFixed(2)}%
      </div>
      <div className={status?.halted ? 'error' : 'empty-state'} role="status">
        표본 {status?.sampleCount ?? 0}건 · 평균 슬리피지{' '}
        {Number(status?.averageSlippageRate ?? 0).toFixed(4)} · 평균 순수익률{' '}
        {Number(status?.averageNetReturnRate ?? 0).toFixed(4)}
        {status?.halted && ` · 주문 중단: ${status.haltReason}`}
      </div>
      <div className="button-group">
        <button
          type="button"
          onClick={() => verification.mutate()}
          disabled={verification.isPending}
        >
          {verification.isPending ? '검증 중...' : '로컬 주문 흐름 검증'}
        </button>
      </div>
      {verification.data && (
        <div className={verification.data.passed ? 'empty-state' : 'error'} role="status">
          주문 흐름 검증: {verification.data.passed ? '통과' : '실패'} · 부분 체결, 미체결, 정정,
          취소, 복구, 중복 체결 방지
        </div>
      )}
      {!candidates.data?.length ? (
        <EmptyState>발견된 전략 후보가 없습니다.</EmptyState>
      ) : (
        <ul className="result-list" aria-label="매매 후보 목록">
          {candidates.data.map((candidate) => (
            <li key={candidate.id}>
              <strong>{candidate.code}</strong> · {candidate.reason}
              <div>
                기준가 {Number(candidate.referencePrice).toLocaleString()}원 ·{' '}
                {candidate.suggestedQuantity}주 · {candidate.status}
              </div>
            </li>
          ))}
        </ul>
      )}
      <h3>보유 중</h3>
      <TradeCycles items={cycles.data?.filter((item) => item.status === 'HOLDING')} />
      <h3>청산 조건 감지</h3>
      <TradeCycles items={cycles.data?.filter((item) => item.status === 'EXIT_PENDING')} />
      <h3>완료 거래</h3>
      {!results.data?.length ? (
        <EmptyState>완료된 PAPER 거래가 없습니다.</EmptyState>
      ) : (
        <ul className="result-list">
          {results.data.map((item) => (
            <li key={item.cycleId}>
              #{item.cycleId} · {item.exitReason} · 순손익 {Number(item.netPnl).toLocaleString()}원
              · 순수익률 {(Number(item.netReturnRate) * 100).toFixed(2)}%
            </li>
          ))}
        </ul>
      )}
      <div className="empty-state" role="status">
        완료 {summary.data?.completedTrades ?? 0}건 · 승률{' '}
        {((summary.data?.winRate ?? 0) * 100).toFixed(1)}% · 손익비{' '}
        {Number(summary.data?.payoffRatio ?? 0).toFixed(2)} · Profit Factor{' '}
        {Number(summary.data?.profitFactor ?? 0).toFixed(2)} · 연속 손실{' '}
        {summary.data?.consecutiveLosses ?? 0}회 · 최대 낙폭{' '}
        {((summary.data?.maximumDrawdownRate ?? 0) * 100).toFixed(2)}%
      </div>
    </section>
  );
}

function TradeCycles({ items = [] }) {
  if (!items.length) return <EmptyState>해당 상태의 PAPER 포지션이 없습니다.</EmptyState>;
  return (
    <ul className="result-list">
      {items.map((item) => (
        <li key={item.id}>
          <strong>{item.code}</strong> · {item.quantity}주 · 진입가{' '}
          {Number(item.entryPrice).toLocaleString()}원
          <div>
            손절 {Number(item.stopLossPrice).toLocaleString()}원 · 익절{' '}
            {Number(item.takeProfitPrice).toLocaleString()}원 · 최대 {item.maxHoldingDays}일
          </div>
        </li>
      ))}
    </ul>
  );
}
