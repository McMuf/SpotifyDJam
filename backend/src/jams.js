const { v4: uuidv4 } = require('uuid')
const db = require('./db')

// Create a new jam session
function createJam(hostUuid, jukeboxX, jukeboxY, jukeboxZ, playlistId) {
  const jamId = uuidv4()

  db.prepare(`
    INSERT INTO jams (id, jukebox_x, jukebox_y, jukebox_z, host_uuid, playlist_id)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(jamId, jukeboxX, jukeboxY, jukeboxZ, hostUuid, playlistId)

  // Add host as first member
  db.prepare(`
    INSERT INTO jam_members (jam_id, player_uuid) VALUES (?, ?)
  `).run(jamId, hostUuid)

  console.log(`Jam ${jamId} created by ${hostUuid}`)
  return jamId
}

// Add a player to an existing jam
function joinJam(jamId, playerUuid) {
  db.prepare(`
    INSERT OR IGNORE INTO jam_members (jam_id, player_uuid) VALUES (?, ?)
  `).run(jamId, playerUuid)
  console.log(`Player ${playerUuid} joined jam ${jamId}`)
}

// Remove a player from a jam
function leaveJam(jamId, playerUuid) {
  db.prepare(`
    DELETE FROM jam_members WHERE jam_id = ? AND player_uuid = ?
  `).run(jamId, playerUuid)
  console.log(`Player ${playerUuid} left jam ${jamId}`)
}

// End a jam entirely
function endJam(jamId) {
  db.prepare('DELETE FROM jam_members WHERE jam_id = ?').run(jamId)
  db.prepare('DELETE FROM jams WHERE id = ?').run(jamId)
  console.log(`Jam ${jamId} ended`)
}

// Get all members of a jam
function getJamMembers(jamId) {
  return db.prepare('SELECT player_uuid FROM jam_members WHERE jam_id = ?').all(jamId)
}

// Get jam info
function getJam(jamId) {
  return db.prepare('SELECT * FROM jams WHERE id = ?').get(jamId)
}

// Update jam playback state
function updateJamState(jamId, trackId, progressMs, isPlaying) {
  db.prepare(`
    UPDATE jams SET current_track_id = ?, progress_ms = ?, is_playing = ? WHERE id = ?
  `).run(trackId, progressMs, isPlaying ? 1 : 0, jamId)
}

// Find a jam by jukebox position
function getJamByJukebox(x, y, z) {
  return db.prepare(`
    SELECT * FROM jams WHERE jukebox_x = ? AND jukebox_y = ? AND jukebox_z = ?
  `).get(x, y, z)
}

module.exports = { createJam, joinJam, leaveJam, endJam, getJamMembers, getJam, updateJamState, getJamByJukebox }