import {useEffect, useState} from 'react';
import {
    createAlertRule, deleteAlertRule, evaluateAlerts, getAlertEvents, getAlertRules,
    markAlertRead, updateAlertRule
} from '../api/kiwoomApi';

const formatPrice = (value) => Number(value).toLocaleString('ko-KR');
const conditionLabel = (type) => type === 'PRICE_BELOW' ? '이하' : '이상';

function AlertCenter({onError}) {
    const [rules, setRules] = useState([]);
    const [events, setEvents] = useState([]);
    const [code, setCode] = useState('');
    const [conditionType, setConditionType] = useState('PRICE_ABOVE');
    const [threshold, setThreshold] = useState('');
    const [loading, setLoading] = useState(false);

    const load = async () => {
        const [nextRules, nextEvents] = await Promise.all([getAlertRules(), getAlertEvents()]);
        setRules(nextRules);
        setEvents(nextEvents);
    };

    useEffect(() => {
        Promise.all([getAlertRules(), getAlertEvents()])
            .then(([nextRules, nextEvents]) => {
                setRules(nextRules);
                setEvents(nextEvents);
            })
            .catch(onError);
    }, [onError]);

    const run = async (operation) => {
        setLoading(true);
        try { await operation(); }
        catch (error) { onError(error); throw error; }
        finally { setLoading(false); }
    };

    const submit = async (event) => {
        event.preventDefault();
        try {
            await run(async () => {
                await createAlertRule(code, conditionType, Number(threshold));
                await load();
                setCode('');
                setThreshold('');
            });
        } catch { /* 오류는 상위 알림 영역에 표시한다. */ }
    };

    const toggleRule = (rule) => run(async () => {
        await updateAlertRule(rule.id, rule.threshold, !rule.enabled);
        await load();
    }).catch(() => {});

    const removeRule = (id) => run(async () => {
        await deleteAlertRule(id);
        await load();
    }).catch(() => {});

    const evaluate = () => run(async () => {
        await evaluateAlerts();
        await load();
    }).catch(() => {});

    const markRead = (id) => run(async () => {
        await markAlertRead(id);
        setEvents((items) => items.map((item) => item.id === id
            ? {...item, readAt: new Date().toISOString()} : item));
    }).catch(() => {});

    const unreadCount = events.filter((event) => !event.readAt).length;

    return <section className="alert-center" aria-labelledby="alert-title">
        <div className="alert-heading">
            <h2 id="alert-title">목표가 알림</h2>
            {unreadCount > 0 && <span className="alert-badge" aria-label={`읽지 않은 알림 ${unreadCount}개`}>
                {unreadCount}
            </span>}
        </div>

        <form className="alert-form" onSubmit={submit}>
            <label htmlFor="alert-code">종목 코드</label>
            <input id="alert-code" value={code} onChange={(event) => setCode(event.target.value)}
                   inputMode="numeric" pattern="[0-9]{6}" maxLength="6" required placeholder="005930"/>
            <label htmlFor="alert-condition">조건</label>
            <select id="alert-condition" value={conditionType}
                    onChange={(event) => setConditionType(event.target.value)}>
                <option value="PRICE_ABOVE">목표가 이상</option>
                <option value="PRICE_BELOW">목표가 이하</option>
            </select>
            <label htmlFor="alert-threshold">목표가</label>
            <input id="alert-threshold" type="number" value={threshold}
                   onChange={(event) => setThreshold(event.target.value)} min="1" step="any" required/>
            <button type="submit" disabled={loading}>알림 규칙 추가</button>
        </form>

        <div className="alert-toolbar">
            <button type="button" onClick={evaluate} disabled={loading || rules.length === 0}>
                {loading ? '처리 중...' : '현재가로 알림 확인'}
            </button>
        </div>

        <h3 className="alert-section-title">설정된 규칙</h3>
        {rules.length === 0 ? <p>설정된 목표가 알림이 없습니다.</p> : <ul className="alert-list">
            {rules.map((rule) => <li className="alert-rule-row" key={rule.id}>
                <span>{rule.code} · {formatPrice(rule.threshold)}원 {conditionLabel(rule.conditionType)}</span>
                <button type="button" onClick={() => toggleRule(rule)} disabled={loading}>
                    {rule.enabled ? '끄기' : '켜기'}
                </button>
                <button type="button" onClick={() => removeRule(rule.id)} disabled={loading}
                        aria-label={`${rule.code} 알림 규칙 삭제`}>삭제</button>
            </li>)}
        </ul>}

        <h3 className="alert-section-title">알림 내역</h3>
        {events.length === 0 ? <p>발생한 알림이 없습니다.</p> : <ul className="alert-list" aria-live="polite">
            {events.map((item) => <li className={item.readAt ? '' : 'alert-event-unread'} key={item.id}>
                <div className="alert-event-row">
                    <span>{item.code} · 현재 {formatPrice(item.observedValue)}원
                        {' / '}목표 {formatPrice(item.threshold)}원 {conditionLabel(item.conditionType)}</span>
                    {!item.readAt && <button type="button" onClick={() => markRead(item.id)} disabled={loading}>
                        읽음
                    </button>}
                </div>
            </li>)}
        </ul>}
    </section>;
}

export default AlertCenter;
