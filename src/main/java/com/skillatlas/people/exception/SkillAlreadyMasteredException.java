package com.skillatlas.people.exception;

// Rule §4.1: you cannot want-to-learn a skill you already know at the maximum level (5).
public class SkillAlreadyMasteredException extends RuntimeException {

    public SkillAlreadyMasteredException(String skillId) {
        super("Skill already known at level 5, cannot add as a wish: " + skillId);
    }
}
