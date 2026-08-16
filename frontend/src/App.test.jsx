import {fireEvent, render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import App from './App';
import * as api from './api/kiwoomApi';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';

vi.mock('./api/kiwoomApi');

describe('App routes', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        api.getWatchlist.mockResolvedValue([]);
        api.getPortfolio.mockResolvedValue([]);
        api.getAlertRules.mockResolvedValue([]);
        api.getAlertEvents.mockResolvedValue([]);
    });

    it('내비게이션으로 포트폴리오와 알림 화면을 분리해 표시한다', async () => {
        const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
        render(<QueryClientProvider client={client}>
            <MemoryRouter initialEntries={['/']}><App/></MemoryRouter>
        </QueryClientProvider>);
        expect(screen.getByLabelText('종목 코드 (단일 조회)')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('link', {name: '포트폴리오'}));
        expect(await screen.findByRole('heading', {name: '포트폴리오'})).toBeInTheDocument();
        expect(screen.queryByLabelText('종목 코드 (단일 조회)')).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole('link', {name: '알림'}));
        expect(await screen.findByRole('heading', {name: '주가·지표 알림'})).toBeInTheDocument();
    });
});
