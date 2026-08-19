import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PeopleApi, SkillApi, TeamApi } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { Page, Person, Skill, Team } from '../../core/models/models';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { PersonCreateComponent } from './person-create.component';

@Component({
  selector: 'sa-people-list',
  standalone: true,
  imports: [FormsModule, RouterLink, SkeletonComponent, PersonCreateComponent],
  templateUrl: './people-list.component.html',
  styleUrl: './people-list.component.css',
})
export class PeopleListComponent {
  private readonly peopleApi = inject(PeopleApi);
  private readonly teamApi = inject(TeamApi);
  private readonly skillApi = inject(SkillApi);
  private readonly auth = inject(AuthService);
  private readonly size = 6;

  search = '';
  team = '';
  skill = '';
  readonly page = signal(0);
  readonly data = signal<Page<Person> | null>(null);
  readonly loading = signal(false);
  readonly teams = signal<Team[]>([]);
  readonly skills = signal<Skill[]>([]);
  readonly showCreate = signal(false);
  readonly error = signal('');

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
      .subscribe({
        next: (res) => {
          // Deleting the last row of the last page leaves it empty — step back rather than show
          // "no results" for filters that are fine.
          if (!res.content.length && this.page() > 0) {
            this.page.set(this.page() - 1);
            this.load();
            return;
          }
          this.data.set(res);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Could not load people. Check the API and try again.');
          this.loading.set(false);
        },
      });
  }

  isSelf(p: Person): boolean {
    return p.id === this.auth.user()?.id;
  }

  softDelete(p: Person): void {
    const name = `${p.firstName} ${p.lastName}`;
    if (!confirm(`Soft-delete ${name}? They keep their relations and drop out of every list.`)) return;
    this.error.set('');
    this.peopleApi.remove(p.id).subscribe({
      next: () => this.load(),
      error: (err) => this.error.set(err?.error?.error ?? `Could not delete ${name}.`),
    });
  }

  onCreated(created: boolean): void {
    this.showCreate.set(false);
    if (created) this.load();
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
