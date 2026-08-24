import { Component, Input, signal } from '@angular/core';

@Component({
  selector: 'sa-avatar',
  standalone: true,
  templateUrl: './avatar.component.html',
  styleUrl: './avatar.component.css',
})
export class AvatarComponent {
  @Input() initials = '';
  @Input() size = 30;
  @Input() src: string | null = null;

  // Not defensive padding: the signed URL expires after ~15 minutes, so a page left open long
  // enough will genuinely 403. Falling back to the initials is the same thing it showed before.
  readonly failed = signal(false);
}
