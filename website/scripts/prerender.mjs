import { createServer } from 'vite';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
const server=await createServer({server:{middlewareMode:true},appType:'custom'});
const siteBase=server.config.base;
try {
  const {render}=await server.ssrLoadModule('/src/entry-server.tsx');
  const base=await readFile('dist/index.html','utf8');
  for(const [route,locale] of [['','en'],['en','en'],['it','it']]){
    let html=base.replace('<div id="root"></div>','<div id="root">'+render(locale)+'</div>').replace('</head>',`<link rel="alternate" hreflang="en" href="${siteBase}en/"><link rel="alternate" hreflang="it" href="${siteBase}it/"><link rel="alternate" hreflang="x-default" href="${siteBase}"></head>`);
    if(locale==='it')html=html.replace('lang="en"','lang="it"').replace('Songlass | Your music, your way','Songlass | La musica, a modo tuo').replace('Your music, your way. Discover Songlass: a local music player with three interfaces to choose from.','La tua musica, a modo tuo. Scopri Songlass: un lettore musicale locale, tre interfacce tra cui scegliere.').replaceAll('data-locale-url content="https://cmdr-chara.github.io/Songlass/"','data-locale-url content="https://cmdr-chara.github.io/Songlass/it/"');
    const dir=route?'dist/'+route:'dist';await mkdir(dir,{recursive:true});await writeFile(dir+'/index.html',html);
  }
  console.log('HTML pre-renderizzato: inglese e italiano, leggibili anche senza JavaScript.');
} finally { await server.close(); }
