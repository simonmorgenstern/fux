const WebSocket = require('ws');

const ws = new WebSocket('ws://fux.local:80');

ws.on('open', function open() {
  console.log('Connected to fux');
  ws.send('EFFECT:aurora');
  console.log('Sent: EFFECT:aurora');
});

ws.on('message', function message(data) {
  console.log('Response:', data.toString());
  ws.close();
});

ws.on('error', function error(err) {
  console.error('Error:', err.message);
  process.exit(1);
});

ws.on('close', function close() {
  console.log('Disconnected');
  process.exit(0);
});

setTimeout(() => {
  console.log('Timeout - closing');
  ws.close();
  process.exit(0);
}, 3000);
