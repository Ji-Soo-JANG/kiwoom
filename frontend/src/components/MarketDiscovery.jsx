import { useQuery } from '@tanstack/react-query';
import { getMarketRankings, getStrategyCandidates } from '../api/kiwoomApi';

const sections = [
  { key: 'gainers', title: '급등주', description: '전일 대비 상승률 상위' },
  { key: 'losers', title: '급락주', description: '전일 대비 하락률 상위' },
  { key: 'mostTraded', title: '거래량 상위', description: '오늘 거래가 가장 활발한 종목' }
];

const formatNumber = (value) => Number(value).toLocaleString('ko-KR');

function MarketDiscovery({ onSelectStock }) {
  const strategy = useQuery({
    queryKey: ['strategy-candidates'],
    queryFn: getStrategyCandidates,
    staleTime: 5 * 60 * 1000,
    retry: false
  });
  const rankings = useQuery({
    queryKey: ['market-rankings'],
    queryFn: getMarketRankings,
    staleTime: 30 * 1000,
    refetchInterval: 60 * 1000
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
        <button type="button" onClick={() => rankings.refetch()} disabled={rankings.isFetching}>
          {rankings.isFetching ? '새로고침 중...' : '새로고침'}
        </button>
      </div>

      <article className="strategy-results" aria-labelledby="strategy-results-title">
        <div className="strategy-heading">
          <div>
            <h3 id="strategy-results-title">급락 후 횡보·돌파·눌림목 후보</h3>
            <p>당일 순위 종목의 최근 250개 일봉을 분석한 1차 후보입니다.</p>
          </div>
          <button type="button" onClick={() => strategy.refetch()} disabled={strategy.isFetching}>
            {strategy.isFetching ? '조건 검색 중...' : '조건 다시 검색'}
          </button>
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
              범위: {strategy.data.scope} · {strategy.data.scannedCount}개 분석 · 70점 이상 조건 충족
            </p>
            {strategy.data.candidates.length === 0 ? (
              <p className="empty-state">현재 후보군에는 분석 가능한 종목이 없습니다.</p>
            ) : (
              <div className="strategy-candidate-list">
                {strategy.data.candidates.map((stock) => (
                  <button
                    type="button"
                    className={stock.qualified ? 'strategy-candidate qualified' : 'strategy-candidate'}
                    key={stock.code}
                    onClick={() => onSelectStock(stock.code)}
                  >
                    <span className="strategy-score">{stock.score}점</span>
                    <span className="strategy-stock">
                      <strong>{stock.name}</strong>
                      <small>{stock.code}</small>
                    </span>
                    <span className="strategy-metrics">
                      급락 {stock.drawdownRate.toFixed(1)}% · 박스폭 {stock.boxRangeRate.toFixed(1)}% ·
                      눌림 {stock.pullbackRate.toFixed(1)}%
                      <small>{stock.matchedConditions.join(' · ')}</small>
                    </span>
                  </button>
                ))}
              </div>
            )}
          </>
        )}
        <p className="strategy-warning">
          전체 시장 스캔이 아니라 당일 순위 후보군을 분석한 결과이며 매수 추천이 아닙니다.
        </p>
      </article>

      <div className="ranking-grid">
        {sections.map((section) => (
          <article className="ranking-card" key={section.key}>
            <h3>{section.title}</h3>
            <p>{section.description}</p>
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
              ))}
            </ol>
          </article>
        ))}
      </div>
    </section>
  );
}

export default MarketDiscovery;
