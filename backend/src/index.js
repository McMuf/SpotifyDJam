require('dotenv').config()
const express = require('express')
const { WebSocketServer } = require('ws')
const http = require('http')
const db = require('./db')
const auth = require('./auth')
const jams = require('./jams')

const app = express()
app.use(express.json())

const PORT = process.env.PORT || 4000
const WS_PORT = 4001

const server = http.createServer(app)

const wss = new WebSocketServer({ port: WS_PORT })
console.log(`WebSocket server running on ws://127.0.0.1:${WS_PORT}`)

const clients = new Map()

wss.on('connection', (ws) => {
  console.log('New WebSocket connection')

  ws.on('message', async (raw) => {
    let msg
    try {
      msg = JSON.parse(raw)
    } catch {
      return
    }

    const { type, jamId, playerUuid, data } = msg

    switch (type) {
      case 'JOIN': {
        ws.playerUuid = playerUuid
        ws.jamId = jamId
        clients.set(playerUuid, ws)
        jams.joinJam(jamId, playerUuid)
        const jam = jams.getJam(jamId)
        ws.send(JSON.stringify({ type: 'JAM_STATE', data: jam }))
        break
      }
      case 'SKIP': { broadcastToJam(jamId, { type: 'SKIP' }); break }
      case 'PREV': { broadcastToJam(jamId, { type: 'PREV' }); break }
      case 'PAUSE': { broadcastToJam(jamId, { type: 'PAUSE' }); break }
      case 'RESUME': { broadcastToJam(jamId, { type: 'RESUME' }); break }
      case 'TRACK_CHANGED': {
        jams.updateJamState(jamId, data.trackId, data.progressMs, data.isPlaying)
        broadcastToJam(jamId, { type: 'TRACK_CHANGED', data })
        break
      }
      case 'LEAVE': {
        clients.delete(playerUuid)
        jams.leaveJam(jamId, playerUuid)
        break
      }
      case 'END_JAM': {
        const jam = jams.getJam(jamId)
        if (jam && jam.host_uuid === playerUuid) {
          broadcastToJam(jamId, { type: 'JAM_ENDED' })
          jams.endJam(jamId)
        }
        break
      }
    }
  })

  ws.on('close', () => {
    if (ws.playerUuid) {
      clients.delete(ws.playerUuid)
      console.log(`Player ${ws.playerUuid} disconnected`)
    }
  })
})

function broadcastToJam(jamId, message) {
  const members = jams.getJamMembers(jamId)
  for (const member of members) {
    const client = clients.get(member.player_uuid)
    if (client && client.readyState === 1) {
      client.send(JSON.stringify(message))
    }
  }
}

// ─── Auth Routes ──────────────────────────────────────────────────────

app.get('/login', (req, res) => {
  const { playerId } = req.query
  if (!playerId) return res.status(400).json({ error: 'playerId required' })
  const url = auth.getAuthUrl(playerId)
  res.redirect(url)
})

app.get('/callback', async (req, res) => {
  const { code, state } = req.query
  try {
    await auth.handleCallback(code, state)
    res.send(`<h2>✅ Spotify linked!</h2><p>You can close this tab and return to Minecraft.</p>`)
  } catch (err) {
    console.error('Auth error:', err)
    res.status(500).send('Something went wrong.')
  }
})

app.get('/auth-status', (req, res) => {
  const { playerId } = req.query
  if (!playerId) return res.status(400).json({ error: 'playerId required' })
  const linked = auth.isPlayerLinked(playerId)
  res.json({ linked })
})

// ─── Playlist Routes ──────────────────────────────────────────────────

app.get('/playlists', async (req, res) => {
  const { playerId } = req.query
  if (!playerId) return res.status(400).json({ error: 'playerId required' })
  try {
    const token = await auth.getTokenForPlayer(playerId)
    if (!token) return res.status(401).json({ error: 'Player not linked' })
    auth.spotifyApi.setAccessToken(token)
    const data = await auth.spotifyApi.getUserPlaylists({ limit: 50 })
    console.log(`Fetched playlists for ${playerId}`)
    res.json({ playlists: data.body.items })
  } catch (err) {
    console.error('Playlist fetch error:', err)
    res.status(500).json({ error: 'Failed to fetch playlists' })
  }
})

// ─── Search Route ─────────────────────────────────────────────────────

app.get('/search', async (req, res) => {
  const { playerId, query } = req.query
  if (!playerId || !query) return res.status(400).json({ error: 'playerId and query required' })
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    const data = await auth.spotifyApi.searchTracks(query, { limit: 10 })
    const tracks = data.body.tracks.items.map(t => ({
      id: t.id,
      uri: t.uri,
      name: t.name,
      artist: t.artists.map(a => a.name).join(', '),
      duration: Math.floor(t.duration_ms / 1000)
    }))
    res.json({ tracks })
  } catch (err) {
    console.error('Search error:', err.message)
    res.status(500).json({ error: err.message })
  }
})

// ─── Jam Routes ───────────────────────────────────────────────────────

