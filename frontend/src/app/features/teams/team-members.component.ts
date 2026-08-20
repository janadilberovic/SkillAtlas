import { Component, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { PeopleApi, TeamApi } from '../../core/api/api';
import { Person, Team } from '../../core/models/models';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';
import { SelectComponent, SelectOption } from '../../shared/components/select/select.component';

interface Candidate {
  id: string;
  name: string;
  initials: string;
  position: string;
  tag: string;
  already: boolean;
}

/** Admin adds people to a team (MEMBER_OF). No role and no period — the relationship carries neither. */
@Component({
  selector: 'sa-team-members',
  standalone: true,
  imports: [FormsModule, AvatarComponent, SelectComponent],
  templateUrl: './team-members.component.html',
  styleUrl: './team-members.component.css',
})
export class TeamMembersComponent {
  private readonly teamApi = inject(TeamApi);
  private readonly peopleApi = inject(PeopleApi);

  readonly closed = output<boolean>();

  search = '';

  readonly teamId = signal('');
  readonly teams = signal<Team[]>([]);
  readonly people = signal<Person[]>([]);
  readonly picked = signal<string[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal('');
  /** Bumped by the search box so the filtered list is a computed, not a hand-rolled refresh. */
  readonly term = signal('');

  readonly teamOptions = computed<SelectOption[]>(() =>
    this.teams().map((t) => ({ value: t.id, label: t.name })),
  );

  readonly teamName = computed(() => this.teams().find((t) => t.id === this.teamId())?.name ?? '');

  readonly candidates = computed<Candidate[]>(() => {
    const team = this.teamName();
    const q = this.term().trim().toLowerCase();
    return this.people()
      .filter((p) => {
        if (!q) return true;
        return `${p.firstName} ${p.lastName} ${p.position ?? ''}`.toLowerCase().includes(q);
      })
      .map((p) => {
        const teams = p.teams ?? [];
        const already = team !== '' && teams.includes(team);
        return {
          id: p.id,
          name: `${p.firstName} ${p.lastName}`,
          initials: `${p.firstName.slice(0, 1)}${p.lastName.slice(0, 1)}`,
          position: p.position ?? '—',
          tag: already ? 'already here' : teams.join(' / ') || 'no team',
          already,
        };
      });
  });

  readonly countLabel = computed(() => {
    const count = this.picked().length;
    return count === 0 ? 'none selected' : `${count} selected`;
  });

  readonly submitLabel = computed(() => {
    const count = this.picked().length;
    const team = this.teamName();
    if (this.submitting()) return 'Adding…';
    if (count < 2) return team ? `Add to ${team}` : 'Add to team';
    return `Add ${count} people to ${team}`;
  });

  constructor() {
    forkJoin({ teams: this.teamApi.list(), people: this.peopleApi.list({ size: 100 }) }).subscribe({
      next: ({ teams, people }) => {
        this.teams.set(teams);
        this.people.set(people.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load teams and people. Check the API and try again.');
        this.loading.set(false);
      },
    });
  }

  // A different team means the rows mean something else, so the selection does not carry over.
  onTeamChange(id: string): void {
    this.teamId.set(id);
    this.picked.set([]);
  }

  isPicked(id: string): boolean {
    return this.picked().includes(id);
  }

  toggle(c: Candidate): void {
    if (c.already) return;
    this.picked.update((ids) => (ids.includes(c.id) ? ids.filter((x) => x !== c.id) : [...ids, c.id]));
  }

  canSubmit(): boolean {
    return this.teamId() !== '' && this.picked().length > 0 && !this.submitting();
  }

  add(): void {
    if (!this.canSubmit()) return;
    this.submitting.set(true);
    this.error.set('');
    forkJoin(this.picked().map((personId) => this.teamApi.addMember(this.teamId(), personId))).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closed.emit(true);
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err?.error?.error ?? 'The server refused at least one of them.');
      },
    });
  }
}
