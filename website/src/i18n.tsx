import {sitePath} from './site-path.js';
import {createContext,useContext,useLayoutEffect,useState,useCallback} from 'react';
import type {ReactNode} from 'react';
import {flushSync} from 'react-dom';
import english from './en.json';
import {chooseLocale,pathLocale} from './locale.js';
import {preserveReadingPosition} from './reading-position';
export type Locale='en'|'it';
const messages:Record<string,string>=english;
const translate=(key:string,locale:Locale)=>locale==='en'?(messages[key]??key):key;
const Context=createContext<{locale:Locale;t:(key:string)=>string;setLocale:(locale:Locale)=>void}>({locale:'en',t:key=>translate(key,'en'),setLocale:()=>{}});
const metadata={en:{title:'Songlass | Your music, your way',description:'Your music, your way. Discover Songlass: a local music player with three interfaces to choose from.'},it:{title:'Songlass | La musica, a modo tuo',description:'La tua musica, a modo tuo. Scopri Songlass: un lettore musicale locale, tre interfacce tra cui scegliere.'}};
export function LocaleProvider({initialLocale='en',children}:{initialLocale?:Locale;children:ReactNode}){
 const[locale,setLanguage]=useState<Locale>(initialLocale);
 useLayoutEffect(()=>{
  const sync=()=>{let saved;try{saved=localStorage.getItem('songlass-language');}catch{/* optional storage */}const query=new URLSearchParams(location.search).get('lang');const explicit=query==='en'||query==='it'?query:pathLocale(location.pathname);setLanguage(chooseLocale(navigator.languages,saved,explicit) as Locale);};
  sync();addEventListener('popstate',sync);return()=>removeEventListener('popstate',sync);
 },[]);
 useLayoutEffect(()=>{document.documentElement.lang=locale;document.title=metadata[locale].title;document.querySelector('meta[name=description]')?.setAttribute('content',metadata[locale].description);},[locale]);
 const setLocale=useCallback((next:Locale)=>{preserveReadingPosition(()=>flushSync(()=>setLanguage(next)));try{localStorage.setItem('songlass-language',next);}catch{/* optional storage */}const url=new URL(location.href);url.pathname=sitePath(`${next}/`);url.searchParams.delete('lang');history.replaceState(null,'',url.pathname+url.search+url.hash);},[]);
 return <Context.Provider value={{locale,setLocale,t:key=>translate(key,locale)}}>{children}</Context.Provider>;
}
export const useI18n=()=>useContext(Context);
export function LanguageToggle(){const{locale,setLocale}=useI18n();return <a className="language-toggle" href={sitePath(locale==='en'?'it/':'en/')} hrefLang={locale==='en'?'it':'en'} lang={locale} aria-label={locale==='en'?'Switch to Italian':'Passa all’inglese'} title={locale==='en'?'Italiano':'English'} onClick={event=>{if(event.ctrlKey||event.metaKey||event.shiftKey||event.altKey)return;event.preventDefault();setLocale(locale==='en'?'it':'en');}}>{locale==='en'?'IT':'EN'}</a>;}
