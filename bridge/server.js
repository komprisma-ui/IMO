const { WebSocketServer } = require('ws');
const PORT = Number(process.env.PORT || 8787);
const DEVICE_TOKEN = process.env.DEVICE_TOKEN || '';
const CONTROLLER_TOKEN = process.env.CONTROLLER_TOKEN || '';
if (!DEVICE_TOKEN || !CONTROLLER_TOKEN) throw new Error('Set DEVICE_TOKEN and CONTROLLER_TOKEN before starting.');
const wss = new WebSocketServer({ port: PORT });
let device = null; const controllers = new Set();
function auth(req, expected) { return (req.headers.authorization || '') === 'Bearer ' + expected; }
function send(ws, obj) { if (ws && ws.readyState === 1) ws.send(JSON.stringify(obj)); }
function reject(ws, code, message) { send(ws,{type:'error',code,error:message}); try{ws.close(1008,message);}catch(_){} }
wss.on('connection',(ws,req)=>{
 const path=new URL(req.url||'/', 'http://localhost').pathname;
 if(path==='/device'){
  if(!auth(req,DEVICE_TOKEN)) return reject(ws,'unauthorized','device authentication failed');
  if(device&&device.readyState===1) try{device.close(4001,'replaced');}catch(_){} device=ws;
  send(ws,{type:'bridge_ready',protocol:1});
  ws.on('message',raw=>{let msg;try{msg=JSON.parse(raw.toString());}catch(_){return;} for(const c of controllers) send(c,msg);});
  ws.on('close',()=>{if(device===ws)device=null;}); ws.on('error',()=>{if(device===ws)device=null;}); return;
 }
 if(path==='/controller'){
  if(!auth(req,CONTROLLER_TOKEN)) return reject(ws,'unauthorized','controller authentication failed');
  controllers.add(ws); send(ws,{type:'controller_ready',protocol:1,device_online:!!device&&device.readyState===1});
  ws.on('message',raw=>{let msg;try{msg=JSON.parse(raw.toString());}catch(_){return reject(ws,'bad_json','invalid JSON');}
   const type=String(msg.type||'').toLowerCase();
   if(!['observe','action','ping'].includes(type)) return reject(ws,'bad_type','unsupported message type');
   if((type==='action'||type==='observe')&&(!msg.id||typeof msg.id!=='string')) return reject(ws,'bad_id','id is required');
   if(!device||device.readyState!==1) return send(ws,{type:'result',id:msg.id||'',ok:false,message:'device_offline'}); send(device,msg);
  });
  ws.on('close',()=>controllers.delete(ws)); ws.on('error',()=>controllers.delete(ws)); return;
 }
 reject(ws,'bad_path','use /device or /controller');
});
setInterval(()=>{if(device&&device.readyState===1)send(device,{type:'ping'});for(const c of controllers)if(c.readyState===1)send(c,{type:'ping'});},20000);
console.log('IMO bridge relay listening on :'+PORT);