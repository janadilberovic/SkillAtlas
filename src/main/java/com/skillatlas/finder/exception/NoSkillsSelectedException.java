package com.skillatlas.finder.exception;

public class NoSkillsSelectedException extends RuntimeException {

    public NoSkillsSelectedException() {
        super("At least one skill is required");
    }
}
