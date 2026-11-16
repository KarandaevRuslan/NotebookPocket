import test from 'node:test';
import assert from 'node:assert/strict';
import {createPocketHost} from '../src/pocket-context.js';
function storage() {const records=new Map();return {getItem:k=>records.get(k)??null,setItem:(k,v)=>records.set(k,v),removeItem:k=>records.delete(k),records};}
function host(t,s,key='doc',privateMode=false) {
 const h=createPocketHost({wall:()=>t.wall,monotonic:()=>t.mono});
 h.configure({language:'ru',locale:'ru-RU',timeZone:'Asia/Krasnoyarsk',hourCycle:'h23',epochMillis:t.wall,elapsedRealtimeMillis:t.uptime,bootCount:t.boot});
 return {h,p:h.context({key,storage:s,protectedMode:privateMode})};
}
const time=()=>({wall:1800000000000,mono:100,uptime:5000,boot:9});
test('app context provides read-only language, locale, time zone and current Date',()=>{
 const t=time(),{h,p}=host(t,storage());assert.ok(Object.isFrozen(p));assert.equal(p.language,'ru');assert.equal(p.now().getTime(),t.wall);t.wall+=1000;assert.equal(p.timestamp(),t.wall);
 h.configure({language:'de',locale:'---',timeZone:'bad/zone'});const n=h.context({key:'x',storage:storage()});assert.equal(n.language,'en');assert.doesNotThrow(()=>new Intl.DateTimeFormat(n.locale,{timeZone:n.timeZone}).format(n.now()));
});
test('start/pause/resume/reset use monotonic time despite wall clock changes',()=>{
 const t=time(),{p}=host(t,storage()),timer=p.timer('rest',5000);timer.start();t.mono+=1000;t.wall+=3600000;assert.equal(timer.snapshot().remainingMs,4000);timer.pause();t.mono+=5000;assert.equal(timer.snapshot().remainingMs,4000);timer.start();t.mono+=4000;assert.ok(timer.snapshot().finished);assert.equal(timer.start().running,false);timer.reset(15000);assert.equal(timer.snapshot().remainingMs,15000);
});
test('public timers restore after reopening, sleep and changing system time within one boot',()=>{
 const t=time(),s=storage();host(t,s).p.timer('rest',120000).start();t.wall+=3600000;t.uptime+=60000;t.mono=0;
 const {h,p}=host(t,s);assert.equal(p.timer('rest').snapshot().remainingMs,60000);t.uptime+=60000;t.wall+=60000;h.configure({epochMillis:t.wall,elapsedRealtimeMillis:t.uptime,bootCount:t.boot});assert.ok(p.timer('rest').snapshot().finished);
});
test('reboot restores the absolute deadline when a monotonic clock is no longer available',()=>{
 const t=time(),s=storage();host(t,s).p.timer('rest',120000).start();t.wall+=30000;t.uptime=100;t.boot++;t.mono=0;assert.equal(host(t,s).p.timer('rest').snapshot().remainingMs,90000);
});
test('private timers are memory-only, document-scoped and reset independently',()=>{
 const t=time(),s=storage(),{h,p}=host(t,s,'a',true);p.timer('rest',15000).start();t.mono+=2000;assert.equal(h.context({key:'a',storage:s,protectedMode:true}).timer('rest').snapshot().remainingMs,13000);assert.equal(s.records.size,0);
 const other=h.context({key:'b',storage:s,protectedMode:true});other.timer('rest',20000).start();h.resetTimers('a',s);assert.equal(h.context({key:'a',storage:s,protectedMode:true}).timer('rest',15000).snapshot().running,false);assert.ok(other.timer('rest').snapshot().running);
});
test('corrupt records and invalid timer parameters do not break other documents',()=>{
 const t=time(),s=storage();s.setItem('notebook-pocket:timers:doc','{"bad":{"duration":-1}}');const {p}=host(t,s);assert.equal(p.timer('rest',1000).snapshot().remainingMs,1000);assert.throws(()=>p.timer('../bad',1000));assert.throws(()=>p.timer('x',0));assert.throws(()=>p.timer('rest').reset(Infinity));
});
test('resolved light/dark theme is exposed and clock synchronization preserves the context preferences',()=>{
 const t=time(),s=storage(),{h,p}=host(t,s);assert.equal(p.theme,'light');assert.equal(p.dark,false);
 h.configure({dark:true,language:'ru',locale:'ru-RU',timeZone:'Asia/Krasnoyarsk'});
 const dark=h.context({key:'dark',storage:s,protectedMode:true});assert.equal(dark.theme,'dark');assert.equal(dark.dark,true);
 const timer=dark.timer('rest',60000);timer.start();t.uptime+=30000;t.wall+=30000;
 h.syncClock({epochMillis:t.wall,elapsedRealtimeMillis:t.uptime,bootCount:t.boot});
 assert.equal(timer.snapshot().remainingMs,30000);const refreshed=h.context({key:'dark',storage:s,protectedMode:true});assert.equal(refreshed.theme,'dark');assert.equal(refreshed.language,'ru');assert.equal(refreshed.timeZone,'Asia/Krasnoyarsk');
});
