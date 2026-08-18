import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { Moon, Sun } from 'lucide-react';
import { useTheme } from '@/app/ThemeProvider';
import { useAuth } from '@/features/auth/AuthContext';
import { Button } from '@/shared/components/ui/Button';
import { NotificationBell } from '@/features/notifications/components/NotificationBell';
import { ErrorBoundary } from '@/shared/components/ErrorBoundary';

export function AppLayout() {
  const { theme, toggleTheme } = useTheme();
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const isCustomer = user?.roles.includes('CUSTOMER');
  const isEmployee = user?.roles.includes('EMPLOYEE');
  const isAdmin = user?.roles.includes('ADMIN');

  return (
    <div className="min-h-screen flex flex-col">
      <header className="border-b border-border px-6 py-4 flex items-center justify-between">
        <Link to="/" className="font-semibold text-lg">Transaction Platform</Link>
        <nav className="flex items-center gap-4 text-sm">
          {isAuthenticated ? (
            <>
              {isCustomer && (
                <>
                  <Link to="/dashboard" className="hover:underline">Dashboard</Link>
                  <Link to="/transfer" className="hover:underline">Transfer</Link>
                  <Link to="/transactions" className="hover:underline">History</Link>
                </>
              )}
              {isEmployee && (
                <Link to="/command-center" className="hover:underline">Command Center</Link>
              )}
              {isEmployee && (
                <Link to="/approvals" className="hover:underline">Approvals</Link>
              )}
              {isEmployee && (
                <Link to="/exceptions" className="hover:underline">Exceptions</Link>
              )}
              {isEmployee && (
                <Link to="/customers" className="hover:underline">Customers</Link>
              )}
              {isEmployee && (
                <Link to="/employee/accounts" className="hover:underline">Manage Accounts</Link>
              )}
              {isAdmin && (
                <>
                  <Link to="/admin" className="hover:underline">Overview</Link>
                  <Link to="/admin/users" className="hover:underline">Users</Link>
                  <Link to="/admin/audit" className="hover:underline">Audit Log</Link>
                </>
              )}
              <span className="text-muted-foreground hidden sm:inline">
                {user?.firstName} · {user?.roles.join(', ')}
              </span>
              <NotificationBell />
              <Link to="/profile" className="hover:underline">Profile</Link>
              <Button variant="outline" onClick={handleLogout}>Log out</Button>
            </>
          ) : (
            <>
              <Link to="/login" className="hover:underline">Log in</Link>
              <Link to="/register" className="hover:underline">Register</Link>
            </>
          )}
          <Button variant="ghost" onClick={toggleTheme} aria-label="Toggle theme">
            {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
          </Button>
        </nav>
      </header>
      <main className="flex-1 p-6">
        <ErrorBoundary key={location.pathname}>
          <Outlet />
        </ErrorBoundary>
      </main>
    </div>
  );
}
