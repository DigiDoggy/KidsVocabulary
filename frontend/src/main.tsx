import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
// child-area fonts (self-hosted; unicode-range keeps unused scripts from loading)
import '@fontsource/andika/700.css';
import '@fontsource/nunito/700.css';
import '@fontsource/nunito/800.css';
import '@fontsource/nunito/900.css';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
