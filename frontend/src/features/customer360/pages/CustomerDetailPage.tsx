import { type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getCustomer360 } from '@/features/customer360/api/customer360Api';
import { RiskBadge } from '@/features/transactions/components/RiskBadge';
import { ExceptionStatusBadge } from '@/features/exceptions/components/ExceptionStatusBadge';
import { PriorityBadge } from '@/features/exceptions/components/PriorityBadge';
import { formatCurrency } from '@/shared/lib/format';
import type { RiskLevel } from '@/features/transactions/types';
import type { ExceptionStatus, ExceptionPriority } from '@/features/exceptions/types';

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="border border-border rounded-lg p-4">
      <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide mb-3">{title}</h2>
      {children}
    </div>
  );
}

function Field({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-sm font-medium">{value ?? '—'}</p>
    </div>
  );
}

export function CustomerDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data, isLoading, error } = useQuery({
    queryKey: ['customers', id, '360'],
    queryFn: () => getCustomer360(id!),
    enabled: !!id,
  });

  if (isLoading) return <p className="text-muted-foreground">Loading...</p>;
  if (error || !data) return <p className="text-red-500 text-sm">Could not load this customer.</p>;

  const { profile, accounts, transactionActivity, risk, openExceptions, timeline } = data;

  return (
    <div className="space-y-6">
      <div>
        <Link to="/customers" className="text-xs text-muted-foreground hover:underline">
          ← Back to customers
        </Link>
        <div className="flex items-center justify-between mt-1">
          <div>
            <h1 className="text-2xl font-semibold">{profile.firstName} {profile.lastName}</h1>
            <p className="text-sm text-muted-foreground">{profile.email}</p>
          </div>
          <div className="flex items-center gap-2">
            <RiskBadge level={risk.mostRecentRiskLevel as RiskLevel | null} />
            <span className="text-xs rounded-full px-2.5 py-0.5 bg-muted font-medium">{profile.status}</span>
          </div>
        </div>
      </div>

      <Section title="Overview">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Field label="Customer Since" value={new Date(profile.customerSince).toLocaleDateString()} />
          <Field label="Accounts" value={accounts.length} />
          <Field label="Open Exceptions" value={openExceptions.length} />
          <Field label="30-Day Activity" value={`${transactionActivity.last30DayCount} transfers`} />
        </div>
      </Section>

      <Section title="Accounts">
        {accounts.length > 0 ? (
          <div className="divide-y divide-border">
            {accounts.map((a) => (
              <div key={a.id} className="flex items-center justify-between py-2 text-sm">
                <span className="text-muted-foreground">•••• {a.accountNumber.slice(-4)}</span>
                <div className="flex items-center gap-3">
                  <span className="font-medium">{formatCurrency(a.balance)}</span>
                  <span className="text-xs rounded-full px-2 py-0.5 bg-muted">{a.status}</span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">No accounts.</p>
        )}
      </Section>

      <Section title="Transaction Activity">
        <div className="grid grid-cols-2 md:grid-cols-5 gap-4 mb-4">
          <Field label="30-Day Volume" value={transactionActivity.last30DayCount} />
          <Field label="30-Day Value" value={formatCurrency(transactionActivity.last30DayValue)} />
          <Field
            label="Avg. Amount"
            value={transactionActivity.averageTransactionAmount ? formatCurrency(transactionActivity.averageTransactionAmount) : '—'}
          />
          <Field label="Pending" value={transactionActivity.pendingCount} />
          <Field label="Failed" value={transactionActivity.failedCount} />
        </div>
        <div className="divide-y divide-border border-t border-border pt-2">
          {transactionActivity.recentTransactions.map((t) => (
            <Link
              key={t.id}
              to={`/transactions/${t.id}`}
              className="flex items-center justify-between py-2 text-sm hover:bg-muted transition-colors -mx-2 px-2 rounded"
            >
              <span>
                → •••• {t.destinationAccountNumber.slice(-4)}{' '}
                <span className="text-xs text-muted-foreground">{new Date(t.createdAt).toLocaleDateString()}</span>
              </span>
              <div className="flex items-center gap-2">
                <span className="font-medium">{formatCurrency(t.amount)}</span>
                <span className="text-xs text-muted-foreground">{t.status.replace('_', ' ')}</span>
              </div>
            </Link>
          ))}
          {transactionActivity.recentTransactions.length === 0 && (
            <p className="text-sm text-muted-foreground py-2">No transactions in the last 90 days.</p>
          )}
        </div>
      </Section>

      <Section title="Risk Profile">
        <div className="space-y-3">
          <div className="flex items-center gap-4 text-sm">
            {Object.entries(risk.assessmentCountByLevel).map(([level, count]) => (
              <span key={level}>
                <span className="font-medium">{count}</span>{' '}
                <span className="text-muted-foreground">{level.toLowerCase()}</span>
              </span>
            ))}
            {Object.keys(risk.assessmentCountByLevel).length === 0 && (
              <span className="text-muted-foreground">No assessed transactions in the last 90 days.</span>
            )}
          </div>
          {risk.distinctRiskReasons.length > 0 && (
            <ul className="text-sm list-disc list-inside space-y-0.5">
              {risk.distinctRiskReasons.map((reason, i) => (
                <li key={i}>{reason}</li>
              ))}
            </ul>
          )}
        </div>
      </Section>

      <Section title="Open Exceptions">
        {openExceptions.length > 0 ? (
          <div className="divide-y divide-border">
            {openExceptions.map((e) => (
              <Link
                key={e.id}
                to={`/exceptions/${e.id}`}
                className="flex items-center justify-between py-2 text-sm hover:bg-muted transition-colors -mx-2 px-2 rounded"
              >
                <div>
                  <p>{e.reason}</p>
                  <p className="text-xs text-muted-foreground">
                    {e.assignedToName ? `Assigned to ${e.assignedToName}` : 'Unassigned'}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <PriorityBadge priority={e.priority as ExceptionPriority} />
                  <ExceptionStatusBadge status={e.status as ExceptionStatus} />
                </div>
              </Link>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">No open exceptions.</p>
        )}
      </Section>

      <Section title="Activity Timeline">
        <div className="space-y-3">
          {timeline.map((entry, i) => (
            <div key={i} className="flex items-start gap-3 text-sm">
              <span className="text-xs text-muted-foreground w-40 shrink-0">
                {new Date(entry.occurredAt).toLocaleString()}
              </span>
              <span>{entry.description}</span>
            </div>
          ))}
          {timeline.length === 0 && <p className="text-sm text-muted-foreground">No recent activity.</p>}
        </div>
      </Section>
    </div>
  );
}
