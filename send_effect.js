const WebSocket = require('ws');

const ws = new WebSocket('ws://192.168.178.82:80');

ws.on('open', function open() {
  console.log('Connected to fux');
  ws.send('EFFECT:rainbow_pulse');
  console.log('Sent: EFFECT:rainbow_pulse');
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
