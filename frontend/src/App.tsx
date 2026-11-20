import { useRef, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import { Spinner } from './components/ui';
import { LoginPage, RegisterPage } from './features/auth/AuthPages';
import { ChildHome } from './features/child/ChildHome';
import { ChildLayout } from './features/child/ChildLayout';
import { TrainingPage } from './features/child/TrainingPage';
import { ChildFormPage } from './features/parent/ChildFormPage';
import { ChildPage } from './features/parent/ChildPage';
import { ChildrenPage } from './features/parent/ChildrenPage';
import { MonthPage } from './features/parent/MonthPage';
import { ParentLayout } from './features/parent/ParentLayout';
import { ProfilePicker } from './features/parent/ProfilePicker';
import { SettingsPage } from './features/parent/SettingsPage';
import { WeekPage } from './features/parent/WeekPage';
import { I18nProvider, type UiLanguage } from './i18n/I18nProvider';

/** Sends each mode to its own area: anonymous → login, child mode → child screens, parent → parent screens. */
function Guard({ need, children }: { need: 'anonymous' | 'PARENT' | 'CHILD'; children: ReactNode }) {
  const auth = useAuth();
  // remembered so that returning from child mode opens that child's page
  const lastChildId = useRef<number | null>(null);
  if (auth.status === 'signed-in' && auth.childId !== null) {
    lastChildId.current = auth.childId;
  }
  if (auth.status === 'loading') {
    return (
      <main className="content">
        <Spinner />
      </main>
    );
  }
  const current = auth.status === 'anonymous' ? 'anonymous' : auth.mode;
  if (current === need) {
    return <>{children}</>;
  }
  const parentHome = need === 'CHILD' && lastChildId.current !== null ? `/children/${lastChildId.current}` : '/';
  const home = current === 'anonymous' ? '/login' : current === 'CHILD' ? '/child' : parentHome;
  return <Navigate to={home} replace />;
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Guard need="anonymous"><LoginPage /></Guard>} />
      <Route path="/register" element={<Guard need="anonymous"><RegisterPage /></Guard>} />
      <Route path="/child" element={<Guard need="CHILD"><ChildLayout /></Guard>}>
        <Route index element={<ChildHome />} />
        <Route path="train" element={<TrainingPage />} />
      </Route>
      <Route element={<Guard need="PARENT"><ParentLayout /></Guard>}>
        <Route index element={<ChildrenPage />} />
        <Route path="children/new" element={<ChildFormPage />} />
        <Route path="children/:id" element={<ChildPage />} />
        <Route path="children/:id/edit" element={<ChildFormPage />} />
        <Route path="weeks/:weekId" element={<WeekPage />} />
        <Route path="months/:monthId" element={<MonthPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="pick" element={<ProfilePicker />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export function App({ initialLang }: { initialLang?: UiLanguage }) {
  return (
    <I18nProvider initialLang={initialLang}>
      <ErrorBoundary>
        <AuthProvider>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </AuthProvider>
      </ErrorBoundary>
    </I18nProvider>
  );
}
