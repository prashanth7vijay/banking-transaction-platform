import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { listTransactionsByStatus } from '@/features/transactions/api/transactionsApi';
import { StatusBadge } from '@/features/transactions/components/StatusBadge';
import { formatCurrency } from '@/shared/lib/format';
import type { TransactionStatus } from '@/features/transactions/types';

const ALL_STATUSES: TransactionStatus[] = [
  'SUBMITTED',
  'PENDING_APPROVAL',
  'APPROVED',
  'PROCESSING',
  'COMPLETED',
  'REJECTED',
  'FAILED',
  'CANCELLED',
];

export function TransactionsByStatusPage() {
  const [searchParams] = useSearchParams();
  const status = (searchParams.get('status') ?? 'FAILED') as TransactionStatus;

  const { data: transactions, isLoading } = useQuery({
    queryKey: ['transactions', 'by-status', status],
    queryFn: () => listTransactionsByStatus(status),
  });

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Transactions — {status.replace('_', ' ')}</h1>
        <div className="flex flex-wrap gap-1">
          {ALL_STATUSES.map((s) => (
            <Link
              key={s}
              to={`/transactions/by-status?status=${s}`}
              className={`text-xs rounded-full px-2.5 py-1 border ${
                s === status ? 'border-foreground font-medium' : 'border-border text-muted-foreground hover:border-foreground'
              }`}
            >
              {s.replace('_', ' ')}
            </Link>
          ))}
        </div>
      </div>

      {isLoading && <p className="text-muted-foreground">Loading...</p>}

      <div className="border border-border rounded-lg divide-y divide-border">
        {transactions?.map((tx) => (
          <Link
            key={tx.id}
            to={`/transactions/${tx.id}`}
            className="flex items-center justify-between px-4 py-3 hover:bg-muted transition-colors"
          >
            <div>
              <p className="font-medium">To •••• {tx.destinationAccountNumber.slice(-4)}</p>
              <p className="text-xs text-muted-foreground">
                {new Date(tx.createdAt).toLocaleString()}
                {tx.note && ` · ${tx.note}`}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <span className="font-medium">{formatCurrency(tx.amount)}</span>
              <StatusBadge status={tx.status} />
            </div>
          </Link>
        ))}
        {transactions?.length === 0 && (
          <p className="text-muted-foreground text-sm px-4 py-6">No transactions with this status.</p>
        )}
      </div>
    </div>
  );
}
