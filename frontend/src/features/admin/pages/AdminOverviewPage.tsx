import { useQuery } from '@tanstack/react-query';
import { getAdminMetrics } from '@/features/admin/api/adminApi';
import { formatCurrency } from '@/shared/lib/format';

export function AdminOverviewPage() {
  const { data: metrics, isLoading } = useQuery({
    queryKey: ['admin', 'metrics'],
    queryFn: getAdminMetrics,
  });

  if (isLoading || !metrics) {
    return <p className="text-muted-foreground">Loading...</p>;
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-6">Admin Overview</h1>

      <div className="grid gap-4 sm:grid-cols-3 mb-8">
        <MetricCard label="Total accounts" value={metrics.totalAccounts.toString()} />
        <MetricCard label="Total balance across accounts" value={formatCurrency(metrics.totalBalance)} />
        <MetricCard
          label="Total users"
          value={Object.values(metrics.usersByRole).reduce((a, b) => a + b, 0).toString()}
        />
      </div>

      <div className="grid gap-6 sm:grid-cols-2">
        <BreakdownCard title="Users by role" data={metrics.usersByRole} />
        <BreakdownCard title="Transactions by status" data={metrics.transactionsByStatus} />
      </div>
    </div>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border p-5">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="text-2xl font-semibold mt-1">{value}</p>
    </div>
  );
}

function BreakdownCard({ title, data }: { title: string; data: Record<string, number> }) {
  const entries = Object.entries(data);
  return (
    <div className="rounded-lg border border-border p-5">
      <p className="text-sm font-medium mb-3">{title}</p>
      <div className="space-y-2">
        {entries.map(([key, value]) => (
          <div key={key} className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">{key}</span>
            <span className="font-medium">{value}</span>
          </div>
        ))}
        {entries.length === 0 && <p className="text-sm text-muted-foreground">No data yet.</p>}
      </div>
    </div>
  );
}
