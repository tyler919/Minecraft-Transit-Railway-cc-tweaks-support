# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## About This Fork

This is a fork of MTR (Minecraft Transit Railway) with **CC: Tweaked peripheral support**. The integration allows ComputerCraft computers to interact with MTR blocks via Lua scripts.

**Wiki:** https://wiki.minecrafttransitrailway.com/start

## Build Commands

```bash
# Initial setup (required before first build)
./gradlew setupFiles -PminecraftVersion="1.20.4"

# Build for both Fabric and Forge
./gradlew build -PminecraftVersion="1.20.4"

# Build server-only version (no assets)
./gradlew build -PminecraftVersion="1.20.4" -PexcludeAssets=true
```

**Supported Minecraft versions:** 1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4, 1.20.1, 1.20.4

**CC: Tweaked support:** 1.20.1+ only (requires `majorVersion >= 20`)

**Build output:** `build/release/MTR-<fabric|forge>-<version>+<minecraft>.jar`

**Requirements:** Java 21+ (buildSrc requires Java 21 toolchain)

**Java location on this system:**
```bash
export JAVA_HOME=/home/server_3n1/java/jdk-21.0.2
export PATH=$JAVA_HOME/bin:$PATH
```

**Build Fabric only** (avoids Forge mapping issues with CC:T code):
```bash
./gradlew :fabric:build -PminecraftVersion="1.20.4"
```

## Architecture

### Multi-Loader Setup

MTR uses a **single-source-of-truth** architecture:

- **Fabric module** (`fabric/src/main/java/org/mtr/`) is the primary codebase
- **Forge module** is auto-generated during `setupFiles` by copying from Fabric
- All code changes must be made in the Fabric module

During build, the `setupFiles` task copies:
- `fabric/src/main/java/org/mtr/mod` → `forge/src/main/java/org/mtr/mod`
- `fabric/src/main/java/org/mtr/legacy` → `forge/src/main/java/org/mtr/legacy`
- `fabric/src/main/java/org/mtr/core` → `forge/src/main/java/org/mtr/core`
- All assets and data resources

### Key Entry Points

| File | Purpose |
|------|---------|
| `fabric/src/main/java/org/mtr/init/MTR.java` | Fabric mod initializer |
| `fabric/src/main/java/org/mtr/init/MTRClient.java` | Fabric client initializer |
| `fabric/src/main/java/org/mtr/mod/Init.java` | Main initialization (registers blocks, items, packets, etc.) |
| `fabric/src/main/java/org/mtr/mod/InitClient.java` | Client-side initialization (rendering, keybinds, sounds) |

### Package Structure

```
fabric/src/main/java/org/mtr/mod/
├── block/         # Custom blocks
├── client/        # Client-side logic
├── config/        # Configuration classes
├── data/          # Data structures and world storage
├── entity/        # Custom entities
├── generated/     # Auto-generated code from schemas
├── item/          # Custom items
├── model/         # 3D model/rendering
├── packet/        # Network packets (50+ types)
├── render/        # Block/entity renderers
├── resource/      # Resource management
├── screen/        # GUI screens
├── servlet/       # Built-in web server servlets
└── sound/         # Sound management
```

### Code Generation

The build system generates Java code from JSON schemas in `buildSrc/src/main/resources/schema/`. Template files (`*.template.java`, `*.template.json`) are processed during `setupFiles` with token replacement for version numbers and configuration.

### Platform-Specific Code

- **Fabric-only:** `fabric/src/main/java/org/mtr/init/` (entry points), `fabric/src/main/java/org/mtr/mixin/` (mixins)
- **Forge-only:** `forge/src/main/java/org/mtr/init/` (entry points, auto-generated)

### Dependencies

Custom MTR libraries are in `libs/`:
- `Shadow-Libraries-net` / `Shadow-Libraries-util` - Network and utility code
- `Minecraft-Mappings-common` - Cross-version mappings abstraction
- `Transport-Simulation-Core` - Vehicle physics and simulation engine

### Web Server

