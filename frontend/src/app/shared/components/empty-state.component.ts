import { Component, Input } from '@angular/core';

@Component({
  selector: 'sa-empty-state',
  standalone: true,
  template: `
    <div class="empty card-flat">
      @if (eyebrow) {
        <span class="eyebrow eyebrow-mute">{{ eyebrow }}</span>
      }
      <span class="title">{{ title }}</span>
      @if (message) {
        <p class="muted msg">{{ message }}</p>
      }
      <ng-content></ng-content>
    </div>
  `,
  styles: [
    `
      .empty {
        display: flex;
        flex-direction: column;
        gap: 9px;
      }
      .title {
        font-size: 20px;
        font-weight: 500;
        line-height: 1.2;
      }
      .msg {
        font-size: 13px;
        margin: 0;
      }
    `,
  ],
})
export class EmptyStateComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() message = '';
}
