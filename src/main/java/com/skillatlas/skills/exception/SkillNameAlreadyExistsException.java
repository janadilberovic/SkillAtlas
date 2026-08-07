package com.skillatlas.skills.exception;

public class SkillNameAlreadyExistsException extends RuntimeException {

    public SkillNameAlreadyExistsException(String name) {
        super("Skill name already in use: " + name);
    }
}
