import js from '@eslint/js';
import ts from 'typescript-eslint';
import hooks from 'eslint-plugin-react-hooks';
export default ts.config(
  { ignores: ['dist/**','node_modules/**'] },
  js.configs.recommended,
  ...ts.configs.recommended,
  { files: ['**/*.{js,mjs,ts,tsx}'], languageOptions: { globals: { performance:'readonly',location:'readonly',navigator:'readonly',localStorage:'readonly',addEventListener:'readonly',removeEventListener:'readonly',scrollY:'readonly',scrollTo:'readonly',innerWidth:'readonly',console:'readonly', process:'readonly', URL:'readonly', setTimeout:'readonly', clearTimeout:'readonly', WebSocket:'readonly', Buffer:'readonly', document:'readonly', requestAnimationFrame:'readonly', window:'readonly', Image:'readonly', globalThis:'readonly' } } },
  { files: ['src/**/*.{ts,tsx}'], plugins: { 'react-hooks':hooks }, rules: { ...hooks.configs.recommended.rules, 'react-hooks/refs':'off', 'react-hooks/set-state-in-effect':'off', 'react-hooks/immutability':'off', '@typescript-eslint/no-explicit-any':'error' } },
);
