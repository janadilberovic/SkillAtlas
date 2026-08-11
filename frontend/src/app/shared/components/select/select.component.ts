import { Component, ElementRef, HostListener, computed, inject, input, model, signal } from '@angular/core';

export interface SelectOption {
  value: string;
  label: string;
}

/**
 * A select whose open list we actually control.
 *
 * A native `<select>` popup is drawn by the OS, so it can't be themed — colouring
 * `option` is the only lever and it makes Chrome on Windows drop the selection
 * highlight. This renders the list itself instead.
 */
@Component({
  selector: 'sa-select',
  standalone: true,
  templateUrl: './select.component.html',
  styleUrl: './select.component.css',
})
export class SelectComponent {
  readonly options = input<SelectOption[]>([]);
  readonly placeholder = input('Select…');
  readonly value = model('');

  readonly open = signal(false);
  /** Keyboard cursor; -1 is the placeholder row. */
  readonly active = signal(-1);

  private readonly host = inject(ElementRef<HTMLElement>);

  readonly label = computed(
    () => this.options().find((o) => o.value === this.value())?.label ?? this.placeholder(),
  );

  toggle(): void {
    this.open() ? this.close() : this.openList();
  }

  openList(): void {
    this.active.set(this.options().findIndex((o) => o.value === this.value()));
    this.open.set(true);
  }

  close(): void {
    this.open.set(false);
  }

  pick(option: SelectOption): void {
    this.value.set(option.value);
    this.close();
  }

  clear(): void {
    this.value.set('');
    this.close();
  }

  onKeydown(event: KeyboardEvent): void {
    const options = this.options();
    if (!this.open()) {
      if (event.key === 'Enter' || event.key === ' ' || event.key === 'ArrowDown') {
        event.preventDefault();
        this.openList();
      }
      return;
    }
    switch (event.key) {
      case 'Escape':
        this.close();
        break;
      case 'ArrowDown':
        event.preventDefault();
        this.active.set(Math.min(options.length - 1, this.active() + 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.active.set(Math.max(-1, this.active() - 1));
        break;
      case 'Home':
        event.preventDefault();
        this.active.set(-1);
        break;
      case 'End':
        event.preventDefault();
        this.active.set(options.length - 1);
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        this.active() < 0 ? this.clear() : this.pick(options[this.active()]);
        break;
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }
}
