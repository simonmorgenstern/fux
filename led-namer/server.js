const express = require('express');
const bodyParser = require('body-parser');
const fs = require('fs').promises;
const path = require('path');
const cors = require('cors');

const app = express();
const PORT = 3000;
const DATA_FILE = path.join(__dirname, 'led-groups.json');
const CONNECTIONS_FILE = path.join(__dirname, 'led-connections.json');
const BOXES_FILE = path.join(__dirname, 'led-boxes.json');

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use(express.static('public'));

// Ensure data file exists
async function ensureDataFile() {
  try {
    await fs.access(DATA_FILE);
  } catch (err) {
    // File doesn't exist, create with empty array
    await fs.writeFile(DATA_FILE, JSON.stringify([], null, 2));
  }
}

// Read groups
async function readGroups() {
  await ensureDataFile();
  const data = await fs.readFile(DATA_FILE, 'utf8');
  return JSON.parse(data);
}

// Write groups
async function writeGroups(groups) {
  await fs.writeFile(DATA_FILE, JSON.stringify(groups, null, 2));
}

// Routes
app.get('/api/groups', async (req, res) => {
  try {
    const groups = await readGroups();
    res.json(groups);
  } catch (err) {
    console.error('Error reading groups:', err);
    res.status(500).json({ error: 'Failed to read groups' });
  }
});

app.post('/api/groups', async (req, res) => {
  try {
    const newGroup = req.body;
    // Validate
    if (!newGroup.name || !newGroup.ranges || !Array.isArray(newGroup.ranges)) {
      return res.status(400).json({ error: 'Invalid group data' });
    }
    const groups = await readGroups();
    // Assign ID
    newGroup.id = Date.now().toString();
    groups.push(newGroup);
    await writeGroups(groups);
    res.status(201).json(newGroup);
  } catch (err) {
    console.error('Error saving group:', err);
    res.status(500).json({ error: 'Failed to save group' });
  }
});

app.put('/api/groups/:id', async (req, res) => {
  try {
    const { id } = req.params;
    const updatedGroup = req.body;
    const groups = await readGroups();
    const index = groups.findIndex(g => g.id === id);
    if (index === -1) {
      return res.status(404).json({ error: 'Group not found' });
    }
    groups[index] = { ...updatedGroup, id };
    await writeGroups(groups);
    res.json(groups[index]);
  } catch (err) {
    console.error('Error updating group:', err);
    res.status(500).json({ error: 'Failed to update group' });
  }
});

app.delete('/api/groups/:id', async (req, res) => {
  try {
    const { id } = req.params;
    const groups = await readGroups();
    const filtered = groups.filter(g => g.id !== id);
    if (filtered.length === groups.length) {
      return res.status(404).json({ error: 'Group not found' });
    }
    await writeGroups(filtered);
    res.status(204).send();
  } catch (err) {
    console.error('Error deleting group:', err);
    res.status(500).json({ error: 'Failed to delete group' });
  }
});

// --- LED Connections API ---

async function readConnections() {
  try {
    await fs.access(CONNECTIONS_FILE);
    const data = await fs.readFile(CONNECTIONS_FILE, 'utf8');
    return JSON.parse(data);
  } catch (err) {
    return [];
  }
}

async function writeConnections(connections) {
  await fs.writeFile(CONNECTIONS_FILE, JSON.stringify(connections, null, 2));
}

app.get('/api/connections', async (req, res) => {
  try {
    const connections = await readConnections();
    res.json(connections);
  } catch (err) {
    console.error('Error reading connections:', err);
    res.status(500).json({ error: 'Failed to read connections' });
  }
});

// Add a connection [a, b]
app.post('/api/connections', async (req, res) => {
  try {
    const { a, b } = req.body;
    if (a === undefined || b === undefined || a === b) {
      return res.status(400).json({ error: 'Invalid connection: need two different LED indices' });
    }
    const connections = await readConnections();
    // Normalize: always store [min, max]
    const edge = [Math.min(a, b), Math.max(a, b)];
    // Check for duplicate
    const exists = connections.some(c => c[0] === edge[0] && c[1] === edge[1]);
    if (exists) {
      return res.status(409).json({ error: 'Connection already exists' });
    }
    connections.push(edge);
    await writeConnections(connections);
    res.status(201).json(edge);
  } catch (err) {
    console.error('Error saving connection:', err);
    res.status(500).json({ error: 'Failed to save connection' });
  }
});

