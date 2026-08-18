import { Observable } from 'rxjs';
import {
  DashboardData,
  FinderResult,
  GraphData,
  GraphNodeKind,
  LoginResponse,
  Me,
  MentorCandidate,
  MySkills,
  Page,
  Person,
  PersonProfile,
  Project,
  Skill,
  SkillCategory,
  SkillCoverage,
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

export abstract class PeopleApi {
  abstract list(query: PeopleQuery): Observable<Page<Person>>;
  /** The rich profile (E4.2) — the person plus skills, projects, mentoring and their neighbourhood. */
  abstract profile(id: string): Observable<PersonProfile>;
}

export interface SkillInput {
  name: string;
  category: SkillCategory;
  color: string;
}

export abstract class SkillApi {
  abstract list(): Observable<Skill[]>;
  abstract create(input: SkillInput): Observable<Skill>;
  abstract remove(id: string): Observable<void>;
}

export interface MemberInput {
  role: string;
  from?: string | null;
  to?: string | null;
}

export abstract class ProjectApi {
  abstract list(): Observable<Project[]>;
  abstract get(id: string): Observable<Project>;
  abstract assignMember(projectId: string, personId: string, input: MemberInput): Observable<void>;
  abstract removeMember(projectId: string, personId: string): Observable<void>;
  abstract setActive(projectId: string, active: boolean): Observable<Project>;
}

export abstract class TeamApi {
  abstract list(): Observable<Team[]>;
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

export abstract class MentoringApi {
  abstract candidates(personId: string, skill: string): Observable<MentorCandidate[]>;
  abstract confirm(mentorId: string, menteeId: string, skill: string): Observable<void>;
}

export abstract class DashboardApi {
  abstract overview(): Observable<DashboardData>;
}
