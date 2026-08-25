import { Outlet, Link, NavLink, useLocation } from 'react-router-dom';
import { Moon, Sun } from 'lucide-react';
import { clsx } from 'clsx';
import { useTheme } from '@/app/ThemeProvider';
import { useAuth } from '@/features/auth/AuthContext';
import { Button } from '@/shared/components/ui/Button';
import { NotificationBell } from '@/features/notifications/components/NotificationBell';
import { UserMenu } from '@/shared/components/UserMenu';
import { ErrorBoundary } from '@/shared/components/ErrorBoundary';

function navLinkClass({ isActive }: { isActive: boolean }): string {
  return clsx(
    'px-2.5 py-1.5 rounded-md text-sm font-medium transition-colors',
    isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:text-foreground hover:bg-muted/60'
  );
}

export function AppLayout() {
  const { theme, toggleTheme } = useTheme();
  const { user, isAuthenticated } = useAuth();
  const location = useLocation();

  const isCustomer = user?.roles.includes('CUSTOMER');
  const isEmployee = user?.roles.includes('EMPLOYEE');
  const isAdmin = user?.roles.includes('ADMIN');

  return (
    <div className="min-h-screen flex flex-col">
      <header className="border-b border-border px-6 py-3 flex items-center justify-between gap-4">
        <Link to="/" className="font-semibold text-lg shrink-0">Transaction Platform</Link>

        {isAuthenticated && (
          <nav className="flex items-center gap-1 flex-1 overflow-x-auto">
            {isCustomer && (
              <>
                <NavLink to="/dashboard" className={navLinkClass}>Dashboard</NavLink>
                <NavLink to="/transfer" className={navLinkClass}>Transfer</NavLink>
                <NavLink to="/transactions" className={navLinkClass}>History</NavLink>
              </>
            )}
            {isEmployee && (
              <>
                <NavLink to="/command-center" className={navLinkClass}>Command Center</NavLink>
                <NavLink to="/approvals" className={navLinkClass}>Approvals</NavLink>
                <NavLink to="/exceptions" className={navLinkClass}>Exceptions</NavLink>
                <NavLink to="/customers" className={navLinkClass}>Customers</NavLink>
              </>
            )}
            {isAdmin && (
              <>
                <NavLink to="/admin" end className={navLinkClass}>Overview</NavLink>
                <NavLink to="/admin/users" className={navLinkClass}>Users</NavLink>
                <NavLink to="/admin/audit" className={navLinkClass}>Audit Log</NavLink>
              </>
            )}
          </nav>
        )}

        <div className="flex items-center gap-2 shrink-0">
          {isAuthenticated ? (
            <>
              <NotificationBell />
              <Button variant="ghost" onClick={toggleTheme} aria-label="Toggle theme">
                {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
              </Button>
              <UserMenu />
            </>
          ) : (
            <>
              <Link to="/login" className="text-sm font-medium hover:underline">Log in</Link>
              <Button variant="ghost" onClick={toggleTheme} aria-label="Toggle theme">
                {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
              </Button>
            </>
          )}
        </div>
      </header>
      <main className="flex-1 p-6">
        <ErrorBoundary key={location.pathname}>
          <Outlet />
        </ErrorBoundary>
      </main>
    </div>
  );
}