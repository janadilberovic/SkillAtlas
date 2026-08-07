package com.skillatlas.teams.exception;

public class TeamNotFoundException extends RuntimeException {

    public TeamNotFoundException(String id) {
        super("Team not found: " + id);
    }
}
