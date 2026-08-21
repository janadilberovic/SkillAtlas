import { Observable } from 'rxjs';
import {
  DashboardData,
  FinderResult,
  GraphData,
  GraphNodeKind,
  LearningPath,
  LoginResponse,
  Me,
  MentorCandidates,
  MentorRequestRow,
  MySkills,
  Page,
  Person,
  PersonProfile,
  Project,
  Role,
  Skill,
  SkillCategory,
  SkillCoverage,
  SkillGapRow,
  Team,
} from '../models/models';
import { SkillTerm } from './finder-query';

/**
 * Abstract API surface. Components inject these tokens and never know whether the
 * data came from a mock or a real HTTP call. Today `app.config.ts` binds each token
 * to a `Mock*` implementation; swapping in `Http*` implementations later is a one-line
 * provider change per token — no component touched.
 */

export abstract class AuthApi {
  abstract login(email: string, password: string): Observable<LoginResponse>;
  abstract me(): Observable<Me>;
}

export interface PeopleQuery {
  search?: string;
  team?: string;
  skill?: string;
  page?: number;
  size?: number;
}

/** Admin create (E2.1). No team here — membership is assigned after the person exists. */
export interface PersonInput {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  position?: string;
  role: Role;
}

export abstract class PeopleApi {
  abstract list(query: PeopleQuery): Observable<Page<Person>>;
  /** The rich profile (E4.2) — the person plus skills, projects, mentoring and their neighbourhood. */
  abstract profile(id: string): Observable<PersonProfile>;
  abstract create(input: PersonInput): Observable<Person>;
  /** Soft delete — the person keeps their relations and drops out of every read. */
  abstract remove(id: string): Observable<void>;
}

export interface SkillInput {
  name: string;
  category: SkillCategory;
  color: string;
}

export interface SkillQuery {
  search?: string;
  category?: SkillCategory | '';
  /** `wanted` ranks by wishes, `known` by thinnest coverage; anything else is alphabetical. */
  sort?: 'name' | 'wanted' | 'known';
  page?: number;
  size?: number;
}

export abstract class SkillApi {
  /** The flat catalog every skill picker needs — first page, large size. */
  abstract list(): Observable<Skill[]>;
  /** The paged catalog with its counts, for the skills screen. */
  abstract page(query: SkillQuery): Observable<Page<Skill>>;
  abstract create(input: SkillInput): Observable<Skill>;
  abstract update(id: string, input: SkillInput): Observable<Skill>;
  abstract remove(id: string): Observable<void>;
}

export interface MemberInput {
  role: string;
  from?: string | null;
  to?: string | null;
}

/** Admin create (E2.4). `skillIds` are the USES edges; omitting `active` creates an active project. */
export interface ProjectInput {
  name: string;
  description?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  skillIds: string[];
  active?: boolean;
}

export interface ProjectQuery {
  search?: string;
  page?: number;
  size?: number;
}

export abstract class ProjectApi {
  abstract page(query: ProjectQuery): Observable<Page<Project>>;
  /** The detail: the same project plus its roster. */
  abstract get(id: string): Observable<Project>;
  abstract create(input: ProjectInput): Observable<Project>;
  /** Replaces the USES edges wholesale — the backend PUT has no partial form. */
  abstract setSkills(projectId: string, skillIds: string[]): Observable<Project>;
  abstract assignMember(projectId: string, personId: string, input: MemberInput): Observable<void>;
  abstract removeMember(projectId: string, personId: string): Observable<void>;
  abstract setActive(projectId: string, active: boolean): Observable<Project>;
}

export abstract class TeamApi {
  abstract list(): Observable<Team[]>;
  /** MEMBER_OF, and the server MERGEs it — adding someone who is already there is a no-op, not a duplicate. */
  abstract addMember(teamId: string, personId: string): Observable<void>;
}

/** My Skills — a person manages their own KNOWS (level 1–5) and WANTS_TO_LEARN. Owner-only writes. */
export abstract class PeopleSkillsApi {
  abstract mine(personId: string): Observable<MySkills>;
  abstract setSkill(personId: string, skillId: string, level: number): Observable<MySkills>;
  abstract removeSkill(personId: string, skillId: string): Observable<MySkills>;
  abstract addWish(personId: string, skillId: string): Observable<MySkills>;
  abstract removeWish(personId: string, skillId: string): Observable<MySkills>;
}

export abstract class FinderApi {
  abstract search(terms: SkillTerm[], team?: string): Observable<FinderResult>;
  /** Company-wide coverage of the same skills, for the bus-factor card. Team filter does not apply. */
  abstract coverage(terms: SkillTerm[]): Observable<SkillCoverage[]>;
}

export interface GraphQuery {
  /** Focus the subgraph on one person — what the profile's "In graph" jump passes. */
  rootId?: string;
  /** Only meaningful with `rootId`: 1 is the person's own edges, 2 adds their colleagues. */
  hops?: number;
  limit?: number;
  team?: string;
  types?: GraphNodeKind[];
}

export abstract class GraphApi {
  abstract explore(query: GraphQuery): Observable<GraphData>;
}

/** E6.1 + E6.2. Both reads take a skill *name*; the write takes the id the read resolved. */
export abstract class MentoringApi {
  abstract candidates(personId: string, skill: string): Observable<MentorCandidates>;
  abstract confirm(mentorId: string, menteeId: string, skillId: string): Observable<void>;
  abstract learningPath(personId: string, skill: string): Observable<LearningPath>;
}

export abstract class DashboardApi {
  abstract overview(): Observable<DashboardData>;
  /** The two long tables page on their own, so walking one does not re-run the other widgets. */
  abstract skillGap(page: number, size: number): Observable<Page<SkillGapRow>>;
  abstract mentorRequests(page: number, size: number): Observable<Page<MentorRequestRow>>;
}
