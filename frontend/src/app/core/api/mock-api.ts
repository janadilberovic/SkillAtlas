import { Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import {
  FinderMatch,
  FinderResult,
  GraphData,
  KnownSkill,
  LoginResponse,
  Me,
  Page,
  Person,
  Project,
  Skill,
  Team,
} from '../models/models';
import { AuthApi, FinderApi, GraphApi, GraphQuery, PeopleApi, PeopleQuery, ProjectApi, SkillApi, TeamApi } from './api';
import { GRAPH, MOCK_CREDENTIALS, PEOPLE, PROJECTS, SKILLS, TEAMS, person } from '../mock/mock-data';

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

  get(id: string): Observable<Person> {
    const p = person(id);
    if (!p) return throwError(() => new Error('Person not found')).pipe(delay(LATENCY));
    return respond(p);
  }
}

@Injectable()
export class MockSkillApi extends SkillApi {
  list(): Observable<Skill[]> {
    return respond(SKILLS);
  }
}

@Injectable()
export class MockProjectApi extends ProjectApi {
  list(): Observable<Project[]> {
    return respond(PROJECTS);
  }
  get(id: string): Observable<Project> {
    const pr = PROJECTS.find((x) => x.id === id);
    if (!pr) return throwError(() => new Error('Project not found')).pipe(delay(LATENCY));
    return respond(pr);
  }
}

@Injectable()
export class MockTeamApi extends TeamApi {
  list(): Observable<Team[]> {
    return respond(TEAMS);
  }
}

@Injectable()
export class MockFinderApi extends FinderApi {
  search(query: string, team?: string): Observable<FinderResult> {
    const terms = parseSkillTerms(query);
    const minLevel = terms.length ? Math.max(...terms.map((t) => t.level)) : 1;
    const wantedNames = terms.map((t) => t.name);

    let pool = PEOPLE.filter((p) => p.active);
    if (team && team !== 'All teams') pool = pool.filter((p) => p.team === team);

    const matches: FinderMatch[] = [];
    const partial: FinderMatch[] = [];

    for (const p of pool) {
      const known = p.knows ?? [];
      const hits: KnownSkill[] = [];
      for (const name of wantedNames) {
        const k = known.find((x) => x.skill.name.toLowerCase() === name.toLowerCase() && x.level >= minLevel);
        if (k) hits.push(k);
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

    const parsed = wantedNames.length
      ? `KNOWS ${wantedNames.join(' AND KNOWS ')}, level ≥ ${minLevel}`
      : 'no skills recognised';

    return respond({
      parsed,
      totalActive: pool.length,
      matches,
      partial,
    });
  }
}

@Injectable()
export class MockGraphApi extends GraphApi {
  explore(_query: GraphQuery): Observable<GraphData> {
    return respond(GRAPH);
  }
}

// Parses free text like "React + Neo4j > 3" / "React and Neo4j level ≥ 3" into skill terms.
function parseSkillTerms(query: string): { name: string; level: number }[] {
  const q = query.trim();
  if (!q) return [];
  const levelMatch = q.match(/(?:>=|≥|>|level\s*)\s*(\d)/i);
  const level = levelMatch ? Math.min(5, Math.max(1, Number(levelMatch[1]))) : 3;
  const found: { name: string; level: number }[] = [];
  for (const s of SKILLS) {
    const re = new RegExp(`\\b${s.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'i');
    if (re.test(q) && !found.some((f) => f.name === s.name)) {
      found.push({ name: s.name, level });
    }
  }
  return found;
}
