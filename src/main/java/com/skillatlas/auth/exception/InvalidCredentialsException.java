package com.skillatlas.auth.exception;

// Same error for unknown email and wrong password — never reveal which one failed.
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
