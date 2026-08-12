import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FinderApi, SkillApi, TeamApi } from '../../core/api/api';
import {
  MAX_LEVEL,
  MIN_LEVEL,
  SkillTerm,
  formatSkillTerm,
  parseSkillQuery,
  parseSkillTerm,
  skillNameFragment,
} from '../../core/api/finder-query';
import { FinderResult, Skill, SkillCoverage } from '../../core/models/models';
import { LevelBarComponent } from '../../shared/components/level-bar/level-bar.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { SelectComponent, SelectOption } from '../../shared/components/select/select.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/** How many result nodes the neighbourhood sketch draws around the query. */
const NEIGHBOURS = 6;
/** Mirrors FinderService.EXPERT_LEVEL — the level the coverage endpoint calls a go-to person. */
const EXPERT_LEVEL = 4;

interface NeighbourNode {
  id: string;
  label: string;
  x: number;
  y: number;
  r: number;
}

@Component({
  selector: 'sa-expert-finder',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    LevelBarComponent,
    EmptyStateComponent,
    SelectComponent,
    SkeletonComponent,
  ],
  templateUrl: './expert-finder.component.html',
  styleUrl: './expert-finder.component.css',
})
export class ExpertFinderComponent {
  private readonly finderApi = inject(FinderApi);
  private readonly teamApi = inject(TeamApi);
  private readonly skillApi = inject(SkillApi);
  private readonly route = inject(ActivatedRoute);

  readonly expertLevel = EXPERT_LEVEL;

  /** Committed query terms. The text box only ever holds the term being typed. */
  readonly terms = signal<SkillTerm[]>([]);
  readonly draft = signal('');
  readonly team = signal('');

