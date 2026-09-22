'use strict';
const $=id=>document.getElementById(id),canvas=$('map'),ctx=canvas.getContext('2d');
const DEFAULT_VIEW=Object.freeze({x:-3072,z:-3072,step:16});
const state={meta:null,seed:'42',params:{},...DEFAULT_VIEW,layer:'elevation',contourInterval:5,revision:0,completeRevision:0,committed:null,controller:null,inspectRevision:0,dirty:false};
const LIMIT=2**40,INPUTS=['x','z','step','seed','layer','version','hydrologyVersion','fluvialVersion','climateVersion','windVersion','substrateVersion','dendriticVersion','contourInterval'];
function error(message){$('error').textContent=message||'';$('error').hidden=!message;}
function syncView(){if(!state.dirty){$('seed').value=state.seed;$('originX').value=state.x;$('originZ').value=state.z;$('step').value=state.step;}}
function capture(){return {seed:state.seed,x:state.x,z:state.z,step:state.step,layer:state.layer,contourInterval:state.contourInterval,hydrologyVersion:state.meta.hydrologyVersion,fluvialVersion:state.meta.fluvialVersion,climateVersion:state.meta.climateVersion,windVersion:state.meta.windVersion,substrateVersion:state.meta.substrateVersion,dendriticVersion:state.meta.dendriticVersion,params:{...state.params}};}
function query(frame,point){const q=new URLSearchParams({seed:frame.seed,x:String(point?.x??frame.x),z:String(point?.z??frame.z),step:String(frame.step),layer:frame.layer,contourInterval:String(frame.contourInterval),...Object.fromEntries(Object.entries(frame.params).map(([k,v])=>[k,String(v)]))});if(!point){q.set('width',canvas.width);q.set('height',canvas.height);}return q;}
function versionKey(frame){return [state.meta.model,state.meta.version,frame.hydrologyVersion,frame.fluvialVersion,frame.climateVersion,frame.windVersion,frame.substrateVersion,frame.dendriticVersion].join('|');}
function validView(x,z,step){return [x,z,step].every(Number.isSafeInteger)&&step>=1&&step<=1048576&&x>=-LIMIT&&z>=-LIMIT&&x+(canvas.width-1)*step<=LIMIT&&z+(canvas.height-1)*step<=LIMIT;}
function layerState(){const field=state.meta.fields.find(f=>f.id===state.layer);$('fieldTitle').textContent=field.label;$('legend').textContent=field.description;$('heightLegend').hidden=state.layer!=='heightMap';$('contourState').textContent=state.layer==='heightMap'?'Updating contours...':'Contours apply to Height + contours';for(const b of $('layers').children)b.setAttribute('aria-pressed',String(b.dataset.field===state.layer));}
// Fixed world tiles; the sample phase preserves arbitrary integer origins exactly.
const TILE_SIZE=128,MIN_HYDRO_TILE=64,TILE_LIMIT=128,TILE_WORKERS=4,
  HYDRO_LAYERS=new Set(['erodedTerrain','dendriticTerrain','dendriticDelta','dendriticRidges','erosionDepth','hardness','riverMap','rainfall','humidity','runoff','lakeDepth','waterSurface','soilDepth','bedrockElevation','bedrockDepth','rockType','infiltration','runoffFraction','drainage','drainageStatus']),
  tileCache=new Map(),tilePending=new Map(),tileQueue=[];
