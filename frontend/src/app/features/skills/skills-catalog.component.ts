import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SkillApi } from '../../core/api/api';
import { Page, Skill, SkillCategory } from '../../core/models/models';
import { SelectComponent, SelectOption } from '../../shared/components/select/select.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

const CATEGORIES: SkillCategory[] = ['FRAMEWORK', 'LANGUAGE', 'TOOL', 'DATABASE'];
const PALETTE = ['#9184d9', '#b5afe8', '#75798c', '#5c5783'];
const CATEGORY_OPTIONS: SelectOption[] = CATEGORIES.map((c) => ({
  value: c,
  label: c.charAt(0) + c.slice(1).toLowerCase(),
}));

@Component({
  selector: 'sa-skills-catalog',
  standalone: true,
  imports: [FormsModule, SelectComponent, SkeletonComponent],
  templateUrl: './skills-catalog.component.html',
  styleUrl: './skills-catalog.component.css',
})
export class SkillsCatalogComponent {
  private readonly api = inject(SkillApi);
  private readonly size = 8;
  private searchDebounce?: ReturnType<typeof setTimeout>;

  readonly palette = PALETTE;
  readonly categoryOptions = CATEGORY_OPTIONS;

  search = '';
  category: SkillCategory | '' = '';
  newName = '';
  newCategory: SkillCategory | '' = 'FRAMEWORK';
  newColor = PALETTE[0];
  editName = '';
  editCategory: SkillCategory | '' = 'FRAMEWORK';
  editColor = PALETTE[0];

  readonly page = signal(0);
  readonly data = signal<Page<Skill> | null>(null);
  readonly loading = signal(true);
  readonly focus = signal<Skill | null>(null);
  readonly addError = signal('');
  readonly editError = signal('');
  readonly error = signal('');
  /** Its own query (sort=wanted): the ranking is company-wide, not a re-sort of the page on screen. */
  readonly mostWanted = signal<Skill[]>([]);

  readonly subtitle = computed(() => {
    const d = this.data();
    return d
      ? `${d.totalElements} skills · unique name + category + graph colour · merging duplicates is out of scope`
      : 'Loading…';
  });
  readonly maxWanted = computed(() => Math.max(1, ...this.mostWanted().map((s) => s.wantedBy ?? 0)));

  constructor() {
    this.load();
    this.loadMostWanted();
  }

  onFilter(): void {
    this.page.set(0);
    this.load();
  }

  onSearch(): void {
    clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => this.onFilter(), 250);
  }

  go(n: number): void {
    this.page.set(n);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.api
      .page({ search: this.search, category: this.category, page: this.page(), size: this.size })
      .subscribe({
        next: (res) => {
          // Deleting the last row of a page leaves it empty — step back rather than claim the
          // filters match nothing.
          if (!res.content.length && this.page() > 0) {
            this.page.set(this.page() - 1);
            this.load();
            return;
          }
          this.data.set(res);
          // The rail must not keep showing counts from before the write that just landed.
          const focused = this.focus();
          if (focused) {
            const fresh = res.content.find((s) => s.id === focused.id);
            if (fresh) this.focus.set(fresh);
          }
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Could not load the skill catalog. Check the API and try again.');
          this.loading.set(false);
        },
      });
  }

  private loadMostWanted(): void {
    this.api.page({ sort: 'wanted', size: 5 }).subscribe({
      next: (res) => this.mostWanted.set(res.content),
      error: () => this.mostWanted.set([]),
    });
  }

  add(): void {
    const name = this.newName.trim();
    this.addError.set('');
    if (!name || !this.newCategory) return;
    this.api.create({ name, category: this.newCategory, color: this.newColor }).subscribe({
      next: () => {
        this.newName = '';
        this.onFilter();
        this.loadMostWanted();
      },
      // Unique name is enforced server-side (Skill.name has a unique constraint), so the 409 is the
      // answer rather than a client-side scan of the page currently loaded.
      error: (err) => this.addError.set(err?.error?.error ?? 'Could not add that skill.'),
    });
  }

  edit(s: Skill): void {
    this.editError.set('');
    this.editName = s.name;
    this.editCategory = s.category;
    this.editColor = s.color || PALETTE[0];
    this.focus.set(s);
  }

  save(): void {
    const s = this.focus();
    const name = this.editName.trim();
    this.editError.set('');
    if (!s || !name || !this.editCategory) return;
    this.api.update(s.id, { name, category: this.editCategory, color: this.editColor }).subscribe({
      next: () => {
        this.load();
        this.loadMostWanted();
      },
      error: (err) => this.editError.set(err?.error?.error ?? 'Could not save that skill.'),
    });
  }

  remove(s: Skill): void {
    const relations = (s.knownBy ?? 0) + (s.wantedBy ?? 0);
    const using = s.usedBy?.length ? ` ${s.usedBy.join(', ')} still use it.` : '';
    if (!confirm(`Delete ${s.name}? Removes ${relations} relations.${using}`)) return;
    this.error.set('');
    this.api.remove(s.id).subscribe({
      next: () => {
        if (this.focus()?.id === s.id) this.focus.set(null);
        this.load();
        this.loadMostWanted();
      },
      error: (err) => this.error.set(err?.error?.error ?? `Could not delete ${s.name}.`),
    });
  }

  rangeLabel(): string {
    const d = this.data();
    if (!d || !d.content.length) return 'No results';
    const start = this.page() * this.size + 1;
    return `Showing ${start}–${start + d.content.length - 1} of ${d.totalElements}`;
  }

  pageNumbers(): number[] {
    const d = this.data();
    if (!d) return [0];
    return Array.from({ length: d.totalPages }, (_, i) => i);
  }
}
