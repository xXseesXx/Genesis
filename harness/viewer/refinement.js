'use strict';
const $=id=>document.getElementById(id);
const inputs=['seed','x','z','level','coarseSpacing','rain','inflow'];
const layers=[['network','Routing network','Gold: inherited crossings. Cyan: internal child routes. Geometry remains unchanged when only runoff amounts change.'],['rain','Local runoff','The parent total is split across four children with an exact deterministic remainder allocation.'],['inflow','External inflow','Only contributions entering from outside this parent; never its own runoff again.'],['flux','Accumulated flow','Local runoff + external inflow + contributions from upstream children, counted once.'],['cost','Routing resistance','Synthetic nonnegative integer costs for this example; not height, geology, or continent generation.'],['distance','Drainage distance','Positive-cost graph distance to the exit child; strictly decreases on internal downstream edges. Not metres.']];
let layer='network',revision=0,controller=null,result=null,snapshot=null,blob=null,objectUrl=null;
function fail(message){$('error').textContent=message;$('error').hidden=!message;}
function query(){
  const q=new URLSearchParams();
  for(const id of inputs){const value=$(id).value.trim();if(!/^-?\d+$/.test(value))throw new Error(id+' must be an integer.');const n=BigInt(value);if(n<-(1n<<63n)||n>=(1n<<63n))throw new Error(id+' is outside signed 64-bit range.');q.set(id,value);}
  return q;
}
function sync(){for(const b of $('layers').children){const active=b.dataset.layer===layer;b.classList.toggle('active',active);b.setAttribute('aria-pressed',String(active));}$('legend').textContent=layers.find(l=>l[0]===layer)[2];}
for(const [id,label] of layers){const b=document.createElement('button');b.textContent=label;b.dataset.layer=id;b.onclick=()=>{layer=id;sync();if(snapshot)renderImage(new URLSearchParams(snapshot.query));};$('layers').append(b);}
async function fetchImage(q,signal){const copy=new URLSearchParams(q);copy.set('layer',layer);const response=await fetch('/api/refinement.png?'+copy,{signal});if(!response.ok)throw new Error((await response.json()).error);return response.blob();}
async function display(imageBlob,ticket){
  const url=URL.createObjectURL(imageBlob),probe=new Image();probe.src=url;
  try{await probe.decode();}catch(e){URL.revokeObjectURL(url);throw e;}
  if(ticket!==revision){URL.revokeObjectURL(url);return false;}
  if(objectUrl)URL.revokeObjectURL(objectUrl);objectUrl=url;blob=imageBlob;$('diagram').src=url;$('diagram').hidden=false;$('png').disabled=false;return true;
}
async function renderImage(q){
  const ticket=++revision;controller?.abort();controller=new AbortController();
  try{fail('');const imageBlob=await fetchImage(q,controller.signal);if(await display(imageBlob,ticket))$('status').textContent='Displayed contract · '+layer;}
  catch(e){if(ticket===revision&&e.name!=='AbortError')fail(e.message);}
}
async function run(){
  const ticket=++revision;controller?.abort();controller=new AbortController();
  try{
    fail('');const q=query();$('status').textContent='Refining…';
    const [response,imageBlob]=await Promise.all([fetch('/api/refinement?'+q,{signal:controller.signal}),fetchImage(q,controller.signal)]);
    const data=await response.json();if(!response.ok)throw new Error(data.error);
    if(ticket!==revision||!await display(imageBlob,ticket))return;
    result=data;snapshot={query:q.toString(),...data};$('export').disabled=false;
    $('snapshot').textContent=`Seed ${data.seed} · x ${data.x}, z ${data.z} · parent level ${data.level}`;
    $('status').textContent='Exact contract · '+layer;$('version').textContent=data.version;
    const [a,b]=data.parents;const total=BigInt(data.externalInflow)+2n*BigInt(data.rainPerParent);
    $('budget').textContent=`A: ${a.inflow} + ${a.rain} = ${a.outflow}. B: ${b.inflow} + ${b.rain} = ${b.outflow}. Whole fixture: ${data.externalInflow} + 2 × ${data.rainPerParent} = ${data.finalOutflow}. ${total===BigInt(data.finalOutflow)?'BALANCED':'FAILED'}`;
    $('ledger').replaceChildren();
    for(const [pi,parent] of data.parents.entries())for(const child of parent.children){
      const tr=document.createElement('tr');for(const value of [(pi?'B':'A')+child.index,child.rain,child.externalInflow,child.outflow,child.resistance,child.distance,child.downstream<0?'Parent exit':(pi?'B':'A')+child.downstream,child.exit.x+', '+child.exit.z]){const td=document.createElement('td');td.textContent=value;tr.append(td);}$('ledger').append(tr);
    }
  }catch(e){if(ticket===revision&&e.name!=='AbortError'){fail(e.message);$('status').textContent='Failed; previous result retained';}}
}
function download(value,name){const url=URL.createObjectURL(value),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
$('export').onclick=()=>{if(snapshot)download(new Blob([JSON.stringify(snapshot,null,2)],{type:'application/json'}),'genesis-refinement.json');};
$('png').onclick=()=>{if(blob)download(blob,'genesis-refinement.png');};
for(const id of inputs)$(id).addEventListener('input',()=>{revision++;controller?.abort();$('status').textContent='Inputs changed — click Refine parents';});
$('run').onclick=run;
const initial=new URLSearchParams(location.search);for(const id of inputs)if(initial.has(id))$(id).value=initial.get(id);
sync();run();
