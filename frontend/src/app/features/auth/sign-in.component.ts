import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'sa-sign-in',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="split">
      <section class="form">
        <span class="brand">SkillAtlas</span>
        <div>
          <h1>Sign in</h1>
          <p class="muted lead">Ko kod nas zna Neo4j — the answer is one query away.</p>
        </div>

        <form (ngSubmit)="submit()">
          <div class="group">
            <label class="label">Email</label>
            <input class="field" name="email" [(ngModel)]="email" autocomplete="username" />
          </div>
          <div class="group">
            <label class="label">Password</label>
            <input class="field" type="password" name="password" [(ngModel)]="password" autocomplete="current-password" />
          </div>

          @if (error()) {
            <div class="error">
              <span class="mark"></span>
              <span>{{ error() }}</span>
            </div>
          }

          <button class="btn" type="submit" [disabled]="loading()">
            {{ loading() ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>

        <p class="fine">
          No account? Accounts are created by an admin — ask your team lead. Passwords are hashed and never logged.
        </p>
        <p class="fine demo">
          Demo: <b>admin&#64;skillatlas.dev</b> (admin) or <b>sara.ilic&#64;firma.rs</b> (member) · <b>Password123!</b>
        </p>
      </section>

      <aside class="art">
        <svg viewBox="0 0 700 980" preserveAspectRatio="xMidYMid slice">
          <g stroke="#3f424d" stroke-width="1">
            <line x1="350" y1="490" x2="200" y2="330" />
            <line x1="350" y1="490" x2="520" y2="360" />
            <line x1="350" y1="490" x2="190" y2="660" />
            <line x1="350" y1="490" x2="530" y2="640" />
            <line x1="350" y1="490" x2="350" y2="250" />
            <line x1="350" y1="490" x2="350" y2="740" />
            <line x1="200" y1="330" x2="350" y2="250" />
            <line x1="520" y1="360" x2="350" y2="250" />
            <line x1="190" y1="660" x2="350" y2="740" />
            <line x1="530" y1="640" x2="350" y2="740" />
            <line x1="200" y1="330" x2="90" y2="450" />
            <line x1="520" y1="360" x2="620" y2="470" />
          </g>
          <circle cx="350" cy="490" r="18" fill="#9184d9" />
          <circle cx="350" cy="490" r="30" fill="none" stroke="#9184d9" opacity="0.4" />
          <circle cx="200" cy="330" r="12" fill="#b5afe8" />
          <circle cx="520" cy="360" r="12" fill="#b5afe8" />
          <circle cx="190" cy="660" r="11" fill="#75798c" />
          <circle cx="530" cy="640" r="11" fill="#75798c" />
          <circle cx="350" cy="250" r="11" fill="#9184d9" opacity="0.7" />
          <circle cx="350" cy="740" r="11" fill="#9184d9" opacity="0.7" />
          <circle cx="90" cy="450" r="9" fill="#595d6c" />
          <circle cx="620" cy="470" r="9" fill="#595d6c" />
        </svg>
      </aside>
    </div>
  `,
  styles: [
    `
      .split {
        min-height: 100vh;
        background: var(--bg-screen);
        display: grid;
        grid-template-columns: 1fr 1fr;
      }
      .form {
        display: flex;
        flex-direction: column;
        justify-content: center;
        gap: 22px;
        padding: 0 90px;
      }
      .brand {
        font-size: 17px;
        font-weight: 600;
        letter-spacing: -0.01em;
      }
      h1 {
        font-size: 38px;
        font-weight: 500;
        letter-spacing: -0.02em;
        margin: 0 0 6px;
      }
      .lead {
        font-size: 14px;
      }
      form {
        display: flex;
        flex-direction: column;
        gap: 16px;
        max-width: 380px;
      }
      .group {
        display: flex;
        flex-direction: column;
        gap: 5px;
      }
      .error {
        display: flex;
        align-items: center;
        gap: 9px;
        padding: 9px 12px;
        border-radius: var(--radius);
        background: var(--surface-accent);
        font-size: 13px;
        color: var(--accent-text);
      }
      .error .mark {
        width: 2px;
        height: 16px;
        background: var(--accent);
      }
      .fine {
        max-width: 380px;
        font-size: 12px;
        color: var(--text-dim);
        margin: 0;
      }
      .demo b {
        color: var(--text-2);
        font-weight: 500;
      }
      .art {
        position: relative;
        overflow: hidden;
        border-left: 1px solid var(--border);
        background: radial-gradient(90% 80% at 60% 45%, #1c2036 0%, #161826 72%);
      }
      .art svg {
        width: 100%;
        height: 100%;
        display: block;
      }
      @media (max-width: 900px) {
        .split {
          grid-template-columns: 1fr;
        }
        .art {
          display: none;
        }
        .form {
          padding: 0 32px;
        }
      }
    `,
  ],
})
export class SignInComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  email = 'admin@skillatlas.dev';
  password = 'Password123!';
  readonly error = signal('');
  readonly loading = signal(false);

  submit(): void {
    this.error.set('');
    this.loading.set(true);
    this.auth.login(this.email, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/finder']);
      },
      error: (e: Error) => {
        this.loading.set(false);
        this.error.set(e.message || 'Incorrect email or password.');
      },
    });
  }
}
