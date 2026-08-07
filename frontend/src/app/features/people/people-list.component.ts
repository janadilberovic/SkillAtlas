import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PeopleApi, SkillApi, TeamApi } from '../../core/api/api';
import { Page, Person, Skill, Team } from '../../core/models/models';
import { SkeletonComponent } from '../../shared/components/skeleton.component';

@Component({
  selector: 'sa-people-list',
  standalone: true,
  imports: [FormsModule, RouterLink, SkeletonComponent],
  template: `
    <div class="page">
      <div class="head">
        <div>
          <h1>People</h1>
          <p class="muted sub">{{ subtitle() }}</p>
        </div>
        <a routerLink="/coming-soon" [queryParams]="{ screen: 'New person' }" class="btn">New person</a>
      </div>

      <div class="filters">
        <input class="field" [(ngModel)]="search" (ngModelChange)="onFilter()" placeholder="Search by name or email" />
        <select class="field team" [(ngModel)]="team" (ngModelChange)="onFilter()">
          <option value="">All teams</option>
          @for (t of teams(); track t.id) {
            <option [value]="t.name">{{ t.name }}</option>
          }
        </select>
        <select class="field team" [(ngModel)]="skill" (ngModelChange)="onFilter()">
          <option value="">Any skill</option>
          @for (s of skills(); track s.id) {
            <option [value]="s.name">{{ s.name }}</option>
          }
        </select>
      </div>

      <div class="row head-row">
        <span class="col-head">Person</span>
        <span class="col-head">Team</span>
        <span class="col-head">Position</span>
        <span class="col-head">Top skills</span>
        <span class="col-head">Actions</span>
      </div>

      @if (loading()) {
        <div class="pad"><sa-skeleton [rows]="6" /></div>
      } @else if (data()) {
        @for (p of data()!.content; track p.id) {
          <div class="row data">
            <div class="person">
              <span>{{ p.firstName }} {{ p.lastName }}</span>
              <span class="dim small">{{ p.email }}</span>
            </div>
            <span class="muted">{{ p.team }}</span>
            <span class="muted">{{ p.position }}</span>
            <div class="topskills">
              @if (p.topSkills?.length) {
                @for (k of p.topSkills; track k.skill.id) {
                  <span class="tag">{{ k.skill.name }} {{ k.level }}</span>
                }
              } @else {
                <span class="imported small">No skills — imported</span>
              }
            </div>
            <div class="actions">
              <a [routerLink]="['/people', p.id]">Edit</a>
              <a class="muted" (click)="softDelete(p)">Delete</a>
            </div>
          </div>
        } @empty {
          <p class="dim pad">No people match those filters.</p>
        }

        <div class="pager">
          <span class="dim small">{{ rangeLabel() }}</span>
          <div class="pagebtns">
            <button class="pg" [disabled]="page() === 0" (click)="go(page() - 1)">Prev</button>
            @for (n of pageNumbers(); track n) {
              <button class="pg" [class.active]="n === page()" (click)="go(n)">{{ n + 1 }}</button>
            }
            <button class="pg" [disabled]="page() >= data()!.totalPages - 1" (click)="go(page() + 1)">Next</button>
          </div>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .page {
        display: flex;
        flex-direction: column;
        gap: 16px;
        padding: 26px 22px;
      }
      .head {
        display: flex;
        align-items: flex-end;
        justify-content: space-between;
        gap: 20px;
      }
      h1 {
        font-size: 38px;
        font-weight: 500;
        letter-spacing: -0.02em;
        margin: 0 0 4px;
      }
      .sub {
        font-size: 14px;
        margin: 0;
      }
      .small {
        font-size: 12px;
      }
      .filters {
        display: flex;
        gap: 10px;
      }
      .filters .field:first-child {
        flex: 1;
      }
      .team {
        min-width: 150px;
        flex: none;
      }
      .row {
        display: grid;
        grid-template-columns: 1.5fr 0.8fr 1fr 1.6fr 0.8fr;
        gap: 14px;
        align-items: center;
      }
      .head-row {
        padding-bottom: 8px;
      }
      .row.data {
        padding: 11px 0;
        border-top: 1px solid var(--border);
        font-size: 14px;
      }
      .row.data:hover {
        background: var(--surface-2);
      }
      .person {
        display: flex;
        flex-direction: column;
      }
      .topskills {
        display: flex;
        gap: 6px;
        flex-wrap: wrap;
      }
      .imported {
        color: var(--accent);
      }
      .actions {
        display: flex;
        gap: 12px;
        font-size: 13px;
      }
      .pager {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding-top: 8px;
        border-top: 1px solid var(--border);
      }
      .pagebtns {
        display: flex;
        gap: 6px;
      }
      .pg {
        height: 32px;
        padding: 0 12px;
        font-size: 13px;
        border-radius: var(--radius);
        border: 1px solid var(--border);
        background: transparent;
        color: var(--text-2);
      }
      .pg:hover:not(:disabled) {
        background: var(--surface);
      }
      .pg.active {
        border-color: var(--accent);
        color: var(--accent-text);
      }
      .pg:disabled {
        color: var(--text-dim);
        cursor: not-allowed;
      }
      .pad {
        padding: 12px 0;
      }
      @media (max-width: 820px) {
        .row {
          grid-template-columns: 1.4fr 1fr 0.8fr;
        }
        .row .col-head:nth-child(4),
        .row .col-head:nth-child(5),
        .topskills,
        .actions {
          display: none;
        }
      }
    `,
  ],
})
export class PeopleListComponent {
  private readonly peopleApi = inject(PeopleApi);
  private readonly teamApi = inject(TeamApi);
  private readonly skillApi = inject(SkillApi);
  private readonly size = 6;

  search = '';
  team = '';
  skill = '';
  readonly page = signal(0);
  readonly data = signal<Page<Person> | null>(null);
  readonly loading = signal(false);
  readonly teams = signal<Team[]>([]);
  readonly skills = signal<Skill[]>([]);
  private readonly hidden = new Set<string>();

  readonly subtitle = computed(() => {
    const d = this.data();
    return d ? `${d.totalElements} active · ${this.teams().length} teams · soft-deleted hidden` : 'Loading…';
  });

  constructor() {
    this.teamApi.list().subscribe((t) => this.teams.set(t));
    this.skillApi.list().subscribe((s) => this.skills.set(s));
    this.load();
  }

  onFilter(): void {
    this.page.set(0);
    this.load();
  }

  go(n: number): void {
    this.page.set(n);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.peopleApi
      .list({ search: this.search, team: this.team, skill: this.skill, page: this.page(), size: this.size })
      .subscribe((res) => {
        // Client-side soft-delete demo: drop locally-hidden rows.
        const content = res.content.filter((p) => !this.hidden.has(p.id));
        this.data.set({ ...res, content, totalElements: res.totalElements - this.hidden.size });
        this.loading.set(false);
      });
  }

  softDelete(p: Person): void {
    if (!confirm(`Soft-delete ${p.firstName} ${p.lastName}? (mock — no data is written)`)) return;
    this.hidden.add(p.id);
    this.load();
  }

  rangeLabel(): string {
    const d = this.data();
    if (!d || !d.content.length) return 'No results';
    const start = this.page() * this.size + 1;
    const end = start + d.content.length - 1;
    return `Showing ${start}–${end} of ${d.totalElements}`;
  }

  pageNumbers(): number[] {
    const d = this.data();
    if (!d) return [0];
    return Array.from({ length: d.totalPages }, (_, i) => i);
  }
}
