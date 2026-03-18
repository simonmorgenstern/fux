const WebSocket = require('ws');
const host = 'fux.local';
const port = 80;
const ws = new WebSocket(`ws://${host}:${port}`);

ws.on('open', function open() {
  console.log('Connected');
  ws.send('STOP_EFFECT');
  console.log('Sent STOP_EFFECT');
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