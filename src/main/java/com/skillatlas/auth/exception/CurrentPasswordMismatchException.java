package com.skillatlas.auth.exception;

// 400, not the 401 that login uses: the token is valid and the caller is known — one field of the
// body is wrong. A 401 here would tell the client to re-authenticate over a typo.
public class CurrentPasswordMismatchException extends RuntimeException {

    public CurrentPasswordMismatchException() {
        super("Current password is incorrect");
    }
}