// Delete a specific connection
app.delete('/api/connections', async (req, res) => {
  try {
    const { a, b } = req.body;
    const edge = [Math.min(a, b), Math.max(a, b)];
    const connections = await readConnections();
    const filtered = connections.filter(c => !(c[0] === edge[0] && c[1] === edge[1]));
    if (filtered.length === connections.length) {
      return res.status(404).json({ error: 'Connection not found' });
    }
    await writeConnections(filtered);
    res.status(204).send();
  } catch (err) {
    console.error('Error deleting connection:', err);
    res.status(500).json({ error: 'Failed to delete connection' });
  }
});

// Delete all connections
app.delete('/api/connections/all', async (req, res) => {
  try {
    await writeConnections([]);
    res.status(204).send();
  } catch (err) {
    console.error('Error clearing connections:', err);
    res.status(500).json({ error: 'Failed to clear connections' });
  }
});

// --- LED Boxes API (closed cycles in the connection graph) ---
//
// Schema (led-boxes.json):
// {
//   "description": "...",
//   "midline_x": 230.0,
//   "boxes": [
//     { "id": 0, "name": "left ear",
//       "perimeter": [12, 13, 14, ...],   // LED indices in cycle order
//       "centroid_x": 144.0, "centroid_y": 434.0,
//       "led_count": 17 }
//   ]
// }

async function readBoxesFile() {
  try {
    await fs.access(BOXES_FILE);
    const data = await fs.readFile(BOXES_FILE, 'utf8');
    return JSON.parse(data);
  } catch (err) {
    return { description: 'Closed boxes for symmetric LED effects', midline_x: 230.0, boxes: [] };
  }
}

async function writeBoxesFile(doc) {
  await fs.writeFile(BOXES_FILE, JSON.stringify(doc, null, 2));
}

async function readCoords() {
  const coordsPath = path.join(__dirname, '..', 'assets', 'coords.json');
  const data = await fs.readFile(coordsPath, 'utf8');
  return JSON.parse(data);
}

// Centroids must live in the same coordinate system the Java backend uses
// (assets/pixelCoordinates.json), NOT the editor display coords (assets/coords.json),
// otherwise mirror-pair analysis on the Java side breaks.
async function readPixelCoords() {
  const p = path.join(__dirname, '..', 'assets', 'pixelCoordinates.json');
  const data = await fs.readFile(p, 'utf8');
  // pixelCoordinates.json is an array of {x,y,index}; index it by `index` for safe lookup.
  const arr = JSON.parse(data);
  const byIndex = new Array(arr.length);
  for (const pt of arr) byIndex[pt.index] = pt;
  return byIndex;
}

function computeCentroid(perimeter, coords) {
  if (!perimeter || perimeter.length === 0) return { x: 0, y: 0 };
  let sx = 0, sy = 0, n = 0;
  for (const idx of perimeter) {
    const c = coords[idx];
    if (c) { sx += c.x; sy += c.y; n++; }
  }
  if (n === 0) return { x: 0, y: 0 };
  return { x: Math.round((sx / n) * 10) / 10, y: Math.round((sy / n) * 10) / 10 };
}

async function enrichBox(box) {
  const coords = await readPixelCoords();
  const c = computeCentroid(box.perimeter, coords);
  return {
    id: box.id,
    name: box.name || null,
    perimeter: box.perimeter,
    centroid_x: c.x,
    centroid_y: c.y,
    led_count: box.perimeter.length,
    mirror_box_id: typeof box.mirror_box_id === 'number' ? box.mirror_box_id : null
  };
}

app.get('/api/boxes', async (req, res) => {
  try {
    const doc = await readBoxesFile();
    res.json(doc);
  } catch (err) {
    console.error('Error reading boxes:', err);
    res.status(500).json({ error: 'Failed to read boxes' });
  }
});

// Replace the entire boxes array (used by editor's auto-save)
app.put('/api/boxes', async (req, res) => {
  try {
    const body = req.body || {};
    if (!Array.isArray(body.boxes)) {
      return res.status(400).json({ error: 'Body must contain a boxes array' });
    }
    const coords = await readPixelCoords();
    const enriched = body.boxes.map((b, i) => {
      if (!Array.isArray(b.perimeter) || b.perimeter.length < 3) {
        throw new Error(`Box at index ${i} needs perimeter with at least 3 LEDs`);
      }
      const c = computeCentroid(b.perimeter, coords);
      return {
        id: typeof b.id === 'number' ? b.id : i,
        name: b.name || null,
        perimeter: b.perimeter,
        centroid_x: c.x,
        centroid_y: c.y,
        led_count: b.perimeter.length,
        mirror_box_id: typeof b.mirror_box_id === 'number' ? b.mirror_box_id : null
      };
    });
    const doc = {
      description: body.description || 'Closed boxes for symmetric LED effects',
      midline_x: typeof body.midline_x === 'number' ? body.midline_x : 230.0,
      boxes: enriched
    };
    await writeBoxesFile(doc);
    res.json(doc);
  } catch (err) {
    console.error('Error writing boxes:', err);
    res.status(500).json({ error: 'Failed to write boxes: ' + err.message });
  }
});

