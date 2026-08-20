import { Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import {
  FinderMatch,
  FinderResult,
  LoginResponse,
  MatchedSkill,
  Me,
  Page,
  Person,
  PersonProfile,
  Project,
  Skill,
  SkillCoverage,
  Team,
} from '../models/models';
import { SkillTerm, formatSkillTerm } from './finder-query';
import {
  AuthApi,
  FinderApi,
  MemberInput,
  PeopleApi,
  PeopleQuery,
  ProjectApi,
  ProjectInput,
  SkillApi,
  SkillInput,
  SkillQuery,
  TeamApi,
} from './api';
import {
  MOCK_CREDENTIALS,
  PEOPLE,
  PROJECTS,
  SKILLS,
  TEAMS,
  addSkillMock,
  assignMemberMock,
  person,
  removeMemberMock,
  removeSkillMock,
  setProjectActiveMock,
} from '../mock/mock-data';

// Simulated network latency so loading skeletons are exercised, not just theoretical.
const LATENCY = 320;
function respond<T>(value: T): Observable<T> {
  return of(value).pipe(delay(LATENCY));
}

@Injectable()
export class MockAuthApi extends AuthApi {
  login(email: string, password: string): Observable<LoginResponse> {
    const known = MOCK_CREDENTIALS[email.trim().toLowerCase()];
    const p = PEOPLE.find((x) => x.email.toLowerCase() === email.trim().toLowerCase());
    if (!known || known !== password || !p) {
      // Generic error — never reveals which half was wrong (matches the design copy).
      return throwError(() => new Error('Incorrect email or password.')).pipe(delay(LATENCY));
    }
    return respond({ token: `mock.${p.id}`, tokenType: 'Bearer', role: p.role });
  }

  me(): Observable<Me> {
    // Not used while mocked (AuthService derives the user from the login response), but kept
    // so the token surface matches the real GET /me contract.
    const p = PEOPLE[0];
    return respond({ id: p.id, email: p.email, fullName: `${p.firstName} ${p.lastName}`, role: p.role });
  }
}

@Injectable()
export class MockPeopleApi extends PeopleApi {
  list(query: PeopleQuery): Observable<Page<Person>> {
    const size = query.size ?? 6;
    const page = query.page ?? 0;
    const term = (query.search ?? '').trim().toLowerCase();

    let rows = PEOPLE.filter((p) => p.active);
    if (term) {
      rows = rows.filter(
        (p) =>
          `${p.firstName} ${p.lastName}`.toLowerCase().includes(term) ||
          p.email.toLowerCase().includes(term),
      );
    }
    if (query.team && query.team !== 'All teams') {
      rows = rows.filter((p) => p.team === query.team);
    }
    if (query.skill && query.skill !== 'Any skill') {
      rows = rows.filter((p) => (p.knows ?? []).some((k) => k.skill.name === query.skill));
    }

    const totalElements = rows.length;
    const start = page * size;
    const content = rows.slice(start, start + size);
    return respond({
      content,
      page,
      size,
      totalElements,
      totalPages: Math.max(1, Math.ceil(totalElements / size)),
    });
  }

  profile(id: string): Observable<PersonProfile> {
    const p = person(id);
    if (!p) return throwError(() => new Error('Person not found')).pipe(delay(LATENCY));
    // Bound to no token (see CLAUDE.md): enough to satisfy the seam, not a second source of truth.
    return respond({
      ...p,
      teams: p.team ? [p.team] : [],
      skills: (p.knows ?? []).map((k) => ({
        skillId: k.skill.id,
        name: k.skill.name,
        category: k.skill.category,
        color: k.skill.color,
        level: k.level,
        since: null,
      })),
      wishes: (p.wantsToLearn ?? []).map((s) => ({
        skillId: s.id,
        name: s.name,
        category: s.category,
        color: s.color,
      })),
      projects: p.projects ?? [],
      mentoring: { mentees: [], mentors: [] },
      neighbourhood: { nodes: [], edges: [], truncated: false },
    });
  }
}

@Injectable()
export class MockSkillApi extends SkillApi {
  list(): Observable<Skill[]> {
    return respond([...SKILLS]);
  }
  page(query: SkillQuery): Observable<Page<Skill>> {
    const size = query.size ?? 20;
    const page = query.page ?? 0;
    return respond({
      content: SKILLS.slice(page * size, page * size + size),
      page,
      size,
      totalElements: SKILLS.length,
      totalPages: Math.ceil(SKILLS.length / size),
    });
  }
  create(input: SkillInput): Observable<Skill> {
    return respond(addSkillMock(input));
  }
  update(id: string, input: SkillInput): Observable<Skill> {
    return respond({ id, ...input });
  }
  remove(id: string): Observable<void> {
    removeSkillMock(id);
    return respond(undefined);
  }
}

@Injectable()
export class MockProjectApi extends ProjectApi {
  list(): Observable<Project[]> {
    return respond([...PROJECTS]);
  }
  get(id: string): Observable<Project> {
    const pr = PROJECTS.find((x) => x.id === id);
    if (!pr) return throwError(() => new Error('Project not found')).pipe(delay(LATENCY));
    return respond(pr);
  }
  create(input: ProjectInput): Observable<Project> {
    const created: Project = {
      id: `pr-${input.name.toLowerCase().replace(/\s+/g, '-')}`,
      name: input.name,
      description: input.description ?? null,
      startDate: input.startDate ?? null,
      endDate: input.endDate ?? null,
      active: input.active ?? true,
      skills: SKILLS.filter((s) => input.skillIds.includes(s.id)),
      members: [],
    };
    PROJECTS.unshift(created);
    return respond(created);
  }
  assignMember(projectId: string, personId: string, input: MemberInput): Observable<void> {
    assignMemberMock(projectId, personId, input);
    return respond(undefined);
  }
  removeMember(projectId: string, personId: string): Observable<void> {
    removeMemberMock(projectId, personId);
    return respond(undefined);
  }
  setActive(projectId: string, active: boolean): Observable<Project> {
    const pr = setProjectActiveMock(projectId, active);
    if (!pr) return throwError(() => new Error('Project not found')).pipe(delay(LATENCY));
    return respond(pr);
  }
}

@Injectable()
export class MockTeamApi extends TeamApi {
  list(): Observable<Team[]> {
    return respond(TEAMS);
  }
  addMember(): Observable<void> {
    return respond(undefined);
  }
}

@Injectable()
export class MockFinderApi extends FinderApi {
  search(terms: SkillTerm[], team?: string): Observable<FinderResult> {
    const wantedNames = terms.map((t) => t.name);
    const barFor = (name: string) =>
      terms.find((t) => t.name.toLowerCase() === name.toLowerCase())?.minLevel ?? 1;

    let pool = PEOPLE.filter((p) => p.active);
    if (team && team !== 'All teams') pool = pool.filter((p) => p.team === team);

    const matches: FinderMatch[] = [];
    const partial: FinderMatch[] = [];

    for (const p of pool) {
      const known = p.knows ?? [];
      const hits: MatchedSkill[] = [];
      for (const name of wantedNames) {
        const k = known.find(
          (x) => x.skill.name.toLowerCase() === name.toLowerCase() && x.level >= barFor(name),
        );
        if (k) hits.push({ name: k.skill.name, level: k.level });
      }
      if (!wantedNames.length) continue;
      const score = hits.reduce((sum, k) => sum + k.level, 0);
      if (hits.length === wantedNames.length) {
        matches.push({ person: p, matched: hits, score, full: true });
      } else if (hits.length > 0) {
        partial.push({ person: p, matched: hits, score, full: false });
      }
    }

    matches.sort((a, b) => b.score - a.score);
    partial.sort((a, b) => b.score - a.score);

    const parsed = terms.length
      ? terms.map((t) => `KNOWS ${formatSkillTerm(t)}`).join(' AND ')
      : 'no skills recognised';

    return respond({
      parsed,
      totalMatches: matches.length,
      matches,
      partial,
    });
  }

  coverage(terms: SkillTerm[]): Observable<SkillCoverage[]> {
    const active = PEOPLE.filter((p) => p.active);
    return respond(
      terms.map((term) => {
        const levels = active
          .map((p) => (p.knows ?? []).find((k) => k.skill.name.toLowerCase() === term.name.toLowerCase()))
          .filter((k): k is NonNullable<typeof k> => !!k);
        return {
          skill: term.name,
          knownBy: levels.length,
          experts: active
            .filter((p) =>
              (p.knows ?? []).some(
                (k) => k.skill.name.toLowerCase() === term.name.toLowerCase() && k.level >= 4,
              ),
            )
            .map((p) => `${p.firstName} ${p.lastName}`),
        };
      }),
    );
  }
}
