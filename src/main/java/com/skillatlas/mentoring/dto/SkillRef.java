package com.skillatlas.mentoring.dto;

/** The skill a request asked for by name, resolved once so the response can echo it back. */
public record SkillRef(String id, String name) {
}
