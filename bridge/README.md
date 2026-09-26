# IMO Bridge Protocol

IMO connects outbound to a **WSS** endpoint configured in the app. The server must authenticate the Bearer token and keep the socket private.

## Device -> server
```json
{"type":"hello","device":"android","app":"IMO","version":"2.2.0","accessibility":true}
```

## Server -> device
Observe:
```json
{"type":"observe","id":"1","screenshot":true}
```

Execute:
```json
{"type":"action","id":"2","confirmed":false,"action":{"type":"CLICK_TEXT","value":"WhatsApp"},"screenshot":true}
```

Supported actions include OPEN_APP, CLICK_TEXT, CLICK_DESC, CLICK_ID, CLICK_POINT, LONG_CLICK_*, TYPE, CLEAR_TEXT, PRESS_ENTER, SCROLL, SWIPE, BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, POWER_DIALOG, LOCK_SCREEN, SPLIT_SCREEN, OPEN_URL, OPEN_SETTINGS, SET_VOLUME, MUTE and WAIT.

Sensitive actions require `confirmed:true`.

Use TLS (`wss://`), a strong random token, and never expose the bridge without authentication.

The bridge is the transport layer. An AI controller still has to connect to the server and decide which structured actions to send. The ChatGPT conversation is not automatically connected to this socket.
