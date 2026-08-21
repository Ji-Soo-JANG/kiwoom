import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  ApiError,
  getAlertEvents,
  getCurrentPrice,
  getPortfolioProfitTrend,
  importPortfolioTrades,
  markAlertRead,
  runBacktest
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

  it('페이지 알림과 포트폴리오 분석 API를 호출한다', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ content: [] }), { status: 200 }))
      );
    vi.stubGlobal('fetch', fetchMock);

    await getAlertEvents(true, 2, 10);
    await getPortfolioProfitTrend();
    await importPortfolioTrades('code,type,quantity,price,fee,tax');

    expect(fetchMock.mock.calls[0][0]).toContain('unreadOnly=true&page=2&size=10');
    expect(fetchMock.mock.calls[2][1]).toMatchObject({ method: 'POST', body: expect.any(String) });
  });

  it('204 읽음 처리 응답을 null로 변환한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(markAlertRead(4)).resolves.toBeNull();
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
});
