import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PeopleApi, SkillApi } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { KnownSkill, Person, Skill } from '../../core/models/models';
import { AvatarComponent } from '../../shared/components/avatar.component';
import { LevelBarComponent } from '../../shared/components/level-bar.component';
import { SkeletonComponent } from '../../shared/components/skeleton.component';

@Component({
  selector: 'sa-person-profile',
  standalone: true,
  imports: [FormsModule, RouterLink, AvatarComponent, LevelBarComponent, SkeletonComponent],
  template: `
    <div class="page">
      @if (loading()) {
        <div class="pad"><sa-skeleton [rows]="5" /></div>
      } @else if (person(); as p) {
        <div class="main">
          <div class="phead">
            <sa-avatar [initials]="initials(p)" [size]="64" />
            <div class="pid">
              <h1>{{ p.firstName }} {{ p.lastName }}</h1>
              <span class="muted">{{ p.team }} · {{ p.position }} · {{ p.email }}</span>
            </div>
            <div class="phead-actions">
              <a routerLink="/graph" class="btn btn-ghost">In graph</a>
              @if (auth.isAdmin() && !isOwn()) {
                <button class="btn" (click)="suggestMentor.set(!suggestMentor())">Suggest a mentor</button>
              }
            </div>
          </div>

          @if (suggestMentor()) {
            <div class="note">Mentor suggestions are part of the second-pass mentor-matching screen. (mock)</div>
          }

          <div class="divider-fade sep"></div>

          <div class="skillhead">
            <span class="col-head">Skills · level 1–5</span>
            @if (isOwn()) {
              <button class="btn btn-sm" (click)="addOpen.set(!addOpen())">Add skill</button>
            }
          </div>

          @if (addOpen()) {
            <div class="addrow">
              <select class="field" [(ngModel)]="newSkillId">
                <option value="">Select a skill…</option>
                @for (s of skills(); track s.id) {
                  <option [value]="s.id">{{ s.name }}</option>
                }
              </select>
              <input class="field lvl" type="number" min="1" max="5" [(ngModel)]="newLevel" />
              <button class="btn" (click)="addSkill()">Add</button>
            </div>
            @if (addError()) {
              <span class="err small">{{ addError() }}</span>
            }
          }

          <div class="skillgrid">
            @for (k of knows(); track k.skill.id) {
              <div class="skillcard">
                <span class="sname">{{ k.skill.name }}</span>
                <sa-level-bar [level]="k.level" [width]="110" />
                <span class="lvlnum">{{ k.level }}</span>
                <span class="dim small">{{ k.since ? 'since ' + k.since : '—' }}</span>
              </div>
            }
          </div>

          @if (p.wantsToLearn?.length) {
            <div class="wants">
              <span class="col-head">Wants to learn</span>
              @for (s of p.wantsToLearn; track s.id) {
                <span class="tag tag-outline">{{ s.name }}</span>
              }
              <span class="dim small">Level 5 skills can't be added as a wish.</span>
            </div>
          }

          @if (p.projects?.length) {
            <div class="projects">
              <span class="col-head">Projects</span>
              @for (pr of p.projects; track pr.projectId) {
                <div class="projcard">
                  <div>
                    <span class="pname">{{ pr.name }}</span>
                    <span class="muted small block">{{ pr.role }} · {{ period(pr.from, pr.to) }} · uses {{ pr.uses.join(', ') }}</span>
                  </div>
                  <span class="tag" [class.tag-mute]="!pr.active">{{ pr.active ? 'active' : 'archived' }}</span>
                </div>
              }
            </div>
          }
        </div>

        <aside class="rail">
          <div class="card">
            <span class="eyebrow">Learning path · Neo4j</span>
            <span class="rail-title">Nearest mentor through the graph</span>
            <svg viewBox="0 0 320 120" class="mini">
              <line x1="34" y1="60" x2="126" y2="60" stroke="#9184d9" stroke-width="1.5" />
              <line x1="126" y1="60" x2="218" y2="60" stroke="#9184d9" stroke-width="1.5" />
              <line x1="218" y1="60" x2="288" y2="60" stroke="#9184d9" stroke-width="1.5" />
              <circle cx="34" cy="60" r="13" fill="#9184d9" /><circle cx="126" cy="60" r="11" fill="#75798c" />
              <circle cx="218" cy="60" r="13" fill="#b5afe8" /><circle cx="288" cy="60" r="11" fill="#595d6c" />
              <text x="34" y="96" text-anchor="middle" font-size="11" fill="#9397ab">{{ p.firstName }}</text>
              <text x="126" y="96" text-anchor="middle" font-size="11" fill="#9397ab">Atlas</text>
              <text x="218" y="96" text-anchor="middle" font-size="11" fill="#9397ab">Mila</text>
              <text x="288" y="96" text-anchor="middle" font-size="11" fill="#9397ab">Neo4j</text>
            </svg>
            <p class="muted small m0">Nearest mentor on the path: Mila Radovanović — Neo4j 5, 1 active mentorship.</p>
          </div>

          @if (p.mentorships?.length) {
            <div class="card">
              <span class="eyebrow">Mentorships</span>
              @for (m of p.mentorships; track $index) {
                <div class="mentrow small">
                  <span class="muted">{{ m.direction === 'MENTORED_BY' ? 'Mentored by ' + m.personName : 'Mentors ' + m.personName }}</span>
                  <span class="dim">{{ m.skill }} · since {{ m.since }}</span>
                </div>
              }
            </div>
          }

          <div class="card-flat">
            <span class="eyebrow eyebrow-mute">Neighbourhood · 1 hop</span>
            <svg viewBox="0 0 320 150" class="mini">
              <g stroke="#595d6c" stroke-width="1">
                <line x1="160" y1="75" x2="60" y2="35" /><line x1="160" y1="75" x2="265" y2="40" />
                <line x1="160" y1="75" x2="70" y2="120" /><line x1="160" y1="75" x2="255" y2="118" />
              </g>
              <circle cx="160" cy="75" r="12" fill="#9184d9" />
              <circle cx="60" cy="35" r="8" fill="#b5afe8" /><circle cx="265" cy="40" r="8" fill="#b5afe8" />
              <circle cx="70" cy="120" r="8" fill="#75798c" /><circle cx="255" cy="118" r="8" fill="#75798c" />
            </svg>
          </div>
        </aside>
      } @else {
        <p class="dim pad">Person not found.</p>
      }
    </div>
  `,
  styles: [
    `
      .page {
        display: grid;
        grid-template-columns: 1fr 380px;
        gap: 28px;
        padding: 26px 22px;
      }
      .main {
        min-width: 0;
        display: flex;
        flex-direction: column;
      }
      .small {
        font-size: 12px;
      }
      .block {
        display: block;
      }
      .m0 {
        margin: 0;
      }
      .phead {
        display: flex;
        align-items: flex-start;
        gap: 16px;
      }
      h1 {
        font-size: 34px;
        font-weight: 500;
        letter-spacing: -0.02em;
        margin: 0;
      }
      .pid {
        display: flex;
        flex-direction: column;
        gap: 3px;
        min-width: 0;
      }
      .phead-actions {
        margin-left: auto;
        display: flex;
        gap: 8px;
      }
      .note,
      .err {
        color: var(--accent-text);
      }
      .note {
        margin-top: 12px;
        padding: 9px 12px;
        border-radius: var(--radius);
        background: var(--surface-accent);
        font-size: 13px;
      }
      .sep {
        margin: 22px 0 20px;
      }
      .skillhead {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        margin-bottom: 12px;
      }
      .addrow {
        display: flex;
        gap: 8px;
        margin-bottom: 10px;
      }
      .addrow .field {
        flex: 1;
      }
      .addrow .lvl {
        width: 70px;
        flex: none;
      }
      .err {
        display: block;
        margin: -4px 0 10px;
      }
      .skillgrid {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 10px;
      }
      .skillcard {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 11px 13px;
        border-radius: var(--radius);
        background: var(--surface);
      }
      .sname {
        font-size: 14px;
        flex: 1;
      }
      .lvlnum {
        font-size: 13px;
        width: 12px;
      }
      .wants {
        margin-top: 22px;
        display: flex;
        align-items: center;
        gap: 10px;
        flex-wrap: wrap;
      }
      .projects {
        margin-top: 24px;
        display: flex;
        flex-direction: column;
        gap: 10px;
      }
      .projcard {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 11px 13px;
        border-radius: var(--radius);
        background: var(--surface);
      }
      .pname {
        font-size: 14px;
        font-weight: 500;
      }
      .rail {
        display: flex;
        flex-direction: column;
        gap: 17px;
        min-width: 0;
      }
      .card,
      .card-flat {
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .rail-title {
        font-size: 17px;
        font-weight: 500;
        line-height: 1.2;
      }
      .mini {
        width: 100%;
      }
      .mentrow {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .pad {
        padding: 12px 0;
      }
      @media (max-width: 1040px) {
        .page {
          grid-template-columns: 1fr;
        }
        .skillgrid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class PersonProfileComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly peopleApi = inject(PeopleApi);
  private readonly skillApi = inject(SkillApi);
  readonly auth = inject(AuthService);

  readonly person = signal<Person | null>(null);
  readonly loading = signal(true);
  readonly skills = signal<Skill[]>([]);
  readonly addOpen = signal(false);
  readonly addError = signal('');
  readonly suggestMentor = signal(false);
  private readonly extraKnows = signal<KnownSkill[]>([]);

  newSkillId = '';
  newLevel = 3;

  readonly isOwn = computed(() => this.person()?.id === this.auth.user()?.id);
  readonly knows = computed(() => [...(this.person()?.knows ?? []), ...this.extraKnows()]);

  constructor() {
    this.skillApi.list().subscribe((s) => this.skills.set(s));
    this.route.paramMap.subscribe((pm) => {
      const id = pm.get('id') ?? this.auth.user()?.id ?? '';
      this.loading.set(true);
      this.extraKnows.set([]);
      this.peopleApi.get(id).subscribe({
        next: (p) => {
          this.person.set(p);
          this.loading.set(false);
        },
        error: () => {
          this.person.set(null);
          this.loading.set(false);
        },
      });
    });
  }

  initials(p: Person): string {
    return (p.firstName[0] + p.lastName[0]).toUpperCase();
  }

  period(from?: string | null, to?: string | null): string {
    const y = (d?: string | null) => (d ? new Date(d).getFullYear() : null);
    const f = y(from);
    return `${f ?? '—'} — ${to ? y(to) : 'now'}`;
  }

  addSkill(): void {
    this.addError.set('');
    const level = Number(this.newLevel);
    if (!this.newSkillId) {
      this.addError.set('Pick a skill first.');
      return;
    }
    if (!Number.isInteger(level) || level < 1 || level > 5) {
      // Client-side for the feel, server-side for the truth — the API rejects it either way.
      this.addError.set('Level must be a whole number between 1 and 5.');
      return;
    }
    const skill = this.skills().find((s) => s.id === this.newSkillId);
    if (!skill) return;
    if (this.knows().some((k) => k.skill.id === skill.id)) {
      this.addError.set('You already list that skill.');
      return;
    }
    this.extraKnows.update((list) => [...list, { skill, level, since: new Date().getFullYear() }]);
    this.newSkillId = '';
    this.newLevel = 3;
    this.addOpen.set(false);
  }
}
