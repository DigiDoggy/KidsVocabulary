import { render, screen } from '@testing-library/react';
import { App } from './App';

describe('App', () => {
  afterEach(() => vi.restoreAllMocks());

  it('shows languages when the backend is available', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          name: 'KidsVocabulary',
          version: '0.1.0',
          languages: [
            { code: 'en', name: 'English' },
            { code: 'et', name: 'Estonian' },
            { code: 'ru', name: 'Russian' },
          ],
        }),
        { status: 200 },
      ),
    );

    render(<App />);

    expect(await screen.findByText(/Server is up/)).toBeInTheDocument();
    expect(screen.getByText('Estonian')).toBeInTheDocument();
  });

  it('shows an error when the backend is unavailable', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 502 }));

    render(<App />);

    expect(await screen.findByRole('alert')).toHaveTextContent('HTTP 502');
  });
});
