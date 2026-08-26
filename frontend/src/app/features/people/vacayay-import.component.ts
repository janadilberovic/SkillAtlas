import { Component, computed, inject, output, signal } from '@angular/core';
import { VacaYayApi } from '../../core/api/api';
import { ImportResult, Page, VacaYayRosterRow } from '../../core/models/models';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * E3.1 · the import picker. The old system is the only source of fields — this screen sends ids,
 * never names or emails.
 */
@Component({
  selector: 'sa-vacayay-import',
  standalone: true,
  imports: [SkeletonComponent],
  templateUrl: './vacayay-import.component.html',
  styleUrl: './vacayay-import.component.css',
})
export class VacaYayImportComponent {
  private readonly api = inject(VacaYayApi);
  private readonly size = 8;

  readonly page = signal(0);
  readonly data = signal<Page<VacaYayRosterRow> | null>(null);
  readonly loading = signal(false);
  readonly submitting = signal(false);
  readonly error = signal('');
  readonly result = signal<ImportResult | null>(null);
  /** Ticked ids survive turning the page — the roster is one list, not one list per page. */
  readonly picked = signal<Set<number>>(new Set());

  readonly closed = output<boolean>();

  readonly pickedCount = computed(() => this.picked().size);
  readonly pickableOnPage = computed(() =>
    (this.data()?.content ?? []).filter((r) => this.canPick(r)),
  );
  readonly allOnPagePicked = computed(() => {
    const pickable = this.pickableOnPage();
    return pickable.length > 0 && pickable.every((r) => this.picked().has(r.id));
  });

  constructor() {
    this.load();
  }

  canPick(row: VacaYayRosterRow): boolean {
    return !row.alreadyImported && !row.issue;
  }

  isPicked(row: VacaYayRosterRow): boolean {
    return this.picked().has(row.id);
  }

  toggle(row: VacaYayRosterRow): void {
    if (!this.canPick(row)) return;
    const next = new Set(this.picked());
    if (!next.delete(row.id)) next.add(row.id);
    this.picked.set(next);
  }

  togglePage(): void {
    const next = new Set(this.picked());
    const select = !this.allOnPagePicked();
    for (const row of this.pickableOnPage()) {
      if (select) next.add(row.id);
      else next.delete(row.id);
    }
    this.picked.set(next);
  }

  go(n: number): void {
    this.page.set(n);
    this.load();
  }

  runImport(): void {
    if (!this.pickedCount() || this.submitting()) return;
    this.submitting.set(true);
    this.error.set('');
    this.api.import([...this.picked()]).subscribe({
      next: (result) => {
        this.submitting.set(false);
        this.result.set(result);
        this.picked.set(new Set());
        // The roster's alreadyImported flags are stale the moment the import lands.
        this.page.set(0);
        this.load();
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err?.error?.error ?? 'Could not import from VacaYAY. Check the old system and try again.');
      },
    });
  }

  /** True once the import created anyone, so closing has to refresh the people list. */
  close(): void {
    this.closed.emit((this.result()?.imported ?? 0) > 0);
  }

  rangeLabel(): string {
    const d = this.data();
    if (!d || !d.content.length) return 'Nobody to import';
    const start = this.page() * this.size + 1;
    return `Showing ${start}–${start + d.content.length - 1} of ${d.totalElements}`;
  }

  pageNumbers(): number[] {
    const d = this.data();
    if (!d) return [0];
    return Array.from({ length: d.totalPages }, (_, i) => i);
  }

  private load(): void {
    this.loading.set(true);
    this.api.roster(this.page(), this.size).subscribe({
      next: (res) => {
        this.data.set(res);
        this.loading.set(false);
      },
      // A 502 here means the old system is down, and its sentence is the useful one.
      error: (err) => {
        this.error.set(err?.error?.error ?? 'Could not reach VacaYAY. Is the old system running?');
        this.data.set(null);
        this.loading.set(false);
      },
    });
  }
}
