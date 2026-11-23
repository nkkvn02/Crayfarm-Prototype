import {useMemo, useState} from 'react';
import {AlertTriangle, Check, CircleOff, Cpu, Plus, RefreshCw, RotateCcw, ShieldOff, ToggleLeft, ToggleRight} from 'lucide-react';

type Props={operations:any[];busy:boolean;onRefresh:()=>Promise<void>;onRecover:(kind:'account'|'device')=>Promise<void>;onReconcile:(operation:any)=>Promise<void>};
const labels:Record<string,string>={disable:'Account disabled',enable:'Account re-enabled',revokeAdmin:'Admin access revoked',register:'Device registered',activate:'Device activated',deactivate:'Device deactivated'};
const icons:Record<string,typeof Check>={disable:CircleOff,enable:Check,revokeAdmin:ShieldOff,register:Plus,activate:ToggleRight,deactivate:ToggleLeft};

export default function Activity({operations,busy,onRefresh,onRecover,onReconcile}:Props){
 const [filter,setFilter]=useState('all');
 const pendingAccount=!!sessionStorage.getItem('crayfarm-pending'),pendingDevice=!!sessionStorage.getItem('crayfarm-device-pending');
 const visible=useMemo(()=>operations.slice().reverse().filter(operation=>filter==='all'||filter===operation.kind||(filter==='attention'&&operation.status!=='succeeded')),[operations,filter]);
 const filters=[['all','All'],['account','Accounts'],['device','Devices'],['attention','Needs attention']];
 return <section className="activity-view">
  <div className="activity-toolbar"><div className="activity-filters" aria-label="Activity filters">{filters.map(([value,label])=><button key={value} className={filter===value?'filter-chip active':'filter-chip'} onClick={()=>setFilter(value)}>{label}</button>)}</div><div className="activity-actions"><button className="secondary compact" disabled={busy} onClick={()=>void onRefresh()}><RefreshCw size={15}/>Refresh</button>{pendingAccount&&<button className="secondary compact" disabled={busy} onClick={()=>void onRecover('account')}><RotateCcw size={15}/>Recover account</button>}{pendingDevice&&<button className="secondary compact" disabled={busy} onClick={()=>void onRecover('device')}><Cpu size={15}/>Recover device</button>}</div></div>
  <div className="activity-list">{visible.map(operation=>{const successful=operation.status==='succeeded',target=operation.deviceId||operation.targetUid,Icon=icons[operation.action]||AlertTriangle;return <article className="audit-row" key={operation.operationId}><span className={`audit-icon ${successful?'success':'attention'}`}><Icon size={19}/></span><div className="audit-main"><h2>{labels[operation.action]||operation.action}</h2><div className="audit-target"><span>{operation.kind==='device'?'Device':'Account'}</span><code>{target}</code></div></div><div className="audit-meta"><span className={`audit-status ${successful?'success':'attention'}`}><i/>{successful?'Completed':operation.status.replaceAll('_',' ')}</span><time dateTime={new Date(operation.createdAt).toISOString()}>{new Intl.DateTimeFormat(undefined,{dateStyle:'medium',timeStyle:'short'}).format(new Date(operation.createdAt))}</time></div>{!successful&&<button className="secondary compact audit-reconcile" disabled={busy} onClick={()=>void onReconcile(operation)}><RotateCcw size={15}/>Reconcile</button>}</article>})}{!visible.length&&<div className="activity-empty"><span><Check size={20}/></span><h2>{filter==='attention'?'Nothing needs attention':'No activity yet'}</h2></div>}</div>
 </section>;
}