// Append a single box
app.post('/api/boxes', async (req, res) => {
  try {
    const incoming = req.body;
    if (!incoming || !Array.isArray(incoming.perimeter) || incoming.perimeter.length < 3) {
      return res.status(400).json({ error: 'Box needs a perimeter array with at least 3 LEDs' });
    }
    const doc = await readBoxesFile();
    const nextId = (doc.boxes.reduce((m, b) => Math.max(m, b.id), -1)) + 1;
    const enriched = await enrichBox({ ...incoming, id: nextId });
    doc.boxes.push(enriched);
    await writeBoxesFile(doc);
    res.status(201).json(enriched);
  } catch (err) {
    console.error('Error appending box:', err);
    res.status(500).json({ error: 'Failed to append box' });
  }
});

// Delete a single box by id
app.delete('/api/boxes/:id', async (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    const doc = await readBoxesFile();
    const before = doc.boxes.length;
    doc.boxes = doc.boxes.filter(b => b.id !== id);
    if (doc.boxes.length === before) {
      return res.status(404).json({ error: 'Box not found' });
    }
    await writeBoxesFile(doc);
    res.status(204).send();
  } catch (err) {
    console.error('Error deleting box:', err);
    res.status(500).json({ error: 'Failed to delete box' });
  }
});

// --- Per-LED Mirror Pairings API ---
//
// Schema (led-mirrors.json):
// {
//   "description": "...",
//   "midline_x": 230.0,
//   "pairs": [[58, 267], [45, 45], ...]    // [a, b] with a<=b; self-mirror: [x, x]
// }

const MIRRORS_FILE = path.join(__dirname, 'led-mirrors.json');

async function readMirrorsFile() {
  try {
    await fs.access(MIRRORS_FILE);
    const data = await fs.readFile(MIRRORS_FILE, 'utf8');
    return JSON.parse(data);
  } catch (err) {
    return { description: 'Per-LED mirror pairings around the vertical midline', midline_x: 230.0, pairs: [] };
  }
}

async function writeMirrorsFile(doc) {
  await fs.writeFile(MIRRORS_FILE, JSON.stringify(doc, null, 2));
}

app.get('/api/led-mirrors', async (req, res) => {
  try {
    const doc = await readMirrorsFile();
    res.json(doc);
  } catch (err) {
    console.error('Error reading led-mirrors:', err);
    res.status(500).json({ error: 'Failed to read led-mirrors' });
  }
});

app.put('/api/led-mirrors', async (req, res) => {
  try {
    const body = req.body || {};
    if (!Array.isArray(body.pairs)) {
      return res.status(400).json({ error: 'Body must contain a pairs array' });
    }
    // Normalize: ensure [min, max] in each pair
    const normalized = body.pairs.map(p => {
      if (!Array.isArray(p) || p.length !== 2) throw new Error('Each pair must be [a, b]');
      const a = p[0], b = p[1];
      if (a === b) return [a, b]; // self-mirror
      return [Math.min(a, b), Math.max(a, b)];
    });
    const doc = {
      description: body.description || 'Per-LED mirror pairings around the vertical midline',
      midline_x: typeof body.midline_x === 'number' ? body.midline_x : 230.0,
      pairs: normalized
    };
    await writeMirrorsFile(doc);
    res.json(doc);
  } catch (err) {
    console.error('Error writing led-mirrors:', err);
    res.status(500).json({ error: 'Failed to write led-mirrors: ' + err.message });
  }
});

// Serve LED coordinates
app.get('/api/coords', async (req, res) => {
  try {
    const coordsPath = path.join(__dirname, '..', 'assets', 'coords.json');
    const data = await fs.readFile(coordsPath, 'utf8');
    res.json(JSON.parse(data));
  } catch (err) {
    console.error('Error reading coords:', err);
    res.status(500).json({ error: 'Failed to load LED coordinates' });
  }
});

// Start server
app.listen(PORT, () => {
  console.log(`LED Namer server running on http://localhost:${PORT}`);
});