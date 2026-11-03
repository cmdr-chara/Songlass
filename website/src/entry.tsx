import {LocaleProvider} from './i18n';
import { createRoot, hydrateRoot } from 'react-dom/client';
import App from './App';
import 'lenis/dist/lenis.css';
import '../styles.css';
import './experience.css';
import './editions.css';
import './footer.css';
const root = document.getElementById('root')!;
const app = <LocaleProvider initialLocale={document.documentElement.lang==='it'?'it':'en'}><App /></LocaleProvider>;
if (root.hasChildNodes()) hydrateRoot(root, app);
else createRoot(root).render(app);

import './signature.css';

import './scroll-and-theme.css';