let tileWorkers=0,wantedTiles=new Set();
function tileSizeFor(frame){return HYDRO_LAYERS.has(frame.layer)?Math.max(1,Math.min(TILE_SIZE,Math.floor(3*frame.params.plateSpacing*frame.params.size/frame.step))):TILE_SIZE;}
function tilesFor(frame,width=canvas.width,height=canvas.height){
  const tileSize=tileSizeFor(frame);
  const phaseX=((frame.x%frame.step)+frame.step)%frame.step,phaseZ=((frame.z%frame.step)+frame.step)%frame.step;
  const minX=Math.ceil((-LIMIT-phaseX)/frame.step),maxX=Math.floor((LIMIT-phaseX)/frame.step),minZ=Math.ceil((-LIMIT-phaseZ)/frame.step),maxZ=Math.floor((LIMIT-phaseZ)/frame.step);
  const gx=(frame.x-phaseX)/frame.step,gz=(frame.z-phaseZ)/frame.step,tiles=[];
  for(let iz=Math.floor(gz/tileSize);iz<=Math.floor((gz+height-1)/tileSize);iz++)for(let ix=Math.floor(gx/tileSize);ix<=Math.floor((gx+width-1)/tileSize);ix++){
    const ax=Math.max(ix*tileSize,minX),az=Math.max(iz*tileSize,minZ),w=Math.min((ix+1)*tileSize-1,maxX)-ax+1,h=Math.min((iz+1)*tileSize-1,maxZ)-az+1;
    const tile={x:phaseX+ax*frame.step,z:phaseZ+az*frame.step,width:w,height:h};
    const q=query({...frame,x:tile.x,z:tile.z});q.set('width',w);q.set('height',h);
    tile.url='/api/tectonic/render?'+q;tile.key=versionKey(frame)+'|'+tile.url;tiles.push(tile);
  }
  return tiles;
}
function cachedTile(key){const found=tileCache.get(key);if(found){tileCache.delete(key);tileCache.set(key,found);}return found;}
function trimTiles(){for(const [key,tile] of tileCache){if(tileCache.size<=TILE_LIMIT)break;if(wantedTiles.has(key))continue;tileCache.delete(key);tile.bitmap.close();}}
function pumpTiles(){
  while(tileWorkers<TILE_WORKERS&&tileQueue.length){
    const job=tileQueue.shift();
    if(!wantedTiles.has(job.tile.key)){tilePending.delete(job.tile.key);job.reject(new DOMException('Obsolete tile','AbortError'));continue;}
    tileWorkers++;
    (async()=>{
      const response=await fetch(job.tile.url);
      if(!response.ok)throw new Error((await response.json()).error||'Tile render failed');
      if(response.headers.get('X-World-Model')!==state.meta.model||response.headers.get('X-World-Version')!==state.meta.version||response.headers.get('X-Hydrology-Version')!==job.frame.hydrologyVersion||response.headers.get('X-Fluvial-Version')!==job.frame.fluvialVersion||response.headers.get('X-Climate-Version')!==job.frame.climateVersion||response.headers.get('X-Wind-Version')!==job.frame.windVersion||response.headers.get('X-Substrate-Version')!==job.frame.substrateVersion||response.headers.get('X-Dendritic-Version')!==job.frame.dendriticVersion||Number(response.headers.get('X-Native-Scale'))!==job.frame.params.size)throw new Error('Wrong tile world/version returned. Reload the page.');
      const bitmap=await createImageBitmap(await response.blob());
      const entry={bitmap,ms:Number(response.headers.get('X-Render-Ms')),contours:Number(response.headers.get('X-Contour-Interval')),land:Number(response.headers.get('X-Land-Fraction'))};
      tileCache.set(job.tile.key,entry);trimTiles();return entry;
    })().then(job.resolve,job.reject).finally(()=>{tilePending.delete(job.tile.key);tileWorkers--;pumpTiles();});
  }
}
function loadTile(tile,frame){
  const cached=cachedTile(tile.key);if(cached)return Promise.resolve(cached);
  if(tilePending.has(tile.key))return tilePending.get(tile.key);
  const pending=new Promise((resolve,reject)=>tileQueue.push({tile,frame,resolve,reject}));tilePending.set(tile.key,pending);pumpTiles();return pending;
}
function paintTiles(frame,tiles,target=ctx){
  target.fillStyle='#e1e9e7';target.fillRect(0,0,canvas.width,canvas.height);
  target.imageSmoothingEnabled=false;
  for(const tile of tiles){const entry=cachedTile(tile.key);if(entry)target.drawImage(entry.bitmap,(tile.x-frame.x)/frame.step,(tile.z-frame.z)/frame.step);}
}
async function render(){
  if(!state.meta)return;
  hideHover();
  if(!validView(state.x,state.z,state.step)){error('The requested view exceeds the supported coordinates or step range.');return;}
  const revision=++state.revision,frame=capture(),width=canvas.width,height=canvas.height;
  if(HYDRO_LAYERS.has(frame.layer)&&tileSizeFor(frame)<MIN_HYDRO_TILE){state.inspectRevision++;$('inspection').replaceChildren();$('export').disabled=true;error('This climate, ground or hydrology view spans too many cold continents. Zoom in before rendering it.');$('renderState').hidden=false;$('renderState').textContent='Coupled terrain view is too wide';return;}
  const tiles=tilesFor(frame,width,height);
  wantedTiles=new Set(tiles.map(t=>t.key));const hits=tiles.filter(t=>tileCache.has(t.key)).length;paintTiles(frame,tiles);
  state.inspectRevision++;$('inspection').replaceChildren();$('inspectCoords').textContent='Click the completed map to inspect all fields.';$('export').disabled=true;
  $('waterSummary').textContent='Click the map for its complete continent climate, ground-water and overflow budget.';
  $('renderState').hidden=false;$('renderState').textContent='Rendering world coordinates…';error('');syncView();layerState();
  try{
    let complete=hits;
    const entries=await Promise.all(tiles.map(async tile=>{const wasCached=tileCache.has(tile.key),entry=await loadTile(tile,frame);if(revision===state.revision){if(!wasCached)complete++;paintTiles(frame,tiles);$('renderState').textContent=`Loading map: ${complete}/${tiles.length} tiles`;}return entry;}));
    if(revision!==state.revision)return;
    const snapshot=document.createElement('canvas');snapshot.width=width;snapshot.height=height;paintTiles(frame,tiles,snapshot.getContext('2d'));
    const blob=await new Promise(resolve=>snapshot.toBlob(resolve,'image/png'));if(revision!==state.revision)return;if(!blob)throw new Error('Could not export the completed map');
    state.committed={...frame,blob};state.completeRevision=revision;$('renderState').hidden=true;$('export').disabled=false;
    if(frame.layer==='heightMap'){const interval=entries[0].contours;$('contourState').textContent=interval?`Showing ${interval}-block contours (requested ${frame.contourInterval}); heavier every ${interval*5} blocks. Zoom in for finer lines.`:'Contours off';}
    $('landFraction').textContent=(100*entries.reduce((sum,e,i)=>sum+e.land*tiles[i].width*tiles[i].height,0)/tiles.reduce((sum,t)=>sum+t.width*t.height,0)).toFixed(2)+'% land in loaded tiles';
    $('nativeScale').textContent=`Size ${frame.params.size} · height ${256*frame.params.size} · sea Y ${frame.params.seaLevel*frame.params.size}`;
    $('timing').textContent=`${hits}/${tiles.length} tiles reused · ${tileCache.size}/${TILE_LIMIT} cached`;
    $('coords').textContent=`x ${frame.x.toLocaleString()} · z ${frame.z.toLocaleString()} · ${frame.step.toLocaleString()} blocks / px`;
    const saved=query(frame);saved.delete('width');saved.delete('height');saved.set('version',state.meta.version);for(const key of ['hydrologyVersion','fluvialVersion','climateVersion','windVersion','substrateVersion','dendriticVersion'])saved.set(key,frame[key]);history.replaceState(null,'','/tectonics.html?'+saved);
  }catch(e){if(e.name==='AbortError'||revision!==state.revision)return;error(e.message);$('renderState').textContent='Render failed — previous image is not the requested view';}
}
function apply(){
  if(!state.meta)return;
  try{
    const rawSeed=$('seed').value.trim();if(!/^-?\d+$/.test(rawSeed)||BigInt(rawSeed)<-(1n<<63n)||BigInt(rawSeed)>(1n<<63n)-1n)throw new Error('Seed must be an exact signed 64-bit integer.');const seed=BigInt(rawSeed).toString();
    const x=Number($('originX').value),z=Number($('originZ').value),step=Number($('step').value);if(!validView(x,z,step))throw new Error('Invalid view coordinates or step.');
    const params={};for(const s of state.meta.params){const input=$('p-'+s.id),value=s.control==='checkbox'?(input.checked?1:0):Number(input.value);if(!Number.isSafeInteger(value)||value<s.min||value>s.max)throw new Error('Invalid '+s.label);params[s.id]=value;}
    if(params.hardStableAngle<params.softStableAngle)throw new Error('Hard-rock stable angle must be at least the soft-material angle.');
    Object.assign(state,{seed,x,z,step,params,dirty:false});$('draftState').hidden=true;render();
  }catch(e){error(e.message);}
}
function move(x,z,step){if(!validView(x,z,step)){error('That move exceeds the supported coordinate range.');return;}drag=null;clearTimeout(dragTimer);canvas.style.cursor='grab';Object.assign(state,{x,z,step});render();}
function zoom(factor,px=canvas.width/2,py=canvas.height/2){const step=Math.max(1,Math.min(1048576,Math.round(state.step*factor)));if(step===state.step)return;move(Math.round(state.x+px*(state.step-step)),Math.round(state.z+py*(state.step-step)),step);}
function position(event){const r=canvas.getBoundingClientRect();return {x:Math.max(0,Math.min(canvas.width-1,Math.floor((event.clientX-r.left)*canvas.width/r.width))),z:Math.max(0,Math.min(canvas.height-1,Math.floor((event.clientY-r.top)*canvas.height/r.height)))};}
async function inspect(pixel){
  if(!state.committed||state.completeRevision!==state.revision)return;
  const frame=state.committed,revision=++state.inspectRevision,point={x:frame.x+pixel.x*frame.step,z:frame.z+pixel.z*frame.step};
  $('inspectCoords').textContent=`x ${point.x} · z ${point.z} — loading`;
  try{const response=await fetch('/api/tectonic/sample?'+query(frame,point));if(!response.ok)throw new Error((await response.json()).error||'Inspection failed');const data=await response.json();
    if(revision!==state.inspectRevision)return;if(data.model!==state.meta.model||data.version!==state.meta.version||data.hydrologyVersion!==frame.hydrologyVersion||data.fluvialVersion!==frame.fluvialVersion||data.climateVersion!==frame.climateVersion||data.windVersion!==frame.windVersion||data.substrateVersion!==frame.substrateVersion||data.dendriticVersion!==frame.dendriticVersion||data.seed!==frame.seed||data.x!==String(point.x)||data.z!==String(point.z)||data.size!==frame.params.size)throw new Error('Inspector returned a mismatched world point.');
    $('inspectCoords').textContent=`x ${data.x} · z ${data.z}`;$('inspection').replaceChildren();
    for(const field of state.meta.fields){const dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=field.label;dd.textContent=fieldText(field,data.fields[field.id]);$('inspection').append(dt,dd);}
    $('waterSummary').textContent=waterText(data.hydrology);
  }catch(e){if(revision===state.inspectRevision)error(e.message);}
}
const hoverCache=new Map();let hoverTimer=null,hoverRevision=0,hoverController=null;
function hideHover(){clearTimeout(hoverTimer);hoverRevision++;hoverController?.abort();$('hoverTip').hidden=true;}
function number(value,digits=2){return typeof value==='number'&&Number.isFinite(value)?value.toFixed(digits):'unavailable';}
function fieldText(field,value){
  if(value===null||value===undefined)return 'unavailable';
  if(typeof value==='number')return Number.isFinite(value)?(Number.isInteger(value)?String(value):value.toFixed(3)):'unavailable';
  if(typeof value==='string'||typeof value==='boolean')return String(value);
  try{return JSON.stringify(value)??'unavailable';}catch(_ignored){return 'unavailable';}
}
function channelText(channel,prefix='River'){
  const form=String(channel.planform).toLowerCase();
  return `${prefix}: ${number(channel.meanDischarge,3)} m³/s at ${number(channel.currentVelocity)} m/s · ${number(channel.currentWidth)} × ${number(channel.currentDepth)} m · ${form}. Bankfull ${number(channel.bankfullDischarge,3)} m³/s · ${number(channel.bankfullWidth)} × ${number(channel.bankfullDepth)} m.`;
}
function waterText(h){
  if(!h?.status)return 'Hydrology unresolved or complete-continent support unavailable here; no map-edge outlet is invented.';
  const node=`Node ${h.nodeX}, ${h.nodeZ} · ${h.contributingArea} block² contributing area · Strahler ${h.strahlerOrder}.`;
  const climate=h.climate?` Climate: ${number(h.climate.rainfall,0)} mm/year rain, humidity ${number(h.climate.humidity,3)}, wind ${number(h.climate.wind?.directionDegrees,1)}° at ${number(h.climate.wind?.speed)}.`:'';
  const ground=h.ground?` Ground: ${h.ground.rock}, ${number(h.ground.soilDepth)} m soil, ${h.ground.infiltrationPermille}‰ infiltration, ${h.ground.runoffPermille}‰ runoff, ${String(h.ground.drainage).toLowerCase().replaceAll('_',' ')}.`:'';
  let water='No realized fine lake or channel water at this point.';
  if(h.lake)water=`Lake ${h.lake.id} · flat surface Y ${number(h.lake.surface)} · depth ${number(h.lake.depth)} / ${number(h.lake.maxDepth)} m.`;
  else if(h.channel?.insideCurrent)water=channelText(h.channel);
  else if(h.channel?.insideBankfull)water=channelText(h.channel,'Bankfull corridor; current thread nearby');
  return `${node}${climate}${ground} ${water} Complete family: ${h.activeCells} nodes; supplied ${h.supplied}, discharged ${h.discharged}, unresolved ${h.unresolved}.`;
}
function hoverText(data){
  const f=data.fields,h=data.hydrology;
  let water='Water: none in the fine channel/lake mask';
  if(h?.lake)water=`Lake: Y ${number(h.lake.surface)} · ${number(h.lake.depth)} m deep`;
  else if(h?.channel?.insideCurrent)water=channelText(h.channel);
  else if(h?.channel?.insideBankfull)water=channelText(h.channel,'Bankfull corridor');
  const rain=typeof f.rainfall==='number'&&Number.isFinite(f.rainfall)?Math.round(f.rainfall):'unavailable';
  return `X ${data.x} · Z ${data.z}\nSurface Y ${number(f.erodedTerrain)} · sea Y ${data.seaLevel}\nBefore erosion ${number(f.elevation)} · erosion ${number(f.erosionDepth)} blocks\nClimate: rain ${rain} mm/year · humidity ${number(f.humidity,3)} · wind ${number(f.windDirection,1)}° at ${number(f.windSpeed)}\nGround: ${f.rockType??'unavailable'} · soil ${number(f.soilDepth)} m · bedrock Y ${number(f.bedrockElevation)} · drainage ${f.drainage??'unavailable'}\n${water}\nPlate age ${f.age} (synthetic) · ${f.regime}`;
}
function hoverMove(e){
  if(drag||!state.committed||state.completeRevision!==state.revision){hideHover();return;}
  hideHover();const frame=state.committed,pixel=position(e),point={x:frame.x+pixel.x*frame.step,z:frame.z+pixel.z*frame.step},revision=hoverRevision;
  const tip=$('hoverTip'),r=canvas.getBoundingClientRect(),parent=canvas.parentElement?.getBoundingClientRect()??r;
  tip.style.left=Math.max(0,Math.min(e.clientX-parent.left+14,r.right===undefined?r.width-286:r.right-parent.left-286))+'px';
  tip.style.top=Math.max(0,Math.min(e.clientY-parent.top+14,r.top-parent.top+r.height-165))+'px';
  tip.textContent=`X ${point.x} · Z ${point.z}\nLoading height and terrain…`;tip.hidden=false;
  const url='/api/tectonic/sample?'+query(frame,point),key=versionKey(frame)+'|'+url;
  if(hoverCache.has(key)){const data=hoverCache.get(key);hoverCache.delete(key);hoverCache.set(key,data);tip.textContent=hoverText(data);return;}
  hoverTimer=setTimeout(async()=>{
    hoverController=new AbortController();
    try{
      const response=await fetch(url,{signal:hoverController.signal});if(!response.ok)throw new Error('Terrain details unavailable');const data=await response.json();
      if(revision!==hoverRevision)return;
      if(data.version!==state.meta.version||data.hydrologyVersion!==frame.hydrologyVersion||data.fluvialVersion!==frame.fluvialVersion||data.climateVersion!==frame.climateVersion||data.windVersion!==frame.windVersion||data.substrateVersion!==frame.substrateVersion||data.dendriticVersion!==frame.dendriticVersion||data.seed!==frame.seed||data.x!==String(point.x)||data.z!==String(point.z))throw new Error('Terrain details out of date');
      hoverCache.set(key,data);if(hoverCache.size>256)hoverCache.delete(hoverCache.keys().next().value);tip.textContent=hoverText(data);
    }catch(e){if(revision===hoverRevision&&e.name!=='AbortError')tip.textContent=`X ${point.x} · Z ${point.z}\n${e.message}`;}
  },160);
}
canvas.addEventListener('pointerleave',hideHover);
let drag=null,wheelTimer=null,dragTimer=null;
canvas.addEventListener('pointerdown',e=>{if(e.button!==0||!state.meta)return;hideHover();e.preventDefault();canvas.setPointerCapture(e.pointerId);const rect=canvas.getBoundingClientRect();drag={id:e.pointerId,px:e.clientX,py:e.clientY,x:state.x,z:state.z,step:state.step,sx:canvas.width/rect.width,sz:canvas.height/rect.height,moved:false};canvas.style.cursor='grabbing';});
function dragMove(e){
  if(!drag||drag.id!==e.pointerId)return;const d=drag,dx=e.clientX-d.px,dz=e.clientY-d.py;
  if(!d.moved&&Math.hypot(dx,dz)<4)return;d.moved=true;
  const x=d.x-Math.round(dx*d.sx)*d.step,z=d.z-Math.round(dz*d.sz)*d.step;
  if(!validView(x,z,d.step))return;
  Object.assign(state,{x,z,step:d.step});state.revision++;state.inspectRevision++;$('export').disabled=true;
  const frame=capture(),tiles=tilesFor(frame);wantedTiles=new Set(tiles.map(t=>t.key));paintTiles(frame,tiles);syncView();
  clearTimeout(dragTimer);dragTimer=setTimeout(()=>render(),100);
}
canvas.addEventListener('pointermove',e=>{dragMove(e);hoverMove(e);});
canvas.addEventListener('pointerup',e=>{if(!drag||drag.id!==e.pointerId)return;dragMove(e);const moved=drag.moved;drag=null;clearTimeout(dragTimer);canvas.style.cursor='grab';canvas.releasePointerCapture?.(e.pointerId);if(moved)render();else inspect(position(e));});
function cancelDrag(){if(!drag)return;drag=null;clearTimeout(dragTimer);canvas.style.cursor='grab';render();}
canvas.addEventListener('pointercancel',cancelDrag);canvas.addEventListener('lostpointercapture',cancelDrag);
canvas.addEventListener('wheel',e=>{e.preventDefault();const p=position(e);clearTimeout(wheelTimer);wheelTimer=setTimeout(()=>zoom(e.deltaY>0?2:.5,p.x,p.z),100);},{passive:false});
canvas.addEventListener('keydown',e=>{const moves={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,-1],ArrowDown:[0,1]};if(moves[e.key]){e.preventDefault();const [dx,dz]=moves[e.key];move(state.x+dx*48*state.step,state.z+dz*48*state.step,state.step);}else if(e.key==='+'||e.key==='='){e.preventDefault();zoom(.5);}else if(e.key==='-'){e.preventDefault();zoom(2);}});
$('worldForm').addEventListener('submit',e=>{e.preventDefault();apply();});$('zoomIn').onclick=()=>zoom(.5);$('zoomOut').onclick=()=>zoom(2);
$('worldForm').addEventListener('input',e=>{if(e?.target?.id==='liveHydrology')return;state.dirty=true;$('draftState').hidden=false;if(e?.target&&$('liveHydrology').checked){clearTimeout(liveHydrologyTimer);liveHydrologyTimer=setTimeout(apply,350);}});
$('overview').onclick=()=>{if(!state.meta)return;const spacing=state.params.plateSpacing*state.params.size,step=Math.max(1,Math.round(spacing*3/canvas.width));move(-canvas.width/2*step,-canvas.height/2*step,step);};
$('reset').onclick=()=>{if(!state.meta)return;for(const s of state.meta.params)setParameterValue(s,s.default);$('seed').value='42';$('originX').value=DEFAULT_VIEW.x;$('originZ').value=DEFAULT_VIEW.z;$('step').value=DEFAULT_VIEW.step;state.contourInterval=5;$('contourInterval').value=5;apply();};
$('resetHydrology').onclick=()=>{if(!state.meta)return;for(const s of state.meta.params)if(s.section!=='terrain')setParameterValue(s,s.default);apply();};
$('contourInterval').addEventListener('change',()=>{const value=Number($('contourInterval').value);if(!Number.isSafeInteger(value)||(value!==0&&(value<1||value>256))){error('Contour spacing must be 0 (off) or 1..256 blocks.');return;}state.contourInterval=value;render();});
const mapPanel=$('mapPanel');
async function toggleFullscreen(){try{if(document.fullscreenElement===mapPanel)await document.exitFullscreen();else await mapPanel.requestFullscreen();}catch(e){error('Fullscreen could not be opened: '+e.message);}}
$('fullscreen').onclick=toggleFullscreen;$('exitFullscreen').onclick=toggleFullscreen;
if(typeof mapPanel.requestFullscreen!=='function')$('fullscreen').disabled=true;
document.addEventListener?.('fullscreenchange',()=>{const full=document.fullscreenElement===mapPanel;$('fullscreen').textContent=full?'Exit fullscreen':'Fullscreen';canvas.width=canvas.height=full?512:384;if(state.meta)render();});
function download(blob,name){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);}
$('export').onclick=()=>{const c=state.committed;if(!c||state.completeRevision!==state.revision)return;const {blob,...config}=c;download(blob,`genesis-tectonic-${c.seed}-${c.layer}.png`);download(new Blob([JSON.stringify({model:state.meta.model,version:state.meta.version,...config,width:canvas.width,height:canvas.height,waterStatus:state.meta.waterStatus},null,2)],{type:'application/json'}),'genesis-tectonic-config.json');};
async function init(){
  try{$('upgradeNotice').hidden=true;const response=await fetch('/api/tectonic/meta');if(!response.ok)throw new Error('Tectonic API unavailable. Restart the updated local server.');state.meta=await response.json();
    const url=new URLSearchParams(location.search),legacy=['crustProvinceScale','crustRadiusPermille'];
    const upgrade=legacy.some(k=>url.has(k))||['tectonic-terrain-v1','tectonic-terrain-v2','tectonic-terrain-v3'].includes(url.get('version'));
    for(const key of url.keys())if(!INPUTS.includes(key)&&!state.meta.params.some(p=>p.id===key)&&!(upgrade&&legacy.includes(key)))throw new Error('Unknown saved configuration key: '+key);
    if(upgrade){for(const s of state.meta.params)url.delete(s.id);for(const key of legacy)url.delete(key);url.delete('version');$('upgradeNotice').hidden=false;}
    const savedVersion=url.get('version');
    if(savedVersion==='tectonic-terrain-v4')migrateCompactScale(url,32,'This v4 link used the original model-metre terrain. Its location, sampling step and plate spacing were divided by 32 exactly once for the Minecraft-scale v8 world; compatible settings are preserved.');
    if(savedVersion==='tectonic-terrain-v5'||savedVersion==='tectonic-terrain-v6')migrateCompactScale(url,4,'This saved link predates the fourfold-smaller v8 world. Its location, sampling step and plate spacing were divided by 4 exactly once; compatible settings are preserved.');
    if(url.has('version')&&url.get('version')!==state.meta.version)throw new Error('This saved world uses another generator version. Open /tectonics.html for the current preset.');
    if(url.has('hydrologyVersion')&&url.get('hydrologyVersion')!==state.meta.hydrologyVersion)throw new Error('This saved world uses another hydrology version. Open /tectonics.html for the current preset.');
    if(url.has('fluvialVersion')&&url.get('fluvialVersion')!==state.meta.fluvialVersion)throw new Error('This saved world uses another fluvial-network version. Open /tectonics.html for the current preset.');
    if(url.has('climateVersion')&&url.get('climateVersion')!==state.meta.climateVersion)throw new Error('This saved world uses another climate version. Open /tectonics.html for the current preset.');
    if(url.has('windVersion')&&url.get('windVersion')!==state.meta.windVersion)throw new Error('This saved world uses another wind version. Open /tectonics.html for the current preset.');
    if(url.has('substrateVersion')&&url.get('substrateVersion')!==state.meta.substrateVersion)throw new Error('This saved world uses another substrate version. Open /tectonics.html for the current preset.');
    if(url.has('dendriticVersion')&&url.get('dendriticVersion')!==state.meta.dendriticVersion)throw new Error('This saved world uses another dendritic-terrain version. Open /tectonics.html for the current preset.');
    buildParameterControls(url);$('layers').replaceChildren();
    for(const f of state.meta.fields){const b=document.createElement('button');b.type='button';b.textContent=f.label;b.title=f.description;b.dataset.field=f.id;b.onclick=()=>{state.layer=f.id;render();};$('layers').append(b);}
    state.layer=url.get('layer')||'erodedTerrain';if(!state.meta.fields.some(f=>f.id===state.layer))throw new Error('Unknown saved field.');
    state.contourInterval=Number(url.get('contourInterval')??5);if(!Number.isSafeInteger(state.contourInterval)||(state.contourInterval!==0&&(state.contourInterval<1||state.contourInterval>256)))throw new Error('Invalid saved contour interval.');$('contourInterval').value=state.contourInterval;
    $('seed').value=url.get('seed')??state.seed;$('originX').value=url.get('x')??state.x;$('originZ').value=url.get('z')??state.z;$('step').value=url.get('step')??state.step;
    $('version').textContent=[state.meta.version,state.meta.hydrologyVersion,state.meta.fluvialVersion,state.meta.climateVersion,state.meta.windVersion,state.meta.substrateVersion,state.meta.dendriticVersion].join(' · ');apply();
  }catch(e){error(e.message);$('renderState').textContent='Viewer unavailable';}
}
let liveHydrologyTimer;
const HYDRO_SECTION_LABELS={climate:'Climate + rainfall',ground:'Soil + runoff',routing:'Routing grid',erosion:'Erosion + slopes',channels:'River geometry',morphology:'Fine relief'};
function numericInput(spec,value){const input=document.createElement('input');input.id='p-'+spec.id;input.type='number';input.min=spec.min;input.max=spec.max;input.step=spec.step;input.value=value;return input;}
function setParameterValue(spec,value){const input=$('p-'+spec.id);if(spec.control==='checkbox')input.checked=Number(value)!==0;else input.value=value;if(spec.control==='range')$('r-'+spec.id).value=value;}
function buildParameterControls(url){
  $('params').replaceChildren();$('hydrologyParams').replaceChildren();const sections=new Map();
  for(const spec of state.meta.params){const value=url.get(spec.id)??spec.default;
    if(spec.section==='terrain'){const label=document.createElement('label'),input=numericInput(spec,value);label.textContent=spec.label;label.append(input);$('params').append(label);continue;}
    let section=sections.get(spec.section);if(!section){section=document.createElement('fieldset');section.className='hydrology-section';const legend=document.createElement('legend');legend.textContent=HYDRO_SECTION_LABELS[spec.section]||spec.section;section.append(legend);sections.set(spec.section,section);$('hydrologyParams').append(section);}
    if(spec.control==='checkbox'){const label=document.createElement('label'),input=document.createElement('input'),copy=document.createElement('span'),help=document.createElement('small');label.className='toggle-control';input.id='p-'+spec.id;input.type='checkbox';input.checked=Number(value)!==0;input.dataset.hydrology='true';copy.textContent=spec.label;help.className='control-help';help.textContent=spec.help;label.append(input,copy,help);section.append(label);continue;}
    const control=document.createElement('div'),title=document.createElement('label'),help=document.createElement('small'),inputs=document.createElement('div'),number=numericInput(spec,value);control.className='hydro-control';title.className='control-title';title.textContent=spec.label;title.htmlFor='p-'+spec.id;help.className='control-help';help.textContent=spec.help;inputs.className='control-inputs';number.dataset.hydrology='true';
    if(spec.control==='range'){const range=document.createElement('input');range.id='r-'+spec.id;range.type='range';range.min=spec.min;range.max=spec.max;range.step=spec.step;range.value=value;range.dataset.hydrology='true';range.oninput=()=>{number.value=range.value;};number.oninput=()=>{range.value=number.value;};inputs.append(range,number);}else inputs.append(number);
    control.append(title,help,inputs);section.append(control);
  }
}
function migrateCompactScale(url,divisor,message){
  if(url.has('plateSpacing'))url.set('plateSpacing',Math.max(2048,Math.round(Number(url.get('plateSpacing'))/divisor)));
  for(const key of ['x','z','step'])if(url.has(key))url.set(key,key==='step'?Math.max(1,Math.round(Number(url.get(key))/divisor)):Math.floor(Number(url.get(key))/divisor));
  for(const key of ['version','hydrologyVersion','fluvialVersion','climateVersion','windVersion','substrateVersion','dendriticVersion'])url.delete(key);$('upgradeNotice').textContent=message;$('upgradeNotice').hidden=false;
}
init();
