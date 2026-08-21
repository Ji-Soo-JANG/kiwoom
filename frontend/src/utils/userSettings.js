export const SETTINGS_KEYS = {
  chartPeriod: 'kiwoom.chart.period',
  chartTimeframe: 'kiwoom.chart.timeframe',
  marketCards: 'kiwoom.marketDiscovery.cards',
  recentStocks: 'kiwoom-recent-stocks',
  searchFilters: 'kiwoom.search.filters',
  selectedAccount: 'kiwoom.account.selected'
};

const SAFE_KEYS = Object.values(SETTINGS_KEYS);

export function readLocalSettings() {
  return Object.fromEntries(
    SAFE_KEYS.flatMap((key) => {
      const value = localStorage.getItem(key);
      return value == null ? [] : [[key, value]];
    })
  );
}

export function resetLocalSettings() {
  SAFE_KEYS.forEach((key) => localStorage.removeItem(key));
}

export function downloadLocalSettings() {
  const payload = {
    format: 'kiwoom-ui-settings',
    version: 1,
    exportedAt: new Date().toISOString(),
    settings: readLocalSettings()
  };
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  );
  const link = document.createElement('a');
  link.href = url;
  link.download = `kiwoom-settings-${new Date().toISOString().slice(0, 10)}.json`;
  link.click();
  URL.revokeObjectURL(url);
}
