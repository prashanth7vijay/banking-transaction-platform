import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button } from '@/shared/components/ui/Button';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  error: Error | null;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled UI error:', error, info.componentStack);
  }

  handleReset = () => {
    this.setState({ error: null });
  };

  render() {
    if (this.state.error) {
      return (
        <div className="p-6 max-w-md mx-auto text-center">
          <p className="text-lg font-semibold mb-2">Something went wrong</p>
          <p className="text-sm text-muted-foreground mb-4">
            This part of the page hit an unexpected error. You can try again, or navigate elsewhere.
          </p>
          <Button onClick={this.handleReset}>Try again</Button>
        </div>
      );
    }
    return this.props.children;
  }
}
