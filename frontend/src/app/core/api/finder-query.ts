/**
 * The finder's query language: free text in, skill terms out.
 *
 * "React + Neo4j > 3" -> [{name: 'React', minLevel: 1}, {name: 'Neo4j', minLevel: 4}]
 *
 * A term is a skill name with an optional minimum level. `>` is strict and `>=` inclusive, so
 * "> 3" and ">= 4" are the same bar — the chip in the query bar always shows the resulting
 * "≥ N" so there is no guessing. The threshold belongs to its own term: "React ≥ 3 + Neo4j ≥ 5"
 * asks for a competent React dev who is also a Neo4j expert, not for level 5 on both.
 *
 * `GET /api/v1/experts` parses the same suffixes server-side (see SkillTerm.java); this file only
 * has to agree with it on the wire format produced by `toSkillParam`.
 */

export interface SkillTerm {
  name: string;
  /** 1 means "knows it at all" — the level that is not really a filter. */
  minLevel: number;
}

export const MIN_LEVEL = 1;
export const MAX_LEVEL = 5;

// "neo4j >= 4" / "neo4j ≥ 4" / "neo4j > 3" / "neo4j level 4" / "neo4j 4+".
const THRESHOLD = /^(.*?)\s*(?:(>=|≥|>|level)\s*([1-5])|\b([1-5])\s*\+)$/i;

/** Splits free text on the term separators and parses each part. Empty parts are dropped. */
export function parseSkillQuery(query: string): SkillTerm[] {
  const terms: SkillTerm[] = [];
  for (const part of query.split(/[+,]|\band\b/i)) {
    const term = parseSkillTerm(part);
    // The stricter threshold wins if the same skill is typed twice.
    const existing = terms.find((t) => t.name.toLowerCase() === term.name.toLowerCase());
    if (!term.name) continue;
    if (existing) existing.minLevel = Math.max(existing.minLevel, term.minLevel);
    else terms.push(term);
  }
  return terms;
}

/** Parses a single term. A name with no recognised suffix comes back at level 1. */
export function parseSkillTerm(raw: string): SkillTerm {
  const text = raw.trim();
  const match = THRESHOLD.exec(text);
  if (!match) return { name: text, minLevel: MIN_LEVEL };

  const [, name, operator, level, plusLevel] = match;
  const declared = Number(level ?? plusLevel);
  const minLevel = operator === '>' ? declared + 1 : declared;
  return { name: name.trim(), minLevel: clamp(minLevel) };
}

/** The name part of a half-typed term, for matching against the skill catalogue. */
export function skillNameFragment(raw: string): string {
  return parseSkillTerm(raw).name;
}

/** Wire form of one term: `neo4j` or `neo4j>=4`. */
export function toSkillParam(term: SkillTerm): string {
  return term.minLevel > MIN_LEVEL ? `${term.name}>=${term.minLevel}` : term.name;
}

/** How a term reads in the UI: `Neo4j` or `Neo4j ≥ 4`. */
export function formatSkillTerm(term: SkillTerm): string {
  return term.minLevel > MIN_LEVEL ? `${term.name} ≥ ${term.minLevel}` : term.name;
}

// The server rejects a level outside 1–5; clamping here keeps a typo from becoming a 400.
function clamp(level: number): number {
  return Math.min(MAX_LEVEL, Math.max(MIN_LEVEL, level));
}
