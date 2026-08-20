import { Component, ElementRef, HostListener, computed, inject, input, model, signal } from '@angular/core';

interface DayCell {
  key: string;
  day: number;
  inMonth: boolean;
  selected: boolean;
  today: boolean;
  disabled: boolean;
}

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** Local-time yyyy-mm-dd. `toISOString()` is UTC and shifts the date by one east of Greenwich. */
function iso(d: Date): string {
  const m = d.getMonth() + 1;
  const day = d.getDate();
  return `${d.getFullYear()}-${m < 10 ? '0' : ''}${m}-${day < 10 ? '0' : ''}${day}`;
}

/**
 * A date field whose calendar we actually draw.
 *
 * Same reason `sa-select` exists: the native `<input type="date">` popup is an OS widget,
 * so it can't be themed and reads as a different app on a dark surface.
 */
@Component({
  selector: 'sa-datepicker',
  standalone: true,
  templateUrl: './datepicker.component.html',
  styleUrl: './datepicker.component.css',
})
export class DatepickerComponent {
  readonly value = model('');
  readonly placeholder = input('Pick a date');
  /** Earliest selectable day, yyyy-mm-dd. Anything before it is rendered disabled. */
  readonly min = input('');
  readonly clearable = input(false);

  readonly open = signal(false);
  readonly weekdays = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

  /** The month on screen, `YYYY-MM`. Null follows the value (or today) instead of pinning. */
  private readonly view = signal<string | null>(null);
  /** Keyboard cursor, yyyy-mm-dd. Null until the arrows are used. */
  readonly cursor = signal<string | null>(null);

  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly today = iso(new Date());

  readonly label = computed(() => {
    const v = this.value();
    if (!v) return this.placeholder();
    return `${SHORT[+v.slice(5, 7) - 1]} ${+v.slice(8, 10)}, ${v.slice(0, 4)}`;
  });

  readonly monthLabel = computed(() => {
    const v = this.month();
    return `${MONTHS[+v.slice(5, 7) - 1]} ${v.slice(0, 4)}`;
  });

  readonly cells = computed<DayCell[]>(() => {
    const v = this.month();
    const year = +v.slice(0, 4);
    const month = +v.slice(5, 7) - 1;
    const min = this.min();
    const selected = this.value();
    const lead = (new Date(year, month, 1).getDay() + 6) % 7;

    // Always 42 cells: the sheet keeps one height, so it never jumps as you page months.
    return Array.from({ length: 42 }, (_, i) => {
      const d = new Date(year, month, 1 - lead + i);
      const key = iso(d);
      const inMonth = d.getMonth() === month;
      return {
        key,
        day: d.getDate(),
        inMonth,
        selected: inMonth && key === selected,
        today: inMonth && key === this.today,
        disabled: !!min && key < min,
      };
    });
  });

  toggle(): void {
    this.open() ? this.close() : this.openSheet();
  }

  openSheet(): void {
    this.view.set(null);
    this.cursor.set(this.value() || null);
    this.open.set(true);
  }

  close(): void {
    this.open.set(false);
    this.cursor.set(null);
  }

  pick(key: string): void {
    this.value.set(key);
    this.close();
  }

  pickToday(): void {
    this.pick(this.today);
  }

  clear(): void {
    this.value.set('');
    this.close();
  }

  shiftMonth(step: number): void {
    const v = this.month();
    this.view.set(iso(new Date(+v.slice(0, 4), +v.slice(5, 7) - 1 + step, 1)).slice(0, 7));
  }

  onKeydown(event: KeyboardEvent): void {
    if (!this.open()) {
      if (event.key === 'Enter' || event.key === ' ' || event.key === 'ArrowDown') {
        event.preventDefault();
        this.openSheet();
      }
      return;
    }
    switch (event.key) {
      case 'Escape':
        this.close();
        break;
      case 'ArrowLeft':
        event.preventDefault();
        this.moveCursor(-1);
        break;
      case 'ArrowRight':
        event.preventDefault();
        this.moveCursor(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.moveCursor(-7);
        break;
      case 'ArrowDown':
        event.preventDefault();
        this.moveCursor(7);
        break;
      case 'Enter':
      case ' ': {
        event.preventDefault();
        const key = this.cursor();
        if (key && !this.isBlocked(key)) this.pick(key);
        break;
      }
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  private month(): string {
    return this.view() ?? (this.value() || this.today).slice(0, 7);
  }

  private isBlocked(key: string): boolean {
    const min = this.min();
    return !!min && key < min;
  }

  // Walking off the edge of the month pulls the view along with the cursor.
  private moveCursor(days: number): void {
    const base = this.cursor() || this.value() || this.today;
    const d = new Date(+base.slice(0, 4), +base.slice(5, 7) - 1, +base.slice(8, 10) + days);
    const key = iso(d);
    this.cursor.set(key);
    this.view.set(key.slice(0, 7));
  }
}
