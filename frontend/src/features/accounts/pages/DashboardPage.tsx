import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getMyAccounts } from '@/features/accounts/api/accountsApi';
import { formatCurrency } from '@/shared/lib/format';

export function DashboardPage() {
  const { data: accounts, isLoading, isError } = useQuery({
    queryKey: ['accounts'],
    queryFn: getMyAccounts,
  });

  if (isLoading) return <DashboardSkeleton />;
  if (isError) return <p className="text-red-500">Could not load your accounts.</p>;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Your Accounts</h1>
        <Link to="/beneficiaries" className="text-sm hover:underline text-muted-foreground">
          Manage beneficiaries
        </Link>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        {accounts?.map((account) => (
          <Link
            key={account.id}
            to={`/accounts/${account.id}`}
            className="rounded-lg border border-border p-5 hover:border-primary transition-colors"
          >
            <p className="text-sm text-muted-foreground">{account.accountType}</p>
            <p className="text-2xl font-semibold mt-1">{formatCurrency(account.balance)}</p>
            <p className="text-xs text-muted-foreground mt-2">•••• {account.accountNumber.slice(-4)}</p>
          </Link>
        ))}
      </div>
      {accounts?.length === 0 && (
        <p className="text-muted-foreground">You don't have any accounts yet.</p>
      )}
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {[0, 1].map((i) => (
        <div key={i} className="rounded-lg border border-border p-5 animate-pulse">
          <div className="h-3 w-16 bg-muted rounded mb-3" />
          <div className="h-7 w-28 bg-muted rounded mb-3" />
          <div className="h-3 w-20 bg-muted rounded" />
        </div>
      ))}
    </div>
  );
}
