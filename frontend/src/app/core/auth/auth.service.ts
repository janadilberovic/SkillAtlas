import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, switchMap, tap } from 'rxjs';
import { AuthApi } from '../api/api';
import { Me, Role } from '../models/models';

interface SessionUser {
  id: string;
  fullName: string;
  initials: string;
  role: Role;
  email: string;
  // Persisted with the rest of the session even though the signature expires: after that
  // sa-avatar falls back to the initials, which is the same thing it would show anyway.
  avatarUrl?: string | null;
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

  login(email: string, password: string): Observable<Me> {
    return this.api.login(email, password).pipe(
      // Store the token first so the interceptor can attach it to GET /me.
      tap((res) => {
        this._token.set(res.token);
        localStorage.setItem(STORAGE_KEY + '.token', res.token);
      }),
      switchMap(() => this.api.me()),
      tap((me) => {
        const user: SessionUser = {
          id: me.id,
          fullName: me.fullName,
          initials: initialsFrom(me.fullName, me.email),
          role: me.role,
          email: me.email,
          avatarUrl: me.avatarUrl ?? null,
        };
        this._user.set(user);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
      }),
    );
  }

  /** Keeps the topbar and the stored session in step after the owner changes their picture. */
  setAvatarUrl(avatarUrl: string | null): void {
    const user = this._user();
    if (!user) return;
    const next = { ...user, avatarUrl };
    this._user.set(next);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }

  /**
   * The session survives on purpose: the token carries only the person and the role, so it does
   * not go stale when the hash behind it does.
   */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.api.changePassword(currentPassword, newPassword);
  }

  logout(): void {
    this._user.set(null);
    this._token.set(null);
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(STORAGE_KEY + '.token');
  }
}

function initialsFrom(fullName: string, email: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  if (parts.length === 1 && parts[0].length >= 2) return parts[0].slice(0, 2).toUpperCase();
  return email.slice(0, 2).toUpperCase();
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
