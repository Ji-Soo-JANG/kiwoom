import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  commitBoxEvaluation,
  getBoxEvaluationBatches,
  getBoxEvaluationBatchItems,
  getBoxEvaluationCandles,
  getBoxEvaluationItem,
  getBoxEvaluation,
  getNextBoxEvaluationItem,
  getBoxEvaluationProgress,
  saveBoxEvaluationDraft,
  getBoxFormationEvaluation,
  saveBoxFormationEvaluation
} from '../api/kiwoomApi';

const labels = [
  ['VALID_BOX', '유효한 박스권'],
  ['PARTIAL_BOX', '부분적으로 유효'],
  ['NOT_BOX', '박스권 아님'],
  ['INSUFFICIENT_DATA', '자료 부족'],
  ['DATA_QUALITY_ISSUE', '데이터 품질 문제']
];
const reasons = [
  'STABLE_RANGE',
  'LOW_SLOPE',
  'VOLATILITY_CONTRACTION',
  'VOLUME_SPIKES',
  'BOUNDARY_AMBIGUOUS'
];
const emptyForm = () => ({
  boundaryDecision: '',
  selectedCandidateKey: '',
  startDate: '',
  endDate: '',
  labelCode: '',
  confidence: '',
  reasonCodes: [],
  comment: ''
});

