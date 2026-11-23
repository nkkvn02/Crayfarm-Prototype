import {readFile} from 'node:fs/promises';

const expectedProject = 'crayfarm-eb2ea';
const expectedAppId = '1:356838404753:web:7f79b20d4c68935b188530';
const production = JSON.parse(await readFile(new URL('../firebase.production.json', import.meta.url), 'utf8'));
const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
const builtHtml = await readFile(new URL('../dist/index.html', import.meta.url), 'utf8');
const assetMatch = builtHtml.match(/src="([^"]+\.js)"/);

function requireRelease(condition, message) {
  if (!condition) throw new Error(`Release preflight failed: ${message}`);
}

requireRelease(!('database' in production), 'production config must never contain a database-rules target');
requireRelease(production.hosting?.site === expectedProject, 'Hosting site is not locked to crayfarm-eb2ea');
requireRelease(production.functions?.length === 1, 'exactly one Functions codebase is required');
requireRelease(production.functions[0].codebase === 'crayfarm-admin', 'unexpected Functions codebase');
requireRelease(production.functions[0].source === 'functions', 'unexpected Functions source directory');
requireRelease(production.functions[0].runtime === 'nodejs22', 'Functions runtime must be locked to Node.js 22');
requireRelease(source.includes(expectedProject), 'frontend source does not target the reviewed Firebase project');
requireRelease(source.includes(expectedAppId), 'frontend source does not target the reviewed Firebase web app');
requireRelease(assetMatch, 'production build has no JavaScript entry asset');

const builtJs = await readFile(new URL(`../dist${assetMatch[1]}`, import.meta.url), 'utf8');
requireRelease(builtJs.includes(expectedProject), 'production bundle is missing the production project ID');
requireRelease(!builtJs.includes('demo-crayfarm'), 'production bundle contains emulator project configuration');
requireRelease(!builtJs.includes('127.0.0.1:9099'), 'production bundle contains the Auth emulator endpoint');
requireRelease(!builtJs.includes('127.0.0.1:5001'), 'production bundle contains the Functions emulator endpoint');
requireRelease(!builtJs.includes('/api/'), 'production bundle contains the localhost backend route');

console.log('Release preflight passed: production project/app verified; emulator endpoints absent; database rules excluded.');
