import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

export interface ToastAction {
  label: string;
  run: () => void;
}

/** Toasts. No color, no success icon (see design 6c). */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  show(message: string, action?: ToastAction): void {
    const ref = this.snackBar.open(message, action?.label, {
      duration: 4000,
      horizontalPosition: 'center',
      verticalPosition: 'bottom',
    });
    if (action) ref.onAction().subscribe(() => action.run());
  }
}