export function MiniPriceChart({ candles, candidates, activeKeys, period, onPeriodChange, zones, onZonesChange }) {
  const drag = useRef(null);
  const chartRef = useRef(null);
  const [zoom, setZoom] = useState(1);
  const [offset, setOffset] = useState(0);
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) return undefined;
    const handleWheel = (event) => {
      event.preventDefault();
      setZoom((value) => Math.max(1, Math.min(8, value * (event.deltaY < 0 ? 1.15 : 0.87))));
    };
    chart.addEventListener('wheel', handleWheel, { passive: false });
    return () => chart.removeEventListener('wheel', handleWheel);
  }, [candles.length]);
  if (!candles.length) return <p className="empty-state">표시할 일봉이 없습니다.</p>;
  const closes = candles.map((c) => Number(c.closePrice));
  const min = Math.min(...closes);
  const max = Math.max(...closes);
  const x = (i) => 20 + ((i - offset) / Math.max(candles.length / zoom - 1, 1)) * 760;
  const y = (v) => 220 - ((v - min) / Math.max(max - min, 1)) * 190;
  const points = closes.map((v, i) => `${x(i)},${y(v)}`).join(' ');
  const indexForDate = (date) => candles.findIndex((c) => c.tradeDate === date);
  const snap = (event) => {
    const rect = (chartRef.current ?? event.currentTarget).getBoundingClientRect();
    const index = Math.round(offset + ((event.clientX - rect.left) / rect.width) * Math.max(candles.length / zoom - 1, 1));
    return candles[Math.max(0, Math.min(candles.length - 1, index))].tradeDate;
  };
  const priceAt = (event) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const screenY = ((event.clientY - rect.top) / rect.height) * 250;
    return (max - ((screenY - 30) / 190) * (max - min)).toFixed(2);
  };
  const startIndex = period?.startDate ? indexForDate(period.startDate) : -1;
  const endIndex = period?.endDate ? indexForDate(period.endDate) : -1;
  const rangeStart = Math.max(0, Math.min(startIndex, endIndex));
  const rangeEnd = Math.max(rangeStart, Math.max(startIndex, endIndex));
  return (
    <div className="chart-editor">
      <button type="button" onClick={() => { setZoom(1); setOffset(0); }}>Reset view</button>
    <svg
      ref={chartRef}
      className="box-evaluation-chart"
      viewBox="0 0 800 250"
      role="img"
      aria-label="기준일 이하 실제 종가와 박스권 후보"
      onClick={(event) => {
        if (!onPeriodChange) return;
        const date = snap(event);
        if (!period?.startDate) onPeriodChange(date, '');
        else if (!period?.endDate) onPeriodChange(period.startDate, date);
      }}
      onPointerDown={(event) => { if (event.target === event.currentTarget) drag.current = { x: event.clientX, offset }; }}
      onPointerMove={(event) => {
        if (drag.current?.handle) {
          const date = snap(event);
          onPeriodChange?.(drag.current.handle === 'start' ? date : period?.startDate, drag.current.handle === 'end' ? date : period?.endDate);
        } else if (drag.current?.zone) {
          const value = priceAt(event);
          if (drag.current.zone === 'lower' || drag.current.zone === 'upper') {
            const delta = Number(value) - drag.current.startPrice;
            const initial = drag.current.initialZones;
            const keys = drag.current.zone === 'lower' ? ['lowerMin', 'lowerMax'] : ['upperMin', 'upperMax'];
            onZonesChange?.({
              ...zones,
              [keys[0]]: (Number(initial[keys[0]]) + delta).toFixed(2),
              [keys[1]]: (Number(initial[keys[1]]) + delta).toFixed(2)
            });
          } else {
            onZonesChange?.({ ...zones, [drag.current.zone]: value });
          }
        } else if (drag.current) setOffset(Math.max(0, Math.min(candles.length - 2, drag.current.offset - (event.clientX - drag.current.x) / 10)));
      }}
      onPointerUp={() => { drag.current = null; }}
    >
      {startIndex >= 0 && endIndex >= 0 && <rect
        data-testid="final-period-range"
        x={x(rangeStart)}
        y="10"
        width={Math.max(x(rangeEnd) - x(rangeStart), 3)}
        height="215"
        className="final-period-range"
        pointerEvents="none"
      />}
      {period?.startDate && <rect data-testid="period-start-handle" x={x(indexForDate(period.startDate))-4} y="5" width="8" height="220" className="period-handle" onPointerDown={(event) => { event.stopPropagation(); drag.current = { handle: 'start' }; }} />}
      {period?.endDate && <rect data-testid="period-end-handle" x={x(indexForDate(period.endDate))-4} y="5" width="8" height="220" className="period-handle" onPointerDown={(event) => { event.stopPropagation(); drag.current = { handle: 'end' }; }} />}
      {zones?.lowerMin && zones?.lowerMax && <rect data-testid="lower-support-zone" x="20" y={y(Number(zones.lowerMax))} width="760" height={Math.max(2, y(Number(zones.lowerMin))-y(Number(zones.lowerMax)))} className="support-zone" onPointerDown={(event) => { event.stopPropagation(); drag.current = { zone: 'lower', startPrice: priceAt(event), initialZones: { ...zones } }; }} />}
      {zones?.upperMin && zones?.upperMax && <rect data-testid="upper-resistance-zone" x="20" y={y(Number(zones.upperMax))} width="760" height={Math.max(2, y(Number(zones.upperMin))-y(Number(zones.upperMax)))} className="resistance-zone" onPointerDown={(event) => { event.stopPropagation(); drag.current = { zone: 'upper', startPrice: priceAt(event), initialZones: { ...zones } }; }} />}
      {zones?.lowerMin && <rect data-testid="lower-support-min-handle" x="20" y={y(Number(zones.lowerMin))-3} width="760" height="6" className="zone-handle" onPointerDown={(event) => { event.stopPropagation(); drag.current = { zone: 'lowerMin' }; }} />}
      {zones?.lowerMax && <rect data-testid="lower-support-max-handle" x="20" y={y(Number(zones.lowerMax))-3} width="760" height="6" className="zone-handle" onPointerDown={(event) => { event.stopPropagation(); drag.current = { zone: 'lowerMax' }; }} />}
      {zones?.upperMin && <rect data-testid="upper-resistance-min-handle" x="20" y={y(Number(zones.upperMin))-3} width="760" height="6" className="zone-handle" onPointerDown={(event) => { event.stopPropagation(); drag.current = { zone: 'upperMin' }; }} />}
      {zones?.upperMax && <rect data-testid="upper-resistance-max-handle" x="20" y={y(Number(zones.upperMax))-3} width="760" height="6" className="zone-handle" onPointerDown={(event) => { event.stopPropagation(); drag.current = { zone: 'upperMax' }; }} />}
      {candidates
        .filter((c) => activeKeys.includes(c.candidateKey))
        .map((c, index) => {
          const start = candles.findIndex((v) => v.tradeDate >= c.startDate);
          const end = candles.findIndex((v) => v.tradeDate >= c.endDate);
          return (
            <rect
              key={c.candidateKey}
              x={x(Math.max(start, 0))}
              y="10"
              width={Math.max(x(Math.max(end, start)) - x(Math.max(start, 0)), 3)}
              height="215"
              className={`candidate-zone candidate-${index + 1}`}
            />
          );
        })}
      <polyline points={points} className="price-line" />
    </svg>
    </div>
  );
}

