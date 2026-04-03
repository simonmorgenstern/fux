const express = require('express');
const bodyParser = require('body-parser');
const fs = require('fs').promises;
const path = require('path');
const cors = require('cors');

const app = express();
const PORT = 3000;
const DATA_FILE = path.join(__dirname, 'led-groups.json');
const CONNECTIONS_FILE = path.join(__dirname, 'led-connections.json');

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