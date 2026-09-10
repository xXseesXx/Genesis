'use strict';
const $=id=>document.getElementById(id),canvas=$('map'),ctx=canvas.getContext('2d');
const state={meta:null,seed:'42',params:{},x:-786432,z:-786432,step:4096,layer:'elevation',revision:0,completeRevision:0,committed:null,controller:null,inspectRevision:0,dirty:false};
const LIMIT=2**40,INPUTS=['x','z','step','seed','layer','version'];
function error(message){$('error').textContent=message||'';$('error').hidden=!message;}
function syncView(){if(!state.dirty){$('seed').value=state.seed;$('originX').value=state.x;$('originZ').value=state.z;$('step').value=state.step;}}
function capture(){return {seed:state.seed,x:state.x,z:state.z,step:state.step,layer:state.layer,params:{...state.params}};}
function query(frame,point){const q=new URLSearchParams({seed:frame.seed,x:String(point?.x??frame.x),z:String(point?.z??frame.z),step:String(frame.step),layer:frame.layer,...Object.fromEntries(Object.entries(frame.params).map(([k,v])=>[k,String(v)]))});if(!point){q.set('width',canvas.width);q.set('height',canvas.height);}return q;}
function validView(x,z,step){return [x,z,step].every(Number.isSafeInteger)&&step>=1&&step<=1048576&&x>=-LIMIT&&z>=-LIMIT&&x+(canvas.width-1)*step<=LIMIT&&z+(canvas.height-1)*step<=LIMIT;}
function layerState(){const field=state.meta.fields.find(f=>f.id===state.layer);$('fieldTitle').textContent=field.label;$('legend').textContent=field.description;for(const b of $('layers').children)b.setAttribute('aria-pressed',String(b.dataset.field===state.layer));}
async function render(){
  if(!state.meta)return;
  if(!validView(state.x,state.z,state.step)){error('The requested view exceeds the supported coordinates or step range.');return;}
  state.controller?.abort();state.controller=new AbortController();const revision=++state.revision,frame=capture();
  state.inspectRevision++;$('inspection').replaceChildren();$('inspectCoords').textContent='Click the completed map to inspect all fields.';$('export').disabled=true;
  $('renderState').hidden=false;$('renderState').textContent='Rendering world coordinates…';error('');syncView();layerState();
  try{
    const response=await fetch('/api/tectonic/render?'+query(frame),{signal:state.controller.signal});
    if(!response.ok)throw new Error((await response.json()).error||'Render failed');
    if(response.headers.get('X-World-Model')!==state.meta.model||response.headers.get('X-World-Version')!==state.meta.version)throw new Error('Wrong world model/version returned. Reload the page.');
    const blob=await response.blob(),bitmap=await createImageBitmap(blob);
    if(revision!==state.revision){bitmap.close();return;}ctx.drawImage(bitmap,0,0);bitmap.close();
    state.committed={...frame,blob};state.completeRevision=revision;$('renderState').hidden=true;$('export').disabled=false;
    $('landFraction').textContent=(100*Number(response.headers.get('X-Land-Fraction'))).toFixed(2)+'% local land';
    $('timing').textContent=Number(response.headers.get('X-Render-Ms')).toFixed(0)+' ms generator + color rendering';
    $('coords').textContent=`x ${frame.x.toLocaleString()} · z ${frame.z.toLocaleString()} · ${frame.step.toLocaleString()} blocks / px`;
    const saved=query(frame);saved.delete('width');saved.delete('height');saved.set('version',state.meta.version);history.replaceState(null,'','/tectonics.html?'+saved);
  }catch(e){if(e.name==='AbortError'||revision!==state.revision)return;error(e.message);$('renderState').textContent='Render failed — previous image is not the requested view';}
}
function apply(){
  if(!state.meta)return;
  try{
    const rawSeed=$('seed').value.trim();if(!/^-?\d+$/.test(rawSeed)||BigInt(rawSeed)<-(1n<<63n)||BigInt(rawSeed)>(1n<<63n)-1n)throw new Error('Seed must be an exact signed 64-bit integer.');const seed=BigInt(rawSeed).toString();
    const x=Number($('originX').value),z=Number($('originZ').value),step=Number($('step').value);if(!validView(x,z,step))throw new Error('Invalid view coordinates or step.');
    const params={};for(const s of state.meta.params){const value=Number($('p-'+s.id).value);if(!Number.isSafeInteger(value)||value<s.min||value>s.max)throw new Error('Invalid '+s.label);params[s.id]=value;}
    Object.assign(state,{seed,x,z,step,params,dirty:false});$('draftState').hidden=true;render();
  }catch(e){error(e.message);}
}
function move(x,z,step){if(!validView(x,z,step)){error('That move exceeds the supported coordinate range.');return;}Object.assign(state,{x,z,step});render();}
function zoom(factor,px=canvas.width/2,py=canvas.height/2){const step=Math.max(1,Math.min(1048576,Math.round(state.step*factor)));if(step===state.step)return;move(Math.round(state.x+px*(state.step-step)),Math.round(state.z+py*(state.step-step)),step);}
function position(event){const r=canvas.getBoundingClientRect();return {x:Math.max(0,Math.min(canvas.width-1,Math.floor((event.clientX-r.left)*canvas.width/r.width))),z:Math.max(0,Math.min(canvas.height-1,Math.floor((event.clientY-r.top)*canvas.height/r.height)))};}
async function inspect(pixel){
  if(!state.committed||state.completeRevision!==state.revision)return;
  const frame=state.committed,revision=++state.inspectRevision,point={x:frame.x+pixel.x*frame.step,z:frame.z+pixel.z*frame.step};
  $('inspectCoords').textContent=`x ${point.x} · z ${point.z} — loading`;
  try{const response=await fetch('/api/tectonic/sample?'+query(frame,point));if(!response.ok)throw new Error((await response.json()).error||'Inspection failed');const data=await response.json();
    if(revision!==state.inspectRevision)return;if(data.model!==state.meta.model||data.version!==state.meta.version||data.seed!==frame.seed||data.x!==String(point.x)||data.z!==String(point.z))throw new Error('Inspector returned a mismatched world point.');
    $('inspectCoords').textContent=`x ${data.x} · z ${data.z}`;$('inspection').replaceChildren();
    for(const field of state.meta.fields){const dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=field.label;const value=data.fields[field.id];dd.textContent=typeof value==='number'?(Number.isInteger(value)?String(value):value.toFixed(3)):String(value);$('inspection').append(dt,dd);}
  }catch(e){if(revision===state.inspectRevision)error(e.message);}
}
let drag=null,wheelTimer=null;
canvas.addEventListener('pointerdown',e=>{if(e.button!==0||!state.committed||state.completeRevision!==state.revision)return;canvas.setPointerCapture(e.pointerId);drag={id:e.pointerId,px:e.clientX,py:e.clientY,x:state.x,z:state.z,step:state.step,revision:state.revision};});
canvas.addEventListener('pointerup',e=>{if(!drag||drag.id!==e.pointerId)return;const d=drag;drag=null;if(d.revision!==state.revision)return;const dx=e.clientX-d.px,dz=e.clientY-d.py;if(Math.hypot(dx,dz)<4){inspect(position(e));return;}const rect=canvas.getBoundingClientRect();move(d.x-Math.round(dx*canvas.width/rect.width)*d.step,d.z-Math.round(dz*canvas.height/rect.height)*d.step,d.step);});
canvas.addEventListener('pointercancel',()=>{drag=null;});
canvas.addEventListener('wheel',e=>{e.preventDefault();const p=position(e);clearTimeout(wheelTimer);wheelTimer=setTimeout(()=>zoom(e.deltaY>0?2:.5,p.x,p.z),100);},{passive:false});
canvas.addEventListener('keydown',e=>{const moves={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,-1],ArrowDown:[0,1]};if(moves[e.key]){e.preventDefault();const [dx,dz]=moves[e.key];move(state.x+dx*48*state.step,state.z+dz*48*state.step,state.step);}else if(e.key==='+'||e.key==='='){e.preventDefault();zoom(.5);}else if(e.key==='-'){e.preventDefault();zoom(2);}});
$('worldForm').addEventListener('submit',e=>{e.preventDefault();apply();});$('zoomIn').onclick=()=>zoom(.5);$('zoomOut').onclick=()=>zoom(2);
$('worldForm').addEventListener('input',()=>{state.dirty=true;$('draftState').hidden=false;});
$('overview').onclick=()=>{if(!state.meta)return;const step=Math.max(1,Math.round(state.params.plateSpacing*3/canvas.width));move(-canvas.width/2*step,-canvas.height/2*step,step);};
$('reset').onclick=()=>{if(!state.meta)return;for(const s of state.meta.params)$('p-'+s.id).value=s.default;$('seed').value='42';$('originX').value=-786432;$('originZ').value=-786432;$('step').value=4096;apply();};
function download(blob,name){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);}
$('export').onclick=()=>{const c=state.committed;if(!c||state.completeRevision!==state.revision)return;const {blob,...config}=c;download(blob,`genesis-tectonic-${c.seed}-${c.layer}.png`);download(new Blob([JSON.stringify({model:state.meta.model,version:state.meta.version,...config,width:canvas.width,height:canvas.height,waterStatus:'not solved'},null,2)],{type:'application/json'}),'genesis-tectonic-config.json');};
async function init(){
  try{const response=await fetch('/api/tectonic/meta');if(!response.ok)throw new Error('Tectonic API unavailable. Restart the updated local server.');state.meta=await response.json();
    const url=new URLSearchParams(location.search),legacy=['crustProvinceScale','crustRadiusPermille'];
    const upgrade=legacy.some(k=>url.has(k))||url.get('version')==='tectonic-terrain-v1';
    for(const key of url.keys())if(!INPUTS.includes(key)&&!state.meta.params.some(p=>p.id===key)&&!(upgrade&&legacy.includes(key)))throw new Error('Unknown saved configuration key: '+key);
    if(upgrade){for(const s of state.meta.params)url.delete(s.id);for(const key of legacy)url.delete(key);url.delete('version');$('upgradeNotice').hidden=false;}
    if(url.has('version')&&url.get('version')!==state.meta.version)throw new Error('This saved world uses another generator version. Open /tectonics.html for the current preset.');
    for(const s of state.meta.params){const label=document.createElement('label'),input=document.createElement('input');label.textContent=s.label;input.id='p-'+s.id;input.type='number';input.min=s.min;input.max=s.max;input.step=s.step;input.value=url.get(s.id)??s.default;label.append(input);$('params').append(label);}
    for(const f of state.meta.fields){const b=document.createElement('button');b.type='button';b.textContent=f.label;b.title=f.description;b.dataset.field=f.id;b.onclick=()=>{state.layer=f.id;render();};$('layers').append(b);}
    state.layer=url.get('layer')||'elevation';if(!state.meta.fields.some(f=>f.id===state.layer))throw new Error('Unknown saved field.');
    $('seed').value=url.get('seed')??state.seed;$('originX').value=url.get('x')??state.x;$('originZ').value=url.get('z')??state.z;$('step').value=url.get('step')??state.step;
    $('version').textContent=state.meta.version+' · '+state.meta.model;apply();
  }catch(e){error(e.message);$('renderState').textContent='Viewer unavailable';}
}
init();
