# MTR (Minecraft Transit Railway) - CC:Tweaked Integration Fork

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%2B-green.svg)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![CC:Tweaked](https://img.shields.io/badge/CC%3ATweaked-Integration-orange.svg)](https://tweaked.cc/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A fork of the [Minecraft Transit Railway (MTR)](https://github.com/jonafanho/Minecraft-Transit-Railway) mod that adds **ComputerCraft/CC:Tweaked peripheral support**, allowing Lua scripts to interact with MTR transit systems.

> **Original MTR Repository:** [jonafanho/Minecraft-Transit-Railway](https://github.com/jonafanho/Minecraft-Transit-Railway)

---

## What This Fork Adds

This fork extends MTR with comprehensive CC:Tweaked integration, enabling:

- **CC:Tweaked peripheral support** for 9 different MTR block types
- **Lua scripting capabilities** to control and monitor transit systems
- **Real-time data access** to stations, depots, routes, and arrivals
- **Programmatic control** of PIDS displays, signals, sensors, and lifts

Use cases include:
- Custom departure boards with dynamic formatting
- Automated announcement systems
- Signal monitoring dashboards
- Route information displays
- Lift floor management systems

---

## Available Peripherals

| Peripheral Type | MTR Block | Description |
|-----------------|-----------|-------------|
| `mtr_pids` | PIDS displays | Get arrivals, platform IDs, custom messages |
| `mtr_signal` | Signal blocks | Get/set signal aspects and route colors |
| `mtr_train_sensor` | Train sensors | Get/set route filters, stopped/moving only |
| `mtr_train_announcer` | Announcers | Get/set announcement messages and sounds |
| `mtr_train_schedule_sensor` | Schedule sensors | Get/set route filters and timing |
| `mtr_lift_buttons` | Lift buttons | Get track positions, floor details |
| `mtr_lift_track_floor` | Lift floors | Get/set floor number and description |
| `mtr_arrival_projector` | Arrival projectors | Get arrivals, platform IDs (extends PIDS) |
| `mtr_data_terminal` | Data Terminal | Global access to stations, depots, routes, arrivals |

---

## Lua API Documentation

### mtr_data_terminal

The Data Terminal provides global access to the MTR network data. Place a Data Terminal block and wrap it as a peripheral.

```lua
local terminal = peripheral.find("mtr_data_terminal")
```

#### Data Readiness Functions

| Function | Returns | Description |
|----------|---------|-------------|
| `isDataReady()` | boolean | Check if cache is populated (non-blocking) |
| `waitForData()` | boolean | Wait up to 5 seconds for data to load |
| `waitForDataWithTimeout(ms)` | boolean | Wait with custom timeout in milliseconds |
| `refreshData()` | boolean | Force a cache refresh |
| `getDiagnostics()` | table | Get debug info about cache status |

#### Data Query Functions

| Function | Returns | Description |
|----------|---------|-------------|
| `getStations()` | table | List of all stations with platforms |
| `getDepots()` | table | List of all depots with sidings |
| `getRoutes()` | table | List of all routes with platform IDs |
| `getStationCount()` | number | Number of stations |
| `getDepotCount()` | number | Number of depots |
| `getRouteCount()` | number | Number of routes |

#### Arrival Functions

| Function | Returns | Description |
|----------|---------|-------------|
| `getArrivalsForPlatform(platformId)` | table | Arrivals for a single platform |
| `getArrivalsForPlatforms({id1, id2, ...})` | table | Arrivals for multiple platforms |
| `refreshArrivalsCache()` | number | Refresh arrivals cache, returns platform count |
| `getTerminalPosition()` | table | Terminal block coordinates {x, y, z} |

### mtr_pids

PIDS (Passenger Information Display System) peripherals show train arrivals.

```lua
local pids = peripheral.find("mtr_pids")
```

| Function | Returns | Description |
|----------|---------|-------------|
| `getArrivals()` | table | List of upcoming arrivals |
| `getPlatformIds()` | table | List of configured platform IDs |
| `getMaxArrivals()` | number | Maximum arrivals this PIDS can display |
| `getDisplayPage()` | number | Current display page |
| `getMessage(index)` | string | Custom message at index (1-indexed) |
| `getHideArrival(index)` | boolean | Whether arrival at index is hidden |
| `setMessage(index, message)` | void | Set custom message at index |
| `setHideArrival(index, hide)` | void | Hide/show arrival at index |
| `setDisplayPage(page)` | void | Set display page |
| `setPlatformIds({id1, id2, ...})` | void | Set which platforms to display |

### mtr_signal

Signal peripherals control railway signals.

```lua
local signal = peripheral.find("mtr_signal")
```

| Function | Returns | Description |
|----------|---------|-------------|
| `getAspect(isBackSide)` | number | Current signal aspect (0=clear, 1=occupied, 2=recent, 3=caution) |
| `getSignalColors(isBackSide)` | table | List of signal color values |
| `isDoubleSided()` | boolean | Whether signal is double-sided |
| `getAcceptRedstone()` | boolean | Whether signal accepts redstone input |
| `getOutputRedstone()` | boolean | Whether signal outputs redstone |
| `setSignalColors(colors, isBackSide)` | void | Set signal route colors |
| `setAcceptRedstone(accept)` | void | Enable/disable redstone input |
| `setOutputRedstone(output)` | void | Enable/disable redstone output |

### mtr_train_sensor

Train sensor peripherals detect passing trains.

```lua
local sensor = peripheral.find("mtr_train_sensor")
```

| Function | Returns | Description |
|----------|---------|-------------|
| `getFilterRouteIds()` | table | List of filtered route IDs |
| `getStoppedOnly()` | boolean | Only trigger for stopped trains |
| `getMovingOnly()` | boolean | Only trigger for moving trains |
| `setFilterRouteIds({id1, id2, ...})` | void | Set route filter |
| `setStoppedOnly(stoppedOnly)` | void | Set stopped-only mode |
| `setMovingOnly(movingOnly)` | void | Set moving-only mode |

### mtr_train_announcer

Train announcer peripherals play announcements (extends train sensor).

```lua
local announcer = peripheral.find("mtr_train_announcer")
```

| Function | Returns | Description |
|----------|---------|-------------|
| `getMessage()` | string | Current announcement message |
| `getSoundId()` | string | Current sound resource ID |
| `getDelay()` | number | Announcement delay in ticks |
| `setAnnouncerData(message, soundId, delay)` | void | Set all announcer properties |
| `triggerAnnounce()` | void | Manually trigger announcement |
| *Plus all train sensor functions* | | |

### mtr_train_schedule_sensor

Schedule sensor peripherals trigger based on arrival times (extends train sensor).

```lua
local scheduleSensor = peripheral.find("mtr_train_schedule_sensor")
```

| Function | Returns | Description |
|----------|---------|-------------|
| `getSeconds()` | number | Seconds before arrival to trigger |
| `getRealtimeOnly()` | boolean | Only use realtime data |
| `setScheduleData(seconds, realtimeOnly)` | void | Set schedule parameters |
| *Plus all train sensor functions* | | |

### mtr_lift_buttons

Lift button peripherals manage elevator floor buttons.

```lua
local liftButtons = peripheral.find("mtr_lift_buttons")
```

| Function | Returns | Description |
|----------|---------|-------------|
| `getTrackPositions()` | table | List of floor positions {x, y, z} |
| `getFloorDetails()` | table | Floor positions with number, description, ding status |
| `registerFloor(x, y, z)` | void | Register a floor at position |
| `unregisterFloor(x, y, z)` | void | Unregister a floor |

### mtr_lift_track_floor

Lift track floor peripherals configure individual lift floors.

```lua
local floor = peripheral.find("mtr_lift_track_floor")
```

| Function | Returns | Description |
|----------|---------|-------------|
| `getFloorNumber()` | string | Floor number/label |
| `getFloorDescription()` | string | Floor description text |
| `getShouldDing()` | boolean | Whether floor plays arrival ding |
| `setFloorData(number, description, shouldDing)` | void | Set all floor properties |

### mtr_arrival_projector

Arrival projector peripherals display arrivals (extends PIDS functionality).

```lua
local projector = peripheral.find("mtr_arrival_projector")
```

*Inherits all functions from `mtr_pids`*

---

## Example Lua Scripts

### Example 1: Station Departure Board

Display upcoming arrivals on a monitor connected to a computer with a Data Terminal.

```lua
-- Station Departure Board
local terminal = peripheral.find("mtr_data_terminal")
local monitor = peripheral.find("monitor")

if not terminal then
    error("No Data Terminal found!")
end

-- Wait for data to be ready
print("Waiting for MTR data...")
if not terminal.waitForData() then
    error("Timeout waiting for data!")
end

monitor.setTextScale(0.5)
monitor.clear()

while true do
    local stations = terminal.getStations()

    monitor.setCursorPos(1, 1)
    monitor.setTextColor(colors.yellow)
    monitor.write("=== STATION DEPARTURES ===")

    local line = 3
    for _, station in ipairs(stations) do
        for _, platform in ipairs(station.platforms) do
            local arrivals = terminal.getArrivalsForPlatform(platform.id)

            for i, arrival in ipairs(arrivals) do
                if i > 3 then break end -- Show max 3 per platform

                monitor.setCursorPos(1, line)
                monitor.setTextColor(colors.white)

                local mins = math.floor((arrival.arrival - os.epoch("utc")) / 60000)
                local timeStr = mins > 0 and (mins .. " min") or "Now"

                monitor.write(string.format("%-12s %-15s %s",
                    platform.name,
                    arrival.destination:sub(1, 15),
                    timeStr
                ))

                line = line + 1
            end
        end
    end

    sleep(5) -- Update every 5 seconds
end
```

### Example 2: PIDS Custom Message Controller

Control PIDS displays based on time of day.

```lua
-- PIDS Message Controller
local pids = peripheral.find("mtr_pids")

if not pids then
    error("No PIDS found!")
end

while true do
    local hour = os.date("*t").hour

    if hour >= 6 and hour < 9 then
        pids.setMessage(1, "Good morning! Peak hours in effect.")
    elseif hour >= 17 and hour < 20 then
        pids.setMessage(1, "Evening rush hour - expect delays.")
    elseif hour >= 23 or hour < 5 then
        pids.setMessage(1, "Late night service - reduced frequency.")
    else
        pids.setMessage(1, "") -- Clear message during normal hours
    end

    sleep(300) -- Check every 5 minutes
end
```

### Example 3: Signal Status Monitor

Monitor all signals and display their status.

```lua
-- Signal Status Monitor
local signals = {peripheral.find("mtr_signal")}
local monitor = peripheral.find("monitor")

if #signals == 0 then
    error("No signals found!")
end

local aspectNames = {
    [0] = "CLEAR",
    [1] = "OCCUPIED",
    [2] = "RECENT",
    [3] = "CAUTION"
}

local aspectColors = {
    [0] = colors.green,
    [1] = colors.red,
    [2] = colors.orange,
    [3] = colors.yellow
}

monitor.setTextScale(0.5)

while true do
    monitor.clear()
    monitor.setCursorPos(1, 1)
    monitor.setTextColor(colors.white)
    monitor.write("=== SIGNAL STATUS ===")

    for i, signal in ipairs(signals) do
        local aspect = signal.getAspect(false)

        monitor.setCursorPos(1, i + 2)
        monitor.setTextColor(colors.white)
        monitor.write("Signal " .. i .. ": ")

        monitor.setTextColor(aspectColors[aspect] or colors.white)
        monitor.write(aspectNames[aspect] or "UNKNOWN")
    end

    sleep(1)
end
```

---

## Changelog / Version History

### Version 1.0.0 - CC:Tweaked Integration (December 2024)

**New Features:**
- Added 9 peripheral types for CC:Tweaked integration:
  - `mtr_pids` - PIDS display control
  - `mtr_signal` - Signal block control
  - `mtr_train_sensor` - Train sensor configuration
  - `mtr_train_announcer` - Announcer control
  - `mtr_train_schedule_sensor` - Schedule sensor configuration
  - `mtr_lift_buttons` - Lift button panel access
  - `mtr_lift_track_floor` - Lift floor configuration
  - `mtr_arrival_projector` - Arrival projector control
  - `mtr_data_terminal` - Global network data access

**Bug Fixes:**
- Fixed multi-block PIDS orientation bug (data is now correctly read from UPPER half for vertical PIDS)
- Fixed async/sync data loading issue in DataTerminalCache

**API Additions:**
- Added `waitForData()` and `waitForDataWithTimeout(ms)` for reliable data loading
- Added `isDataReady()` for non-blocking cache status checks
- Added `getDiagnostics()` for debugging data flow issues
- Added cache pre-population on server startup for faster initial queries

**Developer Experience:**
- Added `[MTR-CCT]` logging prefix for easy debugging of CC:Tweaked integration

---

## Installation

### Requirements

- **Minecraft:** 1.20.1 or higher
- **Mod Loader:** Fabric
- **Required Mods:**
  - [MTR (Minecraft Transit Railway)](https://modrinth.com/mod/minecraft-transit-railway) - This fork
  - [CC:Tweaked](https://modrinth.com/mod/cc-tweaked) - ComputerCraft for modern Minecraft
  - [Fabric API](https://modrinth.com/mod/fabric-api)

### Installation Steps

1. Install Fabric Loader for your Minecraft version
2. Download and install Fabric API
3. Download and install CC:Tweaked
4. Download this fork's MTR mod JAR from the releases
5. Place all mod JARs in your `mods` folder
6. Launch Minecraft with the Fabric profile

---

## Building from Source

### Prerequisites

- Java 21 or higher
- Git

### Build Commands

```bash
# Clone the repository
git clone https://github.com/tyler919/Minecraft-Transit-Railway-cc-tweaks-support.git
cd Minecraft-Transit-Railway-cc-tweaks-support

# Initial setup (required before first build)
./gradlew setupFiles -PminecraftVersion="1.20.4"

# Build for both Fabric and Forge
./gradlew build -PminecraftVersion="1.20.4"

# Build Fabric only (recommended - avoids Forge mapping issues with CC:T code)
./gradlew :fabric:build -PminecraftVersion="1.20.4"

# Build server-only version (no assets)
./gradlew build -PminecraftVersion="1.20.4" -PexcludeAssets=true
```

**Supported Minecraft Versions:** 1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4, 1.20.1, 1.20.4

**Note:** CC:Tweaked support requires Minecraft 1.20.1 or higher.

### Build Output

The compiled mod JAR will be located at:
```
build/release/MTR-<fabric|forge>-<version>+<minecraft>.jar
```

---

## Credits

- **Original MTR Mod:** [jonafanho](https://github.com/jonafanho) and [contributors](https://github.com/jonafanho/Minecraft-Transit-Railway/graphs/contributors)
- **CC:Tweaked Integration:** [tyler919](https://github.com/tyler919)
- **CC:Tweaked Mod:** [SquidDev](https://github.com/cc-tweaked/CC-Tweaked)

---

## Links

- **This Fork:** [tyler919/Minecraft-Transit-Railway-cc-tweaks-support](https://github.com/tyler919/Minecraft-Transit-Railway-cc-tweaks-support)
- **Original MTR Repository:** [jonafanho/Minecraft-Transit-Railway](https://github.com/jonafanho/Minecraft-Transit-Railway)
- **MTR Wiki:** [wiki.minecrafttransitrailway.com](https://wiki.minecrafttransitrailway.com/start)
- **MTR on Modrinth:** [modrinth.com/mod/minecraft-transit-railway](https://modrinth.com/mod/minecraft-transit-railway)
- **CC:Tweaked Documentation:** [tweaked.cc](https://tweaked.cc/)
- **CC:Tweaked on Modrinth:** [modrinth.com/mod/cc-tweaked](https://modrinth.com/mod/cc-tweaked)
- **MTR Discord:** [discord.gg/PVZ2nfUaTW](https://discord.gg/PVZ2nfUaTW)

---

## License

This project is licensed under the [MIT License](https://opensource.org/licenses/MIT).

All [Noto fonts](http://www.google.com/get/noto/), bundled with this mod, are licensed under the [Open Font License](http://scripts.sil.org/OFL).

---

*For the original MTR documentation, see [README_ORIGINAL.md](README_ORIGINAL.md).*
