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

    const submitSingle = () => {
        const code = singleCode.trim();

        if (!/^\d{6}$/.test(code)) {
            setValidationError('종목 코드는 6자리 숫자로 입력하세요.');
            return;
        }
        setValidationError('');
        onSingleSearch(code);
    };

    const submitMultiple = () => {
        const codes = multipleCodes
            .split(',')
            .map((code) => code.trim())
            .filter(Boolean);

        if (codes.length === 0 || codes.some((code) => !/^\d{6}$/.test(code))) {
            setValidationError('모든 종목 코드를 6자리 숫자로 입력하세요.');
            return;
        }
        setValidationError('');
        onMultipleSearch(codes);
    };

    const handleEnter = (event, callback) => {
        if (event.key === 'Enter' && !loading) {
            callback();
        }
    };

    return (
        <>
            {validationError && <p className="error" role="alert">{validationError}</p>}
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
                    aria-describedby="stock-code-help"
                    value={singleCode}
                    disabled={loading}
                    placeholder="예: 005930 (삼성전자)"
                    onChange={(event) =>
                        setSingleCode(event.target.value)
                    }
                    onKeyDown={(event) =>
                        handleEnter(event, submitSingle)
                    }
                />
                <small id="stock-code-help">6자리 숫자 종목 코드를 입력하세요.</small>
            </div>

            <div className="button-group">
                <button
                    type="button"
                    className="btn-single"
                    disabled={loading}
                    onClick={submitSingle}
                >
                    단일 조회
                </button>
            </div>

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
                    onChange={(event) =>
                        setMultipleCodes(event.target.value)
                    }
                    onKeyDown={(event) =>
                        handleEnter(event, submitMultiple)
                    }
                />
            </div>

            <div className="button-group">
                <button
                    type="button"
                    className="btn-multiple"
                    disabled={loading}
                    onClick={submitMultiple}
                >
                    다중 조회
                </button>
            </div>
        </>
    );
}

export default StockSearchForm;
