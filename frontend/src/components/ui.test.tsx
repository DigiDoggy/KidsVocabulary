import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { ApiError } from '../api/http';
import { I18nProvider } from '../i18n/I18nProvider';
import { ErrorBoundary } from './ErrorBoundary';
import { ErrorMessage, Modal, OfflineBanner } from './ui';

const wrap = (ui: React.ReactNode) => render(<I18nProvider initialLang="en">{ui}</I18nProvider>);

describe('ErrorMessage', () => {
  it('shows the localised text, folds the server detail away and offers a retry', async () => {
    const retry = vi.fn();
    wrap(<ErrorMessage error={new ApiError(409, 'CONFLICT', 'That week already has a set')} onRetry={retry} />);

    expect(screen.getByRole('alert')).toHaveTextContent('This action is not possible right now.');
    expect(screen.getByText('That week already has a set').closest('details')).not.toHaveAttribute('open');
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(retry).toHaveBeenCalledOnce();
  });
});

describe('OfflineBanner', () => {
  it('appears while the browser is offline', () => {
    wrap(<OfflineBanner />);
    expect(screen.queryByRole('status')).toBeNull();
    act(() => void window.dispatchEvent(new Event('offline')));
    expect(screen.getByRole('status')).toHaveTextContent('No internet connection');
    act(() => void window.dispatchEvent(new Event('online')));
    expect(screen.queryByRole('status')).toBeNull();
  });
});

describe('Modal', () => {
  function Opener() {
    const [open, setOpen] = useState(false);
    return (
      <>
        <button onClick={() => setOpen(true)}>Open</button>
        {open && (
          <Modal title="Dialog" onClose={() => setOpen(false)}>
            <button>First</button>
            <button>Last</button>
          </Modal>
        )}
      </>
    );
  }

  it('keeps focus inside and returns it to the opener on Escape', async () => {
    wrap(<Opener />);
    await userEvent.click(screen.getByRole('button', { name: 'Open' }));

    expect(screen.getByRole('dialog', { name: 'Dialog' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'First' })).toHaveFocus();
    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'Last' })).toHaveFocus();
    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'First' })).toHaveFocus();
    await userEvent.tab({ shift: true });
    expect(screen.getByRole('button', { name: 'Last' })).toHaveFocus();

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.getByRole('button', { name: 'Open' })).toHaveFocus();
  });
});

describe('ErrorBoundary', () => {
  it('replaces a crashed screen with a reload page', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    function Broken(): never {
      throw new Error('boom');
    }
    wrap(
      <ErrorBoundary>
        <Broken />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong');
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument();
    vi.restoreAllMocks();
  });
});
