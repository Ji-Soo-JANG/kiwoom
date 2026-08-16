import { useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getCurrentUser, logout } from '../api/kiwoomApi';
import { redirectToLogin, restoreReturnPath, saveReturnPath } from '../utils/authNavigation';

export default function AuthGate({ children }) {
  const queryClient = useQueryClient();
  const authQuery = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getCurrentUser,
    retry: false
  });

  useEffect(() => {
    if (authQuery.error?.status !== 401) return;
    saveReturnPath();
    redirectToLogin();
  }, [authQuery.error]);

  useEffect(() => {
    if (!authQuery.data) return;
    restoreReturnPath();
  }, [authQuery.data]);

  const handleLogout = async () => {
    await logout();
    queryClient.clear();
    saveReturnPath('/');
    redirectToLogin();
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
