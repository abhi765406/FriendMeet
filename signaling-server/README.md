# FriendMeet signaling
Run:
npm install
node server.js

Use `ws://SERVER_IP:8080` on a LAN for testing. For Internet deployment use WSS behind TLS.
The server relays signaling only; media uses WebRTC. STUN is included, but a TURN server is needed for some restrictive mobile/carrier NATs.
