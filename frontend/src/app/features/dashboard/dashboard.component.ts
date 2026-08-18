import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardApi } from '../../core/api/api';
import { DashboardData, Page, SkillGapRow } from '../../core/models/models';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'sa-dashboard',
  standalone: true,
  imports: [RouterLink, SkeletonComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent {
  private readonly api = inject(DashboardApi);
  readonly data = signal<DashboardData | null>(null);
  readonly loading = signal(true);
  readonly failed = signal(false);

  /** Starts as the page the overview already carried, so the first paint costs no second call. */
  readonly gap = signal<Page<SkillGapRow> | null>(null);
  readonly gapLoading = signal(false);

  readonly hasPrev = computed(() => (this.gap()?.page ?? 0) > 0);
  readonly hasNext = computed(() => {
    const g = this.gap();
    return !!g && g.page + 1 < g.totalPages;
  });

  // The server sends counts; the hint under each one is the reading of them this screen is for.
  readonly tiles = computed(() => {
    const d = this.data();
    if (!d) return [];
    const gaps = this.gap()?.totalElements ?? 0;
    return [
      { label: 'Active people', value: d.metrics.people, hint: `${d.mappingQueue.total} with no skills mapped`, accent: d.mappingQueue.total > 0 },
      { label: 'Skills in catalog', value: d.metrics.skills, hint: `${d.busFactor.length} hang on one person`, accent: d.busFactor.length > 0 },
      { label: 'Projects', value: d.metrics.projects, hint: `${gaps} team/skill gaps`, accent: gaps > 0 },
      { label: 'Mentorships', value: d.metrics.mentorships, hint: 'Confirmed by an admin', accent: false },
    ];
  });

  constructor() {
    this.api.overview().subscribe({
      next: (d) => {
        this.data.set(d);
        this.gap.set(d.skillGap);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  turnGapPage(delta: number): void {
    const current = this.gap();
    if (!current) return;
    const next = current.page + delta;
    if (next < 0 || next >= current.totalPages) return;
    this.gapLoading.set(true);
    this.api.skillGap(next, current.size).subscribe({
      next: (page) => {
        this.gap.set(page);
        this.gapLoading.set(false);
      },
      // Keep the page that is on screen rather than blanking the table on a failed turn.
      error: () => this.gapLoading.set(false),
    });
  }
}
