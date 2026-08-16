import { useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getCurrentUser, logout } from '../api/kiwoomApi';

const RETURN_PATH_KEY = 'kiwoom.returnPath';

const loginUrl = () => (import.meta.env.DEV ? 'http://localhost:8080/login' : '/login');

export default function AuthGate({ children }) {
  const queryClient = useQueryClient();
  const authQuery = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getCurrentUser,
    retry: false
  });

  useEffect(() => {
    if (authQuery.error?.status !== 401) return;
    sessionStorage.setItem(RETURN_PATH_KEY, window.location.pathname + window.location.search);
    window.location.assign(loginUrl());
  }, [authQuery.error]);

  useEffect(() => {
    if (!authQuery.data) return;
    const returnPath = sessionStorage.getItem(RETURN_PATH_KEY);
    if (returnPath && returnPath !== window.location.pathname + window.location.search) {
      sessionStorage.removeItem(RETURN_PATH_KEY);
      window.history.replaceState(null, '', returnPath);
      window.dispatchEvent(new PopStateEvent('popstate'));
    } else {
      sessionStorage.removeItem(RETURN_PATH_KEY);
    }
  }, [authQuery.data]);

  const handleLogout = async () => {
    await logout();
    queryClient.clear();
    sessionStorage.setItem(RETURN_PATH_KEY, '/');
    window.location.assign(loginUrl());
  };

  if (authQuery.isPending) {
    return <div role="status">로그인 상태를 확인하는 중...</div>;
  }

  if (authQuery.error) {
    if (authQuery.error.status === 401) {
      return <div role="status">로그인 화면으로 이동하는 중...</div>;
    }
    return (
      <div role="alert">
        로그인 상태를 확인하지 못했습니다.{' '}
        <button type="button" onClick={() => authQuery.refetch()}>
          다시 시도
        </button>
      </div>
    );
  }

  return children({ currentUser: authQuery.data, onLogout: handleLogout });
}
