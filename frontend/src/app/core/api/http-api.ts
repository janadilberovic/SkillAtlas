import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, of, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DashboardData,
  Expert,
  FinderResult,
  GraphData,
  LearningPath,
  LoginResponse,
  MentorCandidates,
  MentorRequestRow,
  Me,
  MySkills,
  Page,
  Person,
  PersonProfile,
  Project,
  Skill,
  SkillCoverage,
  SkillGapRow,
  Team,
} from '../models/models';
import {
  AuthApi,
  DashboardApi,
  FinderApi,
  GraphApi,
  GraphQuery,
  MemberInput,
  MentoringApi,
  PeopleApi,
  PeopleQuery,
  PeopleSkillsApi,
  PersonInput,
  ProjectApi,
  ProjectInput,
  ProjectQuery,
  SkillApi,
  SkillInput,
  SkillQuery,
  TeamApi,
} from './api';
import { SkillTerm, formatSkillTerm, toSkillParam } from './finder-query';

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
    if (query.search) params = params.set('search', query.search);
    if (query.team) params = params.set('team', query.team);
    if (query.skill) params = params.set('skill', query.skill);
    return this.http.get<Page<Person>>(`${BASE}/people`, { params });
  }
  profile(id: string): Observable<PersonProfile> {
    return this.http.get<PersonProfile>(`${BASE}/people/${id}`);
  }
  create(input: PersonInput): Observable<Person> {
    return this.http.post<Person>(`${BASE}/people`, input);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/people/${id}`);
  }
}

@Injectable()
export class HttpSkillApi extends SkillApi {
  private readonly http = inject(HttpClient);
  list(): Observable<Skill[]> {
    return this.page({ size: 100 }).pipe(map((page) => page.content));
  }
  page(query: SkillQuery): Observable<Page<Skill>> {
    let params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? 20));
    if (query.search) params = params.set('search', query.search);
    if (query.category) params = params.set('category', query.category);
    if (query.sort) params = params.set('sort', query.sort);
    return this.http.get<Page<Skill>>(`${BASE}/skills`, { params });
  }
  create(input: SkillInput): Observable<Skill> {
    return this.http.post<Skill>(`${BASE}/skills`, input);
  }
  update(id: string, input: SkillInput): Observable<Skill> {
    return this.http.put<Skill>(`${BASE}/skills/${id}`, input);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/skills/${id}`);
  }
}

