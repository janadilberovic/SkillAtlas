import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { PeopleApi, ProjectApi } from '../../core/api/api';
import { Person } from '../../core/models/models';
import { DatepickerComponent } from '../../shared/components/datepicker/datepicker.component';
import { SelectComponent, SelectOption } from '../../shared/components/select/select.component';

interface StaffRow {
  personId: string;
  name: string;
  role: string;
  from: string;
  to: string;
}

/** Staffing a project (E2.4). WORKED_ON carries the role and the period, so each person is queued on their own terms. */
@Component({
  selector: 'sa-project-staffing',
  standalone: true,
  imports: [FormsModule, DatepickerComponent, SelectComponent],
  templateUrl: './project-staffing.component.html',
  styleUrl: './project-staffing.component.css',
})
export class ProjectStaffingComponent {
  private readonly api = inject(ProjectApi);
  private readonly peopleApi = inject(PeopleApi);

  readonly projectId = input.required<string>();
  readonly projectName = input.required<string>();
  /** Everyone already on the project — they drop out of the picker. */
  readonly memberIds = input<string[]>([]);
  readonly closed = output<boolean>();

  personId = '';
  role = '';

  readonly from = signal('');
  readonly to = signal('');
  readonly rows = signal<StaffRow[]>([]);
  readonly people = signal<Person[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal('');

  readonly candidates = computed<SelectOption[]>(() => {
    const taken = new Set([...this.memberIds(), ...this.rows().map((r) => r.personId)]);
    return this.people()
      .filter((p) => !taken.has(p.id))
      .map((p) => ({
        value: p.id,
        label: `${p.firstName} ${p.lastName}${p.teams?.length ? ` · ${p.teams.join(' / ')}` : ''}`,
      }));
  });

  readonly datesInverted = computed(() => {
    const from = this.from();
    const to = this.to();
    return from !== '' && to !== '' && to < from;
  });

  readonly submitLabel = computed(() => {
    const count = this.rows().length;
    if (this.submitting()) return 'Assigning…';
    return count < 2 ? `Assign to ${this.projectName()}` : `Assign ${count} people`;
  });

  constructor() {
    this.peopleApi.list({ size: 100 }).subscribe({
      next: (page) => {
        this.people.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load people. Check the API and try again.');
        this.loading.set(false);
      },
    });
  }

  canAdd(): boolean {
    return this.personId !== '' && this.role.trim() !== '' && this.from() !== '' && !this.datesInverted();
  }

  addRow(): void {
    if (!this.canAdd()) return;
    const person = this.people().find((p) => p.id === this.personId);
    if (!person) return;
    this.rows.update((rows) => [
      ...rows,
      {
        personId: person.id,
        name: `${person.firstName} ${person.lastName}`,
        role: this.role.trim(),
        from: this.from(),
        to: this.to(),
      },
    ]);
    this.personId = '';
    this.role = '';
    this.from.set('');
    this.to.set('');
  }

  removeRow(personId: string): void {
    this.rows.update((rows) => rows.filter((r) => r.personId !== personId));
  }

  assign(): void {
    const rows = this.rows();
    if (!rows.length || this.submitting()) return;
    this.submitting.set(true);
    this.error.set('');
    forkJoin(
      rows.map((r) =>
        this.api.assignMember(this.projectId(), r.personId, { role: r.role, from: r.from, to: r.to || null }),
      ),
    ).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closed.emit(true);
      },
      // One failed write leaves the others applied, so the modal stays open on the queue it still has.
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err?.error?.error ?? 'The server refused at least one assignment.');
      },
    });
  }

  period(row: StaffRow): string {
    const fmt = (d: string) => (d ? new Date(d).toLocaleDateString('en-US', { month: 'short', year: 'numeric' }) : null);
    return `${fmt(row.from) ?? '—'} — ${row.to ? fmt(row.to) : 'now'}`;
  }
}