app.get('/jams/list', (req, res) => {
  const jamsList = db.prepare('SELECT * FROM jams').all()
  res.json({ jams: jamsList })
})

app.get('/jams/at', (req, res) => {
  const { x, y, z } = req.query
  const jam = jams.getJamByJukebox(Number(x), Number(y), Number(z))
  res.json({ jam: jam || null })
})

app.post('/jams/create', async (req, res) => {
  const { hostUuid, jukeboxX, jukeboxY, jukeboxZ, playlistId } = req.body
  if (!hostUuid) return res.status(400).json({ error: 'hostUuid required' })

  const jamId = jams.createJam(hostUuid, jukeboxX, jukeboxY, jukeboxZ, playlistId)

  try {
    const token = await auth.getTokenForPlayer(hostUuid)
    if (token) {
      auth.spotifyApi.setAccessToken(token)
      await auth.spotifyApi.play({ context_uri: `spotify:playlist:${playlistId}` })
      console.log(`Started playback for jam ${jamId}`)
    }
  } catch (err) {
    console.error('Playback error:', err.message)
  }

  res.json({ jamId })
})

app.post('/jams/create-collab', async (req, res) => {
  const { hostUuid, jamName, jukeboxX, jukeboxY, jukeboxZ } = req.body
  try {
    const token = await auth.getTokenForPlayer(hostUuid)
    auth.spotifyApi.setAccessToken(token)

    const playlist = await auth.spotifyApi.createPlaylist(jamName, {
      description: 'SpotifyDJams jam session',
      public: true
    })

    const playlistId = playlist.body.id
    const jamId = jams.createJam(hostUuid, jukeboxX, jukeboxY, jukeboxZ, playlistId)

    db.prepare('UPDATE jams SET collaborative_playlist_id = ?, name = ? WHERE id = ?')
      .run(playlistId, jamName, jamId)

    try {
      await auth.spotifyApi.play({ context_uri: `spotify:playlist:${playlistId}` })
    } catch (playErr) {
      console.log('Could not auto-start playback:', playErr.message)
    }

    res.json({ jamId, playlistId })
  } catch (err) {
    console.error('Create collab error:', err.message)
    res.status(500).json({ error: err.message })
  }
})

app.post('/jams/rename', (req, res) => {
  const { jamId, name, hostUuid } = req.body
  const jam = db.prepare('SELECT * FROM jams WHERE id = ?').get(jamId)
  if (!jam) return res.status(404).json({ error: 'Jam not found' })
  if (jam.host_uuid !== hostUuid) return res.status(403).json({ error: 'Not the host' })
  db.prepare('UPDATE jams SET name = ? WHERE id = ?').run(name, jamId)
  res.json({ success: true })
})

app.post('/jams/addsong', async (req, res) => {
  const { playerId, trackUri } = req.body
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    await auth.spotifyApi.addToQueue(trackUri)
    console.log(`Added ${trackUri} to queue for ${playerId}`)
    res.json({ success: true })
  } catch (err) {
    console.error('Add to queue error:', err.message)
    res.status(500).json({ error: err.message })
  }
})

app.post('/jams/cleanup', async (req, res) => {
  const { playerId } = req.body
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)

    const jamsList = db.prepare('SELECT * FROM jams WHERE collaborative_playlist_id IS NOT NULL').all()

    let deleted = 0
    for (const jam of jamsList) {
      try {
        await auth.spotifyApi.unfollowPlaylist(jam.collaborative_playlist_id)
        deleted++
      } catch (e) {
        console.log(`Could not delete playlist: ${e.message}`)
      }
    }

    db.prepare('DELETE FROM jams').run()
    db.prepare('DELETE FROM jam_members').run()

    res.json({ success: true, cleaned: deleted })
  } catch (err) {
    console.error('Cleanup error:', err.message)
    res.status(500).json({ error: err.message })
  }
})

// ─── Playback Routes ──────────────────────────────────────────────────

app.post('/playback/activate', async (req, res) => {
  const { playerId } = req.body
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    const devices = await auth.spotifyApi.getMyDevices()
    if (devices.body.devices.length === 0) {
      return res.json({ success: false, openUrl: 'https://open.spotify.com' })
    }
    let targetDevice = devices.body.devices.find(d => d.type === 'Computer') || devices.body.devices[0]
    await auth.spotifyApi.transferMyPlayback([targetDevice.id])
    res.json({ success: true, deviceName: targetDevice.name })
  } catch (err) {
    res.json({ success: false, message: err.message })
  }
})

app.post('/playback/start', async (req, res) => {
  const { playerId, playlistId } = req.body
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    await auth.spotifyApi.play({ context_uri: `spotify:playlist:${playlistId}` })
    res.json({ success: true })
  } catch (err) {
    res.status(500).json({ error: err.message })
  }
})

