import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PeopleApi, SkillApi, TeamApi } from '../../core/api/api';
import { Page, Person, Skill, Team } from '../../core/models/models';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'sa-people-list',
  standalone: true,
  imports: [FormsModule, RouterLink, SkeletonComponent],
  templateUrl: './people-list.component.html',
  styleUrl: './people-list.component.css',
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
