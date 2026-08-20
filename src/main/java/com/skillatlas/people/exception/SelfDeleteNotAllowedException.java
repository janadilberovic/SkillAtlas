package com.skillatlas.people.exception;

public class SelfDeleteNotAllowedException extends RuntimeException {

    public SelfDeleteNotAllowedException() {
        super("You cannot delete your own account");
    }
}
