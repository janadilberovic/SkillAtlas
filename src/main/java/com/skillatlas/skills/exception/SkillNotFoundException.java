package com.skillatlas.skills.exception;

public class SkillNotFoundException extends RuntimeException {

    public SkillNotFoundException(String id) {
        super("Skill not found: " + id);
    }
}
