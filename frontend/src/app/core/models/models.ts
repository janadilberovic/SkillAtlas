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
  team?: string; // PLANNED
  topSkills?: KnownSkill[]; // PLANNED
  knows?: KnownSkill[]; // PLANNED
  wantsToLearn?: Skill[]; // PLANNED
  projects?: PersonProject[]; // PLANNED
  mentorships?: Mentorship[]; // PLANNED
  mentorsCount?: number; // PLANNED
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
  score: number;
  matchedSkills: MatchedSkill[];
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

/** PLANNED — GET /graph subgraph (server caps size; whole graph never sent). */
export type GraphNodeKind = 'PERSON' | 'SKILL' | 'PROJECT' | 'TEAM';

export interface GraphNode {
  id: string;
  kind: GraphNodeKind;
  label: string;
  meta: string;
  path: string;
  x: number;
  y: number;
  r: number;
  edges: string[];
}

export interface GraphEdge {
  source: string;
  target: string;
  type: 'KNOWS' | 'WORKED_ON' | 'USES' | 'MENTORS';
}

export interface GraphData {
  nodes: GraphNode[];
  edges: GraphEdge[];
  totalNodes: number;
  totalRelations: number;
  rootLabel: string;
  hops: number;
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
