import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import AlertCenter from './AlertCenter';
import * as api from '../api/kiwoomApi';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';

vi.mock('../api/kiwoomApi');

const renderAlert = (onError = vi.fn()) => {
    const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
    return render(<QueryClientProvider client={client}>
        <AlertCenter onError={onError}/>
    </QueryClientProvider>);
};

describe('AlertCenter', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        api.getAlertRules.mockResolvedValue([]);
        api.getAlertEvents.mockResolvedValue([]);
    });

    it('목표가 규칙을 추가하고 목록을 새로 불러온다', async () => {
        api.createAlertRule.mockResolvedValue({id: 1});
        api.getAlertRules.mockResolvedValueOnce([]).mockResolvedValueOnce([{
            id: 1, code: '005930', conditionType: 'PRICE_ABOVE', threshold: 80000, enabled: true
        }]);
        renderAlert();
        await screen.findByText('설정된 목표가 알림이 없습니다.');

        fireEvent.change(screen.getByLabelText('종목 코드'), {target: {value: '005930'}});
        fireEvent.change(screen.getByLabelText('목표가'), {target: {value: '80000'}});
        fireEvent.click(screen.getByRole('button', {name: '알림 규칙 추가'}));

        await screen.findByText(/005930 · 목표가 이상 80,000/);
        expect(api.createAlertRule).toHaveBeenCalledWith('005930', 'PRICE_ABOVE', 80000);
    });

    it('미읽음 배지를 표시하고 이벤트를 읽음 처리한다', async () => {
        const unreadEvent = {
            id: 4, code: '000660', conditionType: 'PRICE_BELOW', observedValue: 119000,
            threshold: 120000, triggeredAt: '2026-08-16T00:00:00Z', readAt: null
        };
        api.getAlertEvents.mockResolvedValueOnce([unreadEvent])
            .mockResolvedValueOnce([{...unreadEvent, readAt: '2026-08-16T01:00:00Z'}]);
        api.markAlertRead.mockResolvedValue(null);
        renderAlert();

        expect(await screen.findByLabelText('읽지 않은 알림 1개')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', {name: '읽음'}));

        await waitFor(() => expect(screen.queryByLabelText('읽지 않은 알림 1개')).not.toBeInTheDocument());
        expect(api.markAlertRead.mock.calls[0][0]).toBe(4);
    });
});
