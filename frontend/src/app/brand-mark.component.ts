import { Component, input } from '@angular/core';

@Component({
  selector: 'app-brand-mark',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 32 32" fill="none" aria-hidden="true">
      <path d="M11 19.5 4.5 27 11 24.8z" [attr.fill]="color()" />
      <path d="M21 19.5 27.5 27 21 24.8z" [attr.fill]="color()" />
      <path d="M16 2.2c3.8 3.9 5.8 8.6 5.8 13 0 2.6-0.9 5-2.3 6.9-0.2 0.3-0.6 0.4-0.9 0.3L16 21l-2.6 1.4c-0.3 0.1-0.7 0-0.9-0.3C11.1 20.2 10.2 17.8 10.2 15.2c0-4.4 2-9.1 5.8-13z" [attr.fill]="color()" />
      <circle cx="16" cy="13" r="2.4" fill="#ffffff" />
      <path d="M16 23.4c-1.3 1.5-2 3-2 4.1 0 0.8 0.7 1.3 2 1.3s2-0.5 2-1.3c0-1.1-0.7-2.6-2-4.1z" fill="#F59E0B" />
    </svg>
  `,
  styles: [':host { display: inline-flex; }'],
})
export class BrandMarkComponent {
  readonly size = input(26);
  readonly color = input('#5B82FF');
}