MTR includes a built-in Jetty web server for:
- Resource editor UI (Angular app in `website/`)
- Real-time data requests
- System map display

Servlets are in `fabric/src/main/java/org/mtr/mod/servlet/`.

### Simulation Core Data Access

MTR uses a separate Transport-Simulation-Core for vehicle physics. Understanding data access patterns is critical:

**Server-Side Queryable** (accessible from CC:T peripherals via `Init.sendMessageC2S()`):
- `ARRIVALS` - Real-time train arrivals for platforms
- `GET_DATA` / `LIST_DATA` - Stations, depots, routes, platforms, sidings
- `NEARBY_STATIONS` / `NEARBY_DEPOTS` - Areas near a position
- `RAILS` - Rail connections

**Client-Side Only** (NOT accessible from server code):
- Real-time vehicle positions, speed, direction
- Real-time lift positions and current floor
- Signal block live status

This is architectural: `VehicleLiftResponse` is pushed to clients only via `PacketUpdateVehiclesLifts`, not queryable server-side.

### CC: Tweaked Integration

Peripherals are in `fabric/src/main/java/org/mtr/mod/peripheral/`.

#### Available Peripherals

**1. mtr_pids** - PIDS Displays
| Block | PIDS display blocks (all variants) |
|-------|-------------------------------------|
| Methods | `getArrivals()`, `getPlatformIds()`, `getMaxArrivals()`, `getMessage(index)`, `setMessage(index, msg)`, `getHideArrival(index)`, `setHideArrival(index, hide)`, `getDisplayPage()`, `setDisplayPage(page)`, `setPlatformIds(list)` |

**2. mtr_data_terminal** - Data Terminal Block
| Block | Data Terminal |
|-------|---------------|
| Methods | `getStations()`, `getDepots()`, `getRoutes()`, `getArrivalsForPlatform(id)`, `getArrivalsForPlatforms(list)`, **`getArrivalsForPlatformNow(id)`**, **`getArrivalsForPlatformsNow(list)`**, `waitForData()`, `waitForDataWithTimeout(ms)`, `isDataReady()`, `refreshData()`, `refreshArrivalsCache()`, `getDiagnostics()`, `getStationCount()`, `getDepotCount()`, `getRouteCount()`, `getTerminalPosition()` |

> **Arrivals are async.** `getArrivals*` (non-blocking) return empty until `tick()` fills the cache, so a
> one-shot call sees no trains. The `*Now` variants (`mainThread=false`) fire a request and BLOCK until the
> server responds (mirrors `waitForData`), so a single call returns data. Prefer `*Now` for scripts. The
> server response is logged as `[MTR-CCT] ARRIVALS response: N arrival(s)` for diagnostics.

> **CC:Tweaked collection-parameter rule.** CC:T does NOT support `java.util.List` parameters on `@LuaFunction`
> methods at all — `List<Long>` logs "non-wildcard argument", and even `List<?>` logs "Unknown parameter type
> java.util.List". A Lua array/table arrives as a **`Map<?, ?>`** (keys 1,2,3…). So collection-taking methods
> (`setSignalColors`, `setFilterRouteIds`, `setPlatformIds`, `getArrivalsForPlatforms`/`Now`) take `Map<?, ?>` and
> iterate `.values()`, converting via `((Number) o).longValue()/intValue()`. Return types like `List<Map>` are fine.

**3. mtr_signal** - Signal Blocks
| Block | Signal blocks (all variants) |
|-------|------------------------------|
| Methods | `getAspect()`, `getSignalColors()`, `setSignalColors()` |

**4. mtr_train_sensor** - Train Sensors
| Block | Train sensor blocks |
|-------|---------------------|
| Methods | `getFilterRouteIds()`, `setFilterRouteIds()` |

**5. mtr_train_announcer** - Train Announcers
| Block | Train announcer blocks |
|-------|------------------------|
| Methods | `getMessage()`, `getSoundId()`, `setAnnouncerData()` |

