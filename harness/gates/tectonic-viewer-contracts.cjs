'use strict';
// Optional minimal-DOM application-logic checks against a live server, not browser painting/gesture QA.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const base=process.argv[2]||'http://localhost:8787';
async function run(){
  const html=await(await fetch(base+'/tectonics.html')).text(),elements=new Map(),downloads=[],requests=[];
  class Element{
    constructor(tag='div'){this.tagName=tag;this.children=[];this.value='';this.dataset={};this.style={};this.events={};this.hidden=false;this.textContent='';this.disabled=false;}
    set id(value){this._id=value;elements.set(value,this);}get id(){return this._id;}
    append(...items){this.children.push(...items);for(const item of items)item.parent=this;}
    replaceChildren(...items){this.children=[];this.append(...items);}
    remove(){if(this.parent)this.parent.children=this.parent.children.filter(x=>x!==this);}
    setAttribute(name,value){this[name]=value;}addEventListener(name,fn){this.events[name]=fn;}
    getBoundingClientRect(){return {left:0,top:0,width:384,height:384};}setPointerCapture(){}
    getContext(){return {drawImage(){},fillRect(){}};}toBlob(callback){callback(new Blob(['test canvas snapshot'],{type:'image/png'}));}click(){if(this.download)downloads.push({name:this.download,url:this.href});this.onclick?.({preventDefault(){}});}
    async requestFullscreen(){context.document.fullscreenElement=this;context.document.events.fullscreenchange?.();}
  }
  for(const match of html.matchAll(/<[^>]*\bid="([^"]+)"[^>]*>/g)){const e=new Element();e.id=match[1];e.value=/\bvalue="([^"]*)"/.exec(match[0])?.[1]||'';}
  elements.get('map').width=384;elements.get('map').height=384;
  const body=new Element('body'),blobs=new Map();let slowNextRender=false,slowNextPoint=false,urlIndex=0;
  class LocalURL extends URL{static createObjectURL(blob){const id='blob:test-'+urlIndex++;blobs.set(id,blob);return id;}static revokeObjectURL(){}}
  const document={body,events:{},fullscreenElement:null,getElementById:id=>{assert(elements.has(id),'Missing DOM node '+id);return elements.get(id);},createElement:tag=>new Element(tag),addEventListener(name,fn){this.events[name]=fn;},async exitFullscreen(){this.fullscreenElement=null;this.events.fullscreenchange?.();}};
  const context=vm.createContext({document,
    location:{search:'?seed=-9223372036854775808&crustProvinceScale=4&crustRadiusPermille=1500&plateSpacing=65536'},history:{replaceState(_a,_b,url){context.savedURL=url;}},URL:LocalURL,URLSearchParams,AbortController,DOMException,Blob,setTimeout,clearTimeout,
    fetch:async(url,options)=>{requests.push(String(url));const slow=String(url).startsWith('/api/tectonic/render')&&slowNextRender,point=String(url).startsWith('/api/tectonic/sample')&&slowNextPoint;
      if(slow)slowNextRender=false;if(point)slowNextPoint=false;const response=await fetch(new URL(url,base),slow?{}:options);
      if(slow||point)await new Promise(r=>setTimeout(r,350));return response;},
    createImageBitmap:async blob=>{assert.equal(blob.type,'image/png');const bytes=Buffer.from(await blob.arrayBuffer());assert.equal(bytes.subarray(1,4).toString(),'PNG');return {close(){}};}
  });
  const evaluate=code=>vm.runInContext(code,context);
  evaluate(fs.readFileSync('harness/viewer/tectonics.js','utf8'));
  async function settled(){const end=Date.now()+20000;while(Date.now()<end){if(elements.get('error').textContent)throw Error(elements.get('error').textContent);if(evaluate('state.completeRevision>0&&state.completeRevision===state.revision'))return;await new Promise(r=>setTimeout(r,20));}throw Error('Viewer render timeout');}
  await settled();assert.equal(evaluate('state.committed.seed'),'-9223372036854775808');assert.equal(elements.get('layers').children.length,evaluate('state.meta.fields.length'));
  const climateGroundLayers=['windDirection','windSpeed','humidity','rainfall','soilDepth','bedrockElevation','bedrockDepth','rockType','infiltration','runoffFraction','drainage'];
  assert.deepEqual(climateGroundLayers.filter(id=>!evaluate('state.meta.fields.some(field=>field.id==='+JSON.stringify(id)+')')),[],'Missing climate/ground viewer fields');
  assert.deepEqual(climateGroundLayers.filter(id=>!['windDirection','windSpeed'].includes(id)&&!evaluate('HYDRO_LAYERS.has('+JSON.stringify(id)+')')),[],'Complete-root climate/ground fields must use bounded hydrology tiles');
  assert.match(context.savedURL,/plateWarpPermille=220/);assert.equal(evaluate('Object.keys(state.committed.params).length'),21);
  assert.match(context.savedURL,/version=tectonic-terrain-v7/);assert.doesNotMatch(context.savedURL,/crustProvinceScale/);
  assert.equal(evaluate('state.params.elevationOffset'),-53);assert.equal(evaluate('state.params.size'),1);assert.match(context.savedURL,/contourInterval=5/);
  assert.match(context.savedURL,/hydrologyVersion=continental-hydrology-v4/);assert.match(context.savedURL,/fluvialVersion=fluvial-network-v2/);assert.match(context.savedURL,/climateVersion=terrain-climate-v1/);assert.match(context.savedURL,/windVersion=wind-field-v1/);assert.match(context.savedURL,/substrateVersion=terrain-substrate-v1/);assert.equal(evaluate('state.params.rainfallMm'),1000);
  assert.equal(evaluate('state.committed.layer'),'erodedTerrain');assert.equal(evaluate('state.params.erosionStrength'),700);
  assert.equal(evaluate('state.params.plateSpacing'),2048);assert.equal(evaluate('state.x'),-3072);assert.equal(evaluate('state.z'),-3072);assert.equal(evaluate('state.step'),16);assert.equal(elements.get('upgradeNotice').hidden,false);assert.match(elements.get('nativeScale').textContent,/height 256 · sea Y 63/);
  const renderCount=()=>requests.filter(x=>x.startsWith('/api/tectonic/render')).length;
  const initialCount=renderCount(),initialX=evaluate('state.x'),initialZ=evaluate('state.z'),initialStep=evaluate('state.step');
  evaluate('render();render()');await settled();assert.equal(renderCount(),initialCount,'Repeated frames should reuse tiles');
  const pointer=(x,y)=>({button:0,pointerId:7,clientX:x,clientY:y,preventDefault(){}});
  const pointCount=()=>requests.filter(x=>x.startsWith('/api/tectonic/sample')).length;
  elements.get('map').events.pointermove(pointer(120,130));assert.equal(elements.get('hoverTip').hidden,false);assert.match(elements.get('hoverTip').textContent,/X .*Z /);
  const hoverEnd=Date.now()+20000;while(!elements.get('hoverTip').textContent.includes('Surface Y')&&Date.now()<hoverEnd)await new Promise(r=>setTimeout(r,20));
  assert.match(elements.get('hoverTip').textContent,/Surface Y .*sea Y 63/);assert.match(elements.get('hoverTip').textContent,/Climate: rain .*humidity/);assert.match(elements.get('hoverTip').textContent,/Ground: .*soil .*bedrock Y .*drainage/);
  const hoverRequests=pointCount();elements.get('map').events.pointermove(pointer(120,130));await new Promise(r=>setTimeout(r,220));assert.equal(pointCount(),hoverRequests,'Repeated hover must use cache');
  slowNextPoint=true;elements.get('map').events.pointermove(pointer(121,130));await new Promise(r=>setTimeout(r,190));elements.get('map').events.pointerleave();await new Promise(r=>setTimeout(r,450));assert.equal(elements.get('hoverTip').hidden,true,'Late hover reappeared after leave');
  elements.get('map').events.pointerdown(pointer(200,200));elements.get('map').events.pointermove(pointer(232,200));
  assert.equal(evaluate('state.x'),initialX-32*initialStep,'Drag must move before pointer release');assert.equal(renderCount(),initialCount);
  elements.get('map').events.pointerup(pointer(232,200));await settled();assert.equal(renderCount(),initialCount,'Within cached coverage drag should not generate');
  evaluate(`move(${initialX},${initialZ},${initialStep})`);await settled();assert.equal(renderCount(),initialCount);
  evaluate(`move(${initialX+128*initialStep},${initialZ},${initialStep});render()`);await settled();assert.equal(renderCount()-initialCount,4,'Only one new column, with shared in-flight requests');
  const afterPan=renderCount();evaluate(`move(${initialX},${initialZ},${initialStep})`);await settled();assert.equal(renderCount(),afterPan,'Returning to cached region regenerates');
  const clipped=evaluate('tilesFor({...capture(),x:LIMIT-7,z:-LIMIT,step:1},8,8)');
  for(const t of clipped){assert(t.x>=-(2**40)&&t.z>=-(2**40)&&t.x+(t.width-1)<=2**40&&t.z+(t.height-1)<=2**40,'Tile escaped numeric bounds');}
  const coarseWater=evaluate("tilesFor({...capture(),layer:'riverMap',step:256},384,384)");
  assert(coarseWater.length>9&&coarseWater.every(t=>t.width<=96&&t.height<=96),'Coarse hydrology did not shrink its cold-work tiles');
  // Small fixture canvas for repeated field checks; Java HTTP gates separately check real render pixels.
  elements.get('map').width=8;elements.get('map').height=8;
  for(const button of elements.get('layers').children){button.click();await settled();assert.equal(evaluate('state.committed.layer'),button.dataset.field);assert.equal(button['aria-pressed'],'true');}
  evaluate("state.layer='heightMap';render()");await settled();assert.equal(elements.get('heightLegend').hidden,false);
  elements.get('contourInterval').value='1';elements.get('contourInterval').events.change();await settled();assert.equal(evaluate('state.committed.contourInterval'),1);assert.match(context.savedURL,/contourInterval=1/);assert.match(elements.get('contourState').textContent,/requested 1/);
  elements.get('contourInterval').value='257';elements.get('contourInterval').events.change();assert.match(elements.get('error').textContent,/Contour spacing/);assert.equal(evaluate('state.contourInterval'),1);
  elements.get('contourInterval').value='1';evaluate('error("")');
  elements.get('fullscreen').click();await settled();assert.equal(document.fullscreenElement,elements.get('mapPanel'));assert.equal(elements.get('map').width,512);
  elements.get('exitFullscreen').click();await settled();assert.equal(document.fullscreenElement,null);assert.equal(elements.get('map').width,384);
  const oldStep=evaluate('state.step');elements.get('zoomIn').click();await settled();assert.equal(evaluate('state.step'),oldStep/2);
  const oldX=evaluate('state.x');elements.get('map').events.keydown({key:'ArrowRight',preventDefault(){}});await settled();assert.equal(evaluate('state.x'),oldX+48*oldStep/2);
  await evaluate('inspect({x:2,z:3})');assert.equal(elements.get('inspection').children.length,evaluate('state.meta.fields.length*2'));assert.doesNotMatch(elements.get('inspectCoords').textContent,/loading/);assert.match(elements.get('waterSummary').textContent,/Complete family/);
  assert.equal(evaluate(`fieldText({id:'missing'},undefined)`),'unavailable');assert.equal(evaluate(`fieldText({id:'bad'},NaN)`),'unavailable');assert.equal(evaluate(`fieldText({id:'humidity'},.625)`),'0.625');
  const riverCopy=evaluate(`waterText({status:1,nodeX:'1',nodeZ:'2',contributingArea:'4096',strahlerOrder:3,activeCells:9,supplied:'10',discharged:'10',unresolved:'0',channel:{insideCurrent:true,planform:'MEANDERING',meanDischarge:2.5,currentVelocity:1.25,currentWidth:6,currentDepth:.8,bankfullDischarge:30,bankfullWidth:12,bankfullDepth:1.6}})`);
  assert.match(riverCopy,/2\.500 m³\/s at 1\.25 m\/s/);assert.match(riverCopy,/6\.00 × 0\.80 m · meandering/);
  const lakeCopy=evaluate(`waterText({status:1,nodeX:'1',nodeZ:'2',contributingArea:'4096',strahlerOrder:1,activeCells:9,supplied:'10',discharged:'10',unresolved:'0',lake:{id:'-9223372036854775808',surface:64.5,depth:2,maxDepth:7}})`);
  assert.match(lakeCopy,/Lake -9223372036854775808 · flat surface Y 64\.50/);assert.match(evaluate(`waterText({status:0})`),/unresolved/);
  const climateCopy=evaluate(`waterText({status:1,nodeX:'1',nodeZ:'2',contributingArea:'4096',strahlerOrder:1,activeCells:9,supplied:'10',discharged:'10',unresolved:'0',climate:{rainfall:875,humidity:.625,wind:{directionDegrees:-42.5,speed:1.1}},ground:{rock:'LIMESTONE',soilDepth:2.25,infiltrationPermille:480,runoffPermille:320,drainage:'MODERATE'}})`);
  assert.match(climateCopy,/875 mm\/year rain, humidity 0\.625, wind -42\.5° at 1\.10/);assert.match(climateCopy,/LIMESTONE, 2\.25 m soil, 480‰ infiltration, 320‰ runoff, moderate/);
  elements.get('p-forcingPermille').value='0';elements.get('worldForm').events.input();assert.equal(elements.get('draftState').hidden,false);
  elements.get('layers').children[0].click();await settled();assert.equal(evaluate('state.committed.params.forcingPermille'),1000);assert.equal(elements.get('p-forcingPermille').value,'0');
  elements.get('worldForm').events.submit({preventDefault(){}});await settled();assert.equal(evaluate('state.committed.params.forcingPermille'),0);assert.equal(elements.get('draftState').hidden,true);
  // A delayed obsolete response deliberately ignores abort; revision guard must still prevent stale image/config commit.
  slowNextRender=true;evaluate("state.layer='elevation';render()");evaluate("state.layer='land';render()");await settled();await new Promise(r=>setTimeout(r,500));assert.equal(evaluate('state.committed.layer'),'land');
  slowNextPoint=true;const pending=evaluate('inspect({x:1,z:1})');evaluate("state.layer='base';render()");await settled();await pending;assert.equal(elements.get('inspection').children.length,0);
  elements.get('export').click();assert.equal(downloads.length,2);const config=JSON.parse(await blobs.get(downloads[1].url).text());assert.equal(config.model,'tectonic-experimental');assert.equal(config.seed,'-9223372036854775808');assert.equal(config.layer,'base');assert.equal(config.params.forcingPermille,0);assert.match(config.waterStatus,/bounded fine channel corridors/);assert.match(config.waterStatus,/connected-lake/);assert.equal(config.hydrologyVersion,'continental-hydrology-v4');assert.equal(config.fluvialVersion,'fluvial-network-v2');assert.equal(config.climateVersion,'terrain-climate-v1');assert.equal(config.windVersion,'wind-field-v1');assert.equal(config.substrateVersion,'terrain-substrate-v1');
  assert.equal(config.contourInterval,1);assert.equal(config.params.elevationOffset,-53);assert.equal(config.params.size,1);
  elements.get('seed').value='9223372036854775808';elements.get('worldForm').events.submit({preventDefault(){}});assert.match(elements.get('error').textContent,/signed 64-bit/);
  elements.get('seed').value='42';elements.get('p-plateWarpPermille').value='301';elements.get('worldForm').events.submit({preventDefault(){}});assert.match(elements.get('error').textContent,/Invalid Plate-edge warp/);
  elements.get('reset').click();await settled();assert.equal(evaluate('state.seed'),'42');assert.equal(evaluate('state.params.plateSpacing'),2048);assert.equal(evaluate('state.params.plateWarpPermille'),220);
  assert.equal(evaluate('state.x'),-3072);assert.equal(evaluate('state.z'),-3072);assert.equal(evaluate('state.step'),16);assert.equal(evaluate('state.contourInterval'),5);assert.equal(evaluate('state.params.elevationOffset'),-53);assert.equal(evaluate('state.params.size'),1);
  elements.get('p-rainfallMm').value='0';elements.get('worldForm').events.submit({preventDefault(){}});await settled();assert.equal(evaluate('state.committed.params.rainfallMm'),0);assert.match(context.savedURL,/rainfallMm=0/);
  elements.get('p-rainfallMm').value='10001';elements.get('worldForm').events.submit({preventDefault(){}});assert.match(elements.get('error').textContent,/Invalid .*precipitation/i);
  elements.get('reset').click();await settled();assert.equal(evaluate('state.params.rainfallMm'),1000);
  assert(requests.filter(x=>x.startsWith('/api/tectonic/render')).every(x=>x.includes('continentalPercent=')&&x.includes('seaLevel=')&&x.includes('size=')&&x.includes('plateRoughnessPermille=')));
  assert(requests.filter(x=>x.startsWith('/api/tectonic/render')).every(x=>{const q=new URL(x,base).searchParams;return Number(q.get('width'))<=128&&Number(q.get('height'))<=128;}),'Viewport-sized generation request');
  assert(evaluate('tileCache.size<=TILE_LIMIT'),'Tile cache exceeded its bound');
  await evaluate("location.search='?version=tectonic-terrain-v4&hydrologyVersion=continental-hydrology-v1&plateSpacing=65536&size=2&seaLevel=70&x=-98304&z=98304&step=512';init()");await settled();
  assert.equal(evaluate('state.params.plateSpacing'),2048);assert.equal(evaluate('state.x'),-3072);assert.equal(evaluate('state.z'),3072);assert.equal(evaluate('state.step'),16);
  assert.equal(evaluate('state.params.size'),2);assert.equal(evaluate('state.params.seaLevel'),70);assert.equal(elements.get('upgradeNotice').hidden,false);
  await evaluate("location.search='?version=tectonic-terrain-v5&plateSpacing=8192&size=2&seaLevel=70&x=-12288&z=12288&step=64';init()");await settled();
  assert.equal(evaluate('state.params.plateSpacing'),2048);assert.equal(evaluate('state.x'),-3072);assert.equal(evaluate('state.step'),16);assert.equal(evaluate('state.params.size'),2);assert.equal(evaluate('state.params.seaLevel'),70);
  assert.match(elements.get('upgradeNotice').textContent,/divided by 4 exactly once/);
  await evaluate("location.search='?version=tectonic-terrain-v6&hydrologyVersion=continental-hydrology-v3&fluvialVersion=fluvial-network-v1&climateVersion=obsolete&windVersion=obsolete&substrateVersion=obsolete&plateSpacing=8192&x=-16384&z=8192&step=128';init()");await settled();
  assert.equal(evaluate('state.params.plateSpacing'),2048);assert.equal(evaluate('state.x'),-4096);assert.equal(evaluate('state.z'),2048);assert.equal(evaluate('state.step'),32);assert.match(context.savedURL,/version=tectonic-terrain-v7/);assert.match(context.savedURL,/hydrologyVersion=continental-hydrology-v4/);assert.match(context.savedURL,/climateVersion=terrain-climate-v1/);
  await evaluate("location.search='?version=tectonic-terrain-v7&hydrologyVersion=continental-hydrology-v4&fluvialVersion=fluvial-network-v2&climateVersion=terrain-climate-v1&windVersion=wind-field-v1&substrateVersion=terrain-substrate-v1&plateSpacing=2048&x=-3071&z=3073&step=17';init()");await settled();
  assert.equal(evaluate('state.params.plateSpacing'),2048);assert.equal(evaluate('state.x'),-3071);assert.equal(evaluate('state.z'),3073);assert.equal(evaluate('state.step'),17);assert.equal(elements.get('upgradeNotice').hidden,true,'Current-version saved links must not be rescaled');
  console.log('PASS TECTONIC VIEWER LOGIC: v7 compact preset and one-time migration, climate/ground layers, robust inspector, debounced/cached hover, live drag, bounded tiles, zero-request revisit, in-flight reuse, clipping, fullscreen and export');
}
run().catch(error=>{console.error(error);process.exitCode=1;});
