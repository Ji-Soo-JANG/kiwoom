import {useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {
    createAlertRule, deleteAlertRule, evaluateAlerts, getAlertEvents, getAlertRules,
    markAlertRead, updateAlertRule
} from '../api/kiwoomApi';

const formatPrice = (value) => Number(value).toLocaleString('ko-KR');
const conditionLabels = {
    PRICE_ABOVE: '목표가 이상', PRICE_BELOW: '목표가 이하',
    RSI_ABOVE: 'RSI 이상', RSI_BELOW: 'RSI 이하',
    MACD_CROSS_UP: 'MACD 상향 교차', MACD_CROSS_DOWN: 'MACD 하향 교차'
};
const requiresThreshold = (type) => !type.startsWith('MACD_');

function AlertCenter({onError}) {
    const [code, setCode] = useState('');
    const [conditionType, setConditionType] = useState('PRICE_ABOVE');
    const [threshold, setThreshold] = useState('');
    const queryClient = useQueryClient();
    const rulesQuery = useQuery({queryKey: ['alerts', 'rules'], queryFn: getAlertRules});
    const eventsQuery = useQuery({queryKey: ['alerts', 'events'], queryFn: () => getAlertEvents()});
    const rules = rulesQuery.data ?? [];
    const events = eventsQuery.data ?? [];
    const refresh = () => Promise.all([
        queryClient.invalidateQueries({queryKey: ['alerts', 'rules']}),
        queryClient.invalidateQueries({queryKey: ['alerts', 'events']})
    ]);
    const mutationOptions = {onError};
    const createMutation = useMutation({
        mutationFn: ({nextCode, nextType, nextThreshold}) =>
            createAlertRule(nextCode, nextType, nextThreshold),
        onSuccess: refresh, ...mutationOptions
    });
    const updateMutation = useMutation({
        mutationFn: (rule) => updateAlertRule(rule.id, rule.threshold, !rule.enabled),
        onSuccess: refresh, ...mutationOptions
    });
    const deleteMutation = useMutation({
        mutationFn: deleteAlertRule, onSuccess: refresh, ...mutationOptions
    });
    const evaluateMutation = useMutation({
        mutationFn: evaluateAlerts, onSuccess: refresh, ...mutationOptions
    });
    const readMutation = useMutation({
        mutationFn: markAlertRead,
        onSuccess: () => queryClient.invalidateQueries({queryKey: ['alerts', 'events']}),
        ...mutationOptions
    });
    const loading = rulesQuery.isLoading || eventsQuery.isLoading || createMutation.isPending
        || updateMutation.isPending || deleteMutation.isPending || evaluateMutation.isPending
        || readMutation.isPending;

    const submit = async (event) => {
        event.preventDefault();
        try {
            await createMutation.mutateAsync({
                nextCode: code,
                nextType: conditionType,
                nextThreshold: requiresThreshold(conditionType) ? Number(threshold) : null
            });
            setCode('');
            setThreshold('');
        } catch { /* 오류는 상위 알림 영역에 표시한다. */ }
    };

    const toggleRule = (rule) => updateMutation.mutate(rule);
    const removeRule = (id) => deleteMutation.mutate(id);
    const evaluate = () => evaluateMutation.mutate();
    const markRead = (id) => readMutation.mutate(id);

    const unreadCount = events.filter((event) => !event.readAt).length;

    return <section className="alert-center" aria-labelledby="alert-title">
        <div className="alert-heading">
            <h2 id="alert-title">목표가 알림</h2>
            {unreadCount > 0 && <span className="alert-badge" aria-label={`읽지 않은 알림 ${unreadCount}개`}>
                {unreadCount}
            </span>}
        </div>
        {(rulesQuery.error || eventsQuery.error) && <p className="error" role="alert">
            알림 데이터를 불러오지 못했습니다: {(rulesQuery.error || eventsQuery.error).message}
        </p>}

        <form className="alert-form" onSubmit={submit}>
            <label htmlFor="alert-code">종목 코드</label>
            <input id="alert-code" value={code} onChange={(event) => setCode(event.target.value)}
                   inputMode="numeric" pattern="[0-9]{6}" maxLength="6" required placeholder="005930"/>
            <label htmlFor="alert-condition">조건</label>
            <select id="alert-condition" value={conditionType}
                    onChange={(event) => setConditionType(event.target.value)}>
                <option value="PRICE_ABOVE">목표가 이상</option>
                <option value="PRICE_BELOW">목표가 이하</option>
                <option value="RSI_ABOVE">RSI 이상</option>
                <option value="RSI_BELOW">RSI 이하</option>
                <option value="MACD_CROSS_UP">MACD 상향 교차</option>
                <option value="MACD_CROSS_DOWN">MACD 하향 교차</option>
            </select>
            <label htmlFor="alert-threshold">목표가</label>
            <input id="alert-threshold" type="number" value={threshold}
                   onChange={(event) => setThreshold(event.target.value)}
                   min={conditionType.startsWith('RSI_') ? '0' : '1'}
                   max={conditionType.startsWith('RSI_') ? '100' : undefined}
                   step="any" required={requiresThreshold(conditionType)}
                   disabled={!requiresThreshold(conditionType)}/>
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
                <span>{rule.code} · {conditionLabels[rule.conditionType]}
                    {rule.threshold == null ? '' : ` ${formatPrice(rule.threshold)}`}</span>
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
                    <span>{item.code} · {conditionLabels[item.conditionType]} · 관측값 {formatPrice(item.observedValue)}
                        {item.threshold == null ? '' : ` / 기준 ${formatPrice(item.threshold)}`}</span>
                    {!item.readAt && <button type="button" onClick={() => markRead(item.id)} disabled={loading}>
                        읽음
                    </button>}
                </div>
            </li>)}
        </ul>}
    </section>;
}

export default AlertCenter;
