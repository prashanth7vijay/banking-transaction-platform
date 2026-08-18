import { type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getTransaction360 } from '@/features/transactions/api/transactionsApi';
import { getExceptionByTransaction } from '@/features/exceptions/api/exceptionsApi';
import { StatusBadge } from '@/features/transactions/components/StatusBadge';
import { RiskBadge } from '@/features/transactions/components/RiskBadge';
import { LifecycleStepper } from '@/features/transactions/components/LifecycleStepper';
import { ExceptionStatusBadge } from '@/features/exceptions/components/ExceptionStatusBadge';
import { formatCurrency } from '@/shared/lib/format';
import { useAuth } from '@/features/auth/AuthContext';

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

function partyLabel(party: { firstName: string | null; email: string } | null): string {
  if (!party) return '—';
  return party.firstName ? `${party.firstName} (${party.email})` : party.email;
}

export function TransactionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const isOpsUser = user?.roles.includes('EMPLOYEE') || user?.roles.includes('ADMIN');

  const { data, isLoading, error } = useQuery({
    queryKey: ['transactions', id, '360'],
    queryFn: () => getTransaction360(id!),
    enabled: !!id,
  });

  // Only fetched for employees/admins (the exceptions API is EMPLOYEE-only),
  // and only once we know the transaction actually FAILED - Transaction 360
  // links forward to the exception this way rather than the backend embedding
  // it, so `transactions` never has to depend on the `exceptions` module. See
  // ExceptionController's javadoc for the full reasoning.
  const { data: linkedException } = useQuery({
    queryKey: ['exceptions', 'by-transaction', id],
    queryFn: () => getExceptionByTransaction(id!),
    enabled: !!id && !!isOpsUser && data?.transaction.status === 'FAILED',
  });

  if (isLoading) {
    return <p className="text-muted-foreground">Loading...</p>;
  }

  if (error || !data) {
    return <p className="text-red-500 text-sm">Could not load this transaction.</p>;
  }

  const { transaction, customer, sourceAccount, destinationAccount, risk, approval, ledgerLines, timeline } = data;

  return (
    <div className="space-y-6">
      <div>
        <Link to="/transactions" className="text-xs text-muted-foreground hover:underline">
          ← Back to transactions
        </Link>
        <div className="flex items-center justify-between mt-1">
          <h1 className="text-2xl font-semibold">Transaction 360</h1>
          <div className="flex items-center gap-2">
            <RiskBadge level={risk?.riskLevel ?? null} />
            <StatusBadge status={transaction.status} />
          </div>
        </div>
      </div>

      {linkedException && (
        <Link
          to={`/exceptions/${linkedException.id}`}
          className="flex items-center justify-between border border-red-500/30 bg-red-500/5 rounded-lg px-4 py-3 hover:bg-red-500/10 transition-colors"
        >
          <span className="text-sm">
            This transaction has an associated exception: <span className="font-medium">{linkedException.reason}</span>
          </span>
          <span className="flex items-center gap-2 text-sm font-medium">
            <ExceptionStatusBadge status={linkedException.status} />
            View Investigation →
          </span>
        </Link>
      )}

      <Section title="Lifecycle">
        <LifecycleStepper status={transaction.status} timeline={timeline} />
      </Section>

      <Section title="Summary">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Field label="Transaction ID" value={<span className="font-mono text-xs">{transaction.id}</span>} />
          <Field label="Amount" value={formatCurrency(transaction.amount)} />
          <Field label="Currency" value={transaction.currency} />
          <Field label="Type" value={transaction.type} />
          <Field label="Customer" value={partyLabel(customer)} />
          <Field
            label="Source Account"
            value={sourceAccount ? `•••• ${sourceAccount.accountNumber.slice(-4)}` : '—'}
          />
          <Field
            label="Destination Account"
            value={destinationAccount ? `•••• ${destinationAccount.accountNumber.slice(-4)}` : transaction.destinationAccountNumber}
          />
          <Field label="Created" value={new Date(transaction.createdAt).toLocaleString()} />
        </div>
        {transaction.note && (
          <p className="text-sm text-muted-foreground mt-4 border-t border-border pt-3">Note: {transaction.note}</p>
        )}
      </Section>

      <Section title="Risk">
        {risk ? (
          <div className="space-y-3">
            <div className="flex items-center gap-3">
              <RiskBadge level={risk.riskLevel} />
              {risk.blocked && (
                <span className="text-xs font-medium text-red-500">Blocked at risk evaluation</span>
              )}
              <span className="text-xs text-muted-foreground">
                Assessed {new Date(risk.assessedAt).toLocaleString()}
              </span>
            </div>
            {risk.reasons.length > 0 ? (
              <ul className="text-sm list-disc list-inside space-y-1">
                {risk.reasons.map((reason, i) => (
                  <li key={i}>{reason}</li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted-foreground">No risk signals triggered.</p>
            )}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">No risk assessment recorded for this transaction.</p>
        )}
      </Section>

      <Section title="Approval">
        {approval ? (
          <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
            <Field
              label="Decision"
              value={
                <span className={approval.decision === 'REJECTED' ? 'text-red-500' : 'text-green-600'}>
                  {approval.decision ?? '—'}
                </span>
              }
            />
            <Field label="Decided By" value={partyLabel(approval.approvedBy)} />
            <Field label="Decided At" value={new Date(approval.approvedAt).toLocaleString()} />
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">Awaiting an approval decision.</p>
        )}
      </Section>

      <Section title="Ledger">
        {ledgerLines.length > 0 ? (
          <div className="divide-y divide-border">
            {ledgerLines.map((line, i) => (
              <div key={i} className="flex items-center justify-between py-2 text-sm">
                <div>
                  <span
                    className={
                      line.direction === 'DEBIT' ? 'text-red-500 font-medium' : 'text-green-600 font-medium'
                    }
                  >
                    {line.direction}
                  </span>{' '}
                  <span className="text-muted-foreground">
                    {line.accountNumber ? `•••• ${line.accountNumber.slice(-4)}` : 'Unknown account'}
                  </span>
                </div>
                <div className="flex items-center gap-4">
                  <span className="font-medium">{formatCurrency(line.amount)}</span>
                  <span className="text-xs text-muted-foreground">
                    {new Date(line.postedAt).toLocaleString()}
                  </span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">No ledger postings yet - funds have not moved for this transaction.</p>
        )}
      </Section>

      <Section title="Audit Timeline">
        <div className="space-y-3">
          {timeline.map((entry, i) => (
            <div key={i} className="flex items-start gap-3 text-sm">
              <span className="text-xs text-muted-foreground w-40 shrink-0">
                {new Date(entry.occurredAt).toLocaleString()}
              </span>
              <span>
                {entry.fromStatus ? (
                  <>
                    <span className="text-muted-foreground">{entry.fromStatus}</span> → <span className="font-medium">{entry.toStatus}</span>
                  </>
                ) : (
                  <>
                    Transaction created (<span className="font-medium">{entry.toStatus}</span>)
                  </>
                )}
              </span>
            </div>
          ))}
          {timeline.length === 0 && <p className="text-sm text-muted-foreground">No history recorded.</p>}
        </div>
      </Section>
    </div>
  );
}
