import { useQuery } from '@tanstack/react-query';
import { getMarketRankings } from '../api/kiwoomApi';

const sections = [
  { key: 'gainers', title: '급등주', description: '전일 대비 상승률 상위' },
  { key: 'losers', title: '급락주', description: '전일 대비 하락률 상위' },
  { key: 'mostTraded', title: '거래량 상위', description: '오늘 거래가 가장 활발한 종목' }
];

const formatNumber = (value) => Number(value).toLocaleString('ko-KR');

function MarketDiscovery({ onSelectStock }) {
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
