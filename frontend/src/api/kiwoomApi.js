const requestJson = async (url) => {
    const response = await fetch(url);

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