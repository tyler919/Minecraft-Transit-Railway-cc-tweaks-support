--[[
================================================================================
    MTR PREMIUM PIDS DISPLAY
    Version 2.1.0 - Professional Edition

    A polished, feature-rich arrival board display for CC:Tweaked
    Compatible with MTR PIDS (Passenger Information Display System) peripheral

    Features:
    - Professional visual design with box-drawing borders
    - Animated "NOW" arrivals with blinking effect
    - Route color support with hex-to-CC color conversion
    - Auto-scrolling for large arrival lists
    - Delay detection and indicators
    - Real-time clock display
    - Connection status monitoring
    - Splash screen with diagnostics
    - Alternating row colors for readability
    - Countdown timer to next refresh

    Author: Premium Transit Solutions
    License: MIT
================================================================================
--]]

-- ============================================================================
-- CONFIGURATION SECTION
-- Modify these values to customize the display behavior
-- ============================================================================

local CONFIG = {
    -- Timing Settings
    REFRESH_INTERVAL = 30,          -- Seconds between data refreshes
    SCROLL_INTERVAL = 5,            -- Seconds between auto-scroll pages
    BLINK_INTERVAL = 0.5,           -- Seconds between blink states
    SPLASH_DURATION = 3,            -- Seconds to show splash screen

    -- Display Settings
    TEXT_SCALE = 0.5,               -- Monitor text scale (0.5 - 5.0)
    MAX_ROUTE_WIDTH = 12,           -- Maximum characters for route display
    MAX_PLATFORM_WIDTH = 16,        -- Maximum characters for platform
    MAX_DESTINATION_WIDTH = 28,     -- Maximum characters for destination

    -- Feature Toggles
    ENABLE_AUTO_SCROLL = true,      -- Auto-scroll through arrival pages
    ENABLE_BLINK_EFFECT = true,     -- Blink effect for imminent arrivals
    ENABLE_ROUTE_COLORS = true,     -- Show route-specific colors
    ENABLE_DELAY_DETECTION = true,  -- Show delay indicators
    SHOW_STATISTICS = true,         -- Show stats panel
    SHOW_COUNTDOWN = true,          -- Show refresh countdown

    -- Thresholds
    DELAY_THRESHOLD = 120,          -- Seconds deviation to show DELAYED
    IMMINENT_THRESHOLD = 60,        -- Seconds to consider arrival "NOW"

    -- Peripheral Names
    PIDS_NAME = "mtr_pids",         -- PIDS peripheral type

    -- Visual Theme
    THEME = {
        -- Primary Colors
        HEADER_BG = colors.blue,
        HEADER_FG = colors.white,
        BODY_BG = colors.black,

        -- Text Colors
        TITLE = colors.white,
        SUBTITLE = colors.lightBlue,
        LABEL = colors.yellow,
        VALUE = colors.white,
        MUTED = colors.gray,

        -- Status Colors
        SUCCESS = colors.lime,
        WARNING = colors.orange,
        ERROR = colors.red,
        INFO = colors.cyan,

        -- Row Colors
        ROW_ODD = colors.black,
        ROW_EVEN = colors.gray,

        -- Border Colors
        BORDER_PRIMARY = colors.lightBlue,
        BORDER_SECONDARY = colors.gray,

        -- Special
        NOW_BLINK_ON = colors.yellow,
        NOW_BLINK_OFF = colors.orange,
        DELAYED = colors.red,
    }
}

-- ============================================================================
-- BOX DRAWING CHARACTERS
-- Unicode-style characters for professional borders
-- ============================================================================

local BOX = {
    -- Standard box characters (using ASCII alternatives for CC compatibility)
    TL = string.char(150),     -- Top-left corner (using special chars)
    TR = string.char(151),     -- Top-right corner
    BL = string.char(154),     -- Bottom-left corner
    BR = string.char(155),     -- Bottom-right corner
    H = string.char(140),      -- Horizontal line
    V = string.char(149),      -- Vertical line

    -- Fallback ASCII characters (more compatible)
    ASCII = {
        TL = "+",
        TR = "+",
        BL = "+",
        BR = "+",
        H = "-",
        V = "|",
        CROSS = "+",
        T_DOWN = "+",
        T_UP = "+",
        T_LEFT = "+",
        T_RIGHT = "+",
    },

    -- Decorative symbols
    TRAIN = "[>",           -- Train icon
    PLATFORM = "#",         -- Platform icon
    CLOCK = "@",            -- Clock icon
    ARROW = ">",            -- Arrow
    BULLET = "*",           -- Bullet point
    CHECK = "v",            -- Checkmark
    WARN = "!",             -- Warning
    REFRESH = "~",          -- Refresh
}

