const $ = id => document.getElementById(id);
let state, busy = false;
async function request(path, method = 'GET', body) {
  const response = await fetch('/api/' + path, {method, headers: {'Content-Type':'application/json'}, ...(body ? {body:JSON.stringify(body)} : {}), signal:AbortSignal.timeout(5000)});
  if (!response.ok) throw new Error(`Request failed (${response.status}). Check the backend and try again.`);
  return response.json();
}
function connected(ok) { $('connectionDot').classList.toggle('online',ok); $('connection').textContent=ok?'Backend connected · local session':'Backend disconnected'; }
function render(s) {
  state=s; connected(true);
  for(const key of ['sent','dropped','inFlight']) $(key).textContent=s[key].toLocaleString();
  $('delivery').textContent=s.deliveryPercent.toFixed(1); $('latency').textContent=s.meanLatencyMs.toFixed(1); $('jitter').textContent=s.jitterMs.toFixed(1);
  $('run').textContent=s.running?'Pause stream  Ⅱ':'Start stream  ↗';
  $('routeBadge').textContent=s.route==='UNREACHABLE'?'NO AVAILABLE ROUTE':s.route+' ROUTE';
  $('routeBadge').classList.toggle('down',s.route==='UNREACHABLE');
  for(const [id,route,up] of [['primaryPath','PRIMARY',s.controls.primaryUp],['backupPath','BACKUP',s.controls.backupUp]]) $(id).setAttribute('class','link'+(s.route===route?' selected':'')+(s.route===route&&s.running?' flowing':'')+(!up?' off':''));
  $('transport').textContent=`127.0.0.1 : ${s.senderPort} → ${s.receiverPort}`;
  const texts={PRIMARY:['Primary path available','Disable Relay Alpha to observe failover to the longer backup path.'],BACKUP:['Traffic has moved to Relay Bravo','The backup adds 75 ms of modeled base latency. Restore Alpha to recover the preferred route.'],UNREACHABLE:['Ground station is unreachable','Both relays are down. New packets are dropped until at least one route is restored.']};
  $('routeTitle').textContent=texts[s.route][0]; $('routeExplanation').textContent=texts[s.route][1];
  $('events').replaceChildren(...s.events.map(e=>{const row=document.createElement('div');row.className='event';const time=document.createElement('time');time.textContent=new Date(e.time).toLocaleTimeString('en-GB');const type=document.createElement('span');type.className='type'+(['DROP','TIMEOUT'].includes(e.type)?' alert':'');type.textContent=e.type;const msg=document.createElement('span');msg.textContent=e.message;row.append(time,type,msg);return row;}));
  drawChart(s.samples);
}
function syncControls(s){$('primary').checked=s.controls.primaryUp;$('backup').checked=s.controls.backupUp;$('delay').value=s.controls.delayMs;$('loss').value=s.controls.lossPercent;labels();}
function labels(){$('delayValue').textContent=$('delay').value+' ms';$('lossValue').textContent=$('loss').value+'%';}
async function action(path,method='POST',body){if(busy)return;busy=true;document.querySelectorAll('button,input').forEach(e=>e.disabled=true);try{const s=await request(path,method,body);render(s);syncControls(s);$('error').hidden=true;}catch(e){$('error').textContent=e.message;$('error').hidden=false;if(state)syncControls(state);}finally{busy=false;document.querySelectorAll('button,input').forEach(e=>e.disabled=false);}}
$('run').onclick=()=>action(state?.running?'pause':'start');$('reset').onclick=()=>action('reset');
for(const id of ['primary','backup','delay','loss']){$(id).oninput=labels;$(id).onchange=()=>action('controls','PUT',{primaryUp:$('primary').checked,backupUp:$('backup').checked,delayMs:Number($('delay').value),lossPercent:Number($('loss').value)});}
$('export').onclick=()=>{if(!state)return;const blob=new Blob([JSON.stringify({exportedAt:new Date().toISOString(),...state},null,2)],{type:'application/json'});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='mission-network-session.json';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000);};
function drawChart(samples){const canvas=$('chart'),dpr=window.devicePixelRatio||1,w=canvas.clientWidth-40,h=180;canvas.width=w*dpr;canvas.height=h*dpr;const ctx=canvas.getContext('2d');ctx.scale(dpr,dpr);const max=Math.max(150,...samples.map(s=>s.latencyMs))*1.15;ctx.font='10px Arial';for(let i=0;i<4;i++){const y=15+i*42;ctx.strokeStyle='#29312e';ctx.beginPath();ctx.moveTo(36,y);ctx.lineTo(w,y);ctx.stroke();ctx.fillStyle='#9aa8a3';ctx.fillText(Math.round(max*(1-i/3)),0,y+4);}if(!samples.length){ctx.fillStyle='#9aa8a3';ctx.fillText('Start the stream to see packet arrivals.',55,90);return;}const points=samples.map((s,i)=>[36+i*(w-42)/79,15+(1-s.latencyMs/max)*126]);ctx.beginPath();points.forEach(([x,y],i)=>i?ctx.lineTo(x,y):ctx.moveTo(x,y));ctx.strokeStyle='#70dcb0';ctx.lineWidth=2;ctx.stroke();const[x,y]=points.at(-1);ctx.beginPath();ctx.arc(x,y,3,0,Math.PI*2);ctx.fillStyle='#70dcb0';ctx.fill();}
window.addEventListener('resize',()=>state&&drawChart(state.samples));
async function poll(){if(!busy){try{const s=await request('state');if(!busy){if(!state)syncControls(s);render(s);}}catch(e){connected(false);}}setTimeout(poll,500);}poll();
