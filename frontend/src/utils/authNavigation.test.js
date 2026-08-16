import { beforeEach, describe, expect, it, vi } from 'vitest';
import { restoreReturnPath, saveReturnPath } from './authNavigation';

describe('authNavigation', () => {
  beforeEach(() => {
    sessionStorage.clear();
    window.history.replaceState(null, '', '/portfolio');
  });

  it('복귀 경로를 저장한다', () => {
    saveReturnPath('/alerts?unread=true');
    expect(sessionStorage.getItem('kiwoom.returnPath')).toBe('/alerts?unread=true');
  });

  it('저장된 경로를 복원하고 제거한다', () => {
    const listener = vi.fn();
    window.addEventListener('popstate', listener, { once: true });
    saveReturnPath('/watchlist');
    restoreReturnPath();
    expect(window.location.pathname).toBe('/watchlist');
    expect(sessionStorage.getItem('kiwoom.returnPath')).toBeNull();
    expect(listener).toHaveBeenCalled();
  });

  it('현재 경로와 같으면 이동하지 않는다', () => {
    saveReturnPath('/portfolio');
    restoreReturnPath();
    expect(window.location.pathname).toBe('/portfolio');
  });
});
