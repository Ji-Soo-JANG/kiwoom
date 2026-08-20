import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { useDeferredValue } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getFullMarketDataStatus,
  getMarketDataStatus,
  getMarketRankings,
  getStrategyCandidates,
  synchronizeFullMarketData,
  synchronizeMarketData
} from '../api/kiwoomApi';

/**
 * 수동 새로고침 디바운스: 연속 클릭을 방지하고 다음 갱신 가능 시점을 표시합니다.
 * REFRESH_COOLDOWN_MS 이후에야 다시 새로고침할 수 있습니다.
 */
const REFRESH_COOLDOWN_MS = 30_000;

function useRefreshCooldown() {
  const [cooldownEnd, setCooldownEnd] = useState(0);
  const [remaining, setRemaining] = useState(0);
  const timerRef = useRef(null);

  useEffect(() => {
    if (cooldownEnd === 0) return;
    const tick = () => {
      const left = Math.max(0, cooldownEnd - Date.now());
      setRemaining(left);
      if (left <= 0) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
    timerRef.current = setInterval(tick, 1000);
    tick();
    return () => clearInterval(timerRef.current);
  }, [cooldownEnd]);

  const canRefresh = remaining <= 0;
  const triggerCooldown = useCallback(() => {
    setCooldownEnd(Date.now() + REFRESH_COOLDOWN_MS);
  }, []);

  return {
    canRefresh,
    remainingSeconds: Math.ceil(remaining / 1000),
    triggerCooldown
  };
}

const sections = [
  { key: 'gainers', title: '급등주', description: '전일 대비 상승률 상위' },
  { key: 'losers', title: '급락주', description: '전일 대비 하락률 상위' },
  { key: 'mostTraded', title: '거래량 상위', description: '오늘 거래가 가장 활발한 종목' }
];

const formatNumber = (value) => Number(value).toLocaleString('ko-KR');

function MarketDiscovery({ onSelectStock }) {
  const queryClient = useQueryClient();
  const [boxRangeDays, setBoxRangeDays] = useState(60);
  const deferredBoxRangeDays = useDeferredValue(boxRangeDays);
  const refreshCooldown = useRefreshCooldown();
  const marketData = useQuery({ queryKey: ['market-data-status'], queryFn: getMarketDataStatus });
  const synchronize = useMutation({
    mutationFn: () => synchronizeMarketData(20),
    onSuccess: (status) => queryClient.setQueryData(['market-data-status'], status)
  });
  const fullMarketData = useQuery({
    queryKey: ['full-market-data-status'],
    queryFn: getFullMarketDataStatus,
    refetchInterval: (query) => (query.state.data?.running ? 3000 : 30000)
  });
  const startFullSync = useMutation({
    mutationFn: () => synchronizeFullMarketData(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['full-market-data-status'] })
  });
  const strategy = useQuery({
    queryKey: ['strategy-candidates', deferredBoxRangeDays],
    queryFn: () => getStrategyCandidates(deferredBoxRangeDays),
    staleTime: 5 * 60 * 1000,
    retry: false
  });
  const rankings = useQuery({
    queryKey: ['market-rankings'],
    queryFn: getMarketRankings,
    staleTime: 30 * 1000,
    refetchInterval: 60 * 1000,
    refetchOnWindowFocus: false
  });

  if (rankings.isPending) return <div className="loading-text">시장 순위를 불러오는 중...</div>;
  if (rankings.error) {
    return (
      <div className="error" role="alert">
        시장 순위를 불러오지 못했습니다: {rankings.error.message}
      </div>
    );
  }

  return (
    <section className="market-discovery" aria-labelledby="market-discovery-title">
      <div className="market-discovery-heading">
        <div>
          <h2 id="market-discovery-title">종목 발견</h2>
          <p>시장 흐름을 빠르게 살펴보고 종목을 눌러 상세 주가를 확인하세요.</p>
        </div>
        <button
          type="button"
          onClick={() => {
            rankings.refetch();
            refreshCooldown.triggerCooldown();
          }}
          disabled={rankings.isFetching || !refreshCooldown.canRefresh}
        >
          {rankings.isFetching
            ? '새로고침 중...'
            : !refreshCooldown.canRefresh
              ? `${refreshCooldown.remainingSeconds}초 후 갱신 가능`
              : '새로고침'}
        </button>
      </div>

      <section className="market-data-status" aria-labelledby="market-data-status-title">
        <div>
          <h3 id="market-data-status-title">시장 데이터 저장소</h3>
          {marketData.data ? (
            <p>
              종목 {formatNumber(marketData.data.stockCount)}개 · 일봉{' '}
              {formatNumber(marketData.data.candleCount)}건 · 수집 완료{' '}
              {formatNumber(marketData.data.syncedStockCount)}개
              {marketData.data.failedStockCount > 0 &&
                ` · 실패 ${formatNumber(marketData.data.failedStockCount)}개`}
            </p>
          ) : (
            <p>저장된 데이터 상태를 확인하는 중...</p>
          )}
        </div>
        <button type="button" onClick={() => synchronize.mutate()} disabled={synchronize.isPending}>
          {synchronize.isPending ? '20개 종목 수집 중...' : '다음 20개 종목 수집'}
        </button>
        {synchronize.error && (
          <p className="error">데이터 수집 실패: {synchronize.error.message}</p>
        )}
      </section>

      <section className="market-data-status" aria-labelledby="full-market-data-status-title">
        <div>
          <h3 id="full-market-data-status-title">전체 일봉 일괄 수집</h3>
          {fullMarketData.data ? (
            <>
              <p>
                전체 {formatNumber(fullMarketData.data.stockCount)}개 종목 · 일봉{' '}
                {formatNumber(fullMarketData.data.candleCount)}건 · 수집 완료{' '}
                {formatNumber(fullMarketData.data.syncedStockCount)}개
                {fullMarketData.data.failedStockCount > 0 &&
                  ` · 실패 ${formatNumber(fullMarketData.data.failedStockCount)}개`}
                {fullMarketData.data.running && ' · 수집 진행 중...'}
              </p>
              {fullMarketData.data.latestTradeDate && (
                <p>
                  최신 거래일 {fullMarketData.data.latestTradeDate} · 마지막 실행{' '}
                  {formatNumber(fullMarketData.data.processedInLastRun)}개 처리 (성공{' '}
                  {formatNumber(fullMarketData.data.succeededInLastRun)} · 실패{' '}
                  {formatNumber(fullMarketData.data.failedInLastRun)})
                </p>
              )}
            </>
          ) : (
            <p>전체 수집 상태를 확인하는 중...</p>
          )}
          {fullMarketData.error && (
            <p className="error">상태 조회 실패: {fullMarketData.error.message}</p>
          )}
        </div>
        <button
          type="button"
          onClick={() => startFullSync.mutate()}
          disabled={fullMarketData.data?.running || startFullSync.isPending}
        >
          {fullMarketData.data?.running || startFullSync.isPending
            ? '전체 수집 진행 중...'
            : '전체 수집 시작'}
        </button>
        {startFullSync.error && (
          <p className="error">전체 수집 실패: {startFullSync.error.message}</p>
        )}
      </section>

      <article className="strategy-results" aria-labelledby="strategy-results-title">
        <div className="strategy-heading">
          <div>
            <h3 id="strategy-results-title">급락 후 횡보·돌파·눌림목 후보</h3>
            <p>로컬 DB에 저장된 전체 종목의 최근 250개 일봉을 분석합니다.</p>
          </div>
          <button type="button" onClick={() => strategy.refetch()} disabled={strategy.isFetching}>
            {strategy.isFetching ? '조건 검색 중...' : '조건 다시 검색'}
          </button>
        </div>
        <div className="box-range-control">
          <label htmlFor="box-range-days">
            박스권 기준 기간 <strong>{boxRangeDays}거래일</strong>
          </label>
          <input
            id="box-range-days"
            type="range"
            min="30"
            max="120"
            step="10"
            value={boxRangeDays}
            aria-describedby="box-range-days-help"
            onChange={(event) => setBoxRangeDays(Number(event.target.value))}
          />
          <small id="box-range-days-help">
            {deferredBoxRangeDays !== boxRangeDays
              ? '기간 변경 중...'
              : `${deferredBoxRangeDays}거래일 동안의 박스권 횡보를 기준으로 분석합니다.`}
          </small>
        </div>
        {strategy.isPending && <div className="loading-text">전략 조건을 분석하는 중...</div>}
        {strategy.error && (
          <div className="error" role="alert">
            전략 후보를 불러오지 못했습니다: {strategy.error.message}
          </div>
        )}
        {strategy.data && (
          <>
            <p className="strategy-scope">
              범위: {strategy.data.scope} · {strategy.data.scannedCount}개 분석 · 70점 이상 조건
              충족
            </p>
            {strategy.data.candidates.length === 0 ? (
              <p className="empty-state">현재 후보군에는 분석 가능한 종목이 없습니다.</p>
            ) : (
              <div className="strategy-candidate-list">
                {strategy.data.candidates.map((stock) => (
                  <button
                    type="button"
                    className={
                      stock.qualified ? 'strategy-candidate qualified' : 'strategy-candidate'
                    }
                    key={stock.code}
                    onClick={() => onSelectStock(stock.code)}
                  >
                    <span className="strategy-score">{stock.score}점</span>
                    <span className="strategy-stock">
                      <strong>{stock.name}</strong>
                      <small>{stock.code}</small>
                    </span>
                    <span className="strategy-metrics">
                      급락 {stock.drawdownRate.toFixed(1)}% · 박스폭 {stock.boxRangeRate.toFixed(1)}
                      % · 눌림 {stock.pullbackRate.toFixed(1)}%
                      <small>{stock.matchedConditions.join(' · ')}</small>
                    </span>
                  </button>
                ))}
              </div>
            )}
          </>
        )}
        <p className="strategy-warning">
          일봉이 90개 이상 수집된 종목만 분석하며 결과는 매수 추천이 아닙니다.
        </p>
      </article>        {rankings.data.updatedAt && (
          <p className="info-text">
            마지막 갱신: {new Date(rankings.data.updatedAt).toLocaleString('ko-KR')}
          </p>
        )}

        <div className="ranking-grid">
        {sections.map((section) => (
          <article className="ranking-card" key={section.key}>
            <h3>{section.title}</h3>
            <p>{section.description}</p>
            {rankings.data[section.key].length === 0 ? (
              <p className="empty-state">
                장 마감이거나 해당 순위 데이터가 없습니다.
              </p>
            ) : (
            <ol>
              {rankings.data[section.key].map((stock, index) => (
                <li key={stock.code}>
                  <button type="button" onClick={() => onSelectStock(stock.code)}>
                    <span className="ranking-position">{index + 1}</span>
                    <span className="ranking-name">
                      <strong>{stock.name}</strong>
                      <small>{stock.code}</small>
                    </span>
                    <span className={stock.changeRate >= 0 ? 'price-up' : 'price-down'}>
                      {stock.changeRate > 0 ? '+' : ''}
                      {stock.changeRate.toFixed(2)}%
                      <small>{formatNumber(stock.currentPrice)}원</small>
                    </span>
                  </button>
                </li>
              )              )}
            </ol>
            )}
          </article>
        ))}
      </div>
    </section>
  );
}

export default MarketDiscovery;
