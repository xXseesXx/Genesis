'use strict';
const $=id=>document.getElementById(id);
const fields=[
  ['elevation','Macro elevation','Model metres · bathymetry blue; land green to pale'],
  ['filled','Filled elevation','Model metres · minimum spill height to an edge outlet'],
  ['fillDepth','Depression fill','Model metres · dark = no fill; amber to pale = deeper fill (log scale)'],
  ['water','Water connectivity','Green = land · blue = boundary-connected water · purple = enclosed sampled water'],
  ['accumulation','Runoff accumulation','Contributing land cells · dark to cyan (log scale)'],
  ['outlet','Catchments','Categorical color per edge outlet; IDs are row-major cells within this region'],
  ['downstream','Flow direction','D8 flood-tree direction hue · white = edge outlet'],
  ['order','Flood order','Dark to pale = earlier to later processing; every route strictly decreases this index']
];
let data=null, snapshot=null, selected=-1, layer='elevation', revision=0, controller=null, metadata=null;
function fail(message){$('error').textContent=message;$('error').hidden=!message;}
for(const [id,label] of fields){const button=document.createElement('button');button.textContent=label;button.dataset.field=id;button.onclick=()=>{layer=id;draw();};$('layers').append(button);}
function config(){
  const seed=$('seed').value.trim();
  if(!/^-?\d+$/.test(seed)||BigInt(seed)<-(1n<<63n)||BigInt(seed)>=(1n<<63n))throw new Error('Seed must be a signed 64-bit integer.');
  const overrides=JSON.parse($('overrides').value);
  if(!overrides||Array.isArray(overrides)||typeof overrides!=='object')throw new Error('Overrides must be a JSON object.');
  for(const [key,value] of Object.entries(overrides)){
    const spec=metadata.params.find(p=>p.id===key);
    if(!spec||typeof value!=='number'||!Number.isFinite(value))throw new Error('Invalid parameter: '+key);
  }
  const params=Object.fromEntries(metadata.params.map(p=>[p.id,p.default]));
  const q={seed,...params,...overrides};
  for(const id of ['x','z','step']){const raw=$(id).value;if(!/^-?\d+$/.test(raw)||!Number.isSafeInteger(Number(raw)))throw new Error(id+' must be an exact integer.');q[id]=raw;}
  q.width=q.height=Number($('size').value);return new URLSearchParams(q);
}
async function run(){
  const ticket=++revision;controller?.abort();controller=new AbortController();
  try{
    fail('');const query=config();$('status').textContent='Analyzing…';
    const response=await fetch('/api/hydrology?'+query,{signal:controller.signal});
    const result=await response.json();if(!response.ok)throw new Error(result.error);
    if(ticket!==revision)return;
    data=result;snapshot={query:query.toString(),...result};selected=-1;
    $('inspection').textContent='Select a cell to inspect and trace.';
    $('region').textContent=`Seed ${query.get('seed')} · ${data.width} × ${data.height} · x ${data.x}, z ${data.z} · step ${data.step}`;
    $('status').textContent=`${data.milliseconds.toFixed(0)} ms · fixed region`;
    $('metrics').textContent=`${data.landCells.toLocaleString()} land cells → ${data.discharged.toLocaleString()} units at edge outlets. Balance: ${data.landCells===data.discharged?'exact':'FAILED'}. ${data.edgeWaterCells.toLocaleString()} edge-connected water cells; ${data.enclosedWaterCells.toLocaleString()} enclosed water cells.`;
    $('version').textContent=data.version+' / '+data.generatorVersion;$('save').disabled=false;draw();
  }catch(e){if(ticket===revision&&e.name!=='AbortError'){fail(e.message);$('status').textContent='Analysis failed; previous map retained';}}
}
const mix=(a,b,t)=>a.map((v,i)=>Math.round(v+(b[i]-v)*Math.max(0,Math.min(1,t))));
function color(value,p,max){
  if(layer==='water')return [[76,102,71],[39,125,167],[166,101,205]][value];
  if(layer==='outlet'){let h=Math.imul(value+1,0x45d9f3b);h=Math.imul(h^(h>>>16),0x45d9f3b);return [65+(h&127),65+((h>>>8)&127),65+((h>>>16)&127)];}
  if(layer==='downstream'){
    if(value<0)return [238,238,222];
    const dx=value%data.width-p%data.width,dz=Math.floor(value/data.width)-Math.floor(p/data.width);
    return {'-1,-1':[147,96,206],'0,-1':[71,123,211],'1,-1':[61,185,209],'1,0':[85,191,134],'1,1':[181,199,85],'0,1':[235,185,83],'-1,1':[225,115,88],'-1,0':[209,99,164]}[dx+','+dz];
  }
  if(layer==='fillDepth'||layer==='accumulation')return mix([17,30,36],layer==='fillDepth'?[255,211,117]:[108,237,239],Math.log1p(value)/Math.log1p(Math.max(1,max)));
  if(layer==='order')return mix([25,38,57],[226,225,178],value/Math.max(1,max));
  const metres=value/1000;
  if(metres<=0)return mix([84,155,178],[12,34,65],-metres/4000);
  return metres<1500?mix([74,113,76],[168,155,113],metres/1500):mix([168,155,113],[239,234,212],(metres-1500)/3000);
}
function draw(){
  for(const button of $('layers').children){const active=button.dataset.field===layer;button.classList.toggle('active',active);button.setAttribute('aria-pressed',String(active));}
  $('legend').textContent=fields.find(f=>f[0]===layer)[2];if(!data)return;
  const canvas=$('map'),ctx=canvas.getContext('2d'),values=data.fields[layer],max=values.reduce((a,b)=>Math.max(a,b),0);
  canvas.width=data.width*4;canvas.height=data.height*4;ctx.imageSmoothingEnabled=false;
  const raster=document.createElement('canvas');raster.width=data.width;raster.height=data.height;
  const rc=raster.getContext('2d'),pixels=rc.createImageData(data.width,data.height);
  const threshold=Math.max(1,Number($('threshold').value)||1);
  for(let p=0;p<values.length;p++){
    const c=color(values[p],p,max);pixels.data.set([...c,255],p*4);
  }
  rc.putImageData(pixels,0,0);ctx.drawImage(raster,0,0,canvas.width,canvas.height);
  if($('rivers').checked){ctx.fillStyle='#57ecf4';for(let p=0;p<values.length;p++)if(data.fields.water[p]===0&&data.fields.accumulation[p]>=threshold)ctx.fillRect(p%data.width*4+1,Math.floor(p/data.width)*4+1,2,2);}
  if(selected>=0){ctx.strokeStyle='#fff16c';ctx.lineWidth=2;ctx.beginPath();let p=selected;
    ctx.moveTo(p%data.width*4+2,Math.floor(p/data.width)*4+2);
    for(let k=0;k<values.length;k++){p=data.fields.downstream[p];if(p<0)break;ctx.lineTo(p%data.width*4+2,Math.floor(p/data.width)*4+2);}ctx.stroke();
    ctx.strokeRect(selected%data.width*4,Math.floor(selected/data.width)*4,4,4);
  }
}
$('map').onclick=event=>{
  if(!data)return;const rect=$('map').getBoundingClientRect();
  const x=Math.max(0,Math.min(data.width-1,Math.floor((event.clientX-rect.left)/rect.width*data.width)));
  const z=Math.max(0,Math.min(data.height-1,Math.floor((event.clientY-rect.top)/rect.height*data.height)));selected=z*data.width+x;
  let lines=[`Cell ${selected} (${x}, ${z})`,`World x ${Number(data.x)+x*data.step}`,`World z ${Number(data.z)+z*data.step}`];
  for(const [id,label] of fields){const value=data.fields[id][selected];lines.push(`${label}: ${['elevation','filled','fillDepth'].includes(id)?(value/1000).toFixed(3)+' m':value}`);}
  $('inspection').textContent=lines.join('\n');draw();
};
for(const id of ['seed','x','z','step','size','overrides'])$(id).addEventListener('input',()=>{revision++;controller?.abort();$('status').textContent='Configuration changed — click Analyze region';});
for(const id of ['rivers','threshold'])$(id).addEventListener('input',draw);
$('run').onclick=run;
$('save').onclick=()=>{if(!snapshot)return;const url=URL.createObjectURL(new Blob([JSON.stringify(snapshot)],{type:'application/json'}));const link=document.createElement('a');link.href=url;link.download='genesis-hydrology.json';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
async function init(){try{
  $('run').disabled=true;const response=await fetch('/api/meta');if(!response.ok)throw new Error('Cannot load generator metadata.');metadata=await response.json();
  const query=new URLSearchParams(location.search),overrides={};
  for(const id of ['seed','x','z','step'])if(query.has(id))$(id).value=query.get(id);
  for(const spec of metadata.params)if(query.has(spec.id))overrides[spec.id]=Number(query.get(spec.id));
  $('overrides').value=JSON.stringify(overrides);$('run').disabled=false;draw();await run();
}catch(e){fail(e.message);}}
init();
