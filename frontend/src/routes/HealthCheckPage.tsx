import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { useAuth } from '@/features/auth/AuthContext';
import { Button } from '@/shared/components/ui/Button';

interface HealthResponse {
  status: string;
}

async function fetchHealth(): Promise<HealthResponse> {
  const { data } = await axios.get<HealthResponse>('/actuator/health');
  return data;
}

/** Where a logged-in user actually wants to land, by role. */
function homeRouteFor(roles: string[]): string {
  if (roles.includes('ADMIN')) return '/admin';
  if (roles.includes('EMPLOYEE')) return '/command-center';
  return '/dashboard';
}

export function HealthCheckPage() {
  const { user, isAuthenticated, isLoading } = useAuth();
  const navigate = useNavigate();

  const { data: health } = useQuery({
    queryKey: ['backend-health'],
    queryFn: fetchHealth,
    refetchInterval: 30_000,
    retry: false,
  });

  useEffect(() => {
    if (!isLoading && isAuthenticated && user) {
      navigate(homeRouteFor(user.roles), { replace: true });
    }
  }, [isLoading, isAuthenticated, user, navigate]);

  if (isLoading || isAuthenticated) {
    return null;
  }

  return (
    <div className="min-h-[calc(100vh-73px)] flex flex-col items-center justify-center px-4 text-center">
      <h1 className="text-3xl font-semibold mb-3">Transaction Platform</h1>
      <p className="text-muted-foreground max-w-md mb-8">
        A modular banking transaction platform — risk-evaluated transfers, maker-checker approval,
        double-entry ledger posting, and full operational tooling for exceptions and investigations.
      </p>
      <Button onClick={() => navigate('/login')}>Log In</Button>

      <div className="mt-16 flex items-center gap-2 text-xs text-muted-foreground">
        <span
          className={`h-1.5 w-1.5 rounded-full ${health?.status === 'UP' ? 'bg-green-500' : 'bg-muted-foreground/40'}`}
        />
        {health?.status === 'UP' ? 'All systems operational' : 'Checking system status...'}
      </div>
    </div>
  );
}