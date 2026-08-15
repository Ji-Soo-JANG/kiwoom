import {useState} from 'react';

const formatNumber = (value) => Number(value).toLocaleString('ko-KR');

function Portfolio({positions, valuations, loading, onSave, onRemove, onValuate}) {
    const [code, setCode] = useState('');
    const [quantity, setQuantity] = useState('');
    const [averagePrice, setAveragePrice] = useState('');

    const submit = async (event) => {
        event.preventDefault();
        try {
            await onSave(code, Number(quantity), Number(averagePrice));
            setCode('');
            setQuantity('');
            setAveragePrice('');
        } catch {
            // 상위 컴포넌트에서 사용자에게 API 오류를 표시한다.
        }
    };

    return <section className="portfolio" aria-labelledby="portfolio-title">
        <h2 id="portfolio-title">포트폴리오</h2>
        <form className="portfolio-form" onSubmit={submit}>
            <label htmlFor="portfolio-code">종목 코드</label>
            <input id="portfolio-code" value={code} onChange={(event) => setCode(event.target.value)}
                   inputMode="numeric" pattern="[0-9]{6}" maxLength="6" required placeholder="005930"/>
            <label htmlFor="portfolio-quantity">보유 수량</label>
            <input id="portfolio-quantity" type="number" value={quantity}
                   onChange={(event) => setQuantity(event.target.value)} min="0.0001" step="any" required/>
            <label htmlFor="portfolio-average-price">평균 매입가</label>
            <input id="portfolio-average-price" type="number" value={averagePrice}
                   onChange={(event) => setAveragePrice(event.target.value)} min="1" step="any" required/>
            <button type="submit" disabled={loading}>저장</button>
        </form>

        {positions.length === 0 ? <p>등록된 보유 종목이 없습니다.</p> : <ul className="portfolio-list">
            {positions.map((position) => <li key={position.code}>
                <span><strong>{position.code}</strong> · {formatNumber(position.quantity)}주 · 평균 {formatNumber(position.averagePrice)}원</span>
                <button type="button" onClick={() => onRemove(position.code)}
                        aria-label={`${position.code} 포트폴리오 삭제`}>삭제</button>
            </li>)}
        </ul>}

        <button type="button" onClick={onValuate} disabled={loading || positions.length === 0}>
            {loading ? '평가 중...' : '현재가로 평가'}
        </button>

        {valuations.length > 0 && <div className="valuation-table-wrapper">
            <table className="valuation-table">
                <caption>현재가 기준 포트폴리오 평가</caption>
                <thead><tr><th>종목</th><th>현재가</th><th>평가금액</th><th>손익</th><th>수익률</th></tr></thead>
                <tbody>{valuations.map((item) => <tr key={item.code}>
                    <td>{item.code}</td><td>{formatNumber(item.currentPrice)}원</td>
                    <td>{formatNumber(item.evaluationAmount)}원</td>
                    <td className={Number(item.profitLoss) >= 0 ? 'price-up' : 'price-down'}>{formatNumber(item.profitLoss)}원</td>
                    <td>{formatNumber(item.returnRate)}%</td>
                </tr>)}</tbody>
            </table>
        </div>}
    </section>;
}

export default Portfolio;