-- Use ASCII fallback for better compatibility
local BORDER = BOX.ASCII

-- ============================================================================
-- SYMBOLS AND ICONS
-- Visual indicators for various states
-- ============================================================================

local ICONS = {
    TRAIN = "[=]",
    ARRIVING = ">>>",
    PLATFORM = "P:",
    DESTINATION = "->",
    CLOCK = "[*]",
    STATUS_OK = "[OK]",
    STATUS_WARN = "[!!]",
    STATUS_ERR = "[XX]",
    REFRESH = "(~)",
    DELAYED = "[!]",
    ON_TIME = "",
    LOADING = {"[    ]", "[=   ]", "[==  ]", "[=== ]", "[====]", "[ ===]", "[  ==]", "[   =]"},
    SPINNER = {"|", "/", "-", "\\"},
}

-- ============================================================================
-- GLOBAL STATE
-- Runtime variables and state management
-- ============================================================================

local state = {
    pids = nil,                 -- PIDS peripheral reference
    monitor = nil,
    width = 0,
    height = 0,

    -- Data
    platformIds = {},           -- Platform IDs configured on the PIDS
    arrivals = {},              -- Current arrivals data
    maxArrivals = 0,            -- Max arrivals this PIDS can show

    -- Display state
    currentPage = 1,
    totalPages = 1,
    arrivalsPerPage = 10,
    blinkState = true,

    -- Timing
    lastRefresh = 0,
    nextRefresh = 0,
    loadingFrame = 1,

    -- Status
    isConnected = false,
    lastError = nil,
    dataAvailable = false,
}

-- ============================================================================
-- UTILITY FUNCTIONS
-- Helper functions for common operations
-- ============================================================================

--[[
    Clamps a value between min and max bounds
    @param value: The value to clamp
    @param min: Minimum bound
    @param max: Maximum bound
    @return: Clamped value
--]]
local function clamp(value, min, max)
    return math.max(min, math.min(max, value))
end

