import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  ApiError,
  getNextBoxEvaluationItem,
  getAlertEvents,
  getCurrentPrice,
  markAlertRead,
  runBacktest,
  runWalkForward,
  placePaperOrder,
  resumePaperKillSwitch
} from './kiwoomApi';

describe('kiwoomApi 오류 처리', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('백엔드 오류 코드를 사용자 안내 문구로 변환한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: 'KIWOOM_RATE_LIMITED',
            message: 'upstream rate limited'
          }),
          { status: 503, headers: { 'Content-Type': 'application/json' } }
        )
      )
    );

    const error = await getCurrentPrice('005930').catch((value) => value);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('KIWOOM_RATE_LIMITED');
    expect(error.message).toContain('잠시 후 다시 시도');
  });

  it('페이지 알림 API를 호출한다', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ content: [] }), { status: 200 }))
      );
    vi.stubGlobal('fetch', fetchMock);

    await getAlertEvents(true, 2, 10);

    expect(fetchMock.mock.calls[0][0]).toContain('unreadOnly=true&page=2&size=10');
  });

  it('204 읽음 처리 응답을 null로 변환한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(markAlertRead(4)).resolves.toBeNull();
  });

  it('다음 블라인드 항목의 성공한 빈 응답을 완료 상태로 변환한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 200 })));

    await expect(getNextBoxEvaluationItem(1)).resolves.toBeNull();
  });

  it('백테스트 설정을 JSON으로 전송한다', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ tradeCount: 0 }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const request = { code: '005930', startDate: '2025-01-01', endDate: '2026-01-01' };

    await runBacktest(request);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/kiwoom/backtests',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(request) })
    );
  });

  it('워크포워드 설정을 JSON으로 전송한다', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ foldCount: 2 }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const request = {
      backtest: { code: '005930', startDate: '2024-01-01', endDate: '2026-01-01' },
      trainingDays: 240,
      validationDays: 60
    };

    await runWalkForward(request);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/kiwoom/backtests/walk-forward',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(request) })
    );
  });

  it('PAPER 주문과 킬 스위치 수동 재개 요청을 JSON으로 전송한다', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ status: 'FILLED' }), { status: 200 }))
      );
    vi.stubGlobal('fetch', fetchMock);
    const order = {
      decisionId: 'test-buy-1',
      code: '005930',
      side: 'BUY',
      quantity: 1,
      price: 70000
    };

    await placePaperOrder(order);
    await resumePaperKillSwitch('RESUME_PAPER_TRADING', '테스트 재개');

    expect(fetchMock.mock.calls[0][1]).toMatchObject({
      method: 'POST',
      body: JSON.stringify(order)
    });
    expect(fetchMock.mock.calls[1][1].body).toContain('RESUME_PAPER_TRADING');
  });
});
