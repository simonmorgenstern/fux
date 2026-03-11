const WebSocket = require('ws');

const ws = new WebSocket('ws://fux.local:80');

ws.on('open', function open() {
  console.log('Sending STOP command to fux server...');
  ws.send('STOP');
});

ws.on('message', function message(data) {
  console.log('Server response:', data.toString());
  setTimeout(() => ws.close(), 500);
});

ws.on('error', function error(err) {
  console.error('Error:', err.message);
  process.exit(1);
});

ws.on('close', function close() {
  console.log('Server stopped');
  process.exit(0);
});

setTimeout(() => {
  console.log('Stop command sent (timeout)');
  ws.close();
  process.exit(0);
}, 2000);
