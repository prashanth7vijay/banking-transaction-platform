import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { listExceptions, listMyExceptions } from '@/features/exceptions/api/exceptionsApi';
import { ExceptionStatusBadge } from '@/features/exceptions/components/ExceptionStatusBadge';
import { PriorityBadge } from '@/features/exceptions/components/PriorityBadge';
import { SlaBadge } from '@/features/exceptions/components/SlaBadge';
import { formatCurrency } from '@/shared/lib/format';
import { Button } from '@/shared/components/ui/Button';
import type { ExceptionStatus } from '@/features/exceptions/types';

const OPEN_STATUSES: ExceptionStatus[] = ['OPEN', 'ASSIGNED', 'INVESTIGATING', 'ACTION_REQUIRED'];
const ALL_STATUSES: ExceptionStatus[] = ['OPEN', 'ASSIGNED', 'INVESTIGATING', 'ACTION_REQUIRED', 'RESOLVED', 'CLOSED'];

export function ExceptionQueuePage() {
  const [view, setView] = useState<'open' | 'mine' | 'all'>('open');

  const { data: exceptions, isLoading } = useQuery({
    queryKey: ['exceptions', view],
    queryFn: () =>
      view === 'mine'
        ? listMyExceptions()
        : view === 'all'
          ? listExceptions(ALL_STATUSES)
          : listExceptions(OPEN_STATUSES),
    refetchInterval: 30_000,
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Exception Queue</h1>
        <div className="flex items-center gap-2">
          <Button variant={view === 'open' ? 'default' : 'outline'} onClick={() => setView('open')}>
            Open Cases
          </Button>
          <Button variant={view === 'mine' ? 'default' : 'outline'} onClick={() => setView('mine')}>
            Assigned to Me
          </Button>
          <Button variant={view === 'all' ? 'default' : 'outline'} onClick={() => setView('all')}>
            All
          </Button>
        </div>
      </div>

      {isLoading && <p className="text-muted-foreground">Loading...</p>}

      {exceptions && exceptions.length === 0 && (
        <p className="text-muted-foreground">No exceptions here - nothing needs attention.</p>
      )}

      <div className="border border-border rounded-lg divide-y divide-border">
        {exceptions?.map((exc) => (
          <Link
            key={exc.id}
            to={`/exceptions/${exc.id}`}
            className="flex items-center justify-between px-4 py-3 hover:bg-muted transition-colors"
          >
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <span className="font-medium">{formatCurrency(exc.amount)}</span>
                <PriorityBadge priority={exc.priority} />
                <ExceptionStatusBadge status={exc.status} />
                <SlaBadge status={exc.slaStatus} slaDueAt={exc.slaDueAt} />
              </div>
              <p className="text-sm">{exc.reason}</p>
              <p className="text-xs text-muted-foreground">
                Opened {new Date(exc.createdAt).toLocaleString()}
                {exc.assignedTo && ` · Assigned to ${exc.assignedTo.firstName ?? exc.assignedTo.email}`}
              </p>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
