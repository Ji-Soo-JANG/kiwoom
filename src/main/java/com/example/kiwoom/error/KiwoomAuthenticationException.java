package com.example.kiwoom.error;

public class KiwoomAuthenticationException extends KiwoomApiException {

    public KiwoomAuthenticationException(String message) {
        super(KiwoomErrorCode.AUTHENTICATION_FAILED, message);
    }
}
