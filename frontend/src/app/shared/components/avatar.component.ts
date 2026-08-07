import { Component, Input } from '@angular/core';

@Component({
  selector: 'sa-avatar',
  standalone: true,
  template: `<div class="avatar" [style.width.px]="size" [style.height.px]="size" [style.fontSize.px]="size * 0.34">{{ initials }}</div>`,
  styles: [
    `
      .avatar {
        border-radius: var(--radius);
        border: 1px solid var(--accent);
        color: var(--accent-text);
        display: grid;
        place-items: center;
        flex: none;
      }
    `,
  ],
})
export class AvatarComponent {
  @Input() initials = '';
  @Input() size = 30;
}
