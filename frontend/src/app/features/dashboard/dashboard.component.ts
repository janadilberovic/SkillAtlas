import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardApi } from '../../core/api/api';
import { DashboardData, MentorRequestRow, Page, SkillGapRow } from '../../core/models/models';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { MentorMatchingComponent } from '../mentoring/mentor-matching.component';

@Component({
  selector: 'sa-dashboard',
  standalone: true,
  imports: [MentorMatchingComponent, RouterLink, SkeletonComponent],
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

  /** The wish an admin is picking a mentor for, straight from the queue. */
  readonly requests = signal<Page<MentorRequestRow> | null>(null);
  readonly requestsLoading = signal(false);
  readonly picking = signal<MentorRequestRow | null>(null);
  readonly confirmed = signal<string | null>(null);

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
        this.requests.set(d.mentorRequests);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  turnRequestsPage(delta: number): void {
    const current = this.requests();
    if (!current) return;
    const next = current.page + delta;
    if (next < 0 || next >= current.totalPages) return;
    this.loadRequests(next, current.size);
  }

  pick(row: MentorRequestRow): void {
    if (!row.candidates) return;
    this.confirmed.set(null);
    this.picking.set(row);
  }

  onPickClosed(wasConfirmed: boolean): void {
    const row = this.picking();
    this.picking.set(null);
    if (!wasConfirmed || !row) return;
    this.confirmed.set(`${row.personName} · ${row.skillName}`);
    // The row has just left the queue, so reload the page it was on rather than patching it out.
    const current = this.requests();
    this.loadRequests(current?.page ?? 0, current?.size ?? 10);
  }

  private loadRequests(page: number, size: number): void {
    this.requestsLoading.set(true);
    this.api.mentorRequests(page, size).subscribe({
      next: (p) => {
        // A page can empty out under you when the last row on it was just answered.
        if (!p.content.length && p.page > 0) {
          this.loadRequests(p.page - 1, size);
          return;
        }
        this.requests.set(p);
        this.requestsLoading.set(false);
      },
      error: () => this.requestsLoading.set(false),
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