app.get('/playback/current', async (req, res) => {
  const { playerId } = req.query
  if (!playerId) return res.status(400).json({ error: 'playerId required' })
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    const data = await auth.spotifyApi.getMyCurrentPlayingTrack()
    if (data.body && data.body.currently_playing_type === 'ad') {
      res.json({ trackName: null, artistName: null, isPlaying: true, isAd: true })
    } else if (data.body && data.body.item) {
      res.json({
        trackName: data.body.item.name,
        artistName: data.body.item.artists.map(a => a.name).join(', '),
        isPlaying: data.body.is_playing,
        isAd: false
      })
    } else {
      res.json({ trackName: null, artistName: null, isPlaying: false, isAd: false })
    }
  } catch (err) {
    res.status(500).json({ error: err.message })
  }
})

app.get('/playback/position', async (req, res) => {
  const { playerId } = req.query
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    const data = await auth.spotifyApi.getMyCurrentPlayingTrack()
    if (data.body && data.body.currently_playing_type === 'ad') {
      res.json({ trackId: null, positionMs: 0, isPlaying: true, isAd: true })
    } else if (data.body && data.body.item) {
      res.json({
        trackId: data.body.item.id,
        positionMs: data.body.progress_ms,
        isPlaying: data.body.is_playing,
        trackName: data.body.item.name,
        artistName: data.body.item.artists.map(a => a.name).join(', '),
        isAd: false
      })
    } else {
      res.json({ trackId: null, positionMs: 0, isPlaying: false, isAd: false })
    }
  } catch (err) {
    res.status(500).json({ error: err.message })
  }
})

app.get('/playback/analysis', async (req, res) => {
  const { playerId, trackId } = req.query
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    const track = await auth.spotifyApi.getTrack(trackId)
    const durationMs = track.body.duration_ms
    const bpm = 120
    const beatIntervalMs = Math.round(60000 / bpm)
    const beats = []
    for (let t = 0; t < durationMs; t += beatIntervalMs) {
      beats.push({ start: t, confidence: 1.0 })
    }
    res.json({ beats })
  } catch (err) {
    res.status(500).json({ error: err.message })
  }
})

app.post('/playback/pause', async (req, res) => {
  const { playerId } = req.body
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    await auth.spotifyApi.pause()
    res.json({ success: true })
  } catch (err) {
    res.status(500).json({ error: err.message })
  }
})

app.post('/playback/resume', async (req, res) => {
  const { playerId } = req.body
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    await auth.spotifyApi.play()
    res.json({ success: true })
  } catch (err) {
    res.status(500).json({ error: err.message })
  }
})

app.post('/playback/skip', async (req, res) => {
  const { playerId } = req.body
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    await auth.spotifyApi.skipToNext()
    res.json({ success: true })
  } catch (err) {
    res.status(500).json({ error: err.message })
  }
})

app.post('/playback/prev', async (req, res) => {
  const { playerId } = req.body
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    await auth.spotifyApi.skipToPrevious()
    res.json({ success: true })
  } catch (err) {
    res.status(500).json({ error: err.message })
  }
})

app.post('/playback/shuffle', async (req, res) => {
  const { playerId, state } = req.body
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    await auth.spotifyApi.setShuffle(state)
    console.log(`Shuffle set to ${state} for ${playerId}`)
    res.json({ success: true, premium: true })
  } catch (err) {
    console.error('Shuffle error:', err.message)
    // If 403 it means free account
    const isFreeUser = err.message && (err.message.includes('403') || err.message.includes('Premium'))
    res.status(200).json({ success: false, premium: !isFreeUser, error: err.message })
  }
})

app.post('/playback/volume', async (req, res) => {
  const { playerId, volumePercent } = req.body
  try {
    const token = await auth.getTokenForPlayer(playerId)
    auth.spotifyApi.setAccessToken(token)
    await auth.spotifyApi.setVolume(Math.round(volumePercent))
    res.json({ success: true })
  } catch (err) {
    res.status(500).json({ error: err.message })
  }
})

// ─── Settings Routes ──────────────────────────────────────────────────

app.get('/settings', (req, res) => {
  const { playerId } = req.query
  if (!playerId) return res.status(400).json({ error: 'playerId required' })
  let settings = db.prepare('SELECT * FROM settings WHERE player_uuid = ?').get(playerId)
  if (!settings) {
    db.prepare('INSERT INTO settings (player_uuid) VALUES (?)').run(playerId)
    settings = db.prepare('SELECT * FROM settings WHERE player_uuid = ?').get(playerId)
  }
  res.json(settings)
})

app.post('/settings', (req, res) => {
  const { playerId, keyPause, keySkip, keyPrev } = req.body
  if (!playerId) return res.status(400).json({ error: 'playerId required' })
  db.prepare(`
    INSERT OR REPLACE INTO settings (player_uuid, key_pause, key_skip, key_prev)
    VALUES (?, ?, ?, ?)
  `).run(playerId, keyPause, keySkip, keyPrev)
  res.json({ success: true })
})

// ─── Start Server ─────────────────────────────────────────────────────

server.listen(PORT, '127.0.0.1', () => {
  console.log(`SpotifyDJams backend running on http://127.0.0.1:${PORT}`)
})