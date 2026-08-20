package com.skillatlas.skills;

/** Catalog ordering. NAME is the browsing order; WANTED is what the "most wanted" card asks for. */
public enum SkillSort {
    NAME,
    WANTED;

    public static SkillSort of(String value) {
        return WANTED.name().equalsIgnoreCase(value) ? WANTED : NAME;
    }
}
