import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  commitBoxEvaluation,
  getBoxEvaluationBatches,
  getBoxEvaluationCandles,
  getBoxEvaluationItem,
  getNextBoxEvaluationItem,
  saveBoxEvaluationDraft
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

function MiniPriceChart({ candles, candidates, activeKeys }) {
  if (!candles.length) return <p className="empty-state">표시할 일봉이 없습니다.</p>;
  const closes = candles.map((c) => Number(c.closePrice));
  const min = Math.min(...closes);
  const max = Math.max(...closes);
  const x = (i) => 20 + (i / Math.max(candles.length - 1, 1)) * 760;
  const y = (v) => 220 - ((v - min) / Math.max(max - min, 1)) * 190;
  const points = closes.map((v, i) => `${x(i)},${y(v)}`).join(' ');
  return (
    <svg
      className="box-evaluation-chart"
      viewBox="0 0 800 250"
      role="img"
      aria-label="기준일 이하 실제 종가와 박스권 후보"
    >
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
  );
}

export default function BoxEvaluationWorkbench({ reviewerId }) {
  const client = useQueryClient();
  const [batchId, setBatchId] = useState('');
  const [itemId, setItemId] = useState(null);
  const [activeKeys, setActiveKeys] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [notice, setNotice] = useState('');
  const [navigationError, setNavigationError] = useState(null);
  const batches = useQuery({
    queryKey: ['box-evaluation-batches'],
    queryFn: getBoxEvaluationBatches,
    retry: false
  });
  const selectedBatchId = batchId || (batches.data?.[0]?.id ? String(batches.data[0].id) : '');
  const detail = useQuery({
    queryKey: ['box-evaluation-item', itemId, reviewerId],
    queryFn: () => getBoxEvaluationItem(itemId, reviewerId),
    enabled: Boolean(itemId),
    retry: false
  });
  const candles = useQuery({
    queryKey: ['box-evaluation-candles', itemId],
    queryFn: () => getBoxEvaluationCandles(itemId),
    enabled: Boolean(itemId),
    retry: false
  });
  const candidates = useMemo(() => detail.data?.candidates ?? [], [detail.data]);
  useEffect(() => {
    if (!detail.data) return;
    const draft = detail.data.draft;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setActiveKeys(candidates.map((c) => c.candidateKey));
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
  }, [detail.data, candidates]);
  const next = async ({ afterCommit = false } = {}) => {
    setNavigationError(null);
    try {
      const item = await getNextBoxEvaluationItem(selectedBatchId);
      if (!item) {
        setItemId(null);
        setActiveKeys([]);
        setForm(emptyForm());
        setNotice('이 평가 과제의 모든 블라인드 항목을 완료했습니다.');
        return;
      }
      setForm(emptyForm());
      setActiveKeys([]);
      setItemId(item.id);
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
  const error =
    batches.error || detail.error || candles.error || save.error || commit.error || navigationError;
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
  return (
    <section className="box-workbench" aria-labelledby="box-workbench-title">
      <h2 id="box-workbench-title">박스권 경계 탐지 검증</h2>
      <p className="research-warning">
        박스권 구간만 평가합니다. 이후 수익률·급락·회복·눌림·매매 성과는 이 검증에 사용하지
        않습니다.
      </p>
      <div className="workbench-toolbar">
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
      </div>
      {!itemId && (
        <p className="empty-state">
          평가 과제를 선택해 시작하세요. 과제가 없다면 1단계 API로 먼저 생성해야 합니다.
        </p>
      )}
      {detail.data && (
        <>
          <div className="blind-banner" role="status">
            관찰 종료일 {detail.data.item.cutoffDate} · 이후 가격은 서버에서 차단됨
          </div>
          <h3>블라인드 평가 항목 실제 일봉</h3>
          <MiniPriceChart
            candles={candles.data ?? []}
            candidates={candidates}
            activeKeys={activeKeys}
          />
          <fieldset>
            <legend>후보 비교</legend>
            {candidates.map((c, index) => (
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
          </fieldset>
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
            <button type="button" onClick={() => save.mutate()}>
              임시 저장
            </button>
            <button
              type="button"
              disabled={!canCommit || commit.isPending}
              onClick={() => commit.mutate()}
            >
              평가 내용 확인 및 확정
            </button>
          </div>
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
