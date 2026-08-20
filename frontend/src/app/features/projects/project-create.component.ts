import { Component, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProjectApi, SkillApi } from '../../core/api/api';
import { Skill } from '../../core/models/models';
import { DatepickerComponent } from '../../shared/components/datepicker/datepicker.component';
import { SelectComponent, SelectOption } from '../../shared/components/select/select.component';

/** Admin create (E2.4). Members are not here — a project is created first, then staffed. */
@Component({
  selector: 'sa-project-create',
  standalone: true,
  imports: [FormsModule, DatepickerComponent, SelectComponent],
  templateUrl: './project-create.component.html',
  styleUrl: './project-create.component.css',
})
export class ProjectCreateComponent {
  private readonly api = inject(ProjectApi);
  private readonly skillApi = inject(SkillApi);

  readonly closed = output<boolean>();

  readonly statusOptions: SelectOption[] = [
    { value: 'active', label: 'active' },
    { value: 'archived', label: 'archived' },
  ];

  name = '';
  description = '';
  status = 'active';

  readonly startDate = signal('');
  readonly endDate = signal('');
  readonly uses = signal<string[]>([]);
  readonly catalog = signal<Skill[]>([]);
  readonly pickerOpen = signal(false);
  readonly touched = signal(false);
  readonly submitting = signal(false);
  readonly error = signal('');

  readonly chosen = computed(() => {
    const picked = this.uses();
    return this.catalog().filter((s) => picked.includes(s.id));
  });

  readonly datesInverted = computed(() => {
    const from = this.startDate();
    const to = this.endDate();
    return from !== '' && to !== '' && to < from;
  });

  readonly usesLabel = computed(() => {
    const count = this.uses().length;
    return count === 0 ? 'at least one' : `${count} selected`;
  });

  constructor() {
    this.skillApi.list().subscribe({
      next: (skills) => this.catalog.set(skills),
      error: () => this.error.set('Could not load the skill catalog.'),
    });
  }

  isPicked(id: string): boolean {
    return this.uses().includes(id);
  }

  // The list stays open on a pick: a project uses several technologies, and reopening the
  // control for each one is the wrong shape for that.
  toggleSkill(id: string): void {
    this.touched.set(true);
    this.uses.update((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));
  }

  removeSkill(id: string): void {
    this.uses.update((ids) => ids.filter((x) => x !== id));
  }

  /** A finished project is what archived means, so a past end date sets the status for you. */
  onEndDate(value: string): void {
    this.endDate.set(value);
    const today = new Date();
    const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    if (value !== '' && value < todayIso && !this.datesInverted()) this.status = 'archived';
  }

  canSubmit(): boolean {
    return (
      this.name.trim() !== '' &&
      this.uses().length > 0 &&
      this.startDate() !== '' &&
      !this.datesInverted() &&
      !this.submitting()
    );
  }

  create(): void {
    if (!this.canSubmit()) return;
    this.submitting.set(true);
    this.error.set('');
    this.api
      .create({
        name: this.name.trim(),
        description: this.description.trim() || null,
        startDate: this.startDate(),
        endDate: this.endDate() || null,
        skillIds: this.uses(),
        active: this.status === 'active',
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.closed.emit(true);
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.error ?? 'Could not create the project. Check the API and try again.');
        },
      });
  }
}
