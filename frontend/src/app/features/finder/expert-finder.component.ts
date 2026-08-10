import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FinderApi, SkillApi, TeamApi } from '../../core/api/api';
import { FinderResult, Skill, Team } from '../../core/models/models';
import { LevelBarComponent } from '../../shared/components/level-bar/level-bar.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'sa-expert-finder',
  standalone: true,
  imports: [FormsModule, RouterLink, LevelBarComponent, EmptyStateComponent, SkeletonComponent],
  templateUrl: './expert-finder.component.html',
  styleUrl: './expert-finder.component.css',
})
export class ExpertFinderComponent {
  private readonly finderApi = inject(FinderApi);
  private readonly teamApi = inject(TeamApi);
  private readonly skillApi = inject(SkillApi);
  private readonly route = inject(ActivatedRoute);

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
    // A ?q= param (e.g. from a dashboard "Find experts" link) seeds the query.
    const q = this.route.snapshot.queryParamMap.get('q');
    if (q) this.query = `${q} ≥ 3`;
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
