package com.skillatlas.people.exception;

public class PersonNotFoundException extends RuntimeException {

    public PersonNotFoundException(String id) {
        super("Person not found: " + id);
    }
}
