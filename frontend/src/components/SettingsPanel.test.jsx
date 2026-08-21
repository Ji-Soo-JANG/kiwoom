import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SettingsPanel from './SettingsPanel';

describe('SettingsPanel', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('kiwoom.chart.period', '120');
  });

  it('허용된 로컬 설정 상태를 표시하고 초기화한다', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<SettingsPanel />);

    expect(screen.getByText('차트 조회 기간').nextSibling).toHaveTextContent('저장됨');
    fireEvent.click(screen.getByRole('button', { name: '설정 초기화' }));
    expect(localStorage.getItem('kiwoom.chart.period')).toBeNull();
    expect(screen.getByRole('status')).toHaveTextContent('설정을 초기화했습니다');
  });
});
