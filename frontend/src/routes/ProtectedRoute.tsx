import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '@/features/auth/AuthContext';

interface ProtectedRouteProps {
  roles?: string[];
}

export function ProtectedRoute({ roles }: ProtectedRouteProps) {
  const { user, isLoading, isAuthenticated } = useAuth();

  if (isLoading) {
    return <div className="p-6 text-muted-foreground">Loading...</div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (roles && roles.length > 0 && !roles.some((r) => user?.roles.includes(r))) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
