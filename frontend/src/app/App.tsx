import { Routes, Route } from 'react-router-dom';
import { AppLayout } from '@/app/AppLayout';
import { HealthCheckPage } from '@/routes/HealthCheckPage';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { RegisterPage } from '@/features/auth/pages/RegisterPage';
import { ForgotPasswordPage } from '@/features/auth/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '@/features/auth/pages/ResetPasswordPage';
import { ProfilePage } from '@/features/users/pages/ProfilePage';
import { DashboardPage } from '@/features/accounts/pages/DashboardPage';
import { AccountDetailPage } from '@/features/accounts/pages/AccountDetailPage';
import { BeneficiariesPage } from '@/features/accounts/pages/BeneficiariesPage';
import { TransferPage } from '@/features/transactions/pages/TransferPage';
import { TransactionHistoryPage } from '@/features/transactions/pages/TransactionHistoryPage';
import { TransactionDetailPage } from '@/features/transactions/pages/TransactionDetailPage';
import { ApprovalQueuePage } from '@/features/transactions/pages/ApprovalQueuePage';
import { TransactionsByStatusPage } from '@/features/transactions/pages/TransactionsByStatusPage';
import { ExceptionQueuePage } from '@/features/exceptions/pages/ExceptionQueuePage';
import { ExceptionDetailPage } from '@/features/exceptions/pages/ExceptionDetailPage';
import { CommandCenterPage } from '@/features/dashboard/pages/CommandCenterPage';
import { CustomerSearchPage } from '@/features/customer360/pages/CustomerSearchPage';
import { CustomerDetailPage } from '@/features/customer360/pages/CustomerDetailPage';
import { AuditDashboardPage } from '@/features/audit/pages/AuditDashboardPage';
import { AdminOverviewPage } from '@/features/admin/pages/AdminOverviewPage';
import { UserManagementPage } from '@/features/admin/pages/UserManagementPage';
import { ManageAccountsPage } from '@/features/employee/pages/ManageAccountsPage';

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<HealthCheckPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />

        <Route element={<ProtectedRoute />}>
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/transactions/:id" element={<TransactionDetailPage />} />
        </Route>

        <Route element={<ProtectedRoute roles={['CUSTOMER']} />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/accounts/:id" element={<AccountDetailPage />} />
          <Route path="/beneficiaries" element={<BeneficiariesPage />} />
          <Route path="/transfer" element={<TransferPage />} />
          <Route path="/transactions" element={<TransactionHistoryPage />} />
        </Route>

        <Route element={<ProtectedRoute roles={['EMPLOYEE']} />}>
          <Route path="/command-center" element={<CommandCenterPage />} />
          <Route path="/approvals" element={<ApprovalQueuePage />} />
          <Route path="/employee/accounts" element={<ManageAccountsPage />} />
          <Route path="/exceptions" element={<ExceptionQueuePage />} />
          <Route path="/exceptions/:id" element={<ExceptionDetailPage />} />
          <Route path="/transactions/by-status" element={<TransactionsByStatusPage />} />
          <Route path="/customers" element={<CustomerSearchPage />} />
          <Route path="/customers/:id" element={<CustomerDetailPage />} />
        </Route>

        <Route element={<ProtectedRoute roles={['ADMIN']} />}>
          <Route path="/admin" element={<AdminOverviewPage />} />
          <Route path="/admin/users" element={<UserManagementPage />} />
          <Route path="/admin/audit" element={<AuditDashboardPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
