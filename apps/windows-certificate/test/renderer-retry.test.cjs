"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const { userErrorMessage } = require("../src/client.cjs");
function fixture(errorCode) {
  const nodes = new Map(), calls = { pair: 0, cancel: 0, retry: 0, discover: 0 };
  const node = id => {
    if (!nodes.has(id)) {
      const listeners = new Map(), attrs = new Map();
      nodes.set(id, { id, value:"", hidden:true, disabled:false, open:false, textContent:"", dataset:{}, children:[],
        addEventListener(name, fn) { if (!listeners.has(name)) listeners.set(name,new Set()); listeners.get(name).add(fn); },
        removeEventListener(name, fn) { listeners.get(name)?.delete(fn); },
        emit(name,event={}) {return Promise.all([...listeners.get(name)||[]].map(fn=>fn(event)));},
        setAttribute(k,v){attrs.set(k,v);}, getAttribute(k){return attrs.get(k);}, removeAttribute(k){attrs.delete(k);},
        replaceChildren(...children){this.children=children;}, append(){}, reset(){},
        classList:{add(){},remove(){},toggle(){}},
        showModal(){this.open=true;},close(){this.open=false;this.emit("close");},
      });
    }
    return nodes.get(id);
  };
  const certificate = {
    appVersion:async()=>"test",init:async()=>[],
    failureMessage:async message=>userErrorMessage(message.match(/([A-Z][A-Z0-9_]+)$/)?.[1]),
    submit:async()=>{throw new Error(errorCode);},
    discover:async()=>{calls.discover++;return {source:"computer",certificates:[],storage:{revision:"test",deviceCount:0}};},
    retry:async()=>{calls.retry++;},pair:async()=>{calls.pair++;},cancel:async()=>{calls.cancel++;},
  };
  const context=vm.createContext({document:{getElementById:node,querySelectorAll:()=>[]},window:{certificate,addEventListener(){}},setTimeout:()=>1,clearTimeout(){},console,Date});
  vm.runInContext(fs.readFileSync(require.resolve("../src/renderer.js"),"utf8"),context);
  return {node,calls,async seed(){await new Promise(setImmediate);vm.runInContext('institutions=[{organization:"0020",authenticationLevel:3}];paired=true;pairedOrganization="0020";selected="certificate-test";',context);node("registration").hidden=false;node("organization").value="0020";node("computer").setAttribute("aria-pressed","true");node("usb").setAttribute("aria-pressed","false");node("password").value="synthetic";node("accountNumber").value="123";node("accountPassword").value="bad";},context};
}
for (const code of ["CERTIFICATE_SUBMIT_INPUT_INPUT_INVALID", "CERTIFICATE_SUBMIT_FILES_FILE_ACCESS_DENIED"]) {
  test(`${code}: modal acknowledgement returns to same pairing and bank`,async()=>{
    const f=fixture(code);await f.seed();
    const pending=f.node("registration").emit("submit",{preventDefault(){}});
    await new Promise(setImmediate);
    assert.equal(f.node("authenticationFailure").open,true);
    assert.equal(f.node("registration").hidden,true);
    assert.equal(f.node("authenticationFailureText").textContent,userErrorMessage(code));
    await f.node("retryAuthentication").emit("click");await pending;
    assert.equal(f.node("authenticationFailure").open,false);
    assert.equal(f.node("registration").hidden,false);
    assert.equal(f.node("progress").hidden,true);
    assert.equal(f.node("organization").value,"0020");
    assert.equal(f.node("organization").disabled,true);
    assert.equal(f.node("password").value,"");
    assert.equal(f.node("message").hidden,true);
    assert.deepEqual(f.calls,{pair:0,cancel:0,retry:0,discover:1});
  });
}
test("uncertain server upload failure cannot silently retry or discard a session",async()=>{
  const f=fixture("CERTIFICATE_SUBMIT_UPLOAD_HTTP_500");await f.seed();
  await f.node("registration").emit("submit",{preventDefault(){}});
  assert.equal(f.node("authenticationFailure").open,false);
  assert.equal(f.node("registration").hidden,true);
  assert.equal(f.calls.retry,0);
});

test("provider FAILED retains its retry handshake and shared modal return",async()=>{
  const f=fixture("unused");await f.seed();
  const pending=vm.runInContext('terminal({status:"FAILED",failureMessage:"synthetic provider failure"})',f.context);
  await new Promise(setImmediate);
  assert.equal(f.node("authenticationFailure").open,true);
  assert.equal(f.node("authenticationFailureTitle").textContent,"금융사 인증에 실패했어요");
  await f.node("retryAuthentication").emit("click");await pending;
  assert.equal(f.node("registration").hidden,false);
  assert.deepEqual(f.calls,{pair:0,cancel:0,retry:1,discover:1});
});
test("cancellation while a dialog is open cannot restore a stale form",async()=>{
  const f=fixture("CERTIFICATE_SUBMIT_INPUT_INPUT_INVALID");await f.seed();
  const pending=f.node("registration").emit("submit",{preventDefault(){}});
  await new Promise(setImmediate);
  vm.runInContext('generation++;paired=false;',f.context);
  f.node("authenticationFailure").close();await pending;
  assert.equal(f.node("registration").hidden,true);
  assert.equal(f.calls.discover,0);
});
