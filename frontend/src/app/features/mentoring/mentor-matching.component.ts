import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { MentoringApi } from '../../core/api/api';
import { MentorCandidates } from '../../core/models/models';

/** Mentor-matching modal (E6.1). Ranking criteria are visible so an admin sees why someone is first. */
@Component({
  selector: 'sa-mentor-matching',
  standalone: true,
  templateUrl: './mentor-matching.component.html',
  styleUrl: './mentor-matching.component.css',
})
export class MentorMatchingComponent {
  private readonly api = inject(MentoringApi);

  readonly menteeId = input.required<string>();
  readonly menteeName = input.required<string>();
  readonly skill = input.required<string>();
  readonly closed = output<boolean>();

  readonly result = signal<MentorCandidates | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly selected = signal<string | null>(null);
  readonly confirming = signal(false);

  readonly candidates = computed(() => this.result()?.candidates ?? []);
  readonly minLevel = computed(() => this.result()?.minLevel ?? 3);

  readonly confirmLabel = computed(() => {
    const id = this.selected();
    const c = this.candidates().find((x) => x.id === id);
    return c ? `Confirm ${c.firstName} ${c.lastName[0]}. as mentor` : 'Confirm mentor';
  });

  constructor() {
    effect(() => {
      const mentee = this.menteeId();
      const skill = this.skill();
      this.loading.set(true);
      this.error.set('');
      this.api.candidates(mentee, skill).subscribe({
        next: (result) => {
          this.result.set(result);
          this.selected.set(result.candidates[0]?.id ?? null);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(`Could not rank mentors for ${skill}.`);
          this.loading.set(false);
        },
      });
    });
  }

  confirm(): void {
    const mentorId = this.selected();
    const skill = this.result()?.skill;
    if (!mentorId || !skill) return;
    this.confirming.set(true);
    // The write takes the skill id the read resolved, not the name that was typed.
    this.api.confirm(mentorId, this.menteeId(), skill.id).subscribe({
      next: () => {
        this.confirming.set(false);
        this.closed.emit(true);
      },
      error: () => {
        this.confirming.set(false);
        this.error.set('The server refused the mentorship.');
      },
    });
  }
}
