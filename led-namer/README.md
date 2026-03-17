# LED Namer for Fux Controller

A web interface to name specific combinations of LEDs on the Fux LED controller.

## Features

- Select LED ranges (0‑387 total LEDs: 268 main strip + 120 side strip)
- Name your combinations (e.g., "Front Panel", "Back Strip")
- Save, edit, delete groups
- Clean, mobile‑friendly UI
- REST API backend (Node.js + Express)

## Prerequisites

- Node.js (v12 or newer)
- npm (usually bundled with Node)

## Installation & Running

1. Navigate to the `led‑namer` directory:
   ```bash
   cd /home/simon/.openclaw/workspace/fux/led-namer
   ```

2. Run the start script:
   ```bash
   ./start.sh
   ```
   The script will install dependencies automatically if needed.

3. Open your browser to `http://<raspberry‑pi‑ip>:3000`

   If running on the Pi itself, you can use `http://localhost:3000`.

## API Endpoints

- `GET /api/groups` – list all saved groups
- `POST /api/groups` – create a new group
- `PUT /api/groups/:id` – update a group
- `DELETE /api/groups/:id` – delete a group

Data is stored in `led‑groups.json` (created automatically).

## Integration with Fux Controller

The web interface is standalone and does not interfere with the existing WebSocket controller (port 80). A preview feature is included that sends a WebSocket command to highlight the selected LEDs with a dim white color for 1.5 seconds.

To use preview, ensure the Fux controller is running on the same host as the web interface (or adjust `WS_URL` in `public/script.js`). The preview button will send a `setPixels` command to the controller's WebSocket endpoint.

## Project Structure

```
led-namer/
├── public/               # Static frontend files
│   ├── index.html
│   ├── style.css
│   └── script.js
├── server.js             # Express backend
├── package.json
├── start.sh              # Convenience launch script
└── README.md
```

## License

Part of the Fux project.