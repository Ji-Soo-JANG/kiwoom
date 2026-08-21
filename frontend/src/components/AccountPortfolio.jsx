import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { getAccountPortfolio } from '../api/kiwoomApi';

const number = (value) => Number(value).toLocaleString('ko-KR');
const COLORS = [
  '#4f46e5',
  '#0ea5e9',
  '#10b981',
  '#f59e0b',
  '#ef4444',
  '#8b5cf6',
  '#ec4899',
  '#6366f1',
  '#14b8a6',
  '#f97316'
];

const SORT_OPTIONS = [
  { key: 'evaluationAmount', label: '평가금액' },
  { key: 'profitLoss', label: '손익' },
  { key: 'returnRate', label: '수익률' },
  { key: 'weight', label: '비중' },
  { key: 'name', label: '종목명' }
];

const FILTER_OPTIONS = [
  { key: 'all', label: '전체' },
  { key: 'profit', label: '수익' },
  { key: 'loss', label: '손실' }
];

function classifyAsset(name = '') {
  const upper = name.toUpperCase();
  if (upper.includes('ETF') || /^(KODEX|TIGER|ACE|RISE|SOL|PLUS|KOSEF|HANARO)/.test(upper))
    return 'ETF';
  if (upper.includes('ETN')) return 'ETN';
  if (name.includes('리츠')) return '리츠';
  if (name.includes('스팩') || upper.includes('SPAC')) return '스팩';
  if (/우[B-C]?$/.test(name)) return '우선주';
  return '보통주';
}

