import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AvatarComponent } from './avatar.component';

@Component({
  selector: 'sa-app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AvatarComponent],
  template: `
    <div class="screen">
      <header class="topbar">
        <span class="brand">SkillAtlas</span>
        <nav class="nav">
          <a routerLink="/finder" routerLinkActive="active">Expert finder</a>
          <a routerLink="/graph" routerLinkActive="active">Graph</a>
          @if (auth.isAdmin()) {
            <a routerLink="/people" routerLinkActive="active">People</a>
          }
          <a routerLink="/coming-soon" [queryParams]="{ screen: 'Skills' }" routerLinkActive="active">Skills</a>
          <a routerLink="/coming-soon" [queryParams]="{ screen: 'Projects' }" routerLinkActive="active">Projects</a>
          @if (auth.isAdmin()) {
            <a routerLink="/coming-soon" [queryParams]="{ screen: 'Dashboard' }" routerLinkActive="active">Dashboard</a>
          }
          <a routerLink="/me" routerLinkActive="active">My skills</a>
        </nav>

        @if (auth.isAdmin()) {
          <button class="btn btn-sm" (click)="importVacaYay()">Import from VacaYAY</button>
        }

        <div class="user">
          <sa-avatar [initials]="auth.user()?.initials ?? ''" />
          <div class="who">
            <span class="name">{{ shortName() }}</span>
            <span class="role">{{ auth.user()?.role }}</span>
          </div>
          <button class="signout" title="Sign out" (click)="logout()">Sign out</button>
        </div>
      </header>

      @if (importNote()) {
        <div class="banner">
          <span class="mark"></span>
          <span>Import from VacaYAY is mocked in this build — no data was written.</span>
          <button class="dismiss" (click)="importNote.set(false)">Dismiss</button>
        </div>
      }

      <main class="content">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [
    `
      .screen {
        min-height: 100vh;
        background: var(--bg-screen);
        color: var(--text);
        display: flex;
        flex-direction: column;
      }
      .topbar {
        display: flex;
        align-items: center;
        gap: 28px;
        padding: 14px 22px;
        border-bottom: 1px solid var(--border);
      }
      .brand {
        font-size: 17px;
        font-weight: 600;
        letter-spacing: -0.01em;
      }
      .nav {
        display: flex;
        align-items: center;
        gap: 20px;
        font-size: 14px;
        margin-right: auto;
      }
      .nav a {
        color: var(--text-2);
      }
      .nav a.active {
        color: var(--accent-text);
      }
      .user {
        display: flex;
        align-items: center;
        gap: 9px;
      }
      .who {
        display: flex;
        flex-direction: column;
        line-height: 1.25;
      }
      .who .name {
        font-size: 13px;
      }
      .who .role {
        font-size: 10px;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--accent);
      }
      .signout {
        margin-left: 6px;
        background: transparent;
        border: none;
        color: var(--text-dim);
        font-size: 12px;
        padding: 4px 6px;
      }
      .signout:hover {
        color: var(--text-2);
      }
      .banner {
        display: flex;
        align-items: center;
        gap: 10px;
        margin: 14px 22px 0;
        padding: 10px 13px;
        border-radius: var(--radius);
        background: var(--surface-accent);
        font-size: 13px;
        color: var(--accent-text);
      }
      .banner .mark {
        width: 2px;
        height: 16px;
        background: var(--accent);
      }
      .banner .dismiss {
        margin-left: auto;
        background: transparent;
        border: none;
        color: var(--accent-text);
        font-size: 13px;
      }
      .content {
        flex: 1;
        min-height: 0;
      }
    `,
  ],
})
export class AppShellComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly importNote = signal(false);

  shortName(): string {
    const u = this.auth.user();
    if (!u) return '';
    const [first, ...rest] = u.fullName.split(' ');
    return rest.length ? `${first} ${rest[rest.length - 1][0]}.` : first;
  }

  importVacaYay(): void {
    this.importNote.set(true);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