@Injectable()
export class HttpProjectApi extends ProjectApi {
  private readonly http = inject(HttpClient);
  page(query: ProjectQuery): Observable<Page<Project>> {
    let params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? 12));
    if (query.search) params = params.set('search', query.search);
    return this.http.get<Page<Project>>(`${BASE}/projects`, { params });
  }
  get(id: string): Observable<Project> {
    return this.http.get<Project>(`${BASE}/projects/${id}`);
  }
  create(input: ProjectInput): Observable<Project> {
    return this.http.post<Project>(`${BASE}/projects`, input);
  }
  setSkills(projectId: string, skillIds: string[]): Observable<Project> {
    return this.replace(projectId, { skillIds });
  }
  setActive(projectId: string, active: boolean): Observable<Project> {
    return this.replace(projectId, { active });
  }
  assignMember(projectId: string, personId: string, input: MemberInput): Observable<void> {
    return this.http.post<void>(`${BASE}/projects/${projectId}/members/${personId}`, input);
  }
  removeMember(projectId: string, personId: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/projects/${projectId}/members/${personId}`);
  }

  // Backend PUT is a full replacement — fields left out are cleared — so read the project first
  // and write it back with one field changed. The answer carries the roster, not just the project.
  private replace(projectId: string, change: Partial<ProjectInput>): Observable<Project> {
    return this.get(projectId).pipe(
      switchMap((p) =>
        this.http.put<Project>(`${BASE}/projects/${projectId}`, {
          name: p.name,
          description: p.description,
          startDate: p.startDate,
          endDate: p.endDate,
          active: p.active,
          skillIds: (p.skills ?? []).map((s) => s.id),
          ...change,
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
  addMember(teamId: string, personId: string): Observable<void> {
    return this.http.post<void>(`${BASE}/teams/${teamId}/members/${personId}`, {});
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

  search(terms: SkillTerm[], team?: string): Observable<FinderResult> {
    // No skill in the box is an empty result, not a 400 round-trip.
    if (!terms.length) {
      return of(emptyResult('no skills recognised'));
    }
    let params = new HttpParams().set('skills', terms.map(toSkillParam).join(',')).set('size', '50');
    if (team) params = params.set('team', team);

    return this.http.get<Page<Expert>>(`${BASE}/experts`, { params }).pipe(
      map((page) => ({
        parsed: terms.map((t) => `KNOWS ${formatSkillTerm(t)}`).join(' AND '),
        totalMatches: page.totalElements,
        matches: page.content.map((e) => ({
          person: {
            id: e.id,
            email: e.email,
            firstName: e.firstName,
            lastName: e.lastName,
            position: e.position,
            team: (e.teams ?? []).join(' / ') || undefined,
          },
          matched: e.matchedSkills,
          score: e.score,
          full: true,
        })),
        partial: [],
      })),
    );
  }

  coverage(terms: SkillTerm[]): Observable<SkillCoverage[]> {
    if (!terms.length) {
      return of([]);
    }
    // Names only: coverage is about the company's depth on a skill, not about the level the
    // search happened to ask for.
    const params = new HttpParams().set('skills', terms.map((t) => t.name).join(','));
    return this.http.get<SkillCoverage[]>(`${BASE}/experts/coverage`, { params });
  }
}

@Injectable()
export class HttpGraphApi extends GraphApi {
  private readonly http = inject(HttpClient);

  explore(query: GraphQuery): Observable<GraphData> {
    let params = new HttpParams();
    if (query.limit) params = params.set('limit', String(query.limit));
    if (query.team) params = params.set('team', query.team);
    if (query.rootId) params = params.set('rootId', query.rootId);
    if (query.rootId && query.hops) params = params.set('hops', String(query.hops));
    // All four kinds is the server's default, so sending them just makes the URL longer.
    if (query.types?.length && query.types.length < 4) {
      params = params.set('types', query.types.join(','));
    }
    return this.http.get<GraphData>(`${BASE}/graph`, { params });
  }
}

@Injectable()
export class HttpMentoringApi extends MentoringApi {
  private readonly http = inject(HttpClient);

  candidates(personId: string, skill: string): Observable<MentorCandidates> {
    const params = new HttpParams().set('skill', skill);
    return this.http.get<MentorCandidates>(`${BASE}/people/${personId}/mentor-candidates`, { params });
  }

  confirm(mentorId: string, menteeId: string, skillId: string): Observable<void> {
    // The response carries the created mentorship; the screen only needs to know it landed.
    return this.http
      .post(`${BASE}/mentorships`, { mentorId, menteeId, skillId })
      .pipe(map(() => undefined));
  }

  learningPath(personId: string, skill: string): Observable<LearningPath> {
    const params = new HttpParams().set('skill', skill);
    return this.http.get<LearningPath>(`${BASE}/people/${personId}/learning-path`, { params });
  }
}

@Injectable()
export class HttpDashboardApi extends DashboardApi {
  private readonly http = inject(HttpClient);
  overview(): Observable<DashboardData> {
    return this.http.get<DashboardData>(`${BASE}/dashboard`);
  }
  skillGap(page: number, size: number): Observable<Page<SkillGapRow>> {
    return this.http.get<Page<SkillGapRow>>(`${BASE}/dashboard/skill-gap`, { params: paging(page, size) });
  }
  mentorRequests(page: number, size: number): Observable<Page<MentorRequestRow>> {
    return this.http.get<Page<MentorRequestRow>>(`${BASE}/dashboard/mentor-requests`, {
      params: paging(page, size),
    });
  }
}

function paging(page: number, size: number): HttpParams {
  return new HttpParams().set('page', String(page)).set('size', String(size));
}

function emptyResult(parsed: string): FinderResult {
  return { parsed, totalMatches: 0, matches: [], partial: [] };
}
