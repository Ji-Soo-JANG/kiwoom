import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, getCurrentPrice } from './kiwoomApi';

describe('kiwoomApi 오류 처리', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('백엔드 오류 코드를 사용자 안내 문구로 변환한다', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
            code: 'KIWOOM_RATE_LIMITED',
            message: 'upstream rate limited'
        }), {status: 503, headers: {'Content-Type': 'application/json'}})));

        const error = await getCurrentPrice('005930').catch(value => value);

        expect(error).toBeInstanceOf(ApiError);
        expect(error.code).toBe('KIWOOM_RATE_LIMITED');
        expect(error.message).toContain('잠시 후 다시 시도');
    });
});
