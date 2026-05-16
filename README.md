# SpotifyDJam 

A Minecraft Fabric mod that lets you control Spotify directly from in-game jukeboxes. Play your playlists, create collaborative jam sessions with friends, and watch beat-synced particles pulse from your jukebox in time with the music.

![Minecraft 1.21.11](https://img.shields.io/badge/Minecraft-1.21.11-green)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## Features

- **Browse & play your Spotify playlists** from inside Minecraft
- **Create Jam sessions** — start a collaborative listening session at any jukebox
- **Beat-synced particles** — customizable particle effects that pulse to the beat
- **Directional audio** — volume fades as you walk away from the jukebox
- ⇄ **Shuffle support** (Spotify Premium required)
- ⏸ **Playback controls** — pause, skip, previous via keybinds
- **RGB particle mode** — cycle through colors on every beat
- **HUD toggle** — press H to hide/show the now-playing overlay
- **Auto-cleanup** — created playlists are deleted when you close Minecraft

---

## Requirements

- Minecraft 1.21.11
- [Fabric Loader](https://fabricmc.net/use/installer/)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 21
- Node.js 18+ (for the backend)
- A Spotify account (Premium recommended for full features)
- A free [Spotify Developer](https://developer.spotify.com) app

---

## Installation

### 1. Install the Mod

Download the latest `.jar` from [Releases](../../releases) and place it in your Minecraft `mods/` folder along with Fabric API.

### 2. Set Up the Backend

The mod requires a small local backend server to communicate with Spotify's API.

**Clone this repository:**
```bash
git clone https://github.com/YOUR_USERNAME/SpotifyDJam.git
cd SpotifyDJam/backend
```

**Install dependencies:**
```bash
npm install
```

**Create your Spotify Developer App:**
1. Go to [developer.spotify.com](https://developer.spotify.com)
2. Click **Create App**
3. Set the Redirect URI to: `http://127.0.0.1:4000/callback`
4. Copy your **Client ID** and **Client Secret**

**Create a `.env` file in the `backend/` folder:**
```env
SPOTIFY_CLIENT_ID=your_client_id_here
SPOTIFY_CLIENT_SECRET=your_client_secret_here
SPOTIFY_REDIRECT_URI=http://127.0.0.1:4000/callback
PORT=4000
```

**Start the backend:**
```bash
npm run dev
```

You should see:
```
SpotifyDJams backend running on http://127.0.0.1:4000
```

### 3. Launch Minecraft

Start Minecraft with Fabric. Right-click any jukebox — the onboarding screen will appear and guide you through linking your Spotify account automatically.

---

## Usage

| Action | Result |
|--------|--------|
| Right-click empty jukebox | Opens SpotifyDJams menu |
| Shift + Right-click jukebox | Always opens SpotifyDJams menu |
| Right-click with music disc | Normal vanilla behavior |
| Break the jukebox | Ends the jam, pauses music |

### Default Keybinds

| Key | Action |
|-----|--------|
| `C` | Pause / Resume |
| `V` | Skip to next track |
| `Z` | Previous track |
| `N` | Toggle shuffle (Premium only) |
| `H` | Hide / show HUD |

All keybinds are customizable in Settings → Keybinds.

---

## Settings

Open any jukebox and click **Settings** to customize:

- **Keybinds** — remap all controls
- **Particles** — shape (Rings, Burst, Spiral, Fountain), type (Flame, Soul Fire, End Rod, Witch, Glow, RGB), BPM, and frequency
- **Radius** — set the inner/outer radius for directional audio volume fading

---

## Spotify Premium

Some features require Spotify Premium:

| Feature | Free | Premium |
|---------|------|---------|
| Browse & play playlists | Yes | Yes |
| Pause / Resume | Yes | Yes |
| Skip / Previous | Yes | Yes |
| Shuffle | No | Yes |
| Volume control | Yes | Yes |
| Directional audio | Yes | Yes |

---

## Notes on Spotify API Limits

Spotify's API limits new apps to **25 users** without a quota extension. If you want to share this mod with more than 25 people, each user will need to:

1. Create their own free Spotify Developer app
2. Set up their own backend with their own credentials

This is a Spotify limitation, not a mod limitation. See [Spotify's developer docs](https://developer.spotify.com/documentation/web-api) for more info.

---

## Troubleshooting

**"Could not load playlists"**
- Make sure your backend is running (`npm run dev`)
- Make sure you've linked your Spotify account via the onboarding screen

**"No active device found"**
- Open Spotify on your computer or phone before using the mod
- The mod will try to automatically activate a device when you open the menu

**Music doesn't play**
- Spotify must be open on a device before the mod can control it
- Free Spotify users may experience limitations on playback control

**UUID changes every session**
- This only happens in Fabric dev mode. With a real Minecraft account the UUID is always the same.

---

## Building from Source

```bash
git clone https://github.com/YOUR_USERNAME/SpotifyDJam.git
cd SpotifyDJam/spotifydjams-template-1.21.11
./gradlew build
```

The built JAR will be at `build/libs/spotifydjams-template-1.21.11.jar`.

---

## Contributing

Pull requests are welcome! If you find a bug or want to add a feature, open an issue first so we can discuss it.

---

## License

MIT — see [LICENSE](LICENSE) for details.

---

## Credits

Built with ❤️ using:
- [Fabric API](https://fabricmc.net)
- [spotify-web-api-node](https://github.com/thelinmichael/spotify-web-api-node)
- [better-sqlite3](https://github.com/WiseLibs/better-sqlite3)