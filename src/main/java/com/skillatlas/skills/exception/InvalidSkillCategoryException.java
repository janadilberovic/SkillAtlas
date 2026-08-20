package com.skillatlas.skills.exception;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.skillatlas.skills.enums.SkillCategory;

public class InvalidSkillCategoryException extends RuntimeException {

    public InvalidSkillCategoryException(String value) {
        super("Unknown category '" + value + "'. Expected one of: " + Arrays.stream(SkillCategory.values())
                .map(category -> category.name().toLowerCase())
                .collect(Collectors.joining(", ")));
    }
}
