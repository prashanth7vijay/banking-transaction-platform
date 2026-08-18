import { clsx } from 'clsx';
import type { TransactionStatus, TransactionTimelineEntry } from '@/features/transactions/types';

/**
 * A fixed happy-path spine with terminal deviations (REJECTED/FAILED/CANCELLED)
 * appended as a distinct final step rather than bent into the spine.
 * "Done"/"current"/"upcoming" is derived from the transaction's actual
 * `transaction_state_history` (Phase 4), not guessed from its current status -
 * this is what correctly tells apart, for example, a transfer FAILED at
 * creation (blocked by risk, never reached approval) from one FAILED at
 * posting (approved, then failed while moving funds).
 */
const HAPPY_PATH: { label: string; statuses: TransactionStatus[] }[] = [
  { label: 'Created', statuses: ['SUBMITTED'] },
  { label: 'Risk Evaluation', statuses: ['PENDING_APPROVAL'] },
  { label: 'Approval', statuses: ['APPROVED'] },
  { label: 'Processing', statuses: ['PROCESSING'] },
  { label: 'Completed', statuses: ['COMPLETED'] },
];

const TERMINAL_DEVIATIONS: Record<string, string> = {
  REJECTED: 'Rejected',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

interface LifecycleStepperProps {
  status: TransactionStatus;
  timeline: TransactionTimelineEntry[];
}

export function LifecycleStepper({ status, timeline }: LifecycleStepperProps) {
  const reachedStatuses = new Set<string>(timeline.map((entry) => entry.toStatus));
  const deviation = TERMINAL_DEVIATIONS[status];

  return (
    <div className="flex items-center overflow-x-auto pb-1">
      {HAPPY_PATH.map((step, i) => {
        const isCurrent = step.statuses.includes(status);
        const isDone = !isCurrent && step.statuses.some((s) => reachedStatuses.has(s));
        const state = isCurrent && !deviation ? 'current' : isDone ? 'done' : 'upcoming';

        return (
          <div key={step.label} className="flex items-center shrink-0">
            <div className="flex flex-col items-center gap-1">
              <div
                className={clsx(
                  'h-3 w-3 rounded-full border-2',
                  state === 'done' && 'bg-green-500 border-green-500',
                  state === 'current' && 'bg-blue-500 border-blue-500',
                  state === 'upcoming' && 'bg-transparent border-border'
                )}
              />
              <span
                className={clsx(
                  'text-xs whitespace-nowrap',
                  state === 'upcoming' ? 'text-muted-foreground' : 'text-foreground font-medium'
                )}
              >
                {step.label}
              </span>
            </div>
            {i < HAPPY_PATH.length - 1 && (
              <div className={clsx('h-0.5 w-10 mx-1 mb-4', state === 'done' ? 'bg-green-500' : 'bg-border')} />
            )}
          </div>
        );
      })}
      {deviation && (
        <>
          <div className="h-0.5 w-10 mx-1 mb-4 bg-red-400" />
          <div className="flex flex-col items-center gap-1 shrink-0">
            <div className="h-3 w-3 rounded-full bg-red-500 border-2 border-red-500" />
            <span className="text-xs whitespace-nowrap text-red-500 font-medium">{deviation}</span>
          </div>
        </>
      )}
    </div>
  );
}
