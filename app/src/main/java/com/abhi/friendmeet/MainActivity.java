package com.abhi.friendmeet;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import org.webrtc.*;
import java.util.*;

public class MainActivity extends Activity implements SignalingClient.Listener {
 static final int PERM=20,SCREEN=21;
 LinearLayout root; EditText server,room; TextView status,roomText;
 SurfaceViewRenderer local,remote; PeerConnectionFactory factory; PeerConnection peer;
 EglBase egl; VideoCapturer capturer; VideoTrack video; AudioTrack audio;
 SignalingClient signal; String id=UUID.randomUUID().toString(),roomId="",peerId="";
 boolean joined=false,initiator=false,muted=false,camOff=false;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 TextView t(String s,int z){TextView x=new TextView(this);x.setText(s);x.setTextSize(z);x.setTextColor(Color.WHITE);x.setPadding(dp(8),dp(4),dp(8),dp(4));return x;}
 Button b(String s){Button x=new Button(this);x.setText(s);x.setTextColor(Color.WHITE);x.setTextSize(12);x.setAllCaps(false);return x;}

 public void onCreate(Bundle x){super.onCreate(x);egl=EglBase.create();ui();init();if(Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},PERM);}

 void ui(){
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(11,16,23));
  LinearLayout h=new LinearLayout(this);h.setOrientation(LinearLayout.VERTICAL);h.setPadding(dp(10),dp(8),dp(10),dp(2));
  h.addView(t("FriendMeet",24));status=t("Ready • WebRTC low-data mode",12);h.addView(status);root.addView(h);
  LinearLayout j=new LinearLayout(this);
  server=new EditText(this);server.setSingleLine();server.setHint("ws://server:8080");server.setTextColor(Color.WHITE);server.setText("ws://10.0.2.2:8080");j.addView(server,new LinearLayout.LayoutParams(0,dp(48),2));
  room=new EditText(this);room.setSingleLine();room.setHint("Room");room.setTextColor(Color.WHITE);j.addView(room,new LinearLayout.LayoutParams(0,dp(48),1));
  Button join=b("JOIN");join.setOnClickListener(v->join());j.addView(join,new LinearLayout.LayoutParams(dp(72),dp(48)));root.addView(j);
  FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(Color.BLACK);
  remote=new SurfaceViewRenderer(this);remote.init(egl.getEglBaseContext(),null);stage.addView(remote,new FrameLayout.LayoutParams(-1,-1));
  local=new SurfaceViewRenderer(this);local.init(egl.getEglBaseContext(),null);local.setZOrderMediaOverlay(true);
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(130),dp(170),Gravity.TOP|Gravity.END);lp.setMargins(0,dp(8),dp(8),0);stage.addView(local,lp);
  root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));roomText=t("Not connected",12);root.addView(roomText);
  LinearLayout c=new LinearLayout(this);c.setGravity(Gravity.CENTER);
  Button mic=b("Mic");mic.setOnClickListener(v->{muted=!muted;if(audio!=null)audio.setEnabled(!muted);mic.setText(muted?"Mic OFF":"Mic ON");});
  Button cam=b("Camera");cam.setOnClickListener(v->{camOff=!camOff;if(video!=null)video.setEnabled(!camOff);cam.setText(camOff?"Camera OFF":"Camera ON");});
  Button share=b("Share screen");share.setOnClickListener(v->screenPermission());
  Button watch=b("Watch");watch.setOnClickListener(v->watchDialog());
  Button leave=b("Leave");leave.setOnClickListener(v->leave());
  for(Button q:new Button[]{mic,cam,share,watch,leave})c.addView(q,new LinearLayout.LayoutParams(0,dp(50),1));
  root.addView(c);setContentView(root);
 }

 void init(){
  PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions());
  factory=PeerConnectionFactory.builder()
   .setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(),true,false))
   .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext())).createPeerConnectionFactory();
 }
 void join(){
  if(joined)return;roomId=room.getText().toString().trim();if(roomId.length()<3){toast("Use a room code");return;}
  signal=new SignalingClient(this);signal.connect(server.getText().toString().trim());joined=true;roomText.setText("Connecting…");
 }
 public void onState(String s){runOnUiThread(()->{status.setText("Signaling: "+s);if(s.equals("connected"))try{
  JSONObject o=new JSONObject();o.put("type","join");o.put("room",roomId);o.put("id",id);signal.send(o);
 }catch(Exception e){}});}
 public void onMessage(JSONObject o){runOnUiThread(()->handle(o));}

 void handle(JSONObject o){
  try{
   String ty=o.optString("type");
   if(ty.equals("joined")){initiator=o.optBoolean("initiator");peerId=o.optString("peerId");roomText.setText("Room "+roomId+" • "+(initiator?"Host":"Guest"));startCamera();if(initiator)offer();}
   else if(ty.equals("peer-joined")){peerId=o.optString("id");if(initiator)offer();}
   else if(ty.equals("offer")){peerId=o.optString("from");ensurePeer();peer.setRemoteDescription(new SimpleSdpObserver(){public void onSetSuccess(){answer();}},sdpFromJson(o.getString("sdp")));}
   else if(ty.equals("answer")){if(peer!=null)peer.setRemoteDescription(new SimpleSdpObserver(),sdpFromJson(o.getString("sdp")));}
   else if(ty.equals("candidate")){if(peer!=null)peer.addIceCandidate(candidateFromJson(o.getString("candidate")));}
   else if(ty.equals("peer-left")){status.setText("Friend left");}
   else if(ty.equals("watch")){openUrl(o.optString("url"));}
  }catch(Exception e){toast("Signal error");}
 }

 void ensurePeer(){
  if(peer!=null)return;
  ArrayList<PeerConnection.IceServer> ice=new ArrayList<>();
  ice.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
  peer=factory.createPeerConnection(ice,new PeerConnection.Observer(){
   public void onSignalingChange(PeerConnection.SignalingState s){} public void onIceConnectionChange(PeerConnection.IceConnectionState s){runOnUiThread(()->status.setText("Call: "+s));}
   public void onIceConnectionReceivingChange(boolean b){} public void onIceGatheringChange(PeerConnection.IceGatheringState s){}
   public void onIceCandidate(IceCandidate c){try{JSONObject o=new JSONObject();o.put("type","candidate");o.put("room",roomId);o.put("to",peerId);o.put("from",id);o.put("candidate",candidateToJson(c));signal.send(o);}catch(Exception e){}}
   public void onIceCandidatesRemoved(IceCandidate[] c){} public void onAddStream(MediaStream s){}
   public void onRemoveStream(MediaStream s){} public void onDataChannel(DataChannel d){} public void onRenegotiationNeeded(){}
   public void onAddTrack(RtpReceiver r,MediaStream[] s){if(r.track() instanceof VideoTrack)((VideoTrack)r.track()).addSink(remote);}
   public void onConnectionChange(PeerConnection.PeerConnectionState s){}
   public void onSelectedCandidatePairChanged(CandidatePairChangeEvent e){} public void onTrack(RtpTransceiver t){}
  });
  if(video!=null)peer.addTrack(video);if(audio!=null)peer.addTrack(audio);
 }
 void offer(){ensurePeer();peer.createOffer(new SimpleSdpObserver(){public void onCreateSuccess(SessionDescription d){peer.setLocalDescription(new SimpleSdpObserver(),d);sendSdp("offer",d);}},new MediaConstraints());}
 void answer(){peer.createAnswer(new SimpleSdpObserver(){public void onCreateSuccess(SessionDescription d){peer.setLocalDescription(new SimpleSdpObserver(),d);sendSdp("answer",d);}},new MediaConstraints());}
 void sendSdp(String type,SessionDescription d){try{JSONObject o=new JSONObject();o.put("type",type);o.put("room",roomId);o.put("to",peerId);o.put("from",id);o.put("sdp",sdpToJson(d));signal.send(o);}catch(Exception e){}}

 String sdpToJson(SessionDescription d){
  try{ JSONObject o=new JSONObject(); o.put("type",d.type.canonicalForm()); o.put("sdp",d.description); return o.toString(); }catch(Exception e){return "{}";}
 }
 SessionDescription sdpFromJson(String json)throws Exception{
  JSONObject o=new JSONObject(json); return new SessionDescription(SessionDescription.Type.fromCanonicalForm(o.optString("type")),o.optString("sdp"));
 }
 String candidateToJson(IceCandidate c){
  try{ JSONObject o=new JSONObject(); o.put("sdpMid",c.sdpMid); o.put("sdpMLineIndex",c.sdpMLineIndex); o.put("candidate",c.sdp); return o.toString(); }catch(Exception e){return "{}";}
 }
 IceCandidate candidateFromJson(String json)throws Exception{
  JSONObject o=new JSONObject(json); String mid=o.isNull("sdpMid")?null:o.optString("sdpMid",null); int index=o.optInt("sdpMLineIndex",0); return new IceCandidate(mid,index,o.optString("candidate"));
 }

 void startCamera(){
  try{
   CameraEnumerator e=new Camera2Enumerator(this);String name=null;
   for(String n:e.getDeviceNames())if(e.isFrontFacing(n)){name=n;break;}
   if(name==null&&e.getDeviceNames().length>0)name=e.getDeviceNames()[0];
   if(name!=null){VideoSource vs=factory.createVideoSource(false);capturer=e.createCapturer(name,null);capturer.initialize(SurfaceTextureHelper.create("cam",egl.getEglBaseContext()),this,vs.getCapturerObserver());capturer.startCapture(320,240,15);video=factory.createVideoTrack("video",vs);video.addSink(local);}
   audio=factory.createAudioTrack("audio",factory.createAudioSource(new MediaConstraints()));ensurePeer();
  }catch(Exception e){status.setText("Camera/mic unavailable");}
 }

 void screenPermission(){
  if(Build.VERSION.SDK_INT<21){toast("Android 5+ required");return;}
  MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);startActivityForResult(m.createScreenCaptureIntent(),SCREEN);
 }
 protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==SCREEN&&c==RESULT_OK&&d!=null)startScreen(d);}
 void startScreen(Intent data){
  try{
   MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
   VideoSource vs=factory.createVideoSource(false);
   ScreenCapturerAndroid sc=new ScreenCapturerAndroid(data,new MediaProjection.Callback(){});
   sc.initialize(SurfaceTextureHelper.create("screen",egl.getEglBaseContext()),this,vs.getCapturerObserver());sc.startCapture(640,360,12);
   if(video!=null)video.dispose();video=factory.createVideoTrack("screen",vs);video.addSink(local);
   if(peer!=null)for(RtpSender s:peer.getSenders())if(s.track()!=null&&s.track().kind().equals("video"))s.setTrack(video,false);
   status.setText("Screen sharing • 640x360/12fps");toast("Screen sharing started");
  }catch(Exception e){toast("Screen share failed");}
 }
 void watchDialog(){
  EditText e=new EditText(this);e.setSingleLine();e.setHint("Legal online video URL");
  new AlertDialog.Builder(this).setTitle("Watch together").setMessage("For Android 8.1, synchronized local playback is the lowest-data option. Both phones open the same video URL; this app shares the URL with your friend.").setView(e)
   .setPositiveButton("Share",(d,w)->{String u=e.getText().toString().trim();if(u.isEmpty())return;openUrl(u);try{JSONObject o=new JSONObject();o.put("type","watch");o.put("room",roomId);o.put("to",peerId);o.put("url",u);signal.send(o);}catch(Exception x){}})
   .setNegativeButton("Cancel",null).show();
 }
 void openUrl(String u){try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));}catch(Exception e){toast("No app can open URL");}}
 void leave(){try{if(signal!=null)signal.close();if(peer!=null)peer.close();if(capturer!=null)capturer.stopCapture();}catch(Exception e){}joined=false;roomText.setText("Not connected");status.setText("Ready");}
 void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
 public void onBackPressed(){if(joined)leave();else super.onBackPressed();}
 protected void onDestroy(){leave();try{local.release();remote.release();factory.dispose();egl.release();}catch(Exception e){}super.onDestroy();}
}