'use strict';

const path = require('node:path');
const { openDb } = require('./src/db');
const { createApp } = require('./src/app');

const PORT = Number(process.env.PORT) || 3000;
const DB_FILE = process.env.DB_FILE || path.join(__dirname, 'data', 'sistem_pakar.db');

const db = openDb(DB_FILE);
const app = createApp(db);

app.listen(PORT, () => {
  console.log(`Sistem Pakar CF berjalan di http://localhost:${PORT}`);
});
