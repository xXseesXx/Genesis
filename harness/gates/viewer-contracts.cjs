'use strict';
// Optional Node smoke test against a running server. A minimal DOM checks application
// wiring, not browser layout, painting, accessibility, or native pointer behavior.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const base=process.argv[2]||'http://127.0.0.1:8787';
class Element {
  constructor(tag='div'){this.tagName=tag;this.children=[];this.value='';this.textContent='';this.dataset={};this.events={};this.checked=false;this.hidden=false;this.classList={toggle(){}};}
  append(...children){this.children.push(...children);}
  replaceChildren(...children){this.children=children;}
  setAttribute(name,value){this[name]=value;}
  addEventListener(name,fn){this.events[name]=fn;}
  setPointerCapture(){}
  getBoundingClientRect(){return {left:0,top:0,width:512,height:512};}
  getContext(){return {drawImage(){}};}
  click(){this.onclick?.({preventDefault(){}});}
}
async function exercise(path,model){
  const html=await (await fetch(base+path)).text(),elements=new Map();
  for(const match of html.matchAll(/<[^>]*\bid="([^"]+)"[^>]*>/g)){
    const node=new Element();node.value=/\bvalue="([^"]*)"/.exec(match[0])?.[1]||'';elements.set(match[1],node);
  }
  elements.get('paramsB').value='{}';
  const downloads=[],requests=[];
  class TestURL extends URL {static createObjectURL(blob){downloads.push(blob);return 'blob:smoke';}static revokeObjectURL(){}}
  const context=vm.createContext({
    document:{body:{dataset:model==='continental'?{world:model}:{}},getElementById:id=>{assert(elements.has(id),'Missing HTML id '+id);return elements.get(id);},createElement:tag=>new Element(tag),createTextNode:text=>({textContent:text})},
    location:{search:'?seed=42'},URL:TestURL,URLSearchParams,AbortController,Blob,performance,setTimeout,clearTimeout,
    fetch:async(path,options)=>{requests.push(String(path));return fetch(new URL(path,base),options);},
    createImageBitmap:async blob=>{assert.equal(blob.type,'image/png');return {close(){}};}
  });
  const evaluate=code=>vm.runInContext(code,context);
  async function settled(){
    const end=Date.now()+20000;
    while(Date.now()<end){if(elements.get('error').textContent)throw new Error(elements.get('error').textContent);if(evaluate('state.completeRevision===state.revision && state.completeRevision>0'))return;await new Promise(r=>setTimeout(r,30));}
    throw new Error('Viewer failed to finish '+path);
  }
  evaluate(fs.readFileSync('harness/viewer/app.js','utf8'));await settled();
  assert.equal(evaluate("new URLSearchParams(state.requests.A).get('model')"),model);
  assert.equal(evaluate("new URLSearchParams(state.requests.A).get('layers')"),'baseElevation:1');
  if(model==='continental'){
    assert(evaluate("state.layers.some(l=>l.id==='terrainDetail')"));
    // Exercise the actual dynamically created View button.
    evaluate("state.layers.find(l=>l.id==='terrainDetail').elements.row.children[1].children[1].onclick()");await settled();
    assert.equal(evaluate("new URLSearchParams(state.requests.A).get('layers')"),'terrainDetail:1');
    elements.get('continentsOverview').click();await settled();
    assert.equal(evaluate("new URLSearchParams(state.requests.A).get('layers')"),'baseElevation:1');
    assert(!elements.get('params').children.some(n=>n.htmlFor==='param-crustInfluence'));
  }else{
    assert(!evaluate("state.layers.some(l=>l.id==='continentScaffold')"));
    elements.get('continentsOverview').click();assert(elements.get('continentsOverview').href.startsWith('/continents.html?'));
  }
  elements.get('compare').checked=true;elements.get('paramsB').value='{"terrainDetailHeight":1500}';elements.get('compare').onchange();await settled();
  assert.equal(evaluate("new URLSearchParams(state.requests.B).get('model')"),model);
  assert.equal(evaluate("new URLSearchParams(state.requests.B).get('terrainDetailHeight')"),'1500');
  await evaluate('inspect(state.x+256*state.step,state.z+256*state.step)');
  assert(elements.get('inspection').children.length>0);
  const inspectionRequests=requests.filter(q=>q.startsWith('/api/sample?'));assert.equal(inspectionRequests.length,2);
  assert(inspectionRequests.every(q=>new URL(q,base).searchParams.get('model')===model));
  elements.get('hydrologyLink').click();const hydro=new URL(elements.get('hydrologyLink').href,base);
  assert.equal(hydro.searchParams.get('model'),model);assert.equal(hydro.searchParams.get('seed'),'42');
  elements.get('export').click();
  const configurations=await Promise.all(downloads.filter(b=>b.type==='application/json').map(async b=>JSON.parse(await b.text())));
  assert.equal(configurations.length,2);assert(configurations.every(c=>new URLSearchParams(c.query).get('model')===model));
  evaluate('state.step=1027;zoom(1)');await settled();assert.equal(evaluate('state.step'),514);
  evaluate("state.params.terrainDetailHeight=50;schedule();state.params.terrainDetailHeight=250;schedule()");await settled();
  assert.equal(evaluate("new URLSearchParams(state.requests.A).get('terrainDetailHeight')"),'250');
  console.log('PASS viewer logic '+path+': live model, top-bar layers, overview, A/B, inspector, hydrology handoff, exports, integer zoom, latest configuration');
}
(async()=>{await exercise('/','legacy');await exercise('/continents.html','continental');})().catch(e=>{console.error(e);process.exitCode=1;});
