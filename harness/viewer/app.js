'use strict';
const $ = id => document.getElementById(id);
const isContinental=document.body.dataset.world==='continental';
const worldModel=isContinental?'continental':'legacy';
const state = {x:-65536,z:-65536,step:256,params:{},layers:[],revision:0,completeRevision:-1,controller:null,timer:null,images:{},requests:{}};
if(isContinental){state.x=state.z=-262144;state.step=1024;}
let meta;
const error = message => { $('error').textContent=message; $('error').hidden=!message; };
function config(side) {
  const seed=$('seed'+side).value.trim();
  if(!/^-?\d+$/.test(seed)) throw new Error('Seed '+side+' must be a signed 64-bit integer.');
  const integer=BigInt(seed);
  if(integer < -(1n<<63n) || integer >= (1n<<63n)) throw new Error('Seed '+side+' is outside signed 64-bit range.');
  let params={...state.params};
  if(side==='B') {
    const overrides=JSON.parse($('paramsB').value);
    if(!overrides || Array.isArray(overrides) || typeof overrides!=='object') throw new Error('B overrides must be a JSON object.');
    for(const [key,value] of Object.entries(overrides)) {
      if(!meta.params.some(p=>p.id===key) || typeof value!=='number' || !Number.isFinite(value)) throw new Error('Invalid B parameter: '+key);
    }
    params={...params,...overrides};
  }
  return {seed,...params,model:worldModel};
}
function request(side) {
  const layers=state.layers.filter(l=>l.enabled).map(l=>l.id+':'+l.opacity).join(',');
  if(!layers) throw new Error('Enable at least one layer.');
  return new URLSearchParams({...config(side),x:state.x,z:state.z,step:state.step,width:512,height:512,layers});
}
function schedule() {
  clearTimeout(state.timer); state.controller?.abort(); state.revision++; inspectionRevision++;
  $('inspectCoords').textContent='Select a location'; $('inspection').replaceChildren();
  state.timer=setTimeout(render,140);
}
async function render() {
  const revision=++state.revision;
  state.controller?.abort(); state.controller=new AbortController();
  const signal=state.controller.signal;
  const start=performance.now();
  try {
    error(''); $('connection').textContent='Rendering…';
    const sides=$('compare').checked ? ['A','B'] : ['A'];
    const queries=Object.fromEntries(sides.map(side=>[side,request(side)]));
    const results=await Promise.all(sides.map(async side=>{
      const response=await fetch('/api/render?'+queries[side],{signal});
      if(!response.ok) throw new Error((await response.json()).error);
      if(response.headers.get('X-World-Model')!==worldModel)throw new Error('World model mismatch; refresh the viewer.');
      const blob=await response.blob();
      const bitmap=await createImageBitmap(blob);
      return {side,bitmap,blob,ms:Number(response.headers.get('X-Render-Ms')),min:Number(response.headers.get('X-Field-Min')),max:Number(response.headers.get('X-Field-Max')),land:Number(response.headers.get('X-Land-Fraction')??NaN)};
    }));
    if(revision!==state.revision) { results.forEach(r=>r.bitmap.close()); return; }
    for(const r of results) {
      $('map'+r.side).getContext('2d').drawImage(r.bitmap,0,0); r.bitmap.close();
      state.images[r.side]=r.blob; state.requests[r.side]=queries[r.side].toString();
      $('caption'+r.side).textContent='Seed '+queries[r.side].get('seed');
    }
    const r=results[0];
    $('timing').textContent=results.map(v=>v.side+': '+v.ms.toFixed(0)+' ms').join(' / ');
    $('range').textContent=(Number.isFinite(r.min)?`A range ${r.min.toFixed(3)} → ${r.max.toFixed(3)}`:'A: categorical identities')+` · ${Math.round(performance.now()-start)} ms end to end`;
    if(Number.isFinite(r.land))$('range').textContent+=' · land '+(r.land*100).toFixed(1)+'% of sampled viewport';
    $('connection').textContent='● Core connected';
    $('regime').textContent=state.step>=64?'Planetary':state.step>=4?'Regional':'Block scale';
    $('scale').textContent=state.step+' blocks / pixel';
    $('fieldTitle').textContent=state.layers.filter(l=>l.enabled).map(l=>meta.fields.find(f=>f.id===l.id).label).join(' + ');
    showLegend();
    state.completeRevision=revision;
  } catch(e) { if(e.name!=='AbortError' && revision===state.revision) { error(e.message); $('connection').textContent='Render failed'; } }
}
function point(event,canvas) {
  const rect=canvas.getBoundingClientRect();
  return {px:Math.max(0,Math.min(511,Math.floor((event.clientX-rect.left)/rect.width*512))),pz:Math.max(0,Math.min(511,Math.floor((event.clientY-rect.top)/rect.height*512)))};
}
function zoom(direction,px=256,pz=256) {
  const next=Math.max(1,Math.min(1048576,Math.round(direction>0?state.step/2:state.step*2)));
  state.x+=px*(state.step-next); state.z+=pz*(state.step-next); state.step=next; schedule();
}
let inspectionRevision=0;
async function inspect(x,z) {
  if(state.completeRevision!==state.revision)return;
  const revision=++inspectionRevision;
  try {
    const sides=$('compare').checked?['A','B']:['A'];
    const results=await Promise.all(sides.map(async side=>{
      const response=await fetch('/api/sample?'+new URLSearchParams({...config(side),x,z}));
      const data=await response.json(); if(!response.ok) throw new Error(data.error); return {side,data};
    }));
    if(revision!==inspectionRevision) return;
    $('inspectCoords').textContent=`x ${x.toLocaleString()} · z ${z.toLocaleString()}`;
    $('inspection').replaceChildren();
    for(const {side,data} of results) for(const [key,value] of Object.entries(data.fields)) {
      const dt=document.createElement('dt'), dd=document.createElement('dd');dt.textContent=side+' / '+key;
      dd.textContent=typeof value==='string'?value:Number.isInteger(value)?String(value):value.toFixed(6);$('inspection').append(dt,dd);
    }
    for(const {side,data} of results)if(data.column){
      const dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=side+' / Stratigraphic column';
      dd.textContent='Surface '+data.column.surfaceY+' model m; contact intervals, not soil or carved terrain.';$('inspection').append(dt,dd);
      for(const layer of [...data.column.layers].reverse()){
        const name=document.createElement('dt'),value=document.createElement('dd');name.textContent=side+' / '+layer.rock+(layer.atSurface?' (surface)':'');
        value.textContent=(layer.lower??'-infinity')+' to '+(layer.upper??'+infinity')+' m; '+layer.formationAge+' synthetic Ma';$('inspection').append(name,value);
      }
    }
  } catch(e) { if(revision===inspectionRevision) error(e.message); }
}
for(const side of ['A','B']) {
  const canvas=$('map'+side); let drag=null;
  canvas.addEventListener('pointerdown',e=>{if(e.button!==0)return; drag={clientX:e.clientX,clientY:e.clientY,x:state.x,z:state.z,moved:false};canvas.setPointerCapture(e.pointerId);});
  canvas.addEventListener('pointermove',e=>{
    const p=point(e,canvas);$('coords').textContent=`x ${(state.x+p.px*state.step).toLocaleString()} · z ${(state.z+p.pz*state.step).toLocaleString()}`;
    if(!drag)return;
    const dx=e.clientX-drag.clientX,dz=e.clientY-drag.clientY;
    if(Math.abs(dx)+Math.abs(dz)>4)drag.moved=true;
    if(drag.moved){const ratio=512/canvas.getBoundingClientRect().width;state.x=drag.x-Math.round(dx*ratio)*state.step;state.z=drag.z-Math.round(dz*ratio)*state.step;schedule();}
  });
  canvas.addEventListener('pointerup',e=>{if(!drag)return;const p=point(e,canvas);if(!drag.moved)inspect(state.x+p.px*state.step,state.z+p.pz*state.step);drag=null;});
  canvas.addEventListener('pointercancel',()=>{drag=null;});
  canvas.addEventListener('wheel',e=>{e.preventDefault();const p=point(e,canvas);zoom(e.deltaY<0?1:-1,p.px,p.pz);},{passive:false});
}
function download(blob,name) {const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
$('export').onclick=()=>{if(state.images.A){download(state.images.A,'genesis-A.png');download(new Blob([JSON.stringify({version:meta.version,query:state.requests.A},null,2)],{type:'application/json'}),'genesis-A.json');if($('compare').checked&&state.images.B){download(state.images.B,'genesis-B.png');download(new Blob([JSON.stringify({version:meta.version,query:state.requests.B},null,2)],{type:'application/json'}),'genesis-B.json');}}};
$('zoomIn').onclick=()=>zoom(1);$('zoomOut').onclick=()=>zoom(-1);
$('continentsOverview').onclick=event=>{if(!meta)return;if(!isContinental){try{const {model,...inputs}=config('A');$('continentsOverview').href='/continents.html?'+new URLSearchParams(inputs);}catch(e){event.preventDefault();error(e.message);}return;}state.step=Math.max(1,Math.ceil(state.params.continentScale/64));state.x=-256*state.step;state.z=-256*state.step;for(const layer of state.layers){layer.enabled=layer.id==='baseElevation';layer.opacity=1;}syncLayers();schedule();};
$('hydrologyLink').onclick=event=>{try{$('hydrologyLink').href='/hydrology.html?'+new URLSearchParams({...config('A'),x:state.x,z:state.z,step:Math.min(1048576,state.step*4)});}catch(e){event.preventDefault();error(e.message);}};
$('refinementLink').onclick=event=>{try{const a=config('A');$('refinementLink').href='/refinement.html?'+new URLSearchParams({seed:a.seed,x:state.x,z:state.z,coarseSpacing:a.coarseSpacing});}catch(e){event.preventDefault();error(e.message);}};
$('home').onclick=()=>{state.x=-256*state.step;state.z=-256*state.step;schedule();};
$('compare').onchange=()=>{$('figureB').hidden=!$('compare').checked;$('compareOptions').hidden=!$('compare').checked;$('maps').classList.toggle('compare',$('compare').checked);schedule();};
for(const id of ['seedA','seedB','paramsB'])$(id).addEventListener('input',schedule);
function makeParams() {
  $('params').replaceChildren();
  const primary=['continentScale','continentCoverage','continentLobeRadius','continentLobeVariation','continentArmStep','landHeight','mountainHeight','terrainDetailHeight','wavelength','oceanDepth','coarseSpacing','seaSearchRadius'];
  const specs=isContinental?[...primary.map(id=>meta.params.find(p=>p.id===id)),...meta.params.filter(p=>!primary.includes(p.id)&&!['crustInfluence','seaThreshold'].includes(p.id))]:meta.params.filter(p=>!['continentCoverage','continentLobeRadius','continentLobeVariation','continentArmStep','terrainDetailHeight'].includes(p.id));
  for(const spec of specs){
    const label=document.createElement('label'),value=document.createElement('span'),input=document.createElement('input');
    label.htmlFor='param-'+spec.id;label.textContent=spec.id;value.textContent=state.params[spec.id];label.append(value);
    input.id=label.htmlFor;input.type='range';input.min=spec.min;input.max=spec.max;input.step=spec.step;input.value=state.params[spec.id];input.title=spec.description;
    const number=document.createElement('input');number.type='number';number.min=spec.min;number.max=spec.max;number.step=spec.step;number.value=state.params[spec.id];number.setAttribute('aria-label',spec.id+' exact value');number.className='param-number';
    input.oninput=()=>{state.params[spec.id]=Number(input.value);value.textContent=input.value;number.value=input.value;schedule();};
    number.onchange=()=>{state.params[spec.id]=Number(number.value);value.textContent=number.value;input.value=number.value;schedule();};
    const pair=document.createElement('div');pair.className='param-controls';pair.append(input,number);$('params').append(label,pair);
  }
}
$('resetParams').onclick=()=>{for(const spec of meta.params)state.params[spec.id]=spec.default;makeParams();schedule();};
async function init(){
  const response=await fetch('/api/meta?model='+worldModel);if(!response.ok)throw new Error('Could not load core metadata.');meta=await response.json();if(meta.model!==worldModel)throw new Error('Wrong world metadata.');$('version').textContent=meta.version+' / '+worldModel;
  const query=new URLSearchParams(location.search);
  for(const spec of meta.params)state.params[spec.id]=query.has(spec.id)?Number(query.get(spec.id)):spec.default;
  if(query.has('seed'))$('seedA').value=query.get('seed');
  for(const id of ['x','z','step'])if(query.has(id)){const value=Number(query.get(id));if(!Number.isSafeInteger(value)||(id==='step'&&(value<1||value>1048576)))throw new Error('Invalid '+id+' in URL.');state[id]=value;}
  makeParams();
  meta.fields=meta.fields.filter(f=>isContinental?f.id!=='continentSeaMask':!['continentScaffold','continentSeaMask','terrainDetail'].includes(f.id));
  if(isContinental)for(const f of meta.fields){if(f.id==='baseElevation')f.label='Continental terrain';if(f.id==='continentScaffold')f.label='Raw landmass support';if(f.id==='continentality')f.label='Committed continentality';}
  const macro=['baseElevation','coarseChannelFlow','coarseRunoff','coarseRunoffStatus','coarseChannelDistance','drainagePortDistance','seaMask','continentality','tectonicRelief',...(isContinental?['terrainDetail']:[]),'coarseSeaDistance','coarseDrainageRank','coarseFlowDirection'];
  const fields=[...macro.map(id=>meta.fields.find(f=>f.id===id)).filter(Boolean),...meta.fields.filter(f=>!macro.includes(f.id)&&!['noise','ridges','boundaryType'].includes(f.id)),...meta.fields.filter(f=>['noise','ridges'].includes(f.id)),...meta.fields.filter(f=>f.id==='boundaryType')];
  for(const field of fields){
    const layer={id:field.id,enabled:field.id==='baseElevation',opacity:1};state.layers.push(layer);
    const row=document.createElement('div'),label=document.createElement('label'),check=document.createElement('input'),slider=document.createElement('input'),view=document.createElement('button');row.className='layer-row';
    check.type='checkbox';check.checked=layer.enabled;check.onchange=()=>{layer.enabled=check.checked;syncLayers();schedule();};label.append(check,document.createTextNode(field.label));
    view.textContent='View';view.setAttribute('aria-label','View only '+field.label);view.onclick=()=>{for(const entry of state.layers)entry.enabled=entry===layer;layer.opacity=1;syncLayers();schedule();};
    const controls=document.createElement('div');controls.className='layer-controls';
    slider.type='range';slider.min=0;slider.max=1;slider.step=.05;slider.value=1;slider.setAttribute('aria-label',field.label+' opacity');slider.oninput=()=>{layer.opacity=Number(slider.value);schedule();};
    layer.elements={row,check,slider};controls.append(slider,view);row.append(label,controls);$('layers').append(row);
  }
  syncLayers();
  render();
}
function syncLayers(){for(const layer of state.layers){layer.elements.check.checked=layer.enabled;layer.elements.slider.value=layer.opacity;layer.elements.row.classList.toggle('active',layer.enabled);}}
function showLegend(){
  const descriptions=state.layers.filter(l=>l.enabled).map(layer=>{
    const f=meta.fields.find(f=>f.id===layer.id);
    if(f.id==='continentScaffold')return `${f.label}: integer macro-object support BEFORE coarse coastline interpolation. Use Land / sea for the committed coast used by terrain and drainage.`;
    if(f.id==='terrainDetail')return `${f.label}: actual ridged height added to continental land; zero in water, tapered at coasts. Strength: terrainDetailHeight; scale: wavelength.`;
    if(f.id==='rockType')return `${f.label}: basalt (grey), granite (pink), shale (olive), limestone (pale green), sandstone (ochre). Current terrain intersects a folded material stack; no erosion or water feedback yet. Click to inspect the column. Includes seabed bedrock, not soil.`;
    if(f.id==='rockHardness'||f.id==='rockWeatherability')return `${f.label}: material-specific relative coefficient, not measured physical units. These inputs do not yet change terrain or river routes.`;
    if(f.id==='formationAge')return `${f.label}: synthetic crust chronology, with successively younger sediment units. Not a simulated geological history.`;
    if(f.id==='strataDisplacement')return `${f.label}: bounded, seeded broad folds translate all contacts together without changing layer thickness. Does not move terrain. Set geologyFoldAmplitude to zero for flat contacts.`;
    if(f.id==='tectonicRelief')return `${f.label}: actual coast-tapered height contribution from positive tectonic uplift, controlled by mountainHeight.`;
    if(f.id==='coarseChannelFlow')return `${f.label}: brightness and display width follow resolved upstream rain. Each crossing carries its source flow, not the downstream confluence total. Open catchments omit unknown interior rain; inspect Catchment completeness. No carved beds or globally proven ocean mouths.`;
    if(f.id==='coarseRunoff')return `${f.label}: exact sum of one unit per upstream resolved land anchor, including self; no rain from sea anchors. Pink = unresolved. These are coarse graph units, not block area or physical discharge. Open catchments have partial totals.`;
    if(f.id==='coarseRunoffStatus')return `${f.label}: teal = closed under current routing; amber = upstream region touches unresolved routing or numeric support; pink = unresolved anchor. Closed does not prove global ocean connectivity. Never solved from canvas edges.`;
    if(f.id==='coarseChannelDistance')return `${f.label}: cyan world-coordinate guides; unresolved coarse routes stay absent. Use Flow-weighted river guides for runoff. No carving or globally proven ocean mouths yet.`;
    if(f.id==='drainagePortDistance')return `${f.label}: gold shared-edge crossings; identical from either cell and independent of the viewport.`;
    const details=f.type==='Long'?'colors distinguish exact identities':f.id==='baseElevation'?'blue: below sea · green to pale: higher land · display hillshade':f.id==='seaMask'?'blue: sea-level terminal · green: land (global connectivity pending)':f.id==='coarseFlowDirection'?'pink: unresolved · blue: sea · gold: N · green: E · pale blue: S · coral: W':f.id.startsWith('coarse')?`${f.units} · pink: unresolved · values refer to the coarse anchor`:f.id==='boundaryType'?'blue: divergent · gold: transform · orange: convergent':f.id==='crustType'?'blue: oceanic · green: continental':f.id==='uplift'?'blue: extension · dark: neutral · orange: compression':`${f.min} → ${f.max} ${f.units} (display range)`;
    return `${f.label}: ${details}`;
  });
  $('layerLegend').textContent=descriptions.join(' / ');
}
init().catch(e=>error(e.message));
