package com.skillatlas.auth.exception;

public class PasswordUnchangedException extends RuntimeException {

    public PasswordUnchangedException() {
        super("The new password must differ from the current one");
    }
}
