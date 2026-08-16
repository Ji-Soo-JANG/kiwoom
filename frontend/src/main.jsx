import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.jsx';
import './index.css';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AuthGate from './components/AuthGate.jsx';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (failureCount, error) =>
        failureCount < 2 &&
        (error?.code === 'KIWOOM_RATE_LIMITED' ||
          error?.code === 'KIWOOM_UPSTREAM_UNAVAILABLE' ||
          error?.status >= 500),
      refetchOnWindowFocus: false
    },
    mutations: { retry: false }
  }
});

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <AuthGate>
          {({ currentUser, onLogout }) => <App currentUser={currentUser} onLogout={onLogout} />}
        </AuthGate>
      </QueryClientProvider>
    </BrowserRouter>
  </React.StrictMode>
);
