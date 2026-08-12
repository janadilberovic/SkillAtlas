package com.skillatlas.finder;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.skillatlas.finder.exception.InvalidSkillLevelException;

/**
 * One term of an expert query: a skill name plus the lowest KNOWS level that still counts as
 * knowing it.
 *
 * <p>On the wire a term is one entry of the {@code skills} parameter with an optional threshold
 * suffix — {@code neo4j}, {@code neo4j>=4}, {@code neo4j>3}. {@code >} is strict and {@code >=}
 * (or the unicode {@code ≥}) inclusive, so {@code neo4j>3} and {@code neo4j>=4} mean the same
 * thing. No suffix means level 1: "knows it at all".
 *
 * <p>The threshold rides along with the name instead of being one query-wide parameter because
 * a query like "React ≥ 3 + Neo4j ≥ 5" asks a different question per skill.
 */
public record SkillTerm(String name, int minLevel) {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;

    // Two digits at most: keeps a nonsense "neo4j > 999999" out of Integer.parseInt.
    private static final Pattern THRESHOLD = Pattern.compile("^(.*?)\\s*(>=|≥|>)\\s*(\\d{1,2})$");

    /** Names are lowercased here; matching against {@code Skill.name} is case-insensitive. */
    public static SkillTerm parse(String raw) {
        String term = raw.trim();
        Matcher matcher = THRESHOLD.matcher(term);
        if (!matcher.matches()) {
            return new SkillTerm(normalize(term), MIN_LEVEL);
        }
        int declared = Integer.parseInt(matcher.group(3));
        int minLevel = ">".equals(matcher.group(2)) ? declared + 1 : declared;
        if (minLevel < MIN_LEVEL || minLevel > MAX_LEVEL) {
            throw new InvalidSkillLevelException(term, minLevel);
        }
        return new SkillTerm(normalize(matcher.group(1)), minLevel);
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
