import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FinderApi, SkillApi, TeamApi } from '../../core/api/api';
import { FinderResult, Skill, Team } from '../../core/models/models';
import { LevelBarComponent } from '../../shared/components/level-bar.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { SkeletonComponent } from '../../shared/components/skeleton.component';

@Component({
  selector: 'sa-expert-finder',
  standalone: true,
  imports: [FormsModule, RouterLink, LevelBarComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <div class="page">
      <div class="main">
        <h1>Expert finder</h1>
        <p class="muted sub">Ko kod nas zna Neo4j — type a query, get ranked people.</p>

        <form class="querybar" (ngSubmit)="search()">
          <span class="dim small">Query</span>
          @for (t of terms(); track t.name) {
            <span class="tag">{{ t.name }}</span>
            @if (!$last) {
              <span class="dim small">+</span>
            }
          }
          <input class="qinput" name="query" [(ngModel)]="query" placeholder="e.g. React + Neo4j > 3" />
          <button class="btn" type="submit">Search</button>
        </form>
        @if (result()) {
          <div class="parsed">
            <span>Parsed: {{ result()!.parsed }}</span>
            <span>Team: {{ team || 'All' }}</span>
            <span>Sorted by: sum of levels</span>
          </div>
        }

        <div class="divider-fade top-gap"></div>

        <div class="resulthead">
          @if (result()) {
            <span class="muted small">{{ result()!.matches.length }} full matches · {{ result()!.totalActive }} active people</span>
          }
          <span class="dim small">Soft-deleted people excluded</span>
        </div>

        @if (loading()) {
          <sa-skeleton [rows]="4" />
        } @else if (result()) {
          @if (result()!.matches.length) {
            <div class="row head">
              <span class="col-head">Person</span>
              <span class="col-head">Matched skills</span>
              <span class="col-head">Score</span>
              <span class="col-head">Actions</span>
            </div>
            @for (m of result()!.matches; track m.person.id) {
              <div class="row data">
                <div class="person">
                  <span class="name">{{ m.person.firstName }} {{ m.person.lastName }}</span>
                  <span class="muted small">{{ personMeta(m) }}</span>
                </div>
                <div class="skills">
                  @for (k of m.matched; track k.skill.id) {
                    <div class="skillrow">
                      <span class="sname">{{ k.skill.name }}</span>
                      <sa-level-bar [level]="k.level" />
                      <span class="small">{{ k.level }}</span>
                    </div>
                  }
                </div>
                <span class="score">{{ m.score }}</span>
                <div class="actions">
                  <a [routerLink]="['/people', m.person.id]">Profile</a>
                  <a routerLink="/graph">In graph</a>
                </div>
              </div>
            }
          } @else {
            <sa-empty-state
              eyebrow="Expert finder · empty"
              [title]="emptyTitle()"
              message="This is an empty result, not an error — try lowering the level, or show partial matches.">
              <div class="empty-actions">
                <button class="btn" (click)="showPartial.set(true)">Show partial matches</button>
              </div>
            </sa-empty-state>
          }

          @if ((showPartial() || !result()!.matches.length) && result()!.partial.length) {
            <div class="partial">
              <div class="col-head part-head">Partial matches · knows some, not all</div>
              <div class="partial-grid">
                @for (m of result()!.partial; track m.person.id) {
                  <div class="partial-card">
                    <div>
                      <span class="name">{{ m.person.firstName }} {{ m.person.lastName }}</span>
                      <span class="muted small block">{{ partialMeta(m) }}</span>
                    </div>
                    <a [routerLink]="['/people', m.person.id]" class="small">Profile</a>
                  </div>
                }
              </div>
            </div>
          }
        }
      </div>

      <aside class="rail">
        <div class="card">
          <span class="eyebrow">Result neighbourhood</span>
          <span class="rail-title">{{ neighbourhood() }}</span>
          <svg viewBox="0 0 320 180" class="mini">
            <g stroke="#595d6c" stroke-width="1">
              <line x1="160" y1="90" x2="70" y2="45" /><line x1="160" y1="90" x2="255" y2="50" />
              <line x1="160" y1="90" x2="80" y2="145" /><line x1="160" y1="90" x2="250" y2="140" />
              <line x1="70" y1="45" x2="255" y2="50" /><line x1="80" y1="145" x2="250" y2="140" />
            </g>
            <circle cx="160" cy="90" r="11" fill="#9184d9" />
            <circle cx="70" cy="45" r="8" fill="#b2b6ca" /><circle cx="255" cy="50" r="8" fill="#b2b6ca" />
            <circle cx="80" cy="145" r="8" fill="#b2b6ca" /><circle cx="250" cy="140" r="8" fill="#b2b6ca" />
          </svg>
          <a routerLink="/graph" class="btn btn-block center">Open in graph explorer</a>
        </div>

        <div class="card">
          <span class="eyebrow">Bus factor</span>
          <span class="rail-title">Neo4j is known by 5 — Cypher tuning by 1</span>
          <p class="muted small m0">Milan Kostić is the only person above level 3 on Cypher tuning. Projects using it: Atlas, Vega.</p>
        </div>

        <div class="card-flat">
          <span class="eyebrow eyebrow-mute">Filters</span>
          <div class="group">
            <label class="label">Team</label>
            <select class="field" [(ngModel)]="team" (ngModelChange)="search()">
              <option value="">All teams</option>
              @for (t of teams(); track t.id) {
                <option [value]="t.name">{{ t.name }}</option>
              }
            </select>
          </div>
          <div class="chiprow">
            <span class="tag tag-outline">Level ≥ 3</span>
            <span class="tag tag-mute">Active only</span>
          </div>
        </div>
      </aside>
    </div>
  `,
  styles: [
    `
      .page {
        display: grid;
        grid-template-columns: 1fr 340px;
        gap: 28px;
        padding: 26px 22px;
      }
      .main {
        min-width: 0;
        display: flex;
        flex-direction: column;
      }
      h1 {
        font-size: 38px;
        font-weight: 500;
        letter-spacing: -0.02em;
        margin: 0 0 4px;
      }
      .sub {
        font-size: 14px;
        margin: 0 0 18px;
      }
      .small {
        font-size: 12px;
      }
      .querybar {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 10px 12px;
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--radius);
      }
      .qinput {
        flex: 1;
        min-width: 80px;
        background: transparent;
        border: none;
        outline: none;
        color: var(--text);
        font-size: 14px;
      }
      .qinput::placeholder {
        color: var(--text-dim);
      }
      .parsed {
        display: flex;
        gap: 18px;
        margin-top: 9px;
        font-size: 12px;
        color: var(--text-dim);
        flex-wrap: wrap;
      }
      .top-gap {
        margin: 22px 0 0;
      }
      .resulthead {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        padding: 12px 0 8px;
      }
      .row {
        display: grid;
        grid-template-columns: 1.6fr 1.4fr 0.6fr 0.9fr;
        gap: 14px;
        align-items: center;
      }
      .row.head {
        padding: 0 0 8px;
      }
      .row.data {
        padding: 11px 12px;
        margin: 6px -12px 0;
        border-radius: var(--radius);
      }
      .row.data:hover {
        background: var(--surface);
      }
      .person {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .name {
        font-size: 15px;
        font-weight: 500;
      }
      .block {
        display: block;
      }
      .skills {
        display: flex;
        flex-direction: column;
        gap: 5px;
      }
      .skillrow {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .sname {
        font-size: 12px;
        width: 78px;
        color: var(--text-2);
      }
      .score {
        font-size: 19px;
        font-weight: 500;
      }
      .actions {
        display: flex;
        gap: 12px;
        font-size: 13px;
      }
      .empty-actions {
        margin-top: 4px;
      }
      .partial {
        margin-top: 22px;
      }
      .part-head {
        margin-bottom: 9px;
      }
      .partial-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 16px;
      }
      .partial-card {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 11px 13px;
        border-radius: var(--radius);
        background: var(--surface);
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
        height: 170px;
      }
      .center {
        text-align: center;
      }
      .m0 {
        margin: 0;
      }
      .group {
        display: flex;
        flex-direction: column;
        gap: 5px;
      }
      .chiprow {
        display: flex;
        gap: 8px;
        margin-top: 4px;
      }
      @media (max-width: 1040px) {
        .page {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class ExpertFinderComponent {
  private readonly finderApi = inject(FinderApi);
  private readonly teamApi = inject(TeamApi);
  private readonly skillApi = inject(SkillApi);

  query = 'React + Neo4j > 3';
  team = '';
  readonly result = signal<FinderResult | null>(null);
  readonly loading = signal(false);
  readonly showPartial = signal(false);
  readonly teams = signal<Team[]>([]);
  private readonly queryText = signal('React + Neo4j > 3');
  private readonly catalog = signal<Skill[]>([]);

  readonly terms = computed(() => {
    const q = this.queryText();
    return this.catalog()
      .filter((s) => new RegExp(`\\b${escapeRe(s.name)}\\b`, 'i').test(q))
      .map((s) => ({ name: s.name }));
  });

  constructor() {
    this.teamApi.list().subscribe((t) => this.teams.set(t));
    this.skillApi.list().subscribe((s) => this.catalog.set(s));
    this.search();
  }

  search(): void {
    this.loading.set(true);
    this.showPartial.set(false);
    this.queryText.set(this.query);
    this.finderApi.search(this.query, this.team).subscribe((r) => {
      this.result.set(r);
      this.loading.set(false);
    });
  }

  personMeta(m: FinderResult['matches'][number]): string {
    const p = m.person;
    const base = `${p.team ?? ''} · ${p.position ?? ''}`;
    return p.mentorsCount ? `${base} · mentors ${p.mentorsCount}` : base;
  }

  partialMeta(m: FinderResult['partial'][number]): string {
    const known = m.matched.map((k) => `${k.skill.name} ${k.level}`).join(', ');
    return `${m.person.team} · ${known || 'partial'}`;
  }

  emptyTitle(): string {
    const names = this.terms().map((t) => t.name);
    return names.length ? `Nobody knows all of ${names.join(' + ')} yet` : 'No skills recognised in the query';
  }

  neighbourhood(): string {
    const r = this.result();
    if (!r) return '';
    const projects = new Set<string>();
    for (const m of r.matches) for (const pr of m.person.projects ?? []) projects.add(pr.projectId);
    return `${r.matches.length} people · ${this.terms().length} skills · ${projects.size} projects`;
  }
}

function escapeRe(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