**6. mtr_train_schedule_sensor** - Schedule Sensors
| Block | Train schedule sensor blocks |
|-------|------------------------------|
| Methods | `getFilterRouteIds()`, `setFilterRouteIds()` |

**7. mtr_lift_buttons** - Lift Buttons
| Block | Lift button panels |
|-------|-------------------|
| Methods | `getTrackPositions()`, `getFloorDetails()` |

**8. mtr_lift_track_floor** - Lift Track Floors
| Block | Lift floor marker blocks |
|-------|--------------------------|
| Methods | `getFloorNumber()`, `setFloorData()` |

**9. mtr_arrival_projector** - Arrival Projectors
| Block | Arrival projector blocks |
|-------|--------------------------|
| Methods | Extends PIDS: `getArrivals()`, `getPlatformIds()` |

#### Technical Notes

- **Registration:** `CCTweakedConfig.register()` called from `MTR.java`
- **Lookup:** Uses `PeripheralLookup.get().registerFallback()` for peripheral discovery
- **Platform:** CC:T peripherals are **Fabric-only** - Forge builds will fail on peripheral code due to Yarn vs Mojang mapping differences
- **Multi-block PIDS:** Data is stored in the UPPER half only, but the peripheral correctly redirects to the data block regardless of which half is accessed
- **Async data:** `DataTerminalCache` pre-populates on server startup for faster initial queries

#### Data Access Patterns

**Server-side queryable** (accessible via `Init.sendMessageC2S()`):
- `ARRIVALS` - Real-time train arrivals for platforms
- `GET_DATA` / `LIST_DATA` - Stations, depots, routes, platforms, sidings
- `NEARBY_STATIONS` / `NEARBY_DEPOTS` - Areas near a position
- `RAILS` - Rail connections

**Client-side only** (NOT accessible from peripherals):
- Real-time vehicle positions, speed, direction
- Real-time lift positions and current floor
- Signal block live status

This is architectural: `VehicleLiftResponse` is pushed to clients only via `PacketUpdateVehiclesLifts`, not queryable server-side.

#### Logging

All CC:T peripheral logs are prefixed with `[MTR-CCT]` for easy filtering:
- Data requests and responses
- Lua function calls from ComputerCraft
- Peripheral registration events

### In-Mod Log Watcher (`/mtr watcher`)

Ships **inside the mod** (`fabric/src/main/java/org/mtr/mod/watcher/`) so bug reports reach the fork
from remote servers the maintainer can't access. It is **off by default** and enabled per-session in-game.

**Commands** (op level 2):
| Command | Effect |
|---------|--------|
| `/mtr watcher start` | Tail this server's `logs/latest.log`; auto-file crashes + `[MTR-CCT]` errors as issues (label `auto-report`), de-duplicated by signature |
| `/mtr watcher stop` | Stop watching |
| `/mtr watcher status` | Running state + issues filed this session |
| `/mtr watcher report` | Grab the **last ~1000 chars** of the log (anything at all) and open one issue labeled `log` |
| `/mtr watcher test` | File a test issue to confirm the token + connectivity |

**Setup on the host machine:** first run writes `config/mtr-watcher.json`. Put a GitHub token with
`issues:write` on the fork into `githubToken`, then `/mtr watcher start`. **The token is never bundled
in the jar** — it lives only in the operator's config file. Other keys: `githubOwner`, `githubRepo`,
`pollSeconds` (min 2), `maxIssuesPerHour` (rate cap).

**Classes:** `WatcherConfig` (config I/O), `LogWatcher` (tail thread + detection + de-dup + rate limit),
`IssueReporter` (GitHub REST POST, Java-8-safe `HttpsURLConnection`). Wired in `Init.java` under the
`/mtr` command and stopped in the `registerServerStopping` hook.

## Contributing

1. Fork and create a branch from the development version branch
2. Make changes only in `fabric/src/main/java/org/mtr/mod/`
3. Run `./gradlew setupFiles` to propagate changes to Forge
4. Submit PR to development branch

Translations: Use [Crowdin](https://crowdin.com/) (linked from repository)
