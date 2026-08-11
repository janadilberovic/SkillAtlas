/**
 * Turns the finder's free-text box into the `skills` list `GET /api/v1/experts` expects.
 *
 * "React + Neo4j > 3" -> ["React", "Neo4j"]
 *
 * The level threshold is stripped and dropped: E4.1 ranks by the sum of levels and has no
 * minimum-level parameter, so honouring "> 3" client-side would silently break paging.
 */
export function parseSkillQuery(query: string): string[] {
  return query
    .replace(/(?:>=|≥|>|level)\s*\d/gi, ' ')
    .split(/[+,]|\band\b/i)
    .map((term) => term.trim())
    .filter((term) => term.length > 0);
}
