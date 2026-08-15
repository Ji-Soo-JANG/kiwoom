import {useState} from 'react';

function StockSearchForm({
                             loading,
                             onSingleSearch,
                             onMultipleSearch
                         }) {
    const [singleCode, setSingleCode] =
        useState('');

    const [multipleCodes, setMultipleCodes] =
        useState('');

    const submitSingle = () => {
        const code = singleCode.trim();

        if (code) {
            onSingleSearch(code);
        }
    };

    const submitMultiple = () => {
        const codes = multipleCodes
            .split(',')
            .map((code) => code.trim())
            .filter(Boolean);

        if (codes.length > 0) {
            onMultipleSearch(codes);
        }
    };

    const handleEnter = (event, callback) => {
        if (event.key === 'Enter' && !loading) {
            callback();
        }
    };

    return (
        <>
            <div className="input-group">
                <label htmlFor="singleCode">
                    종목 코드 (단일 조회)
                </label>

                <input
                    id="singleCode"
                    type="text"
                    maxLength={6}
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