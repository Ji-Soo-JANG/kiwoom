import { useQuery } from '@tanstack/react-query';
import { getAccountPortfolio } from '../api/kiwoomApi';

const number = (value) => Number(value).toLocaleString('ko-KR');

function AccountPortfolio({ onSelectStock }) {
  const portfolio = useQuery({
    queryKey: ['kiwoom-account-portfolio'],
    queryFn: getAccountPortfolio,
    staleTime: 10 * 1000
  });

  if (portfolio.isPending) return <div className="loading-text">계좌 잔고를 불러오는 중...</div>;
  if (portfolio.error) {
    return (
      <div className="error" role="alert">
        키움 계좌 잔고를 불러오지 못했습니다: {portfolio.error.message}
      </div>
    );
  }

  const data = portfolio.data;
  return (
    <section className="account-portfolio" aria-labelledby="account-portfolio-title">
      <div className="market-discovery-heading">
        <div>
          <h2 id="account-portfolio-title">내 계좌 포트폴리오</h2>
          <p>키움 계좌 {data.accountNumber}의 실시간 평가잔고입니다.</p>
        </div>
        <button type="button" onClick={() => portfolio.refetch()} disabled={portfolio.isFetching}>
          {portfolio.isFetching ? '조회 중...' : '잔고 새로고침'}
        </button>
      </div>

      <dl className="account-summary">
        <div><dt>추정자산</dt><dd>{number(data.estimatedAssets)}원</dd></div>
        <div><dt>총 매입금액</dt><dd>{number(data.totalPurchaseAmount)}원</dd></div>
        <div><dt>총 평가금액</dt><dd>{number(data.totalEvaluationAmount)}원</dd></div>
        <div><dt>평가손익</dt><dd className={data.totalProfitLoss >= 0 ? 'price-up' : 'price-down'}>{number(data.totalProfitLoss)}원</dd></div>
        <div><dt>수익률</dt><dd className={data.totalReturnRate >= 0 ? 'price-up' : 'price-down'}>{data.totalReturnRate.toFixed(2)}%</dd></div>
      </dl>

      {data.positions.length === 0 ? (
        <p className="empty-state">현재 보유 중인 국내주식이 없습니다.</p>
      ) : (
        <div className="valuation-table-wrapper">
          <table className="valuation-table">
            <caption>키움 계좌 보유종목</caption>
            <thead><tr><th>종목</th><th>보유수량</th><th>평균단가</th><th>현재가</th><th>평가금액</th><th>손익</th><th>수익률</th></tr></thead>
            <tbody>
              {data.positions.map((position) => (
                <tr key={position.code} onClick={() => onSelectStock(position.code)}>
                  <td><button type="button">{position.name}<small>{position.code}</small></button></td>
                  <td>{number(position.quantity)}주</td>
                  <td>{number(position.averagePrice)}원</td>
                  <td>{number(position.currentPrice)}원</td>
                  <td>{number(position.evaluationAmount)}원</td>
                  <td className={position.profitLoss >= 0 ? 'price-up' : 'price-down'}>{number(position.profitLoss)}원</td>
                  <td>{position.returnRate.toFixed(2)}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export default AccountPortfolio;
