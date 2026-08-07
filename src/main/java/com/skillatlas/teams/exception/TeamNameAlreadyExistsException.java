package com.skillatlas.teams.exception;

public class TeamNameAlreadyExistsException extends RuntimeException {

    public TeamNameAlreadyExistsException(String name) {
        super("Team name already in use: " + name);
    }
}
