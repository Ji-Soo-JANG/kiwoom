import { useState } from 'react';
import {
  downloadLocalSettings,
  readLocalSettings,
  resetLocalSettings
} from '../utils/userSettings';

const LABELS = {
  'kiwoom.chart.period': '차트 조회 기간',
  'kiwoom.chart.timeframe': '차트 종류',
  'kiwoom.marketDiscovery.cards': '시장 탐색 카드',
  'kiwoom-recent-stocks': '최근 검색 종목',
  'kiwoom.search.filters': '검색 필터',
  'kiwoom.account.selected': '선택 계좌'
};

function SettingsPanel() {
  const [settings, setSettings] = useState(readLocalSettings);
  const [message, setMessage] = useState('');

  const reset = () => {
    if (!window.confirm('브라우저에 저장된 화면 설정을 모두 초기화할까요?')) return;
    resetLocalSettings();
    setSettings({});
    setMessage('설정을 초기화했습니다. 각 화면을 다시 열면 기본값이 적용됩니다.');
  };

  return (
    <section className="settings-panel" aria-labelledby="settings-title">
      <h2 id="settings-title">사용자 설정</h2>
      <p>이 브라우저에만 저장되는 화면 설정입니다. 계좌번호와 인증정보는 저장하지 않습니다.</p>
      <dl className="settings-list">
        {Object.entries(LABELS).map(([key, label]) => (
          <div key={key}>
            <dt>{label}</dt>
            <dd>{settings[key] == null ? '기본값 사용 중' : '저장됨'}</dd>
          </div>
        ))}
      </dl>
      <div className="settings-actions">
        <button type="button" onClick={downloadLocalSettings}>
          설정 내보내기
        </button>
        <button type="button" className="danger-button" onClick={reset}>
          설정 초기화
        </button>
      </div>
      {message && (
        <p role="status" className="success-message">
          {message}
        </p>
      )}
    </section>
  );
}

export default SettingsPanel;
