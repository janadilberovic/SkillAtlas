import { Component, Input } from '@angular/core';

@Component({
  selector: 'sa-empty-state',
  standalone: true,
  templateUrl: './empty-state.component.html',
  styleUrl: './empty-state.component.css',
})
export class EmptyStateComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() message = '';
}
