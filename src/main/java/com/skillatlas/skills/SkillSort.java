package com.skillatlas.skills;

/** Catalog ordering. NAME is the browsing order; the other two feed the two rail cards. */
public enum SkillSort {
    NAME,
    WANTED,
    /** Thinnest coverage first — the bus-factor reading of the same catalog. */
    KNOWN;

    public static SkillSort of(String value) {
        for (SkillSort sort : values()) {
            if (sort.name().equalsIgnoreCase(value)) {
                return sort;
            }
        }
        return NAME;
    }
}
