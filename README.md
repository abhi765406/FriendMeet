# FriendMeet — lightweight WebRTC meet + watch together

Real WebRTC project, designed for old Android phones such as Vivo 1820/Y91.

### Included
- Real peer-to-peer WebRTC audio/video
- Camera on/off
- Microphone mute
- Room codes
- WebSocket signaling server
- STUN
- Android MediaProjection screen sharing
- Low-data capture defaults: camera 320x240 @ 15fps; screen 640x360 @ 12fps
- Watch Together URL handoff
- Android 5+ minimum
- Lightweight native UI
- GitHub Actions APK build

### Important Vivo 1820 limitation
The Vivo 1820 runs Android 8.1/API 27. Android's AudioPlaybackCapture API was introduced in API 29. Therefore this phone cannot generally capture another app's internal movie audio into the WebRTC screen-share stream. Screen video is real; microphone audio is real. For lowest data use, Watch Together mode opens the same legal online video on both devices rather than sending the whole movie over the call.

On Android 10+ devices, internal playback capture can be used only where the source app permits capture. DRM/protected content may still block capture.

### Connectivity
STUN is included. It will not connect every possible carrier/router pair. For production reliability, deploy a TURN server and add its credentials to the `IceServer` list in `MainActivity.java`.

### Signaling
Deploy `signaling-server` on a small Node server. For LAN testing enter `ws://SERVER-IP:8080`. For Internet use `wss://your-domain`.

### Build
Upload project contents to GitHub, then Actions → Build FriendMeet APK → Run workflow.

This is a functional WebRTC prototype/core, not a complete Microsoft Teams/Zoom enterprise clone. It intentionally avoids analytics, ads and heavyweight UI frameworks to preserve performance on the Vivo 1820.
