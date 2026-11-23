import {spawnSync} from 'node:child_process';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

const projectId='crayfarm-eb2ea';
const appId='1:356838404753:web:7f79b20d4c68935b188530';
const reportOnly=process.argv.includes('--report');
const cli=fileURLToPath(new URL('../node_modules/firebase-tools/lib/bin/firebase.js',import.meta.url));
const projectRoot=fileURLToPath(new URL('..',import.meta.url));
const config=JSON.parse(await readFile(new URL('../firebase.production.json',import.meta.url),'utf8'));
const checks={project:false,webApp:false,hosting:false,functionsApi:false,billing:'manual-confirmation-required'};

function command(args,allowFailure=false){
  const result=spawnSync(process.execPath,[cli,...args],{cwd:projectRoot,encoding:'utf8',windowsHide:true});
  let payload;try{payload=JSON.parse(result.stdout);}catch{}
  if(result.status!==0&&!allowFailure)throw new Error(payload?.error||result.stderr.trim()||`Firebase command failed: ${args.join(' ')}`);
  return {ok:result.status===0,payload};
}
function requireReady(condition,message){if(!condition)throw new Error(`Deployment readiness failed: ${message}`);}

try{
  requireReady(!('database' in config),'production configuration must not include database rules');
  requireReady(config.functions?.[0]?.runtime==='nodejs22','Functions runtime is not Node.js 22');
  const projects=command(['projects:list','--json']).payload?.result??[];
  checks.project=projects.some(project=>project.projectId===projectId&&project.projectNumber==='356838404753'&&project.state==='ACTIVE');
  requireReady(checks.project,'the expected Firebase project is not active or accessible');
  const apps=command(['apps:list','WEB','--project',projectId,'--json']).payload?.result??[];
  checks.webApp=apps.some(app=>app.appId===appId&&app.state==='ACTIVE');
  requireReady(checks.webApp,'the reviewed Firebase Web app is not active');
  const sites=command(['hosting:sites:list','--project',projectId,'--json']).payload?.result?.sites??[];
  checks.hosting=sites.some(site=>site.name===`projects/${projectId}/sites/${projectId}`);
  requireReady(checks.hosting,'the expected Firebase Hosting site does not exist');
  checks.functionsApi=command(['functions:list','--project',projectId,'--json'],true).ok;
  if(!checks.functionsApi&&!reportOnly)throw new Error('Deployment readiness failed: Cloud Functions API is disabled. Enable cloudfunctions.googleapis.com after upgrading to Blaze, wait a few minutes, then retry.');
  console.log(JSON.stringify({projectId,ready:checks.project&&checks.webApp&&checks.hosting&&checks.functionsApi,checks,next:checks.functionsApi?'Confirm Blaze and budget alerts before deployment.':'Upgrade to Blaze, set budget alerts, and enable cloudfunctions.googleapis.com.'},null,2));
}catch(error){console.error(error.message);process.exitCode=1;}
