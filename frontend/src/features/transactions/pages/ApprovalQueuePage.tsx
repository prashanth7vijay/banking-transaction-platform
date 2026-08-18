import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import {
  approveTransaction,
  getApprovalSlaSummary,
  listApprovalWorkbench,
  rejectTransaction,
} from '@/features/transactions/api/transactionsApi';
import { Button } from '@/shared/components/ui/Button';
import { Alert } from '@/shared/components/ui/Alert';
import { RiskBadge } from '@/features/transactions/components/RiskBadge';
import { SlaBadge } from '@/features/transactions/components/SlaBadge';
import { formatCurrency } from '@/shared/lib/format';

function extractError(err: unknown): string {
  if (axios.isAxiosError(err) && err.response?.data?.message) {
    return err.response.data.message;
  }
  return 'Action failed';
}

export function ApprovalQueuePage() {
  const queryClient = useQueryClient();
  const [actionError, setActionError] = useState<string | null>(null);

  const { data: items, isLoading } = useQuery({
    queryKey: ['transactions', 'pending', 'workbench'],
    queryFn: listApprovalWorkbench,
    refetchInterval: 30_000,
  });

  const { data: slaSummary } = useQuery({
    queryKey: ['transactions', 'pending', 'sla-summary'],
    queryFn: getApprovalSlaSummary,
    refetchInterval: 30_000,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['transactions', 'pending'] });
  };

  const approveMutation = useMutation({ mutationFn: approveTransaction, onSuccess: invalidate });
  const rejectMutation = useMutation({
    mutationFn: (id: string) => rejectTransaction(id),
    onSuccess: invalidate,
  });

  const anyPending = approveMutation.isPending || rejectMutation.isPending;

  const handleApprove = async (id: string) => {
    setActionError(null);
    try {
      await approveMutation.mutateAsync(id);
    } catch (err) {
      setActionError(extractError(err));
    }
  };

  const handleReject = async (id: string) => {
    setActionError(null);
    try {
      await rejectMutation.mutateAsync(id);
    } catch (err) {
      setActionError(extractError(err));
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Approval Workbench</h1>
        {slaSummary && slaSummary.total > 0 && (
          <div className="flex items-center gap-3 text-xs text-muted-foreground">
            <span>{slaSummary.total} pending</span>
            <span className="text-green-600 font-medium">{slaSummary.within} within SLA</span>
            <span className="text-amber-600 font-medium">{slaSummary.atRisk} at risk</span>
            <span className="text-red-500 font-medium">{slaSummary.breached} breached</span>
          </div>
        )}
      </div>

      {actionError && <Alert>{actionError}</Alert>}
      {isLoading && <p className="text-muted-foreground">Loading...</p>}
      {items?.length === 0 && <p className="text-muted-foreground text-sm">Nothing pending approval.</p>}

      <div className="space-y-4">
        {items?.map((item) => (
          <div key={item.transactionId} className="border border-border rounded-lg p-4 space-y-3">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-lg font-semibold">
                  {formatCurrency(item.amount)}{' '}
                  <span className="text-sm font-normal text-muted-foreground">
                    → •••• {item.destinationAccountNumber.slice(-4)}
                  </span>
                </p>
                <p className="text-xs text-muted-foreground">
                  {item.customer && `${item.customer.firstName ?? item.customer.email} · `}
                  Submitted {new Date(item.createdAt).toLocaleString()}
                  {item.note && ` · ${item.note}`}
                </p>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <RiskBadge level={item.riskLevel} />
                <SlaBadge status={item.slaStatus} slaDueAt={item.slaDueAt} />
              </div>
            </div>

            <div className="bg-muted/50 rounded-md p-3 space-y-1">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                Why does this require approval?
              </p>
              <ul className="text-sm list-disc list-inside space-y-0.5">
                {item.whyApprovalRequired.map((reason, i) => (
                  <li key={i}>{reason}</li>
                ))}
              </ul>
              {item.amountToAverageRatio != null && (
                <p className="text-xs text-muted-foreground pt-1">
                  {item.amountToAverageRatio.toFixed(1)}× this customer's average completed transfer
                  {item.customerAverageAmount && ` (${formatCurrency(item.customerAverageAmount)})`}
                </p>
              )}
            </div>

            <div className="flex items-center justify-between pt-1">
              <Link to={`/transactions/${item.transactionId}`} className="text-xs text-muted-foreground hover:underline">
                View Transaction 360 →
              </Link>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => handleReject(item.transactionId)} disabled={anyPending}>
                  Reject
                </Button>
                <Button onClick={() => handleApprove(item.transactionId)} disabled={anyPending}>
                  Approve
                </Button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
