const Database = require('better-sqlite3')
const path = require('path')

const db = new Database(path.join(__dirname, '../spotifydjams.sqlite'))

db.exec(`
  CREATE TABLE IF NOT EXISTS players (
    uuid TEXT PRIMARY KEY,
    access_token TEXT,
    refresh_token TEXT,
    expires_at INTEGER
  );

  CREATE TABLE IF NOT EXISTS jams (
    id TEXT PRIMARY KEY,
    name TEXT DEFAULT 'Untitled Jam',
    jukebox_x INTEGER,
    jukebox_y INTEGER,
    jukebox_z INTEGER,
    host_uuid TEXT,
    playlist_id TEXT,
    collaborative_playlist_id TEXT,
    current_track_id TEXT,
    progress_ms INTEGER DEFAULT 0,
    is_playing INTEGER DEFAULT 0,
    radius INTEGER DEFAULT 5,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
  );

  CREATE TABLE IF NOT EXISTS jam_members (
    jam_id TEXT,
    player_uuid TEXT,
    PRIMARY KEY (jam_id, player_uuid)
  );

  CREATE TABLE IF NOT EXISTS settings (
    player_uuid TEXT PRIMARY KEY,
    key_pause INTEGER DEFAULT 67,
    key_skip INTEGER DEFAULT 86,
    key_prev INTEGER DEFAULT 90
  );
`)

console.log('Database ready')

module.exports = db