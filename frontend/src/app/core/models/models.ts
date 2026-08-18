/**
 * TypeScript mirror of the SkillAtlas API contract.
 *
 * Fields marked `// PLANNED` do not exist on the current Java DTOs yet — they document
 * the shape the backend must grow to so the mock → real swap stays 1:1. Everything else
 * matches an existing `*Response` record under `com.skillatlas.*`.
 */

export type Role = 'ADMIN' | 'MEMBER';
export type SkillCategory = 'LANGUAGE' | 'FRAMEWORK' | 'TOOL' | 'DATABASE';

/** Spring Data `PageResponse<T>`. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** GET /me */
export interface Me {
  id: string;
  email: string;
  fullName: string;
  role: Role;
}

/** POST /auth/login */
export interface LoginResponse {
  token: string;
  tokenType: string;
  role: Role;
}

/** SkillResponse */
export interface Skill {
  id: string;
  name: string;
  category: SkillCategory;
  color: string;
  knownBy?: number; // PLANNED — count of active KNOWS
  wantedBy?: number; // PLANNED — count of WANTS_TO_LEARN
}

/** `PersonResponse.topSkills` — a KNOWS edge flattened to what a list row prints. */
export interface TopSkill {
  skillId: string;
  name: string;
  level: number;
}

/** A KNOWS edge projected for the UI (level is a relationship property, 1–5). */
export interface KnownSkill {
  skill: Skill;
  level: number;
  since?: number | null;
}

/** PersonResponse + PLANNED graph projections used by profile / people screens. */
export interface Person {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  position: string | null;
  role: Role;
  active: boolean;
  /** MEMBER_OF names, flattened for a list row. */
  teams?: string[];
  /** The strongest few KNOWS, level first. */
  topSkills?: TopSkill[];
  team?: string; // PLANNED — single-team shorthand, mock fixtures only
  knows?: KnownSkill[]; // PLANNED
  wantsToLearn?: Skill[]; // PLANNED
  projects?: PersonProject[]; // PLANNED
  mentorships?: Mentorship[]; // PLANNED
  mentorsCount?: number; // PLANNED
}

/**
 * GET /people/{id} — `PersonProfileResponse`: the person fields above plus the E4.2 aggregate.
 * Every list is present, empty rather than absent, so the template needs no null guards.
 */
export interface PersonProfile extends Person {
  teams: string[];
  skills: ProfileSkill[];
  wishes: ProfileWish[];
  projects: PersonProject[];
  mentoring: Mentoring;
  neighbourhood: Neighbourhood;
}

/** A KNOWS edge on the profile: `level` and `since` belong to the relationship. */
export interface ProfileSkill {
  skillId: string;
  name: string;
  category: SkillCategory;
  color: string;
  level: number;
  since: string | null;
}

export interface ProfileWish {
  skillId: string;
  name: string;
  category: SkillCategory;
  color: string;
}

/** One MENTORS edge; `skill` is null if the Skill node behind it is gone. */
export interface ProfileMentorship {
  personId: string;
  name: string;
  skill: string | null;
  since: string | null;
}

export interface Mentoring {
  mentees: ProfileMentorship[];
  mentors: ProfileMentorship[];
}

export interface Neighbourhood {
  nodes: GraphNode[];
  edges: GraphEdge[];
  /** True when the server hit its relationship cap: the picture is a sample, not the whole story. */
  truncated: boolean;
}

/** WORKED_ON projected onto a person. */
export interface PersonProject {
  projectId: string;
  name: string;
  role: string;
  from?: string | null;
  to?: string | null;
  active: boolean;
  uses: string[];
}

/** MENTORS edge, either direction. */
export interface Mentorship {
  personName: string;
  skill: string;
  since?: number | null;
  direction: 'MENTORED_BY' | 'MENTORS';
}

/** ProjectResponse + PLANNED members. */
export interface Project {
  id: string;
  name: string;
  description: string | null;
  startDate: string | null;
  endDate: string | null;
  active: boolean;
  skills: Skill[];
  members?: ProjectMember[]; // PLANNED
}

