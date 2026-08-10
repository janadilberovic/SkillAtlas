import { Observable } from 'rxjs';
import {
  DashboardData,
  FinderResult,
  GraphData,
  LoginResponse,
  Me,
  MentorCandidate,
  MySkills,
  Page,
  Person,
  Project,
  Skill,
  SkillCategory,
  Team,
} from '../models/models';

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
  abstract get(id: string): Observable<Person>;
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
  abstract search(query: string, team?: string): Observable<FinderResult>;
}

export interface GraphQuery {
  rootId?: string;
  hops?: number;
  limit?: number;
  team?: string;
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
