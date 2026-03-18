const WebSocket = require('ws');

const host = 'fux.local';
const port = 80;
const effectName = 'box_mirror';

console.log(`Connecting to ws://${host}:${port}...`);
const ws = new WebSocket(`ws://${host}:${port}`);

ws.on('open', function open() {
  console.log('Connected to fux');
  const cmd = `EFFECT:${effectName}`;
  ws.send(cmd);
  console.log('Sent:', cmd);
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