export interface ProjectMember {
  personId: string;
  name: string;
  role: string;
  from?: string | null;
  to?: string | null;
  left: boolean;
}

/** PLANNED — Team feature slice. */
export interface Team {
  id: string;
  name: string;
  memberCount: number;
}

/** ExpertResponse — one ranked person from `GET /api/v1/experts?skills=a,b`. */
export interface Expert {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  position: string | null;
  teams: string[];
  score: number;
  matchedSkills: MatchedSkill[];
}

/** SkillCoverageResponse — `GET /api/v1/experts/coverage`, the bus-factor readout. */
export interface SkillCoverage {
  skill: string;
  /** Non-deleted people with a KNOWS edge, at any level. */
  knownBy: number;
  /** Only the people at the go-to level; exactly one name is a bus factor of 1. */
  experts: string[];
}

export interface MatchedSkill {
  name: string;
  level: number;
}

/** The subset of a person the finder rows render. Structurally a `Person` minus role/active. */
export interface ExpertPerson {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  position: string | null;
  team?: string; // PLANNED
  mentorsCount?: number; // PLANNED
  projects?: PersonProject[]; // PLANNED
}

/** A finder row. `full` is always true today — the API does strict AND, so there are no partials. */
export interface FinderMatch {
  person: ExpertPerson;
  matched: MatchedSkill[];
  score: number;
  full: boolean;
}

export interface FinderResult {
  parsed: string;
  totalMatches: number;
  matches: FinderMatch[];
  partial: FinderMatch[]; // PLANNED — no endpoint returns partial matches yet
}

/** GET /graph — a capped subgraph, shared with the profile neighbourhood (E4.2/E5.1). */
export type GraphNodeKind = 'PERSON' | 'SKILL' | 'PROJECT' | 'TEAM';

export type GraphEdgeType =
  | 'KNOWS'
  | 'WANTS_TO_LEARN'
  | 'WORKED_ON'
  | 'MEMBER_OF'
  | 'MENTORS'
  | 'USES';

/** No coordinates: the server does not know the viewport, so layout is d3-force's job here. */
export interface GraphNode {
  id: string;
  kind: GraphNodeKind;
  label: string;
  meta: string | null;
}

export interface GraphEdge {
  source: string;
  target: string;
  type: GraphEdgeType;
}

export interface GraphData {
  nodes: GraphNode[];
  edges: GraphEdge[];
  /** Relationships found before the cap — with `edges.length`, this is "showing 150 of 1204". */
  totalRelations: number;
  /** Company-wide counts per kind, unfiltered: the legend describes the map, not the view. */
  totals: Record<GraphNodeKind, number>;
  truncated: boolean;
}

/** GET /people/{id}/skills — a person's self-declared knowledge (My Skills feature). */
export interface MyKnownSkill {
  skillId: string;
  name: string;
  level: number;
}

export interface MyWish {
  skillId: string;
  name: string;
}

export interface MySkills {
  skills: MyKnownSkill[];
  wishes: MyWish[];
}

/** PLANNED — a ranked mentor candidate for a mentee + skill (2g). */
export interface MentorCandidate {
  person: Person;
  skill: string;
  level: number;
  activeMentorships: number;
  score: number;
}

/** PLANNED — admin skill-gap dashboard (2d). */
export interface StatTile {
  label: string;
  value: string;
  hint: string;
  hintAccent?: boolean;
}

export interface SkillGapRow {
  team: string;
  skill: string;
  projects: string[];
  knows: number;
}

export interface BusFactorEntry {
  skill: string;
  person: string;
}

export interface MappingQueue {
  total: number;
  names: string[];
}

export interface DashboardData {
  stats: StatTile[];
  skillGap: SkillGapRow[];
  busFactor: BusFactorEntry[];
  mappingQueue: MappingQueue;
}
