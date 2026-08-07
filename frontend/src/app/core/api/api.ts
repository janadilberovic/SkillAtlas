import { Observable } from 'rxjs';
import {
  FinderResult,
  GraphData,
  LoginResponse,
  Me,
  Page,
  Person,
  Project,
  Skill,
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

export abstract class SkillApi {
  abstract list(): Observable<Skill[]>;
}

export abstract class ProjectApi {
  abstract list(): Observable<Project[]>;
  abstract get(id: string): Observable<Project>;
}

export abstract class TeamApi {
  abstract list(): Observable<Team[]>;
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
