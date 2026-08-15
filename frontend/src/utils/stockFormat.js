const toNumber = (value) => {
    return Number(
        String(value ?? '0').replace(/,/g, '')
    );
};

export const formatPrice = (value) => {
    const number = toNumber(value);

    return Number.isNaN(number)
        ? value
        : number.toLocaleString('ko-KR');
};

export const formatSignedNumber = (value) => {
    const number = toNumber(value);

    if (Number.isNaN(number)) {
        return value;
    }

    return number > 0
        ? `+${number}`
        : String(number);
};

export const getChangeClass = (value) => {
    const number = toNumber(value);

    if (number > 0) {
        return 'price-up';
    }

    if (number < 0) {
        return 'price-down';
    }

    return '';
};

export const formatDate = (value) => {
    if (!value || value.length !== 8) {
        return value;
    }

    return (
        `${value.substring(0, 4)}-`
        + `${value.substring(4, 6)}-`
        + value.substring(6, 8)
    );
};

export const formatShortDate = (value) => {
    if (!value || value.length !== 8) {
        return value;
    }

    return (
        value.substring(4, 6)
        + '/'
        + value.substring(6, 8)
    );
};