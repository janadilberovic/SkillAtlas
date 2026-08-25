package com.skillatlas.vacayay.exception;

/** The old system did not answer. Becomes a 502, so the screen can say which system is down. */
public class VacaYayUnavailableException extends RuntimeException {

    public VacaYayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public VacaYayUnavailableException(String message) {
        super(message);
    }
}
