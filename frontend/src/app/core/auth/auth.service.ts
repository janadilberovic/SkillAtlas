import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AuthApi } from '../api/api';
import { Role } from '../models/models';
import { PEOPLE } from '../mock/mock-data';

interface SessionUser {
  id: string;
  fullName: string;
  initials: string;
  role: Role;
  email: string;
}

const STORAGE_KEY = 'skillatlas.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApi);

  private readonly _user = signal<SessionUser | null>(restore());
  private readonly _token = signal<string | null>(localStorage.getItem(STORAGE_KEY + '.token'));

  readonly user = this._user.asReadonly();
  readonly token = this._token.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);
  readonly isAdmin = computed(() => this._user()?.role === 'ADMIN');

  login(email: string, password: string): Observable<unknown> {
    return this.api.login(email, password).pipe(
      tap((res) => {
        // While mocked we resolve the person locally; the real flow would call GET /me.
        const p = PEOPLE.find((x) => x.email.toLowerCase() === email.trim().toLowerCase());
        const user: SessionUser = {
          id: p?.id ?? 'unknown',
          fullName: p ? `${p.firstName} ${p.lastName}` : email,
          initials: p ? (p.firstName[0] + p.lastName[0]).toUpperCase() : email.slice(0, 2).toUpperCase(),
          role: res.role,
          email,
        };
        this._user.set(user);
        this._token.set(res.token);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
        localStorage.setItem(STORAGE_KEY + '.token', res.token);
      }),
    );
  }

  logout(): void {
    this._user.set(null);
    this._token.set(null);
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(STORAGE_KEY + '.token');
  }
}

function restore(): SessionUser | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as SessionUser;
  } catch {
    return null;
  }
}
