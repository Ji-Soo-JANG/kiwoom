const requestJson = async (url, options) => {
    const response = await fetch(url, options);

    if (!response.ok) {
        const body = await response.text();

        throw new Error(
            body || `API 요청 실패 (${response.status})`
        );
    }

    return response.json();
};

export const getCurrentPrice = (code) => {
    const encodedCode = encodeURIComponent(code);

    return requestJson(
        `/api/kiwoom/stock-price/${encodedCode}`
    );
};

export const getDailyPrices = (code) => {
    const encodedCode = encodeURIComponent(code);

    return requestJson(
        `/api/kiwoom/stock-price/${encodedCode}/daily`
    );
};

export const getMultiplePrices = (codes) => {
    const parameter = encodeURIComponent(
        codes.join(',')
    );

    return requestJson(
        `/api/kiwoom/stock-prices?codes=${parameter}`
    );
};

export const getWatchlist = () => requestJson('/api/watchlist');
export const addToWatchlist = (code) => requestJson('/api/watchlist', {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({code})
});
export const removeFromWatchlist = async (code) => {
    const response = await fetch(`/api/watchlist/${encodeURIComponent(code)}`, {method: 'DELETE'});
    if (!response.ok) throw new Error(`관심종목 삭제 실패 (${response.status})`);
};
