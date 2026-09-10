'use strict';
// Optional minimal-DOM application-logic checks against a live server, not browser painting/gesture QA.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const base=process.argv[2]||'http://127.0.0.1:8787';
async function run(){
  const html=await(await fetch(base+'/tectonics.html')).text(),elements=new Map(),downloads=[],requests=[];
  class Element{
    constructor(tag='div'){this.tagName=tag;this.children=[];this.value='';this.dataset={};this.events={};this.hidden=false;this.textContent='';this.disabled=false;}
    set id(value){this._id=value;elements.set(value,this);}get id(){return this._id;}
    append(...items){this.children.push(...items);for(const item of items)item.parent=this;}
    replaceChildren(...items){this.children=[];this.append(...items);}
    remove(){if(this.parent)this.parent.children=this.parent.children.filter(x=>x!==this);}
    setAttribute(name,value){this[name]=value;}addEventListener(name,fn){this.events[name]=fn;}
    getBoundingClientRect(){return {left:0,top:0,width:384,height:384};}setPointerCapture(){}
    getContext(){return {drawImage(){}};}click(){if(this.download)downloads.push({name:this.download,url:this.href});this.onclick?.({preventDefault(){}});}
  }
  for(const match of html.matchAll(/<[^>]*\bid="([^"]+)"[^>]*>/g)){const e=new Element();e.id=match[1];e.value=/\bvalue="([^"]*)"/.exec(match[0])?.[1]||'';}
  elements.get('map').width=384;elements.get('map').height=384;
  const body=new Element('body'),blobs=new Map();let slowNextRender=false,slowNextPoint=false,urlIndex=0;
  class LocalURL extends URL{static createObjectURL(blob){const id='blob:test-'+urlIndex++;blobs.set(id,blob);return id;}static revokeObjectURL(){}}
  const context=vm.createContext({document:{body,getElementById:id=>{assert(elements.has(id),'Missing DOM node '+id);return elements.get(id);},createElement:tag=>new Element(tag)},
    location:{search:'?seed=-9223372036854775808&crustProvinceScale=4&crustRadiusPermille=1500&plateSpacing=65536'},history:{replaceState(_a,_b,url){context.savedURL=url;}},URL:LocalURL,URLSearchParams,AbortController,Blob,setTimeout,clearTimeout,
    fetch:async(url,options)=>{requests.push(String(url));const slow=String(url).startsWith('/api/tectonic/render')&&slowNextRender,point=String(url).startsWith('/api/tectonic/sample')&&slowNextPoint;
      if(slow)slowNextRender=false;if(point)slowNextPoint=false;const response=await fetch(new URL(url,base),slow?{}:options);
      if(slow||point)await new Promise(r=>setTimeout(r,350));return response;},
    createImageBitmap:async blob=>{assert.equal(blob.type,'image/png');const bytes=Buffer.from(await blob.arrayBuffer());assert.equal(bytes.subarray(1,4).toString(),'PNG');return {close(){}};}
  });
  const evaluate=code=>vm.runInContext(code,context);
  evaluate(fs.readFileSync('harness/viewer/tectonics.js','utf8'));
  async function settled(){const end=Date.now()+20000;while(Date.now()<end){if(elements.get('error').textContent)throw Error(elements.get('error').textContent);if(evaluate('state.completeRevision>0&&state.completeRevision===state.revision'))return;await new Promise(r=>setTimeout(r,20));}throw Error('Viewer render timeout');}
  await settled();assert.equal(evaluate('state.committed.seed'),'-9223372036854775808');assert.equal(elements.get('layers').children.length,26);
  assert.match(context.savedURL,/plateWarpPermille=220/);assert.equal(evaluate('Object.keys(state.committed.params).length'),17);
  assert.match(context.savedURL,/version=tectonic-terrain-v2/);assert.doesNotMatch(context.savedURL,/crustProvinceScale/);
  assert.equal(evaluate('state.params.plateSpacing'),524288);assert.equal(elements.get('upgradeNotice').hidden,false);
  // Small fixture canvas for repeated field checks; Java HTTP gates separately check real render pixels.
  elements.get('map').width=8;elements.get('map').height=8;
  for(const button of elements.get('layers').children){button.click();await settled();assert.equal(evaluate('state.committed.layer'),button.dataset.field);assert.equal(button['aria-pressed'],'true');}
  const oldStep=evaluate('state.step');elements.get('zoomIn').click();await settled();assert.equal(evaluate('state.step'),oldStep/2);
  const oldX=evaluate('state.x');elements.get('map').events.keydown({key:'ArrowRight',preventDefault(){}});await settled();assert.equal(evaluate('state.x'),oldX+48*oldStep/2);
  await evaluate('inspect({x:2,z:3})');assert.equal(elements.get('inspection').children.length,52);assert.doesNotMatch(elements.get('inspectCoords').textContent,/loading/);
  elements.get('p-forcingPermille').value='0';elements.get('worldForm').events.input();assert.equal(elements.get('draftState').hidden,false);
  elements.get('layers').children[0].click();await settled();assert.equal(evaluate('state.committed.params.forcingPermille'),1000);assert.equal(elements.get('p-forcingPermille').value,'0');
  elements.get('worldForm').events.submit({preventDefault(){}});await settled();assert.equal(evaluate('state.committed.params.forcingPermille'),0);assert.equal(elements.get('draftState').hidden,true);
  // A delayed obsolete response deliberately ignores abort; revision guard must still prevent stale image/config commit.
  slowNextRender=true;evaluate("state.layer='elevation';render()");evaluate("state.layer='land';render()");await settled();await new Promise(r=>setTimeout(r,500));assert.equal(evaluate('state.committed.layer'),'land');
  slowNextPoint=true;const pending=evaluate('inspect({x:1,z:1})');evaluate("state.layer='base';render()");await settled();await pending;assert.equal(elements.get('inspection').children.length,0);
  elements.get('export').click();assert.equal(downloads.length,2);const config=JSON.parse(await blobs.get(downloads[1].url).text());assert.equal(config.model,'tectonic-experimental');assert.equal(config.seed,'-9223372036854775808');assert.equal(config.layer,'base');assert.equal(config.params.forcingPermille,0);assert.equal(config.waterStatus,'not solved');
  elements.get('seed').value='9223372036854775808';elements.get('worldForm').events.submit({preventDefault(){}});assert.match(elements.get('error').textContent,/signed 64-bit/);
  elements.get('seed').value='42';elements.get('p-plateWarpPermille').value='301';elements.get('worldForm').events.submit({preventDefault(){}});assert.match(elements.get('error').textContent,/Invalid Plate-edge warp/);
  elements.get('reset').click();await settled();assert.equal(evaluate('state.seed'),'42');assert.equal(evaluate('state.params.plateSpacing'),524288);assert.equal(evaluate('state.params.plateWarpPermille'),220);
  assert(requests.filter(x=>x.startsWith('/api/tectonic/render')).every(x=>x.includes('continentalPercent=')&&x.includes('seaLevel=')&&x.includes('plateRoughnessPermille=')));
  console.log('PASS TECTONIC VIEWER LOGIC: 26 layers, full configuration/exact seed, navigation, inspector, stale response isolation, export and invalid inputs; not browser visual QA');
}
run().catch(error=>{console.error(error);process.exitCode=1;});
