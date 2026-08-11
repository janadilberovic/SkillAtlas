import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, of, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Expert,
  FinderResult,
  LoginResponse,
  Me,
  MySkills,
  Page,
  Person,
  Project,
  Skill,
  Team,
} from '../models/models';
import {
  AuthApi,
  FinderApi,
  MemberInput,
  PeopleApi,
  PeopleQuery,
  PeopleSkillsApi,
  ProjectApi,
  SkillApi,
  SkillInput,
  TeamApi,
} from './api';
import { parseSkillQuery } from './finder-query';

const BASE = environment.apiBaseUrl;

// Real HTTP implementations of the API seam. Each returns the same model shape the mocks did,
// so components are untouched. Fields the backend doesn't expose yet (models.ts `// PLANNED`)
// simply stay undefined.

@Injectable()
export class HttpAuthApi extends AuthApi {
  private readonly http = inject(HttpClient);
  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${BASE}/auth/login`, { email, password });
  }
  me(): Observable<Me> {
    return this.http.get<Me>(`${BASE}/me`);
  }
}

@Injectable()
export class HttpPeopleApi extends PeopleApi {
  private readonly http = inject(HttpClient);
  list(query: PeopleQuery): Observable<Page<Person>> {
    let params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? 20));
    // NOTE: server-side search/team filtering is planned ([8]); ignored for now.
    if (query.search) params = params.set('search', query.search);
    return this.http.get<Page<Person>>(`${BASE}/people`, { params });
  }
  get(id: string): Observable<Person> {
    return this.http.get<Person>(`${BASE}/people/${id}`);
  }
}

@Injectable()
export class HttpSkillApi extends SkillApi {
  private readonly http = inject(HttpClient);
  list(): Observable<Skill[]> {
    return this.http
      .get<Page<Skill>>(`${BASE}/skills`, { params: new HttpParams().set('size', '100') })
      .pipe(map((page) => page.content));
  }
  create(input: SkillInput): Observable<Skill> {
    return this.http.post<Skill>(`${BASE}/skills`, input);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/skills/${id}`);
  }
}

@Injectable()
export class HttpProjectApi extends ProjectApi {
  private readonly http = inject(HttpClient);
  list(): Observable<Project[]> {
    return this.http
      .get<Page<Project>>(`${BASE}/projects`, { params: new HttpParams().set('size', '100') })
      .pipe(map((page) => page.content));
  }
  get(id: string): Observable<Project> {
    return this.http.get<Project>(`${BASE}/projects/${id}`);
  }
  assignMember(projectId: string, personId: string, input: MemberInput): Observable<void> {
    return this.http.post<void>(`${BASE}/projects/${projectId}/members/${personId}`, input);
  }
  removeMember(projectId: string, personId: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/projects/${projectId}/members/${personId}`);
  }
  // Backend PUT is a full replacement, so read the project first, then write it back toggled.
  setActive(projectId: string, active: boolean): Observable<Project> {
    return this.get(projectId).pipe(
      switchMap((p) =>
        this.http.put<Project>(`${BASE}/projects/${projectId}`, {
          name: p.name,
          description: p.description,
          startDate: p.startDate,
          endDate: p.endDate,
          active,
          skillIds: (p.skills ?? []).map((s) => s.id),
        }),
      ),
    );
  }
}

@Injectable()
export class HttpTeamApi extends TeamApi {
  private readonly http = inject(HttpClient);
  list(): Observable<Team[]> {
    return this.http
      .get<Page<Team>>(`${BASE}/teams`, { params: new HttpParams().set('size', '100') })
      .pipe(map((page) => page.content.map((t) => ({ ...t, memberCount: t.memberCount ?? 0 }))));
  }
}

@Injectable()
export class HttpPeopleSkillsApi extends PeopleSkillsApi {
  private readonly http = inject(HttpClient);
  mine(personId: string): Observable<MySkills> {
    return this.http.get<MySkills>(`${BASE}/people/${personId}/skills`);
  }
  setSkill(personId: string, skillId: string, level: number): Observable<MySkills> {
    return this.http.put<MySkills>(`${BASE}/people/${personId}/skills/${skillId}`, { level });
  }
  removeSkill(personId: string, skillId: string): Observable<MySkills> {
    return this.http.delete<MySkills>(`${BASE}/people/${personId}/skills/${skillId}`);
  }
  addWish(personId: string, skillId: string): Observable<MySkills> {
    return this.http.put<MySkills>(`${BASE}/people/${personId}/wishes/${skillId}`, {});
  }
  removeWish(personId: string, skillId: string): Observable<MySkills> {
    return this.http.delete<MySkills>(`${BASE}/people/${personId}/wishes/${skillId}`);
  }
}

@Injectable()
export class HttpFinderApi extends FinderApi {
  private readonly http = inject(HttpClient);

  search(query: string, team?: string): Observable<FinderResult> {
    const skills = parseSkillQuery(query);
    // No skill in the box is an empty result, not a 400 round-trip.
    if (!skills.length) {
      return of(emptyResult('no skills recognised'));
    }
    let params = new HttpParams().set('skills', skills.join(',')).set('size', '50');
    if (team) params = params.set('team', team);

    return this.http.get<Page<Expert>>(`${BASE}/experts`, { params }).pipe(
      map((page) => ({
        parsed: `KNOWS ${skills.join(' AND KNOWS ')}`,
        totalMatches: page.totalElements,
        matches: page.content.map((e) => ({
          person: {
            id: e.id,
            email: e.email,
            firstName: e.firstName,
            lastName: e.lastName,
            position: e.position,
          },
          matched: e.matchedSkills,
          score: e.score,
          full: true,
        })),
        partial: [],
      })),
    );
  }
}

function emptyResult(parsed: string): FinderResult {
  return { parsed, totalMatches: 0, matches: [], partial: [] };
}
