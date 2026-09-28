package com.abhi.friendmeet;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import java.net.URI;
public class SignalingClient {
 public interface Listener{void onMessage(JSONObject o);void onState(String s);}
 WebSocketClient ws; Listener listener;
 SignalingClient(Listener l){listener=l;}
 void connect(String url){try{ws=new WebSocketClient(new URI(url)){
  public void onOpen(ServerHandshake h){listener.onState("connected");}
  public void onMessage(String s){try{listener.onMessage(new JSONObject(s));}catch(Exception e){}}
  public void onClose(int c,String r,boolean x){listener.onState("closed");}
  public void onError(Exception e){listener.onState("error");}
 };ws.connect();}catch(Exception e){listener.onState("error");}}
 void send(JSONObject o){if(ws!=null&&ws.isOpen())ws.send(o.toString());}
 void close(){if(ws!=null)ws.close();}
}