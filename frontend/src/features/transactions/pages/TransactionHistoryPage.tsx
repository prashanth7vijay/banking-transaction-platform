import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { listMyTransactions } from '@/features/transactions/api/transactionsApi';
import { StatusBadge } from '@/features/transactions/components/StatusBadge';
import { formatCurrency } from '@/shared/lib/format';

export function TransactionHistoryPage() {
  const { data: transactions, isLoading } = useQuery({
    queryKey: ['transactions', 'mine'],
    queryFn: listMyTransactions,
  });

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Transaction History</h1>
        <Link to="/transfer" className="text-sm hover:underline text-muted-foreground">
          New transfer
        </Link>
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
          <p className="text-muted-foreground text-sm px-4 py-6">No transactions yet.</p>
        )}
      </div>
    </div>
  );
}
