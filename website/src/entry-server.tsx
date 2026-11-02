import { renderToString } from 'react-dom/server';
import App from './App';
import {LocaleProvider} from './i18n';
import type {Locale} from './i18n';
export function render(locale:Locale='en') { return renderToString(<LocaleProvider initialLocale={locale}><App /></LocaleProvider>); }
