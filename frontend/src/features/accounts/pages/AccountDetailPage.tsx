import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { getAccount } from '@/features/accounts/api/accountsApi';
import { formatCurrency } from '@/shared/lib/format';

export function AccountDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data: account, isLoading, isError } = useQuery({
    queryKey: ['accounts', id],
    queryFn: () => getAccount(id!),
    enabled: !!id,
  });

  if (isLoading) return <p className="text-muted-foreground">Loading...</p>;
  if (isError || !account) return <p className="text-red-500">Account not found.</p>;

  return (
    <div className="max-w-lg">
      <Link to="/dashboard" className="text-sm text-muted-foreground hover:underline">
        ← Back to accounts
      </Link>
      <h1 className="text-2xl font-semibold mt-3 mb-1">{account.accountType} Account</h1>
      <p className="text-sm text-muted-foreground mb-6">Account number {account.accountNumber}</p>

      <div className="rounded-lg border border-border p-6">
        <p className="text-sm text-muted-foreground">Available balance</p>
        <p className="text-3xl font-semibold mt-1">{formatCurrency(account.balance)}</p>
        <p className="text-xs text-muted-foreground mt-3">Status: {account.status}</p>
      </div>

      <p className="text-sm text-muted-foreground mt-6">
        Transfers and transaction history arrive in Phase 3.
      </p>
    </div>
  );
}
