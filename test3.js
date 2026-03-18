const WebSocket = require('ws');
const host = 'fux.local';
const port = 80;
const effectName = 'box_mirror';
const ws = new WebSocket(`ws://${host}:${port}`);

ws.on('open', function open() {
  console.log('Connected');
  ws.send(`EFFECT:${effectName}`);
  console.log(`Sent EFFECT:${effectName}`);
});

ws.on('message', function message(data) {
  console.log('Response:', data.toString());
});

ws.on('error', function error(err) {
  console.error('Error:', err.message);
});

setTimeout(() => {
  ws.close();
  process.exit(0);
}, 2000);