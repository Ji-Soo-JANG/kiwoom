import {useState} from 'react';

function StockSearchForm({
                             loading,
                             onSingleSearch,
                             onMultipleSearch
                         }) {
    const [validationError, setValidationError] = useState('');
    const [singleCode, setSingleCode] =
        useState('');

    const [multipleCodes, setMultipleCodes] =
        useState('');

    const submitSingle = (event) => {
        event?.preventDefault();
        const code = singleCode.trim();

        if (!/^\d{6}$/.test(code)) {
            setValidationError('종목 코드는 6자리 숫자로 입력하세요.');
            return;
        }
        setValidationError('');
        onSingleSearch(code);
    };

    const submitMultiple = (event) => {
        event?.preventDefault();
        const codes = multipleCodes
            .split(',')
            .map((code) => code.trim())
            .filter(Boolean);

        if (codes.length === 0 || codes.length > 20 || codes.some((code) => !/^\d{6}$/.test(code))) {
            setValidationError('6자리 숫자 종목 코드를 최대 20개까지 입력하세요.');
            return;
        }
        setValidationError('');
        onMultipleSearch(codes);
    };

    return (
        <>
            {validationError && <p id="search-validation-error" className="error" role="alert">{validationError}</p>}
            <form onSubmit={submitSingle} noValidate>
            <div className="input-group">
                <label htmlFor="singleCode">
                    종목 코드 (단일 조회)
                </label>

                <input
                    id="singleCode"
                    type="text"
                    maxLength={6}
                    inputMode="numeric"
                    pattern="[0-9]{6}"
                    aria-describedby={`stock-code-help${validationError ? ' search-validation-error' : ''}`}
                    aria-invalid={Boolean(validationError)}
                    value={singleCode}
                    disabled={loading}
                    placeholder="예: 005930 (삼성전자)"
                    onChange={(event) =>
                        setSingleCode(event.target.value)
                    }
                />
                <small id="stock-code-help">6자리 숫자 종목 코드를 입력하세요.</small>
            </div>

            <div className="button-group">
                <button
                    type="submit"
                    className="btn-single"
                    disabled={loading}
                    onClick={submitSingle}
                >
                    단일 조회
                </button>
            </div>
            </form>

            <form onSubmit={submitMultiple} noValidate>
            <div className="input-group">
                <label htmlFor="multipleCodes">
                    종목 코드 (다중 조회)
                </label>

                <input
                    id="multipleCodes"
                    type="text"
                    value={multipleCodes}
                    disabled={loading}
                    placeholder="예: 005930,000660,035420"
                    aria-describedby={`multiple-code-help${validationError ? ' search-validation-error' : ''}`}
                    aria-invalid={Boolean(validationError)}
                    onChange={(event) =>
                        setMultipleCodes(event.target.value)
                    }
                />
                <small id="multiple-code-help">쉼표로 구분해 최대 20개까지 입력하세요.</small>
            </div>

            <div className="button-group">
                <button
                    type="submit"
                    className="btn-multiple"
                    disabled={loading}
                    onClick={submitMultiple}
                >
                    다중 조회
                </button>
            </div>
            </form>
        </>
    );
}

export default StockSearchForm;