--[[
    Pads a string to the right with spaces
    @param str: Input string
    @param len: Target length
    @return: Padded string
--]]
local function padRight(str, len)
    str = tostring(str or "")
    if #str >= len then return str:sub(1, len) end
    return str .. string.rep(" ", len - #str)
end

--[[
    Pads a string to the left with spaces
    @param str: Input string
    @param len: Target length
    @return: Padded string
--]]
local function padLeft(str, len)
    str = tostring(str or "")
    if #str >= len then return str:sub(1, len) end
    return string.rep(" ", len - #str) .. str
end

--[[
    Centers a string within a given width
    @param str: Input string
    @param len: Target width
    @return: Centered string
--]]
local function centerPad(str, len)
    str = tostring(str or "")
    if #str >= len then return str:sub(1, len) end
    local leftPad = math.floor((len - #str) / 2)
    local rightPad = len - #str - leftPad
    return string.rep(" ", leftPad) .. str .. string.rep(" ", rightPad)
end

--[[
    Truncates a string with ellipsis if too long
    @param str: Input string
    @param maxLen: Maximum length
    @return: Truncated string
--]]
local function truncate(str, maxLen)
    str = tostring(str or "")
    if #str <= maxLen then return str end
    if maxLen <= 3 then return str:sub(1, maxLen) end
    return str:sub(1, maxLen - 2) .. ".."
end

--[[
    Formats milliseconds into human-readable time
    @param milliseconds: Time in milliseconds
    @return: Formatted time string
--]]
local function formatETA(milliseconds)
    if not milliseconds or milliseconds < 0 then return "N/A" end

    local seconds = math.floor(milliseconds / 1000)

    if seconds < CONFIG.IMMINENT_THRESHOLD then
        return "NOW"
    elseif seconds < 60 then
        return seconds .. "s"
    elseif seconds < 3600 then
        local minutes = math.floor(seconds / 60)
        return minutes .. " min"
    else
        local hours = math.floor(seconds / 3600)
        local minutes = math.floor((seconds % 3600) / 60)
        if minutes > 0 then
            return hours .. "h " .. minutes .. "m"
        else
            return hours .. "h"
        end
    end
end

--[[
    Formats seconds into MM:SS countdown
    @param seconds: Time in seconds
    @return: Formatted countdown string
--]]
local function formatCountdown(seconds)
    if seconds < 0 then seconds = 0 end
    local mins = math.floor(seconds / 60)
    local secs = seconds % 60
    return string.format("%02d:%02d", mins, secs)
end

--[[
    Gets current time as formatted string
    @param use24h: Use 24-hour format
    @return: Formatted time string
--]]
local function getCurrentTime(use24h)
    return textutils.formatTime(os.time(), use24h or true)
end

--[[
    Converts a hex color to the closest CC color
    @param hexColor: Hex color value (integer)
    @return: CC color constant
--]]
local function hexToCC(hexColor)
    if not hexColor or type(hexColor) ~= "number" then
        return colors.white
    end

    -- Extract RGB components
    local r = bit32.rshift(bit32.band(hexColor, 0xFF0000), 16)
    local g = bit32.rshift(bit32.band(hexColor, 0x00FF00), 8)
    local b = bit32.band(hexColor, 0x0000FF)

    -- CC color palette with RGB values
    local ccColors = {
        {colors.white, 240, 240, 240},
        {colors.orange, 242, 178, 51},
        {colors.magenta, 229, 127, 216},
        {colors.lightBlue, 153, 178, 242},
        {colors.yellow, 222, 222, 108},
        {colors.lime, 127, 204, 25},
        {colors.pink, 242, 178, 204},
        {colors.gray, 76, 76, 76},
        {colors.lightGray, 153, 153, 153},
        {colors.cyan, 76, 153, 178},
        {colors.purple, 178, 102, 229},
        {colors.blue, 51, 102, 204},
        {colors.brown, 127, 102, 76},
        {colors.green, 87, 166, 78},
        {colors.red, 204, 76, 76},
        {colors.black, 17, 17, 17},
    }

    -- Find closest color by Euclidean distance
    local closestColor = colors.white
    local closestDist = math.huge

    for _, cc in ipairs(ccColors) do
        local dr = r - cc[2]
        local dg = g - cc[3]
        local db = b - cc[4]
        local dist = dr * dr + dg * dg + db * db

        if dist < closestDist then
            closestDist = dist
            closestColor = cc[1]
        end
    end

    return closestColor
end

-- ============================================================================
-- DRAWING FUNCTIONS
-- Low-level monitor drawing operations
-- ============================================================================

--[[
    Writes text at a specific position
    @param x: X coordinate (1-based)
    @param y: Y coordinate (1-based)
    @param text: Text to write
    @param fg: Foreground color
    @param bg: Background color (optional)
--]]
local function writeAt(x, y, text, fg, bg)
    if y < 1 or y > state.height then return end
    if x < 1 then
        text = text:sub(2 - x)
        x = 1
    end
    if x > state.width then return end

    state.monitor.setCursorPos(x, y)
    if bg then state.monitor.setBackgroundColor(bg) end
    state.monitor.setTextColor(fg or colors.white)
    state.monitor.write(text)
end

--[[
    Writes centered text on a line
    @param y: Y coordinate
    @param text: Text to center
    @param fg: Foreground color
    @param bg: Background color (optional)
--]]
local function writeCentered(y, text, fg, bg)
    local x = math.floor((state.width - #text) / 2) + 1
    writeAt(x, y, text, fg, bg)
end

--[[
    Fills a horizontal line with a character
    @param y: Y coordinate
    @param char: Character to repeat
    @param fg: Foreground color
    @param bg: Background color (optional)
    @param startX: Starting X (default 1)
    @param endX: Ending X (default width)
--]]
local function drawHLine(y, char, fg, bg, startX, endX)
    startX = startX or 1
    endX = endX or state.width
    local line = string.rep(char or BORDER.H, endX - startX + 1)
    writeAt(startX, y, line, fg, bg)
end

--[[
    Fills a region with a background color
    @param x1: Left X
    @param y1: Top Y
    @param x2: Right X
    @param y2: Bottom Y
    @param bg: Background color
--]]
local function fillRect(x1, y1, x2, y2, bg)
    state.monitor.setBackgroundColor(bg)
    for y = y1, y2 do
        state.monitor.setCursorPos(x1, y)
        state.monitor.write(string.rep(" ", x2 - x1 + 1))
    end
end

--[[
    Draws a bordered box
    @param x1: Left X
    @param y1: Top Y
    @param x2: Right X
    @param y2: Bottom Y
    @param fg: Border color
    @param bg: Fill color
    @param title: Optional title for top border
--]]
local function drawBox(x1, y1, x2, y2, fg, bg, title)
    -- Fill background
    if bg then
        fillRect(x1, y1, x2, y2, bg)
    end

    -- Draw corners
    writeAt(x1, y1, BORDER.TL, fg, bg)
    writeAt(x2, y1, BORDER.TR, fg, bg)
    writeAt(x1, y2, BORDER.BL, fg, bg)
    writeAt(x2, y2, BORDER.BR, fg, bg)

    -- Draw horizontal lines
    local hLine = string.rep(BORDER.H, x2 - x1 - 1)
    writeAt(x1 + 1, y1, hLine, fg, bg)
    writeAt(x1 + 1, y2, hLine, fg, bg)

    -- Draw vertical lines
    for y = y1 + 1, y2 - 1 do
        writeAt(x1, y, BORDER.V, fg, bg)
        writeAt(x2, y, BORDER.V, fg, bg)
    end

    -- Draw title if provided
    if title then
        local titleText = " " .. title .. " "
        local titleX = math.floor((x2 - x1 - #titleText) / 2) + x1 + 1
        writeAt(titleX, y1, titleText, CONFIG.THEME.TITLE, bg)
    end
end

--[[
    Draws a progress bar
    @param x: Left X
    @param y: Y coordinate
    @param width: Bar width
    @param progress: Progress value (0-1)
    @param fg: Filled color
    @param bg: Empty color
--]]
local function drawProgressBar(x, y, width, progress, fg, bg)
    progress = clamp(progress, 0, 1)
    local filled = math.floor(width * progress)
    local empty = width - filled

    writeAt(x, y, string.rep("=", filled), fg)
    writeAt(x + filled, y, string.rep("-", empty), bg or colors.gray)
end

-- ============================================================================
-- SPLASH SCREEN
-- Professional startup display with diagnostics
-- ============================================================================

--[[
    Displays the startup splash screen with system diagnostics
--]]
local function showSplashScreen()
    state.monitor.setBackgroundColor(colors.black)
    state.monitor.clear()

    local centerY = math.floor(state.height / 2) - 4

    -- Draw decorative border
    drawBox(2, 2, state.width - 1, state.height - 1, CONFIG.THEME.BORDER_PRIMARY, colors.black)

    -- Title with decorative elements
    local titleArt = {
        "  __  __ _____ ____    ",
        " |  \\/  |_   _|  _ \\   ",
        " | |\\/| | | | | |_) |  ",
        " | |  | | | | |  _ <   ",
        " |_|  |_| |_| |_| \\_\\  ",
    }

    -- Draw ASCII art title or simple text based on width
    if state.width >= 50 then
        for i, line in ipairs(titleArt) do
            writeCentered(centerY + i - 1, line, CONFIG.THEME.INFO)
        end
        centerY = centerY + #titleArt + 1
    else
        writeCentered(centerY, "MTR", CONFIG.THEME.INFO)
        centerY = centerY + 2
    end

    writeCentered(centerY, "PREMIUM PIDS DISPLAY", CONFIG.THEME.TITLE)
    centerY = centerY + 1
    writeCentered(centerY, "Version 2.1.0", CONFIG.THEME.MUTED)
    centerY = centerY + 2

    -- Divider
    drawHLine(centerY, BORDER.H, CONFIG.THEME.BORDER_SECONDARY)
    centerY = centerY + 2

    -- System diagnostics
    writeCentered(centerY, "SYSTEM DIAGNOSTICS", CONFIG.THEME.LABEL)
    centerY = centerY + 2

    local diagnostics = {
        {"PIDS", state.isConnected and "Connected" or "Searching..."},
        {"Monitor", state.width .. "x" .. state.height},
        {"Text Scale", tostring(CONFIG.TEXT_SCALE)},
    }

    for _, diag in ipairs(diagnostics) do
        local label = padRight(diag[1] .. ":", 15)
        local value = diag[2]
        local x = math.floor((state.width - 25) / 2)
        writeAt(x, centerY, label, CONFIG.THEME.MUTED)
        writeAt(x + 15, centerY, value, CONFIG.THEME.SUCCESS)
        centerY = centerY + 1
    end

    centerY = centerY + 1

    -- Loading animation
    local loadingY = centerY + 1
    local startTime = os.clock()

    while os.clock() - startTime < CONFIG.SPLASH_DURATION do
        state.loadingFrame = (state.loadingFrame % #ICONS.LOADING) + 1
        local loadingText = "Initializing " .. ICONS.LOADING[state.loadingFrame]
        writeCentered(loadingY, padRight(loadingText, 30), CONFIG.THEME.INFO)
        sleep(0.1)
    end

    writeCentered(loadingY, padRight("Ready " .. ICONS.STATUS_OK, 30), CONFIG.THEME.SUCCESS)
    sleep(0.5)
end

-- ============================================================================
-- HEADER SECTION
-- Top portion of the display with title and clock
-- ============================================================================

--[[
    Draws the header section with title, clock, and status
    @return: Next available Y coordinate
--]]
local function drawHeader()
    local headerHeight = 3

    -- Fill header background
    fillRect(1, 1, state.width, headerHeight, CONFIG.THEME.HEADER_BG)

    -- Main title
    local title = ICONS.TRAIN .. " MTR ARRIVALS BOARD"
    writeAt(3, 2, title, CONFIG.THEME.HEADER_FG, CONFIG.THEME.HEADER_BG)

    -- Version badge
    local version = "v2.1"
    writeAt(3 + #title + 2, 2, version, CONFIG.THEME.SUBTITLE, CONFIG.THEME.HEADER_BG)

    -- Clock on the right
    local timeStr = ICONS.CLOCK .. " " .. getCurrentTime(true)
    writeAt(state.width - #timeStr - 2, 2, timeStr, CONFIG.THEME.HEADER_FG, CONFIG.THEME.HEADER_BG)

    -- Connection status indicator
    local statusIcon = state.isConnected and ICONS.STATUS_OK or ICONS.STATUS_WARN
    local statusColor = state.isConnected and CONFIG.THEME.SUCCESS or CONFIG.THEME.WARNING
    writeAt(state.width - #timeStr - #statusIcon - 5, 2, statusIcon, statusColor, CONFIG.THEME.HEADER_BG)

    -- Bottom border of header
    drawHLine(headerHeight + 1, BORDER.H, CONFIG.THEME.BORDER_PRIMARY, colors.black)

    return headerHeight + 2
end

-- ============================================================================
-- STATISTICS PANEL
-- Shows system statistics in a compact format
-- ============================================================================

--[[
    Draws the statistics panel
    @param y: Starting Y coordinate
    @return: Next available Y coordinate
--]]
local function drawStatsPanel(y)
    if not CONFIG.SHOW_STATISTICS then return y end

    -- PIDS-specific statistics
    local stats = {
        {label = "Platforms", value = #state.platformIds, icon = "P"},
        {label = "Max Slots", value = state.maxArrivals, icon = "#"},
        {label = "Services", value = #state.arrivals, icon = ">"},
    }

    -- Calculate layout
    local panelWidth = math.floor((state.width - 4) / #stats)
    local startX = 2

    -- Draw stats
    state.monitor.setBackgroundColor(colors.black)
    for i, stat in ipairs(stats) do
        local x = startX + (i - 1) * panelWidth
        local text = stat.icon .. " " .. stat.label .. ": " .. stat.value
        writeAt(x, y, truncate(text, panelWidth - 1), CONFIG.THEME.MUTED)
    end

    -- Divider
    drawHLine(y + 1, BORDER.H, CONFIG.THEME.BORDER_SECONDARY)

    return y + 2
end

-- ============================================================================
-- COLUMN HEADER
-- Table header for arrivals display
-- ============================================================================

--[[
    Draws the table column headers
    @param y: Y coordinate for headers
    @return: Next available Y coordinate
--]]
local function drawColumnHeaders(y)
    state.monitor.setBackgroundColor(colors.black)

    -- Calculate column positions based on screen width
    local col1 = 2                                      -- Route
    local col2 = col1 + CONFIG.MAX_ROUTE_WIDTH + 1      -- Platform
    local col3 = col2 + CONFIG.MAX_PLATFORM_WIDTH + 1   -- Destination
    local col4 = state.width - 10                       -- ETA

    -- Store column positions for later use
    state.columns = {
        route = col1,
        platform = col2,
        destination = col3,
        eta = col4,
    }

    -- Header background
    fillRect(1, y, state.width, y, colors.gray)

    -- Column labels
    writeAt(col1, y, padRight("SERVICE", CONFIG.MAX_ROUTE_WIDTH), CONFIG.THEME.LABEL, colors.gray)
    writeAt(col2, y, padRight("PLATFORM", CONFIG.MAX_PLATFORM_WIDTH), CONFIG.THEME.LABEL, colors.gray)
    writeAt(col3, y, padRight("DESTINATION", col4 - col3 - 1), CONFIG.THEME.LABEL, colors.gray)
    writeAt(col4, y, padLeft("ETA", 8), CONFIG.THEME.LABEL, colors.gray)

    return y + 1
end

-- ============================================================================
-- ARRIVAL ROWS
-- Individual arrival display with formatting
-- ============================================================================

--[[
    Gets the display color for a route
    @param arrival: Arrival data
    @return: CC color constant
--]]
local function getRouteColor(arrival)
    if not CONFIG.ENABLE_ROUTE_COLORS then
        return CONFIG.THEME.INFO
    end

    -- Try to get color from route data
    if arrival.routeColor then
        return hexToCC(arrival.routeColor)
    end

    -- Default color
    return CONFIG.THEME.INFO
end

--[[
    Determines if an arrival should show as delayed
    @param arrival: Arrival data
    @return: boolean
--]]
local function isDelayed(arrival)
    if not CONFIG.ENABLE_DELAY_DETECTION then
        return false
    end

    -- Check for deviation field
    if arrival.deviation then
        return math.abs(arrival.deviation) > (CONFIG.DELAY_THRESHOLD * 1000)
    end

    return false
end

--[[
    Gets the ETA color based on arrival time
    @param arrival: Arrival data
    @param eta: Formatted ETA string
    @return: CC color constant
--]]
local function getETAColor(arrival, eta)
    if isDelayed(arrival) then
        return CONFIG.THEME.DELAYED
    end

    if eta == "NOW" then
        -- Handle blinking effect
        if CONFIG.ENABLE_BLINK_EFFECT and state.blinkState then
            return CONFIG.THEME.NOW_BLINK_ON
        else
            return CONFIG.THEME.NOW_BLINK_OFF
        end
    end

    -- Parse minutes for color coding
    local minutes = tonumber(string.match(eta, "(%d+)%s*min"))
    if minutes then
        if minutes <= 2 then
            return CONFIG.THEME.WARNING
        elseif minutes <= 5 then
            return CONFIG.THEME.SUCCESS
        end
    end

    return CONFIG.THEME.VALUE
end

--[[
    Draws a single arrival row
    @param y: Y coordinate
    @param arrival: Arrival data
    @param index: Row index (for alternating colors)
--]]
local function drawArrivalRow(y, arrival, index)
    -- Alternating row background
    local rowBg = (index % 2 == 0) and CONFIG.THEME.ROW_EVEN or CONFIG.THEME.ROW_ODD
    fillRect(1, y, state.width, y, rowBg)

    -- Route/Service
    local routeDisplay = arrival.routeNumber or ""
    if routeDisplay == "" then
        routeDisplay = arrival.routeName or "---"
    end
    routeDisplay = truncate(routeDisplay, CONFIG.MAX_ROUTE_WIDTH)
    local routeColor = getRouteColor(arrival)
    writeAt(state.columns.route, y, padRight(routeDisplay, CONFIG.MAX_ROUTE_WIDTH), routeColor, rowBg)

    -- Platform
    local platformDisplay = arrival.platformName or arrival.stationName or "---"
    platformDisplay = truncate(platformDisplay, CONFIG.MAX_PLATFORM_WIDTH)
    writeAt(state.columns.platform, y, padRight(platformDisplay, CONFIG.MAX_PLATFORM_WIDTH), CONFIG.THEME.VALUE, rowBg)

    -- Destination
    local destDisplay = arrival.destination or "---"
    local destWidth = state.columns.eta - state.columns.destination - 2
    destDisplay = truncate(destDisplay, destWidth)
    writeAt(state.columns.destination, y, padRight(destDisplay, destWidth), CONFIG.THEME.VALUE, rowBg)

    -- ETA with delay indicator
    local eta = formatETA(arrival.arrival)
    local etaColor = getETAColor(arrival, eta)

    if isDelayed(arrival) then
        local delayText = ICONS.DELAYED
        writeAt(state.columns.eta - #delayText - 1, y, delayText, CONFIG.THEME.DELAYED, rowBg)
    end

    writeAt(state.columns.eta, y, padLeft(eta, 8), etaColor, rowBg)
end

--[[
    Draws the "no arrivals" placeholder
    @param y: Starting Y coordinate
--]]
local function drawNoArrivals(y)
    local centerY = y + math.floor((state.height - y - 4) / 2)

    writeCentered(centerY, ICONS.TRAIN, CONFIG.THEME.MUTED)
    writeCentered(centerY + 2, "No Scheduled Services", CONFIG.THEME.WARNING)
    writeCentered(centerY + 3, "Waiting for train data...", CONFIG.THEME.MUTED)
end

-- ============================================================================
-- ARRIVALS TABLE
-- Main arrivals display with pagination
-- ============================================================================

--[[
    Draws the arrivals table with pagination
    @param y: Starting Y coordinate
    @return: Next available Y coordinate
--]]
local function drawArrivalsTable(y)
    -- Calculate available space
    local availableHeight = state.height - y - 3  -- Leave room for footer
    state.arrivalsPerPage = math.max(1, availableHeight)

    -- Calculate pagination
    state.totalPages = math.max(1, math.ceil(#state.arrivals / state.arrivalsPerPage))
    state.currentPage = clamp(state.currentPage, 1, state.totalPages)

    -- No arrivals case
    if #state.arrivals == 0 then
        drawNoArrivals(y)
        return state.height - 2
    end

    -- Calculate which arrivals to show
    local startIndex = (state.currentPage - 1) * state.arrivalsPerPage + 1
    local endIndex = math.min(startIndex + state.arrivalsPerPage - 1, #state.arrivals)

    -- Draw arrivals
    local currentY = y
    local rowIndex = 1

    for i = startIndex, endIndex do
        if currentY > state.height - 3 then break end
        drawArrivalRow(currentY, state.arrivals[i], rowIndex)
        currentY = currentY + 1
        rowIndex = rowIndex + 1
    end

    return currentY
end

-- ============================================================================
-- FOOTER SECTION
-- Status bar with pagination, countdown, and info
-- ============================================================================

--[[
    Draws the footer section
--]]
local function drawFooter()
    local footerY = state.height - 1

    -- Top border
    drawHLine(footerY - 1, BORDER.H, CONFIG.THEME.BORDER_SECONDARY)

    -- Footer background
    fillRect(1, footerY, state.width, state.height, colors.black)

    -- Left side: Last update time
    local updateStr = ICONS.REFRESH .. " " .. getCurrentTime(true)
    writeAt(2, footerY, updateStr, CONFIG.THEME.MUTED)

    -- Center: Pagination info
    if state.totalPages > 1 then
        local pageStr = "Page " .. state.currentPage .. "/" .. state.totalPages
        if CONFIG.ENABLE_AUTO_SCROLL then
            pageStr = pageStr .. " [AUTO]"
        end
        writeCentered(footerY, pageStr, CONFIG.THEME.MUTED)
    end

    -- Right side: Countdown to refresh
    if CONFIG.SHOW_COUNTDOWN then
        local remaining = math.max(0, math.floor(state.nextRefresh - os.clock()))
        local countdownStr = "Next: " .. formatCountdown(remaining)
        writeAt(state.width - #countdownStr - 1, footerY, countdownStr, CONFIG.THEME.MUTED)
    end

    -- Bottom line: More arrivals indicator
    if #state.arrivals > state.arrivalsPerPage then
        local moreCount = #state.arrivals - (state.currentPage * state.arrivalsPerPage)
        if moreCount > 0 then
            local moreStr = "+" .. moreCount .. " more services"
            writeAt(2, state.height, moreStr, CONFIG.THEME.INFO)
        end
    end

    -- Error indicator if present
    if state.lastError then
        local errStr = truncate(ICONS.STATUS_WARN .. " " .. state.lastError, state.width - 4)
        writeAt(2, state.height, errStr, CONFIG.THEME.ERROR)
    end
end

-- ============================================================================
-- DATA FETCHING
-- Functions to retrieve data from MTR PIDS peripheral
-- ============================================================================

--[[
    Fetches arrivals from the PIDS peripheral
    The PIDS already has platform IDs configured via the MTR dashboard.
    We simply call getArrivals() to get all arrivals for those platforms.
    @return: Sorted table of arrival data
--]]
local function fetchArrivals()
    local allArrivals = {}

    -- PIDS getArrivals() returns arrivals for all configured platforms
    local ok, arrivals = pcall(function()
        return state.pids.getArrivals()
    end)

    if ok and arrivals then
        for _, arr in ipairs(arrivals) do
            table.insert(allArrivals, arr)
        end
    end

    -- Sort by arrival time
    table.sort(allArrivals, function(a, b)
        return (a.arrival or math.huge) < (b.arrival or math.huge)
    end)

    return allArrivals
end

--[[
    Refreshes all data from the PIDS peripheral
--]]
local function refreshData()
    state.lastError = nil

    local ok, err = pcall(function()
        -- Fetch platform IDs configured on this PIDS
        local platformIds = state.pids.getPlatformIds()
        if platformIds then
            state.platformIds = platformIds
        else
            state.platformIds = {}
        end

        -- Fetch max arrivals this PIDS can display
        local maxArrivals = state.pids.getMaxArrivals()
        if maxArrivals then
            state.maxArrivals = maxArrivals
        else
            state.maxArrivals = 0
        end

        -- Fetch arrivals
        state.arrivals = fetchArrivals()

        state.dataAvailable = true
        state.isConnected = true
    end)

    if not ok then
        state.lastError = tostring(err)
        state.isConnected = false
    end

    state.lastRefresh = os.clock()
    state.nextRefresh = state.lastRefresh + CONFIG.REFRESH_INTERVAL
end

-- ============================================================================
-- MAIN DISPLAY
-- Orchestrates the complete display rendering
-- ============================================================================

--[[
    Renders the complete display
--]]
local function renderDisplay()
    state.monitor.setBackgroundColor(colors.black)
    state.monitor.clear()

    local y = 1

    -- Draw sections
    y = drawHeader()
    y = drawStatsPanel(y)
    y = drawColumnHeaders(y)
    y = drawArrivalsTable(y)
    drawFooter()
end

-- ============================================================================
-- AUTO-SCROLL HANDLER
-- Manages automatic page cycling
-- ============================================================================

--[[
    Advances to the next page (with wrap-around)
--]]
local function nextPage()
    if state.totalPages <= 1 then return end

    state.currentPage = state.currentPage + 1
    if state.currentPage > state.totalPages then
        state.currentPage = 1
    end
end

-- ============================================================================
-- BLINK HANDLER
-- Manages the blinking effect for imminent arrivals
-- ============================================================================

--[[
    Toggles the blink state
--]]
local function toggleBlink()
    state.blinkState = not state.blinkState
end

-- ============================================================================
-- INITIALIZATION
-- Peripheral detection and setup
-- ============================================================================

--[[
    Initializes the display system
    @return: boolean success
--]]
local function initialize()
    print("MTR Premium PIDS Display v2.1.0")
    print("Initializing...")

    -- Find PIDS peripheral
    state.pids = peripheral.find(CONFIG.PIDS_NAME)
    if not state.pids then
        printError("ERROR: No " .. CONFIG.PIDS_NAME .. " found!")
        printError("Please attach an MTR PIDS peripheral.")
        return false
    end
    print("  [OK] PIDS connected")
    state.isConnected = true

    -- Find monitor
    state.monitor = peripheral.find("monitor")
    if not state.monitor then
        printError("ERROR: No monitor found!")
        printError("Please attach a monitor peripheral.")
        return false
    end
    print("  [OK] Monitor connected")

    -- Configure monitor
    state.monitor.setTextScale(CONFIG.TEXT_SCALE)
    state.width, state.height = state.monitor.getSize()
    print("  [OK] Display: " .. state.width .. "x" .. state.height)

    -- Initial data fetch
    refreshData()
    print("  [OK] Initial data loaded")
    print("  [OK] Platforms: " .. #state.platformIds)
    print("  [OK] Max arrivals: " .. state.maxArrivals)
    print("")
    print("Display active. Press Ctrl+T to stop.")

    return true
end

-- ============================================================================
-- MAIN EVENT LOOP
-- Handles timing and event processing
-- ============================================================================

--[[
    Main program loop
--]]
local function main()
    -- Initialize
    if not initialize() then
        return
    end

    -- Show splash screen
    showSplashScreen()

    -- Timing trackers
    local lastBlink = os.clock()
    local lastScroll = os.clock()
    local lastRefresh = os.clock()

    -- Main loop
    while true do
        local currentTime = os.clock()

        -- Handle refresh
        if currentTime - lastRefresh >= CONFIG.REFRESH_INTERVAL then
            refreshData()
            lastRefresh = currentTime
        end

        -- Handle blink
        if CONFIG.ENABLE_BLINK_EFFECT then
            if currentTime - lastBlink >= CONFIG.BLINK_INTERVAL then
                toggleBlink()
                lastBlink = currentTime
            end
        end

        -- Handle auto-scroll
        if CONFIG.ENABLE_AUTO_SCROLL and state.totalPages > 1 then
            if currentTime - lastScroll >= CONFIG.SCROLL_INTERVAL then
                nextPage()
                lastScroll = currentTime
            end
        end

        -- Render display
        local ok, err = pcall(renderDisplay)
        if not ok then
            state.lastError = "Render error: " .. tostring(err)
        end

        -- Small sleep to prevent excessive CPU usage
        sleep(0.1)
    end
end

-- ============================================================================
-- ERROR HANDLER WRAPPER
-- Graceful error handling for the main program
-- ============================================================================

local function run()
    local ok, err = pcall(main)

    if not ok then
        -- Attempt to display error on monitor
        if state.monitor then
            pcall(function()
                state.monitor.setBackgroundColor(colors.black)
                state.monitor.clear()
                state.monitor.setTextColor(colors.red)
                state.monitor.setCursorPos(1, 1)
                state.monitor.write("FATAL ERROR")
                state.monitor.setCursorPos(1, 3)
                state.monitor.setTextColor(colors.white)
                state.monitor.write(tostring(err):sub(1, state.width))
            end)
        end

        printError("FATAL ERROR: " .. tostring(err))
    end
end

-- ============================================================================
-- PROGRAM ENTRY POINT
-- ============================================================================

run()
