const SpotifyWebApi = require('spotify-web-api-node')
const db = require('./db')
require('dotenv').config()

// Create the Spotify API client
const spotifyApi = new SpotifyWebApi({
  clientId: process.env.SPOTIFY_CLIENT_ID,
  clientSecret: process.env.SPOTIFY_CLIENT_SECRET,
  redirectUri: process.env.SPOTIFY_REDIRECT_URI
})

// The permissions we need from the user's Spotify account
const SCOPES = [
    'user-read-playback-state',
    'user-modify-playback-state',
    'user-read-currently-playing',
    'playlist-read-private',
    'playlist-read-collaborative',
    'playlist-modify-public',
    'playlist-modify-private',
    'streaming'
  ]

// Route 1: Mod calls this to get the login URL for a player
function getAuthUrl(playerId) {
  return spotifyApi.createAuthorizeURL(SCOPES, playerId)
}

// Route 2: Spotify redirects here after player logs in
async function handleCallback(code, playerUuid) {
  const data = await spotifyApi.authorizationCodeGrant(code)

  const accessToken = data.body.access_token
  const refreshToken = data.body.refresh_token
  const expiresAt = Date.now() + data.body.expires_in * 1000

  // Save tokens to database linked to this player's Minecraft UUID
  db.prepare(`
    INSERT OR REPLACE INTO players (uuid, access_token, refresh_token, expires_at)
    VALUES (?, ?, ?, ?)
  `).run(playerUuid, accessToken, refreshToken, expiresAt)

  console.log(`Player ${playerUuid} linked their Spotify account`)
  return true
}

// Helper: get a fresh token for a player (auto-refreshes if expired)
async function getTokenForPlayer(playerUuid) {
  const player = db.prepare('SELECT * FROM players WHERE uuid = ?').get(playerUuid)

  if (!player) return null

  // If token is expired, refresh it
  if (Date.now() > player.expires_at) {
    spotifyApi.setRefreshToken(player.refresh_token)
    const data = await spotifyApi.refreshAccessToken()

    const newAccessToken = data.body.access_token
    const newExpiresAt = Date.now() + data.body.expires_in * 1000

    db.prepare(`
      UPDATE players SET access_token = ?, expires_at = ? WHERE uuid = ?
    `).run(newAccessToken, newExpiresAt, playerUuid)

    return newAccessToken
  }

  return player.access_token
}

// Check if a player has linked their Spotify
function isPlayerLinked(playerUuid) {
  const player = db.prepare('SELECT uuid FROM players WHERE uuid = ?').get(playerUuid)
  return !!player
}

module.exports = { getAuthUrl, handleCallback, getTokenForPlayer, isPlayerLinked, spotifyApi }