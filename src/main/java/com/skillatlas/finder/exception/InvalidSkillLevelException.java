package com.skillatlas.finder.exception;

public class InvalidSkillLevelException extends RuntimeException {

    public InvalidSkillLevelException(String term, int level) {
        super("Level must be between 1 and 5, got " + level + " in '" + term + "'");
    }
}