function AccountPortfolio({ onSelectStock }) {
  const [sortKey, setSortKey] = useState('evaluationAmount');
  const [sortAsc, setSortAsc] = useState(false);
  const [filterKey, setFilterKey] = useState('all');

  const portfolio = useQuery({
    queryKey: ['kiwoom-account-portfolio'],
    queryFn: getAccountPortfolio,
    staleTime: 10 * 1000,
    refetchOnWindowFocus: false
  });

  const positions = useMemo(() => portfolio.data?.positions ?? [], [portfolio.data]);

  useEffect(() => {
    if (portfolio.data) localStorage.setItem('kiwoom.account.selected', 'primary');
  }, [portfolio.data]);

  const analysis = useMemo(() => {
    const data = portfolio.data;
    if (!data) return null;
    const assetTypes = Object.entries(
      positions.reduce((groups, position) => {
        const type = classifyAsset(position.name);
        groups[type] = (groups[type] ?? 0) + Number(position.evaluationAmount);
        return groups;
      }, {})
    ).sort((a, b) => b[1] - a[1]);
    const positionEvaluation = positions.reduce(
      (sum, item) => sum + Number(item.evaluationAmount),
      0
    );
    const positionProfit = positions.reduce((sum, item) => sum + Number(item.profitLoss), 0);
    const cash = Math.max(Number(data.estimatedAssets) - Number(data.totalEvaluationAmount), 0);
    return {
      assetTypes,
      cash,
      cashRate: data.estimatedAssets > 0 ? (cash / Number(data.estimatedAssets)) * 100 : 0,
      evaluationDifference: Number(data.totalEvaluationAmount) - positionEvaluation,
      profitDifference: Number(data.totalProfitLoss) - positionProfit,
      topWeight: Math.max(...positions.map((item) => Number(item.weight)), 0)
    };
  }, [portfolio.data, positions]);

  const sortedPositions = useMemo(() => {
    let list = [...positions];
    if (filterKey === 'profit') list = list.filter((p) => p.profitLoss > 0);
    else if (filterKey === 'loss') list = list.filter((p) => p.profitLoss < 0);
    list.sort((a, b) => {
      const av = a[sortKey];
      const bv = b[sortKey];
      if (typeof av === 'string') return sortAsc ? av.localeCompare(bv) : bv.localeCompare(av);
      return sortAsc ? av - bv : bv - av;
    });
    return list;
  }, [positions, sortKey, sortAsc, filterKey]);

  const pieData = useMemo(() => {
    return positions
      .filter((p) => p.weight > 0)
      .sort((a, b) => b.weight - a.weight)
      .slice(0, 10)
      .map((p) => ({ name: p.name, value: p.weight }));
  }, [positions]);

  const toggleSort = (key) => {
    if (sortKey === key) setSortAsc(!sortAsc);
    else {
      setSortKey(key);
      setSortAsc(false);
    }
  };

  if (portfolio.isPending) return <div className="loading-text">계좌 잔고를 불러오는 중...</div>;
  if (portfolio.error) {
    const msg = portfolio.error.message || '';
    let hint = '키움 계좌 잔고를 불러오지 못했습니다.';
    if (msg.includes('인증') || msg.includes('401'))
      hint = '키움 인증이 만료되었습니다. 앱을 재시작해 주세요.';
    else if (msg.includes('계좌'))
      hint = '등록된 키움 계좌가 없습니다. 키움 앱에서 계좌를 개설해 주세요.';
    return (
      <div className="error" role="alert">
        {hint}
        <small style={{ display: 'block', marginTop: 4, color: '#667085' }}>{msg}</small>
      </div>
    );
  }

  const data = portfolio.data;
  const updatedAt = data.updatedAt ? new Date(data.updatedAt) : null;
  const updatedStr = updatedAt
    ? `${updatedAt.getHours().toString().padStart(2, '0')}:${updatedAt.getMinutes().toString().padStart(2, '0')}:${updatedAt.getSeconds().toString().padStart(2, '0')}`
    : '';

  return (
    <section className="account-portfolio" aria-labelledby="account-portfolio-title">
      <div className="market-discovery-heading">
        <div>
          <h2 id="account-portfolio-title">내 계좌 포트폴리오</h2>
          <p>
            키움 계좌 {data.accountNumber}의 실시간 평가잔고입니다.
            {updatedStr && (
              <small style={{ marginLeft: 8, color: '#98a2b3' }}>갱신: {updatedStr}</small>
            )}
          </p>
        </div>
        <button type="button" onClick={() => portfolio.refetch()} disabled={portfolio.isFetching}>
          {portfolio.isFetching ? '조회 중...' : '잔고 새로고침'}
        </button>
      </div>

      <dl className="account-summary" aria-label="계좌 요약">
        <div>
          <dt>추정자산</dt>
          <dd>{number(data.estimatedAssets)}원</dd>
        </div>
        <div>
          <dt>총 매입금액</dt>
          <dd>{number(data.totalPurchaseAmount)}원</dd>
        </div>
        <div>
          <dt>총 평가금액</dt>
          <dd>{number(data.totalEvaluationAmount)}원</dd>
        </div>
        <div>
          <dt>평가손익</dt>
          <dd className={data.totalProfitLoss >= 0 ? 'price-up' : 'price-down'}>
            {number(data.totalProfitLoss)}원
          </dd>
        </div>
        <div>
          <dt>수익률</dt>
          <dd className={data.totalReturnRate >= 0 ? 'price-up' : 'price-down'}>
            {data.totalReturnRate.toFixed(2)}%
          </dd>
        </div>
      </dl>

      {data.positions.length === 0 ? (
        <p className="empty-state">현재 보유 중인 국내주식이 없습니다.</p>
      ) : (
        <>
          <section className="account-analysis" aria-labelledby="account-analysis-title">
            <h3 id="account-analysis-title">계좌 분석</h3>
            <div className="analysis-summary">
              <div>
                <span>현금 추정액</span>
                <strong>{number(analysis.cash)}원</strong>
                <small>{analysis.cashRate.toFixed(1)}%</small>
              </div>
              <div>
                <span>최대 종목 비중</span>
                <strong>{analysis.topWeight.toFixed(1)}%</strong>
                <small>{analysis.topWeight >= 40 ? '집중도 높음' : '분산 범위'}</small>
              </div>
              <div>
                <span>업종 편중</span>
                <strong>미분류</strong>
                <small>키움 잔고 API 업종 미제공</small>
              </div>
            </div>
            <h4>자산 유형별 비중</h4>
            <ul className="asset-type-list">
              {analysis.assetTypes.map(([type, amount]) => (
                <li key={type}>
                  <span>{type}</span>
                  <strong>
                    {data.totalEvaluationAmount > 0
                      ? ((amount / data.totalEvaluationAmount) * 100).toFixed(1)
                      : '0.0'}
                    %
                  </strong>
                  <small>{number(amount)}원</small>
                </li>
              ))}
            </ul>
            <p className="calculation-note">
              현금은 추정자산−총 평가금액, 자산 유형은 종목명 기준입니다. 보유종목 합계와 키움 총
              평가금액 차이 {number(analysis.evaluationDifference)}원, 손익 합계 차이{' '}
              {number(analysis.profitDifference)}원입니다.
            </p>
          </section>

          {/* 보유 비중 파이 차트 */}
          {pieData.length > 0 && (
            <div style={{ width: '100%', height: 220, marginBottom: 16 }}>
              <ResponsiveContainer>
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="50%"
                    innerRadius={50}
                    outerRadius={80}
                    dataKey="value"
                    label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                  >
                    {pieData.map((_, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip formatter={(value) => `${value.toFixed(1)}%`} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          )}

          {/* 정렬·필터 컨트롤 */}
          <div className="box-range-control" style={{ marginBottom: 12 }}>
            <label>
              정렬
              <select value={sortKey} onChange={(e) => toggleSort(e.target.value)}>
                {SORT_OPTIONS.map((o) => (
                  <option key={o.key} value={o.key}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              onClick={() => setSortAsc(!sortAsc)}
              style={{ padding: '4px 8px' }}
            >
              {sortAsc ? '▲ 오름차순' : '▼ 내림차순'}
            </button>
            <label>
              필터
              <select value={filterKey} onChange={(e) => setFilterKey(e.target.value)}>
                {FILTER_OPTIONS.map((o) => (
                  <option key={o.key} value={o.key}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {/* 데스크톱 테이블 뷰 */}
          <div className="valuation-table-wrapper">
            <table className="valuation-table">
              <caption>키움 계좌 보유종목</caption>
              <thead>
                <tr>
                  <th scope="col">종목명</th>
                  <th scope="col">종목코드</th>
                  <th scope="col">보유수량</th>
                  <th scope="col">평가금액</th>
                  <th scope="col">비중</th>
                  <th scope="col">손익</th>
                  <th scope="col">수익률</th>
                  <th scope="col">수익기여</th>
                </tr>
              </thead>
              <tbody>
                {sortedPositions.map((position) => (
                  <tr
                    key={position.code}
                    className="clickable-row"
                    tabIndex={0}
                    role="button"
                    aria-label={`${position.name} ${position.code} 클릭하면 차트로 이동합니다`}
                    onClick={() => onSelectStock(position.code)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') onSelectStock(position.code);
                    }}
                  >
                    <td>
                      <span className="position-name-btn" aria-hidden="true">
                        {position.name}
                      </span>
                    </td>
                    <td className="position-code">{position.code}</td>
                    <td>{number(position.quantity)}주</td>
                    <td>{number(position.evaluationAmount)}원</td>
                    <td>{position.weight.toFixed(1)}%</td>
                    <td className={position.profitLoss >= 0 ? 'price-up' : 'price-down'}>
                      {number(position.profitLoss)}원
                    </td>
                    <td className={position.returnRate >= 0 ? 'price-up' : 'price-down'}>
                      {position.returnRate.toFixed(2)}%
                    </td>
                    <td className={position.profitContribution >= 0 ? 'price-up' : 'price-down'}>
                      {position.profitContribution.toFixed(1)}%
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 모바일 카드 뷰 */}
          <div className="position-card-list">
            {sortedPositions.map((position) => (
              <article
                key={position.code}
                className="position-card"
                onClick={() => onSelectStock(position.code)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') onSelectStock(position.code);
                }}
              >
                <div className="position-card-header">
                  <span className="position-card-name">{position.name}</span>
                  <span className="position-card-code">{position.code}</span>
                </div>
                <div className="position-card-grid">
                  <div>
                    <dt>평가금액</dt>
                    <dd>{number(position.evaluationAmount)}원</dd>
                  </div>
                  <div>
                    <dt>비중</dt>
                    <dd>{position.weight.toFixed(1)}%</dd>
                  </div>
                  <div>
                    <dt>손익</dt>
                    <dd className={position.profitLoss >= 0 ? 'price-up' : 'price-down'}>
                      {number(position.profitLoss)}원
                    </dd>
                  </div>
                  <div>
                    <dt>수익률</dt>
                    <dd className={position.returnRate >= 0 ? 'price-up' : 'price-down'}>
                      {position.returnRate.toFixed(2)}%
                    </dd>
                  </div>
                </div>
              </article>
            ))}
          </div>
        </>
      )}
    </section>
  );
}

export default AccountPortfolio;
