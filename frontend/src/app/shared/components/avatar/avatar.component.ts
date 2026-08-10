import { Component, Input } from '@angular/core';

@Component({
  selector: 'sa-avatar',
  standalone: true,
  templateUrl: './avatar.component.html',
  styleUrl: './avatar.component.css',
})
export class AvatarComponent {
  @Input() initials = '';
  @Input() size = 30;
}
