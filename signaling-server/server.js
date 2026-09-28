const WebSocket=require("ws"), crypto=require("crypto");
const port=process.env.PORT||8080, rooms=new Map(), wss=new WebSocket.Server({port});
function send(c,o){if(c.readyState===WebSocket.OPEN)c.send(JSON.stringify(o))}
function others(room,except,o){for(const c of rooms.get(room)||[])if(c!==except)send(c,o)}
wss.on("connection",ws=>{
 ws.id=crypto.randomUUID();ws.room=null;
 ws.on("message",raw=>{let o;try{o=JSON.parse(raw)}catch{return}
  if(o.type==="join"){ws.room=String(o.room||"").slice(0,64);ws.id=String(o.id||ws.id);
   if(!rooms.has(ws.room))rooms.set(ws.room,new Set());let set=rooms.get(ws.room), initiator=set.size===0;set.add(ws);
   send(ws,{type:"joined",initiator,id:ws.id});if(!initiator)others(ws.room,ws,{type:"peer-joined",id:ws.id});return}
  if(!ws.room)return;
  if(["offer","answer","candidate","watch"].includes(o.type)){let to=String(o.to||"");for(const c of rooms.get(ws.room)||[])if(c.id===to){o.from=ws.id;send(c,o)}}
 });
 ws.on("close",()=>{if(ws.room&&rooms.has(ws.room)){let s=rooms.get(ws.room);s.delete(ws);others(ws.room,ws,{type:"peer-left",id:ws.id});if(!s.size)rooms.delete(ws.room)}})
});
console.log("FriendMeet signaling on "+port);