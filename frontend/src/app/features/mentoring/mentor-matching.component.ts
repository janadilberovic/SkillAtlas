import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { MentoringApi } from '../../core/api/api';
import { MentorCandidate } from '../../core/models/models';

/** Mentor-matching modal (2g). Ranking criteria are visible so an admin sees why someone is first. */
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

  readonly candidates = signal<MentorCandidate[]>([]);
  readonly loading = signal(true);
  readonly selected = signal<string | null>(null);
  readonly confirming = signal(false);

  readonly confirmLabel = computed(() => {
    const id = this.selected();
    const c = this.candidates().find((x) => x.person.id === id);
    return c ? `Confirm ${c.person.firstName} ${c.person.lastName[0]}. as mentor` : 'Confirm mentor';
  });

  constructor() {
    effect(() => {
      const mentee = this.menteeId();
      const skill = this.skill();
      this.loading.set(true);
      this.api.candidates(mentee, skill).subscribe((list) => {
        this.candidates.set(list);
        this.selected.set(list[0]?.person.id ?? null);
        this.loading.set(false);
      });
    });
  }

  confirm(): void {
    const id = this.selected();
    if (!id) return;
    this.confirming.set(true);
    this.api.confirm(id, this.menteeId(), this.skill()).subscribe(() => {
      this.confirming.set(false);
      this.closed.emit(true);
    });
  }
}