export default function BoxEvaluationWorkbench({ reviewerId, formationMode = false }) {
  const client = useQueryClient();
  const [batchId, setBatchId] = useState('');
  const [itemId, setItemId] = useState(null);
  const [activeKeys, setActiveKeys] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [notice, setNotice] = useState('');
  const [navigationError, setNavigationError] = useState(null);
  const [history, setHistory] = useState([]);
  const [formationLabel, setFormationLabel] = useState('');
  const [formationNote, setFormationNote] = useState('');
  const [zones, setZones] = useState({ lowerMin: '', lowerMax: '', upperMin: '', upperMax: '' });
  const [periodDecision, setPeriodDecision] = useState('ACCEPTED');
  const [zoneDecision, setZoneDecision] = useState('ACCEPTED');
  const batches = useQuery({
    queryKey: ['box-evaluation-batches'],
    queryFn: getBoxEvaluationBatches,
    retry: false
  });
  const selectedBatchId = batchId || (batches.data?.[0]?.id ? String(batches.data[0].id) : '');
  const selectedBatch = (batches.data ?? []).find((b) => String(b.id) === selectedBatchId);
  const batchItems = useQuery({
    queryKey: ['box-evaluation-batch-items', selectedBatchId],
    queryFn: () => getBoxEvaluationBatchItems(selectedBatchId),
    enabled: Boolean(selectedBatchId),
    retry: false
  });
  const persistedCompletedIds = useMemo(
    () => (batchItems.data ?? []).filter((item) => item.status === 'COMMITTED').map((item) => item.id),
    [batchItems.data]
  );
  const previousIds = history.length ? history : persistedCompletedIds.filter((id) => id !== itemId);
  const progress = useQuery({ queryKey: ['box-evaluation-progress', selectedBatchId], queryFn: () => getBoxEvaluationProgress(selectedBatchId), enabled: Boolean(selectedBatchId), retry: false });
  const progressTotal = progress.data?.total ?? selectedBatch?.itemCount ?? '?';
  const progressCompleted = progress.data?.completed ?? history.length;
  useEffect(() => {
    if (!selectedBatchId) return;
    const navigationType = window.performance?.getEntriesByType?.('navigation')?.[0]?.type;
    const saved = window.localStorage.getItem(`box-evaluation:${reviewerId}:${selectedBatchId}`);
    if (saved && navigationType === 'reload' && !itemId) setItemId(Number(saved));
  }, [selectedBatchId, reviewerId, itemId]);
  useEffect(() => {
    if (selectedBatchId && itemId) window.localStorage.setItem(`box-evaluation:${reviewerId}:${selectedBatchId}`, String(itemId));
  }, [selectedBatchId, reviewerId, itemId]);
  const detail = useQuery({
    queryKey: ['box-evaluation-item', itemId, reviewerId],
    queryFn: () => getBoxEvaluationItem(itemId, reviewerId),
    enabled: Boolean(itemId),
    retry: false
  });
  const committedEvaluation = useQuery({
    queryKey: ['box-evaluation-committed', itemId, reviewerId],
    queryFn: () => getBoxEvaluation(itemId, reviewerId),
    enabled: Boolean(itemId),
    retry: false
  });
  const candles = useQuery({
    queryKey: ['box-evaluation-candles', itemId],
    queryFn: () => getBoxEvaluationCandles(itemId),
    enabled: Boolean(itemId),
    retry: false
  });
  const formation = useQuery({
    queryKey: ['box-formation', itemId, reviewerId],
    queryFn: () => getBoxFormationEvaluation(itemId, reviewerId),
    enabled: Boolean(itemId) && formationMode,
    retry: false
  });
  const candidates = useMemo(() => detail.data?.candidates ?? [], [detail.data]);
  const narrowCandidates = useMemo(() => candidates.filter((c) => c.candidateKey === 'NARROW'), [candidates]);
  useEffect(() => {
    if (!detail.data) return;
    const draft = detail.data.draft;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setActiveKeys(candidates.map((c) => c.candidateKey));
    // Formation evaluations have their own persisted form state. Do not let
    // the generic C0 detail response reset it while the formation query loads.
    if (formationMode && !draft) return;
    setForm(
      draft
          ? {
            boundaryDecision: draft.boundaryDecision ?? '',
            selectedCandidateKey: draft.selectedCandidateKey ?? '',
            startDate: draft.editedStartDate ?? '',
            endDate: draft.editedEndDate ?? '',
            labelCode: draft.labelCode ?? '',
            confidence: draft.confidence ?? '',
            reasonCodes: (draft.reasonCodes ?? '').split(',').filter(Boolean),
            comment: draft.comment ?? ''
          }
        : emptyForm()
    );
  }, [detail.data, candidates, formationMode]);
  useEffect(() => {
    if (!committedEvaluation.data || detail.data?.draft) return;
    const evaluation = committedEvaluation.data;
    setForm((value) => ({
      ...value,
      boundaryDecision: evaluation.boundaryDecision ?? value.boundaryDecision,
      selectedCandidateKey: evaluation.selectedCandidateKey ?? value.selectedCandidateKey,
      startDate: evaluation.finalStartDate ?? value.startDate,
      endDate: evaluation.finalEndDate ?? value.endDate,
      labelCode: evaluation.labelCode ?? value.labelCode,
      confidence: evaluation.confidence ?? value.confidence,
      reasonCodes: (evaluation.reasonCodes ?? '').split(',').filter(Boolean),
      comment: evaluation.comment ?? value.comment
    }));
  }, [committedEvaluation.data, detail.data?.draft]);
  useEffect(() => {
    if (!formation.data) return;
    setFormationLabel(formation.data.formationLabel ?? '');
    setFormationNote(formation.data.note ?? '');
    setForm((value) => ({
      ...value,
      boundaryDecision: formation.data.boundaryDecision ?? value.boundaryDecision,
      labelCode: formation.data.labelCode ?? value.labelCode,
      reasonCodes: formation.data.reasonCodes
        ? formation.data.reasonCodes.split(',').filter(Boolean)
        : value.reasonCodes,
      comment: formation.data.comment ?? value.comment,
      startDate: formation.data.finalStartDate ?? value.startDate,
      endDate: formation.data.finalEndDate ?? value.endDate,
      confidence: formation.data.confidence ?? value.confidence
    }));
    setZones({
      lowerMin: formation.data.finalLowerSupportMin ?? formation.data.proposedLowerSupportMin ?? '', lowerMax: formation.data.finalLowerSupportMax ?? formation.data.proposedLowerSupportMax ?? '',
      upperMin: formation.data.finalUpperResistanceMin ?? formation.data.proposedUpperResistanceMin ?? '', upperMax: formation.data.finalUpperResistanceMax ?? formation.data.proposedUpperResistanceMax ?? ''
    });
    setPeriodDecision(formation.data.periodDecision ?? 'ACCEPTED');
    setZoneDecision(formation.data.zoneDecision ?? 'ACCEPTED');
  }, [formation.data]);
  useEffect(() => {
    if (!formationMode) return undefined;
    const onKey = (event) => {
      if (event.target.matches('input,textarea,select')) return;
      if (event.key === '1') setFormationLabel('BOX');
      if (event.key === '2') { setFormationLabel('NOT_BOX'); saveFormation.mutate('NOT_BOX'); }
      if (event.key === '3') { setFormationLabel('UNCERTAIN'); saveFormation.mutate('UNCERTAIN'); }
      if (event.key === 'Escape') { setFormationLabel(formation.data?.formationLabel ?? ''); setForm((v) => ({ ...v, startDate: formation.data?.finalStartDate ?? '', endDate: formation.data?.finalEndDate ?? '' })); }
      if (event.key === 'ArrowLeft') openPrevious();
      if (event.key === 'ArrowRight') next();
      if (event.key === 'Enter' && formationLabel) saveFormation.mutate(formationLabel);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [formationMode, itemId, formation.data, formationLabel, history]);
  const openPrevious = () => {
    const previous = previousIds[previousIds.length - 1];
    if (!previous) return;
    setHistory((v) => (v.length ? v.slice(0, -1) : previousIds.slice(0, -1)));
    setItemId(previous);
    setNotice('기존 평가 항목을 다시 열었습니다.');
  };
  const next = async ({ afterCommit = false } = {}) => {
    setNavigationError(null);
    try {
      const item = await getNextBoxEvaluationItem(selectedBatchId);
      if (!item) {
        if (itemId && history[history.length - 1] !== itemId) setHistory((v) => [...v, itemId]);
        setItemId(null);
        window.localStorage.removeItem(`box-evaluation:${reviewerId}:${selectedBatchId}`);
        setActiveKeys([]);
        setForm(emptyForm());
        setNotice('이 평가 과제의 모든 블라인드 항목을 완료했습니다.');
        return;
      }
      setForm(emptyForm());
      setActiveKeys([]);
      setFormationLabel('');
      setFormationNote('');
      setZones({ lowerMin: '', lowerMax: '', upperMin: '', upperMax: '' });
      setItemId(item.id);
      if (itemId) setHistory((v) => [...v, itemId]);
      setNotice(
        afterCommit
          ? '평가를 확정하고 다음 블라인드 항목을 불러왔습니다.'
          : '미래 데이터 비공개 상태로 항목을 불러왔습니다.'
      );
    } catch (error) {
      setNavigationError(error);
      if (afterCommit) {
        setNotice('평가는 확정됐지만 다음 항목을 불러오지 못했습니다. 다시 시도해 주세요.');
      }
    }
  };
  const save = useMutation({
    mutationFn: () =>
      saveBoxEvaluationDraft(itemId, {
        ...form,
        reasonCodes: form.reasonCodes.join(','),
        expectedRevision: detail.data?.draft?.draftRevision ?? 0
      }),
    onSuccess: () => {
      setNotice('임시 저장했습니다.');
      client.invalidateQueries({ queryKey: ['box-evaluation-item', itemId] });
    }
  });
  const commit = useMutation({
    mutationFn: () =>
      commitBoxEvaluation(itemId, {
        ...form,
        reasonCodes: form.reasonCodes.join(','),
        commitKey: crypto.randomUUID(),
        reviewerId
      }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['box-evaluation-batches'] });
      await next({ afterCommit: true });
    }
  });
  const choose = (candidate) =>
    setForm((v) => ({
      ...v,
      boundaryDecision: 'CANDIDATE',
      selectedCandidateKey: candidate.candidateKey,
      startDate: candidate.startDate,
      endDate: candidate.endDate
    }));
  const saveFormation = useMutation({
    mutationFn: (label = formationLabel) => saveBoxFormationEvaluation(itemId, {
      reviewerId, formationLabel: label, finalStartDate: label === 'BOX' ? (form.startDate || formation.data?.finalStartDate) : null,
      finalEndDate: label === 'BOX' ? (form.endDate || formation.data?.finalEndDate) : null,
      periodDecision, proposedLowerSupportMin: formation.data?.proposedLowerSupportMin ?? null,
      proposedLowerSupportMax: formation.data?.proposedLowerSupportMax ?? null,
      proposedUpperResistanceMin: formation.data?.proposedUpperResistanceMin ?? null,
      proposedUpperResistanceMax: formation.data?.proposedUpperResistanceMax ?? null,
      finalLowerSupportMin: zones.lowerMin || null, finalLowerSupportMax: zones.lowerMax || null,
      finalUpperResistanceMin: zones.upperMin || null, finalUpperResistanceMax: zones.upperMax || null,
      zoneDecision,
      note: formationNote, confidence: form.confidence ? Number(form.confidence) : null,
      boundaryDecision: form.boundaryDecision || null,
      labelCode: form.labelCode || null,
      reasonCodes: form.reasonCodes.join(','),
      comment: form.comment || null,
      expectedRevision: formation.data?.revision ?? 0
    }),
    onSuccess: (_data, label) => {
      return Promise.all([
        client.invalidateQueries({ queryKey: ['box-formation', itemId, reviewerId] }),
        client.invalidateQueries({ queryKey: ['box-evaluation-progress', selectedBatchId], refetchType: 'active' })
      ]).then(() => {
        if (label === 'NOT_BOX' || label === 'UNCERTAIN') return next({ afterCommit: true });
        return undefined;
      });
    }
  });
  const error =
    batches.error || detail.error || candles.error || formation.error || save.error || commit.error || saveFormation.error || navigationError;
  const explanationRequired = ['PARTIAL_BOX', 'INSUFFICIENT_DATA'].includes(form.labelCode);
  const positiveLabel = ['VALID_BOX', 'PARTIAL_BOX'].includes(form.labelCode);
  const negativeLabel = ['NOT_BOX', 'INSUFFICIENT_DATA', 'DATA_QUALITY_ISSUE'].includes(
    form.labelCode
  );
  const boundaryReady =
    (positiveLabel &&
      ['CANDIDATE', 'MANUAL'].includes(form.boundaryDecision) &&
      form.startDate &&
      form.endDate &&
      (form.boundaryDecision !== 'CANDIDATE' || form.selectedCandidateKey)) ||
    (negativeLabel && form.boundaryDecision === 'NO_SUITABLE_CANDIDATE');
  const canCommit =
    form.labelCode &&
    form.confidence &&
    form.reasonCodes.length > 0 &&
    boundaryReady &&
    (!explanationRequired || form.comment.trim());
  const canConfirmFormation =
    formationMode && formationLabel === 'BOX' &&
    (form.startDate || formation.data?.finalStartDate) &&
    (form.endDate || formation.data?.finalEndDate);
  return (
    <section className="box-workbench" aria-labelledby="box-workbench-title">
      <h2 id="box-workbench-title">박스권 경계 탐지 검증</h2>
      <p className="research-warning">
        박스권 구간만 평가합니다. 이후 수익률·급락·회복·눌림·매매 성과는 이 검증에 사용하지
        않습니다.
      </p>
      <div className="workbench-toolbar">
        <span role="status" data-testid="batch-progress">{progressCompleted} / {progressTotal}</span>
        <label>
          평가 과제
          <select value={selectedBatchId} onChange={(e) => setBatchId(e.target.value)}>
            <option value="">선택하세요</option>
            {(batches.data ?? []).map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </select>
        </label>
        <button type="button" disabled={!selectedBatchId} onClick={() => next()}>
          다음 블라인드 항목
        </button>
        <button type="button" data-testid="previous-evaluation" disabled={!previousIds.length} onClick={openPrevious}>이전 평가 항목</button>
      </div>
      {!itemId && (
        <p className="empty-state">
          평가 과제를 선택해 시작하세요. 과제가 없다면 1단계 API로 먼저 생성해야 합니다.
          {previousIds.length > 0 && <button type="button" data-testid="completion-previous" onClick={openPrevious}>이전 평가 항목 열기</button>}
        </p>
      )}
      {detail.data && (
        <>
          {formationMode && (
            <fieldset aria-label="Formation Classification">
              <legend>Formation Classification</legend>
              <label><input type="radio" name="formationLabel" value="BOX" checked={formationLabel === 'BOX'} onChange={() => setFormationLabel('BOX')} /> BOX</label>
              <label><input type="radio" name="formationLabel" value="NOT_BOX" checked={formationLabel === 'NOT_BOX'} onChange={() => setFormationLabel('NOT_BOX')} /> NOT_BOX</label>
              <label><input type="radio" name="formationLabel" value="UNCERTAIN" checked={formationLabel === 'UNCERTAIN'} onChange={() => setFormationLabel('UNCERTAIN')} /> UNCERTAIN</label>
            </fieldset>
          )}
          <div className="blind-banner" role="status">
            관찰 종료일 {detail.data.item.cutoffDate} · 이후 가격은 서버에서 차단됨
          </div>
          <h3>블라인드 평가 항목 실제 일봉</h3>
          <MiniPriceChart
            candles={candles.data ?? []}
            candidates={!formationMode || formationLabel === 'BOX' ? narrowCandidates : []}
            activeKeys={activeKeys}
            period={form}
            onPeriodChange={(startDate, endDate) => setForm((v) => ({ ...v, boundaryDecision: 'MANUAL', selectedCandidateKey: '', startDate, endDate }))}
            zones={zones}
            onZonesChange={setZones}
          />
          {(!formationMode || formationLabel === 'BOX') && <fieldset>
            <legend>후보 비교</legend>
            {narrowCandidates.map((c, index) => (
              <div className="candidate-row" key={c.candidateKey}>
                <label>
                  <input
                    type="checkbox"
                    checked={activeKeys.includes(c.candidateKey)}
                    onChange={() =>
                      setActiveKeys((keys) =>
                        keys.includes(c.candidateKey)
                          ? keys.filter((k) => k !== c.candidateKey)
                          : [...keys, c.candidateKey]
                      )
                    }
                  />
                  후보 {index + 1} · {c.startDate}~{c.endDate}
                </label>
                <button type="button" onClick={() => choose(c)}>
                  이 후보 선택
                </button>
              </div>
            ))}
          </fieldset>}
          {formationMode && formationLabel !== 'BOX' && (
            <p className="info-text">Boundary and candidate details are revealed only after BOX classification.</p>
          )}
          {(!formationMode || formationLabel === 'BOX') && <>
          <fieldset>
            <legend>경계 결정 방식</legend>
            <p className="info-text">
              후보를 선택하거나 직접 경계를 지정하세요. 박스권이 아니거나 판단할 자료가 없으면
              적합 후보 없음을 선택하세요.
            </p>
            <label>
              <input
                type="radio"
                name="boundaryDecision"
                value="MANUAL"
                checked={form.boundaryDecision === 'MANUAL'}
                onChange={() =>
                  setForm({
                    ...form,
                    boundaryDecision: 'MANUAL',
                    selectedCandidateKey: ''
                  })
                }
              />
              직접 시작·종료 경계 지정
            </label>
            <label>
              <input
                type="radio"
                name="boundaryDecision"
                value="NO_SUITABLE_CANDIDATE"
                checked={form.boundaryDecision === 'NO_SUITABLE_CANDIDATE'}
                onChange={() =>
                  setForm({
                    ...form,
                    boundaryDecision: 'NO_SUITABLE_CANDIDATE',
                    selectedCandidateKey: '',
                    startDate: '',
                    endDate: ''
                  })
                }
              />
              적합 후보 없음
            </label>
            {form.boundaryDecision === 'CANDIDATE' && (
              <p className="info-text">후보를 선택했습니다. 날짜를 수정하면 직접 지정으로 바뀝니다.</p>
            )}
          </fieldset>
          <div className="boundary-grid">
            <label>
              시작 거래일
              <input
                type="date"
                value={form.startDate}
                max={detail.data.item.cutoffDate}
                disabled={form.boundaryDecision === 'NO_SUITABLE_CANDIDATE'}
                onChange={(e) =>
                  setForm({
                    ...form,
                    boundaryDecision: 'MANUAL',
                    selectedCandidateKey: '',
                    startDate: e.target.value
                  })
                }
              />
            </label>
            <label>
              종료 거래일
              <input
                type="date"
                value={form.endDate}
                max={detail.data.item.cutoffDate}
                disabled={form.boundaryDecision === 'NO_SUITABLE_CANDIDATE'}
                onChange={(e) =>
                  setForm({
                    ...form,
                    boundaryDecision: 'MANUAL',
                    selectedCandidateKey: '',
                    endDate: e.target.value
                  })
                }
              />
            </label>
          </div>
          <label>
            평가
            <select
              value={form.labelCode}
              onChange={(e) => setForm({ ...form, labelCode: e.target.value })}
            >
              <option value="">선택하세요</option>
              {labels.map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </select>
          </label>
          {formationMode && formationLabel === 'BOX' && (
            <fieldset aria-label="Formation Zones">
              <legend>Lower Support Zone / Upper Resistance Zone</legend>
              <p className="info-text">Proposed: {formation.data?.proposedLowerSupportMin ?? '—'}–{formation.data?.proposedLowerSupportMax ?? '—'} / {formation.data?.proposedUpperResistanceMin ?? '—'}–{formation.data?.proposedUpperResistanceMax ?? '—'}</p>
              {['lowerMin', 'lowerMax', 'upperMin', 'upperMax'].map((key) => (
                <label key={key}>{key}<input type="number" step="any" value={zones[key]} onChange={(e) => setZones({ ...zones, [key]: e.target.value })} /></label>
              ))}
              <label>Formation note<textarea value={formationNote} onChange={(e) => setFormationNote(e.target.value)} /></label>
              <button type="button" disabled={saveFormation.isPending || !formationLabel} onClick={() => saveFormation.mutate()}>Save formation evaluation</button>
            </fieldset>
          )}
          <label>
            확신도
            <select
              value={form.confidence}
              onChange={(e) =>
                setForm({ ...form, confidence: e.target.value ? Number(e.target.value) : '' })
              }
            >
              <option value="">선택하세요</option>
              <option value="1">1 · 매우 낮음</option>
              <option value="2">2 · 낮음</option>
              <option value="3">3 · 보통</option>
              <option value="4">4 · 높음</option>
              <option value="5">5 · 매우 높음</option>
            </select>
          </label>
          <fieldset>
            <legend>판단 근거</legend>
            {reasons.map((r) => (
              <label key={r}>
                <input
                  type="checkbox"
                  checked={form.reasonCodes.includes(r)}
                  onChange={() =>
                    setForm({
                      ...form,
                      reasonCodes: form.reasonCodes.includes(r)
                        ? form.reasonCodes.filter((v) => v !== r)
                        : [...form.reasonCodes, r]
                    })
                  }
                />
                {r}
              </label>
            ))}
          </fieldset>
          <label>
            설명 {explanationRequired && '(필수)'}
            <textarea
              aria-describedby={explanationRequired ? 'evaluation-comment-help' : undefined}
              value={form.comment}
              onChange={(e) => setForm({ ...form, comment: e.target.value })}
            />
          </label>
          {explanationRequired && (
            <p id="evaluation-comment-help" className="info-text">
              부분 유효 또는 자료 부족으로 판단한 이유를 적어주세요.
            </p>
          )}
          <div className="workbench-actions">
            <button type="button" onClick={() => (formationMode ? saveFormation.mutate() : save.mutate())}>
              임시 저장
            </button>
          <button
            type="button"
            disabled={formationMode ? !canConfirmFormation : !canCommit || commit.isPending}
            onClick={() => (formationMode ? saveFormation.mutate('BOX') : commit.mutate())}
          >
              평가 내용 확인 및 확정
            </button>
          </div>
          </>}
        </>
      )}
      {notice && <p aria-live="polite">{notice}</p>}
      {error && (
        <div className="error" role="alert">
          {error.message}
        </div>
      )}
    </section>
  );
}
