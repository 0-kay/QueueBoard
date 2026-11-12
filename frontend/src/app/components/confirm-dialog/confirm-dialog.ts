import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel: string;
}

/** Generic confirmation (used for Delete). Closes with `true` when confirmed. */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content><p>{{ data.message }}</p></mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false" cdkFocusInitial>Cancel</button>
      <button mat-flat-button class="danger" [mat-dialog-close]="true" data-testid="confirm">{{ data.confirmLabel }}</button>
    </mat-dialog-actions>
  `,
  styles: `
    p { margin: 0; font: 400 14px/20px Roboto, sans-serif; color: #49454f; }
    .danger.mat-mdc-unelevated-button { --mat-button-filled-container-color: #b3261e; --mat-button-filled-label-text-color: #fff; }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
