import { useState } from 'react';

const formatNumber = (value) => Number(value).toLocaleString('ko-KR');

function Portfolio({
  positions,
  valuations,
  profitTrend = [],
  loading,
  onSave,
  onRemove,
  onValuate,
  onImportTrades,
  onSelectStock
}) {
  const [code, setCode] = useState('');
  const [quantity, setQuantity] = useState('');
  const [averagePrice, setAveragePrice] = useState('');
  const [editingCode, setEditingCode] = useState(null);

  const clearForm = () => {
    setCode('');
    setQuantity('');
    setAveragePrice('');
    setEditingCode(null);
  };

  const submit = async (event) => {
    event.preventDefault();
    try {
      await onSave(code, Number(quantity), Number(averagePrice));
      clearForm();
    } catch {
      // 상위 컴포넌트에서 사용자에게 API 오류를 표시한다.
    }
  };

  const edit = (position) => {
    setEditingCode(position.code);
    setCode(position.code);
    setQuantity(String(position.quantity));
    setAveragePrice(String(position.averagePrice));
  };

  const summary = valuations.reduce(
    (total, item) => ({
      purchaseAmount:
        total.purchaseAmount +
        Number(item.purchaseAmount ?? Number(item.averagePrice) * Number(item.quantity)),
      evaluationAmount: total.evaluationAmount + Number(item.evaluationAmount),
      profitLoss: total.profitLoss + Number(item.profitLoss)
    }),
    { purchaseAmount: 0, evaluationAmount: 0, profitLoss: 0 }
  );
  const totalReturnRate =
    summary.purchaseAmount === 0 ? 0 : (summary.profitLoss / summary.purchaseAmount) * 100;

  return (
    <section className="portfolio" aria-labelledby="portfolio-title">
      <h2 id="portfolio-title">포트폴리오</h2>
      <form
        className="portfolio-form"
        onSubmit={submit}
        aria-label={editingCode ? `${editingCode} 포지션 수정` : '포지션 등록'}
      >
        <label htmlFor="portfolio-code">종목 코드</label>
        <input
          id="portfolio-code"
          value={code}
          onChange={(event) => setCode(event.target.value)}
          inputMode="numeric"
          pattern="[0-9]{6}"
          maxLength="6"
          required
          placeholder="005930"
          readOnly={Boolean(editingCode)}
        />
        <label htmlFor="portfolio-quantity">보유 수량</label>
        <input
          id="portfolio-quantity"
          type="number"
          value={quantity}
          onChange={(event) => setQuantity(event.target.value)}
          min="0.0001"
          step="any"
          required
        />
        <label htmlFor="portfolio-average-price">평균 매입가</label>
        <input
          id="portfolio-average-price"
          type="number"
          value={averagePrice}
          onChange={(event) => setAveragePrice(event.target.value)}
          min="1"
          step="any"
          required
        />
        <button type="submit" disabled={loading}>
          {editingCode ? '수정 저장' : '저장'}
        </button>
        {editingCode && (
          <button type="button" onClick={clearForm} disabled={loading}>
            수정 취소
          </button>
        )}
      </form>

      {positions.length === 0 ? (
        <p>등록된 보유 종목이 없습니다.</p>
      ) : (
        <ul className="portfolio-list">
          {positions.map((position) => (
            <li key={position.code}>
              <span
                className={onSelectStock ? 'clickable-position' : undefined}
                onClick={onSelectStock ? () => onSelectStock(position.code) : undefined}
                role={onSelectStock ? 'button' : undefined}
                tabIndex={onSelectStock ? 0 : undefined}
                onKeyDown={
                  onSelectStock
                    ? (e) => {
                        if (e.key === 'Enter' || e.key === ' ') onSelectStock(position.code);
                      }
                    : undefined
                }
              >
                <strong>{position.name || position.code}</strong>
                {position.name && <small className="position-code-tag">{position.code}</small>} ·{' '}
                {formatNumber(position.quantity)}주 · 평균 {formatNumber(position.averagePrice)}원
              </span>
              <button
                type="button"
                onClick={() => edit(position)}
                aria-label={`${position.code} 포트폴리오 수정`}
              >
                수정
              </button>
              <button
                type="button"
                onClick={() => onRemove(position.code)}
                aria-label={`${position.code} 포트폴리오 삭제`}
              >
                삭제
              </button>
            </li>
          ))}
        </ul>
      )}

      <button type="button" onClick={onValuate} disabled={loading || positions.length === 0}>
        {loading ? '평가 중...' : '현재가로 평가'}
      </button>

      <div className="portfolio-tools">
        <a href="/api/portfolio/transactions/export" download>
          거래 CSV 내보내기
        </a>
        <label>
          거래 CSV 가져오기
          <input
            type="file"
            accept=".csv,text/csv"
            onChange={async (event) => {
              const file = event.target.files?.[0];
              if (file && onImportTrades) await onImportTrades(await file.text());
              event.target.value = '';
            }}
          />
        </label>
      </div>

      {profitTrend.length > 0 && (
        <section aria-labelledby="profit-trend-title">
          <h3 id="profit-trend-title">기간별 손익 추이</h3>
          <ul>
            {profitTrend.map((point) => (
              <li key={point.date}>
                {point.date} · 실현 {formatNumber(point.realizedProfitLoss)}원 · 미실현{' '}
                {formatNumber(point.unrealizedProfitLoss)}원 · 합계{' '}
                {formatNumber(point.totalProfitLoss)}원
              </li>
            ))}
          </ul>
        </section>
      )}

      {valuations.length > 0 && (
        <>
          <section className="portfolio-summary" aria-labelledby="portfolio-summary-title">
            <h3 id="portfolio-summary-title">전체 자산 요약</h3>
            <dl>
              <div>
                <dt>총 매입금액</dt>
                <dd>{formatNumber(summary.purchaseAmount)}원</dd>
              </div>
              <div>
                <dt>총 평가금액</dt>
                <dd>{formatNumber(summary.evaluationAmount)}원</dd>
              </div>
              <div>
                <dt>총 손익</dt>
                <dd className={summary.profitLoss >= 0 ? 'price-up' : 'price-down'}>
                  {formatNumber(summary.profitLoss)}원
                </dd>
              </div>
              <div>
                <dt>총 수익률</dt>
                <dd>{formatNumber(totalReturnRate.toFixed(2))}%</dd>
              </div>
            </dl>
          </section>
          <div className="valuation-table-wrapper">
            <table className="valuation-table">
              <caption>현재가 기준 포트폴리오 평가</caption>
              <thead>
                <tr>
                  <th>종목명</th>
                  <th>종목코드</th>
                  <th>현재가</th>
                  <th>평가금액</th>
                  <th>비중</th>
                  <th>손익</th>
                  <th>수익률</th>
                </tr>
              </thead>
              <tbody>
                {valuations.map((item) => (
                  <tr
                    key={item.code}
                    onClick={onSelectStock ? () => onSelectStock(item.code) : undefined}
                    className={onSelectStock ? 'clickable-row' : undefined}
                  >
                    <td>{item.name || item.code}</td>
                    <td className="position-code">{item.code}</td>
                    <td>{formatNumber(item.currentPrice)}원</td>
                    <td>{formatNumber(item.evaluationAmount)}원</td>
                    <td>
                      {summary.evaluationAmount === 0
                        ? '0'
                        : (
                            (Number(item.evaluationAmount) / summary.evaluationAmount) *
                            100
                          ).toFixed(1)}
                      %
                    </td>
                    <td className={Number(item.profitLoss) >= 0 ? 'price-up' : 'price-down'}>
                      {formatNumber(item.profitLoss)}원
                    </td>
                    <td>{formatNumber(item.returnRate)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}

export default Portfolio;
