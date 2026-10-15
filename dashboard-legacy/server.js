const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 5000;
const DATA_FILE = path.join(__dirname, 'deaths.json');

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Persistance simple sur disque
let deaths = [];
if (fs.existsSync(DATA_FILE)) {
  deaths = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
  console.log(`${deaths.length} morts chargées depuis deaths.json`);
}

function save() {
  fs.writeFileSync(DATA_FILE, JSON.stringify(deaths, null, 2));
}

// --- API ---

app.post('/api/deaths', (req, res) => {
  const death = { id: deaths.length, ...req.body };
  deaths.push(death);
  save();
  console.log(`[mort #${death.id}] ${death.chapter} | room: ${death.room}`);
  res.json({ ok: true, id: death.id, total: deaths.length });
});

app.post('/api/events', (req, res) => {
  const { type, chapter, room } = req.body;
  console.log(`[event] ${type} | ${chapter} | ${room}`);
  res.json({ ok: true });
});

app.get('/api/stats', (req, res) => {
  if (deaths.length === 0) {
    return res.json({ total: 0, byRoom: {}, byChapter: {}, recentDeaths: [], lastDeath: null });
  }

  const byRoom = {};
  const byChapter = {};

  for (const d of deaths) {
    const roomKey = `${d.chapter} / ${d.room}`;
    byRoom[roomKey] = (byRoom[roomKey] || 0) + 1;
    byChapter[d.chapter] = (byChapter[d.chapter] || 0) + 1;
  }

  const topRooms = Object.fromEntries(
    Object.entries(byRoom).sort((a, b) => b[1] - a[1]).slice(0, 10)
  );

  res.json({
    total: deaths.length,
    byRoom: topRooms,
    byChapter,
    recentDeaths: deaths.slice(-50),
    lastDeath: deaths.at(-1),
  });
});

app.delete('/api/deaths', (req, res) => {
  deaths = [];
  save();
  console.log('[reset] données effacées');
  res.json({ ok: true });
});

// Fallback SPA (syntaxe Express 5)
app.get('/{*path}', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`Dashboard Celeste Death Tracker → http://localhost:${PORT}`);
});
