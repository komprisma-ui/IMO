# IMO Secure Bridge Relay

Server-side half of the IMO device bridge.
Flow: Luna/controller -> WSS reverse proxy -> /controller -> relay -> /device -> IMO Android -> result/observation -> controller.
The relay only authenticates and routes sockets; it does not execute Android actions.

Run:
1. Install Node.js 18+.
2. Run: npm install ws
3. Set separate long random DEVICE_TOKEN and CONTROLLER_TOKEN.
4. Start: DEVICE_TOKEN='...' CONTROLLER_TOKEN='...' node server.js
5. Put it behind HTTPS/WSS using nginx, Caddy, or another TLS reverse proxy.

Endpoints:
- /device uses Bearer DEVICE_TOKEN.
- /controller uses Bearer CONTROLLER_TOKEN.
Sensitive operations remain protected by IMO and require confirmed:true.