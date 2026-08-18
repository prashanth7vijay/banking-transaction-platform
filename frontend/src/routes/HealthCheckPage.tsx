import { useQuery } from '@tanstack/react-query';
import axios from 'axios';

interface HealthResponse {
  status: string;
}

async function fetchHealth(): Promise<HealthResponse> {
  const { data } = await axios.get<HealthResponse>('/actuator/health');
  return data;
}

export function HealthCheckPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['backend-health'],
    queryFn: fetchHealth,
    refetchInterval: 10_000,
  });

  return (
    <div className="max-w-md">
      <h1 className="text-2xl font-semibold mb-4">System Status</h1>
      <div className="rounded-lg border border-border p-4">
        <p className="text-sm text-muted-foreground mb-1">Backend</p>
        {isLoading && <p>Checking...</p>}
        {isError && <p className="text-red-500">Unreachable</p>}
        {data && (
          <p className={data.status === 'UP' ? 'text-green-500 font-medium' : 'text-red-500 font-medium'}>
            {data.status}
          </p>
        )}
      </div>
      <p className="text-sm text-muted-foreground mt-4">
        Login, dashboard, and feature routes arrive in Phase 1.
      </p>
    </div>
  );
}