  readonly result = signal<FinderResult | null>(null);
  readonly coverage = signal<SkillCoverage[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPartial = signal(false);

  private readonly catalog = signal<Skill[]>([]);
  private readonly teams = signal<SelectOption[]>([]);
  /** Keyboard cursor into `suggestions`; -1 means "nothing picked, Enter searches". */
  readonly activeSuggestion = signal(-1);
  private readonly suggestionsOpen = signal(false);

  private readonly input = viewChild<ElementRef<HTMLInputElement>>('queryInput');

  readonly teamOptions = computed(() => this.teams());

  /**
   * Skills from the catalogue that match what is being typed, prefix matches first. The level
   * suffix is stripped before matching, so "neo >= 4" still suggests Neo4j.
   */
  readonly suggestions = computed<Skill[]>(() => {
    if (!this.suggestionsOpen()) return [];
    const fragment = skillNameFragment(this.draft()).trim().toLowerCase();
    if (!fragment) return [];
    const chosen = new Set(this.terms().map((t) => t.name.toLowerCase()));
    return this.catalog()
      .filter((s) => !chosen.has(s.name.toLowerCase()) && s.name.toLowerCase().includes(fragment))
      .sort((a, b) => {
        const byPrefix =
          Number(!a.name.toLowerCase().startsWith(fragment)) -
          Number(!b.name.toLowerCase().startsWith(fragment));
        return byPrefix || a.name.localeCompare(b.name);
      })
      .slice(0, 8);
  });

  constructor() {
    this.skillApi.list().subscribe((skills) => this.catalog.set(skills));
    this.teamApi
      .list()
      .subscribe((teams) => this.teams.set(teams.map((t) => ({ value: t.name, label: t.name }))));

    // A ?q= param (e.g. from a dashboard "Find experts" link) seeds the query.
    const q = this.route.snapshot.queryParamMap.get('q');
    if (q) {
      this.terms.set(parseSkillQuery(q));
      this.search();
    }
  }

  // --- query bar ---------------------------------------------------------

  onDraftChange(value: string): void {
    this.draft.set(value);
    this.suggestionsOpen.set(true);
    this.activeSuggestion.set(-1);
  }

  /** Turns the half-typed text into chips. Returns the full term list so callers can search it. */
  commitDraft(): SkillTerm[] {
    const typed = parseSkillQuery(this.draft());
    if (typed.length) {
      this.terms.update((current) => mergeTerms(current, typed));
    }
    this.draft.set('');
    this.suggestionsOpen.set(false);
    this.activeSuggestion.set(-1);
    return this.terms();
  }

  /** A suggestion keeps whatever level threshold was already typed ("neo >= 4" -> Neo4j ≥ 4). */
  pickSuggestion(skill: Skill): void {
    const minLevel = parseSkillTerm(this.draft()).minLevel;
    this.terms.update((current) => mergeTerms(current, [{ name: skill.name, minLevel }]));
    this.draft.set('');
    this.suggestionsOpen.set(false);
    this.activeSuggestion.set(-1);
    this.input()?.nativeElement.focus();
    this.runSearch(this.terms());
  }

  removeTerm(name: string): void {
    this.terms.update((current) => current.filter((t) => t.name !== name));
    this.runSearch(this.terms());
  }

  /** Cycles a chip's minimum level 1 → 2 → … → 5 → 1, so a picked suggestion can still get a bar. */
  cycleLevel(name: string): void {
    this.terms.update((current) =>
      current.map((t) =>
        t.name === name
          ? { ...t, minLevel: t.minLevel >= MAX_LEVEL ? MIN_LEVEL : t.minLevel + 1 }
          : t,
      ),
    );
    this.runSearch(this.terms());
  }

  onKeydown(event: KeyboardEvent): void {
    const options = this.suggestions();

    switch (event.key) {
      case 'ArrowDown':
        if (!options.length) return;
        event.preventDefault();
        this.activeSuggestion.set(Math.min(options.length - 1, this.activeSuggestion() + 1));
        return;
      case 'ArrowUp':
        if (!options.length) return;
        event.preventDefault();
        this.activeSuggestion.set(Math.max(-1, this.activeSuggestion() - 1));
        return;
      case 'Escape':
        this.suggestionsOpen.set(false);
        this.activeSuggestion.set(-1);
        return;
      case 'Enter':
        if (this.activeSuggestion() >= 0 && options.length) {
          // Pick the highlighted skill instead of submitting the form.
          event.preventDefault();
          this.pickSuggestion(options[this.activeSuggestion()]);
        }
        return;
      case '+':
      case ',':
        // The separators end a term rather than becoming part of the next one.
        event.preventDefault();
        this.search();
        return;
      case 'Backspace':
        if (!this.draft() && this.terms().length) {
          event.preventDefault();
          this.removeTerm(this.terms()[this.terms().length - 1].name);
        }
        return;
      default:
    }
  }

  closeSuggestions(): void {
    // Deferred so a click on a suggestion lands before the list disappears.
    setTimeout(() => this.suggestionsOpen.set(false), 120);
  }

  onTeamChange(team: string): void {
    this.team.set(team);
    this.runSearch(this.terms());
  }

  search(): void {
    this.runSearch(this.commitDraft());
  }

  private runSearch(terms: SkillTerm[]): void {
    if (!terms.length) {
      this.result.set(null);
      this.coverage.set([]);
      this.error.set(null);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.showPartial.set(false);

    this.finderApi.search(terms, this.team()).subscribe({
      next: (r) => {
        this.result.set(r);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.error ?? 'Search failed. Check the API and try again.');
        this.result.set(null);
        this.loading.set(false);
      },
    });
    this.finderApi.coverage(terms).subscribe({
      next: (c) => this.coverage.set(c),
      error: () => this.coverage.set([]),
    });
  }

  // --- rendering helpers -------------------------------------------------

  label(term: SkillTerm): string {
    return formatSkillTerm(term);
  }

  levelBadge(term: SkillTerm): string {
    return term.minLevel > MIN_LEVEL ? `≥ ${term.minLevel}` : 'any';
  }

  personMeta(m: FinderResult['matches'][number]): string {
    const p = m.person;
    return [p.team, p.position].filter(Boolean).join(' · ');
  }

  partialMeta(m: FinderResult['partial'][number]): string {
    const known = m.matched.map((k) => `${k.name} ${k.level}`).join(', ');
    return [m.person.team, known || 'partial'].filter(Boolean).join(' · ');
  }

  emptyTitle(): string {
    const names = this.terms().map((t) => this.label(t));
    return names.length ? `Nobody matches ${names.join(' + ')} yet` : 'Type a skill to search';
  }

  /**
   * The sketch in the rail: the query in the middle, the top-ranked matches around it, node size
   * proportional to score. It is a preview of the same subgraph the explorer draws in full.
   */
  readonly neighbours = computed<NeighbourNode[]>(() => {
    const matches = this.result()?.matches.slice(0, NEIGHBOURS) ?? [];
    const best = Math.max(1, ...matches.map((m) => m.score));
    return matches.map((m, i) => {
      const angle = (2 * Math.PI * i) / matches.length - Math.PI / 2;
      return {
        id: m.person.id,
        label: `${m.person.firstName} ${m.person.lastName} · score ${m.score}`,
        x: 160 + 118 * Math.cos(angle),
        y: 90 + 62 * Math.sin(angle),
        r: 5 + 6 * (m.score / best),
      };
    });
  });

  neighbourhoodCaption(): string {
    const r = this.result();
    if (!r) return 'Run a search to sketch its neighbourhood';
    const shown = Math.min(NEIGHBOURS, r.matches.length);
    const skills = this.terms().length;
    return `${shown} of ${r.totalMatches} people · ${skills} ${skills === 1 ? 'skill' : 'skills'}`;
  }

  busFactorLine(c: SkillCoverage): string {
    if (!c.knownBy) return 'Nobody knows it — a gap, not a bus factor.';
    if (!c.experts.length) return `Known by ${c.knownBy}, nobody at level ${EXPERT_LEVEL}+ yet.`;
    if (c.experts.length === 1) {
      return `Known by ${c.knownBy}, but ${c.experts[0]} is the only one at level ${EXPERT_LEVEL}+.`;
    }
    return `Known by ${c.knownBy} · ${c.experts.length} at level ${EXPERT_LEVEL}+.`;
  }

  isBusFactor(c: SkillCoverage): boolean {
    return c.experts.length <= 1;
  }
}

/** Appends terms that are new; when a skill repeats, the stricter threshold wins. */
function mergeTerms(current: SkillTerm[], incoming: SkillTerm[]): SkillTerm[] {
  const merged = current.map((t) => ({ ...t }));
  for (const term of incoming) {
    const existing = merged.find((t) => t.name.toLowerCase() === term.name.toLowerCase());
    if (existing) existing.minLevel = Math.max(existing.minLevel, term.minLevel);
    else merged.push({ ...term });
  }
  return merged;
}
