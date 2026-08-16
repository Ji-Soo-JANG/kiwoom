import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AuthGate from './AuthGate';
import * as api from '../api/kiwoomApi';
import * as navigation from '../utils/authNavigation';

vi.mock('../api/kiwoomApi');
vi.mock('../utils/authNavigation');

const renderGate = () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <AuthGate>
        {({ currentUser, onLogout }) => (
          <div>
            <span>{currentUser.username}</span>
            <button type="button" onClick={onLogout}>
              로그아웃
            </button>
          </div>
        )}
      </AuthGate>
    </QueryClientProvider>
  );
  return client;
};

describe('AuthGate', () => {
  beforeEach(() => vi.resetAllMocks());

  it('인증 사용자를 표시하고 로그아웃 후 로그인 화면으로 이동한다', async () => {
    api.getCurrentUser.mockResolvedValue({ username: 'local-user' });
    api.logout.mockResolvedValue(null);
    const client = renderGate();

    expect(await screen.findByText('local-user')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }));

    await waitFor(() => expect(api.logout).toHaveBeenCalled());
    expect(navigation.restoreReturnPath).toHaveBeenCalled();
    expect(navigation.saveReturnPath).toHaveBeenCalledWith('/');
    expect(navigation.redirectToLogin).toHaveBeenCalled();
    expect(client.getQueryCache().getAll()).toHaveLength(0);
  });

  it('401이면 현재 경로를 저장하고 로그인 화면으로 이동한다', async () => {
    api.getCurrentUser.mockRejectedValue({ status: 401 });
    renderGate();

    expect(await screen.findByText('로그인 화면으로 이동하는 중...')).toBeInTheDocument();
    expect(navigation.saveReturnPath).toHaveBeenCalledWith();
    expect(navigation.redirectToLogin).toHaveBeenCalled();
  });

  it('일시적인 인증 확인 실패를 다시 시도한다', async () => {
    api.getCurrentUser
      .mockRejectedValueOnce({ status: 500 })
      .mockResolvedValueOnce({ username: 'retry-user' });
    renderGate();

    fireEvent.click(await screen.findByRole('button', { name: '다시 시도' }));
    expect(await screen.findByText('retry-user')).toBeInTheDocument();
  });
});
