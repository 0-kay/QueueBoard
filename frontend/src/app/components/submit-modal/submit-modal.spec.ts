import { ComponentFixture, TestBed } from '@angular/core/testing';
import { appError } from '../../api/app-error';
import { JobType, SubmitJobRequest } from '../../api/models';
import { REFERENCE } from '../../testing/fixtures';
import { SubmitModal } from './submit-modal';

describe('SubmitModal', () => {
  let fixture: ComponentFixture<SubmitModal>;
  const el = () => fixture.nativeElement as HTMLElement;
  // The form is internal; tests drive it the way the Material controls would.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const form = () => (fixture.componentInstance as unknown as { form: any }).form;
  const submitBtn = () => el().querySelector('[data-testid=submit]') as HTMLButtonElement;
  const field = (name: string) => el().querySelector(`[data-testid=${name}]`);

  beforeEach(async () => {
    fixture = TestBed.createComponent(SubmitModal);
    fixture.componentRef.setInput('reference', REFERENCE);
    await fixture.whenStable();
  });

  async function chooseType(type: JobType) {
    form().controls.type.setValue(type);
    await fixture.whenStable();
  }

  it('shows the fields for the chosen type', async () => {
    expect(field('courseCode')).toBeNull();

    await chooseType('GRADE_REPORT');
    expect(field('courseCode')).not.toBeNull();
    expect(field('templateCode')).toBeNull();
    expect(field('termCode')).toBeNull();

    await chooseType('NOTIFICATION_BATCH');
    expect(field('templateCode')).not.toBeNull();
    expect(field('courseCode')).not.toBeNull();
    expect(field('channel')).not.toBeNull();
    expect(field('termCode')).toBeNull();

    await chooseType('BILLING_ASSESSMENT');
    expect(field('termCode')).not.toBeNull();
    expect(field('applyLateFees')).not.toBeNull();
    expect(field('courseCode')).toBeNull();
  });

  it('enables Submit once required fields are filled and emits a JSON-object payload', async () => {
    expect(submitBtn().disabled).toBe(true);
    form().controls.name.setValue('Midterm announcement');
    await chooseType('NOTIFICATION_BATCH');
    expect(submitBtn().disabled).toBe(true);
    form().patchValue({ templateCode: 'ANNOUNCE_MIDTERM', courseCode: 'CSCI-110', channel: 'PUSH' });
    await fixture.whenStable();
    expect(submitBtn().disabled).toBe(false);

    const emitted: SubmitJobRequest[] = [];
    fixture.componentInstance.submitJob.subscribe((r) => emitted.push(r));
    submitBtn().click();
    expect(emitted).toEqual([{
      name: 'Midterm announcement',
      type: 'NOTIFICATION_BATCH',
      payload: { templateCode: 'ANNOUNCE_MIDTERM', courseCode: 'CSCI-110', channel: 'PUSH' },
    }]);
  });

  it('disables Submit (and the fields) while the request is in flight', async () => {
    form().controls.name.setValue('Midterm grades CSCI-110');
    await chooseType('GRADE_REPORT');
    form().controls.courseCode.setValue('CSCI-110');
    await fixture.whenStable();
    expect(submitBtn().disabled).toBe(false);

    fixture.componentRef.setInput('sending', true);
    await fixture.whenStable();
    expect(submitBtn().disabled).toBe(true);
    expect(submitBtn().textContent).toContain('Submitting');
    expect(form().disabled).toBe(true);

    fixture.componentRef.setInput('sending', false);
    await fixture.whenStable();
    expect(submitBtn().disabled).toBe(false);
  });

  it('shows 400 errors under the matching fields and the description at the top', async () => {
    form().controls.name.setValue('Midterm grades CSCI-099');
    await chooseType('GRADE_REPORT');
    form().controls.courseCode.setValue('PHYS-099');
    await fixture.whenStable();

    fixture.componentRef.setInput('serverError', appError(400, 'Some fields need attention.', [
      { field: 'payload.courseCode', message: 'Course not found' },
      { field: 'name', message: 'Name is taken' },
      { field: 'somethingElse', message: 'Unmapped problem' },
    ]));
    await fixture.whenStable();

    expect(field('form-error')?.textContent).toContain('Some fields need attention.');
    expect(field('form-error')?.textContent).toContain('Unmapped problem');
    expect(field('error-courseCode')?.textContent).toContain('Course not found');
    expect(field('error-name')?.textContent).toContain('Name is taken');
    // A server error doesn't block resubmitting.
    expect(submitBtn().disabled).toBe(false);
  });

  it('labels closed and inactive options', async () => {
    await chooseType('NOTIFICATION_BATCH');
    const component = fixture.componentInstance as unknown as { reference: () => typeof REFERENCE };
    expect(component.reference().courses.some((c) => c.status === 'CLOSED')).toBe(true);
    // Options render inside the select overlay; check the template renders suffixes when opened.
    (field('courseCode') as HTMLElement).click();
    await fixture.whenStable();
    const overlay = document.querySelector('.cdk-overlay-container')!;
    expect(overlay.textContent).toContain('(closed)');
  });

  it('pre-fills name, type and payload for Duplicate, keeping extra payload keys', async () => {
    fixture = TestBed.createComponent(SubmitModal);
    fixture.componentRef.setInput('reference', REFERENCE);
    fixture.componentRef.setInput('prefill', {
      name: 'FA26 late fees', type: 'BILLING_ASSESSMENT',
      payload: { termCode: 'FA26', applyLateFees: true, simulateFailure: 'TEMPORARY' },
    });
    await fixture.whenStable();
    expect(el().querySelector('h2')?.textContent).toContain('Duplicate job');
    const emitted: SubmitJobRequest[] = [];
    fixture.componentInstance.submitJob.subscribe((r) => emitted.push(r));
    submitBtn().click();
    expect(emitted[0]).toEqual({
      name: 'FA26 late fees', type: 'BILLING_ASSESSMENT',
      payload: { termCode: 'FA26', applyLateFees: true, simulateFailure: 'TEMPORARY' },
    });
  });
});
