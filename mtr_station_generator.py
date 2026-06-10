#!/usr/bin/env python3
"""
MTR Station Structure Generator v3
Open Elevated Station - no PSDs, just railings and canopy roof.
Uses MTR mod blocks for authentic look.
"""

import struct
import gzip
import io

# ============================================================================
# NBT FORMAT IMPLEMENTATION
# ============================================================================

class NBTWriter:
    TAG_END = 0
    TAG_BYTE = 1
    TAG_SHORT = 2
    TAG_INT = 3
    TAG_LONG = 4
    TAG_FLOAT = 5
    TAG_DOUBLE = 6
    TAG_BYTE_ARRAY = 7
    TAG_STRING = 8
    TAG_LIST = 9
    TAG_COMPOUND = 10
    TAG_INT_ARRAY = 11
    TAG_LONG_ARRAY = 12

    def __init__(self):
        self.buffer = io.BytesIO()

    def write_byte(self, value):
        self.buffer.write(struct.pack('>b', value))

    def write_ubyte(self, value):
        self.buffer.write(struct.pack('>B', value))

    def write_short(self, value):
        self.buffer.write(struct.pack('>h', value))

    def write_int(self, value):
        self.buffer.write(struct.pack('>i', value))

    def write_long(self, value):
        self.buffer.write(struct.pack('>q', value))

    def write_string(self, value):
        encoded = value.encode('utf-8')
        self.write_short(len(encoded))
        self.buffer.write(encoded)

    def write_tag_header(self, tag_type, name):
        self.write_ubyte(tag_type)
        self.write_string(name)

    def write_compound_tag(self, name, data):
        self.write_tag_header(self.TAG_COMPOUND, name)
        self._write_compound_payload(data)

    def _write_compound_payload(self, data):
        for key, value in data.items():
            self._write_named_tag(key, value)
        self.write_ubyte(self.TAG_END)

    def _write_named_tag(self, name, value):
        if isinstance(value, NBTCompound):
            self.write_tag_header(self.TAG_COMPOUND, name)
            self._write_compound_payload(value.data)
        elif isinstance(value, NBTList):
            self.write_tag_header(self.TAG_LIST, name)
            self._write_list_payload(value)
        elif isinstance(value, NBTString):
            self.write_tag_header(self.TAG_STRING, name)
            self.write_string(value.value)
        elif isinstance(value, NBTInt):
            self.write_tag_header(self.TAG_INT, name)
            self.write_int(value.value)

    def _write_list_payload(self, nbt_list):
        if not nbt_list.items:
            self.write_ubyte(self.TAG_END)
            self.write_int(0)
            return

        first = nbt_list.items[0]
        if isinstance(first, NBTCompound):
            self.write_ubyte(self.TAG_COMPOUND)
        elif isinstance(first, NBTInt):
            self.write_ubyte(self.TAG_INT)
        elif isinstance(first, NBTString):
            self.write_ubyte(self.TAG_STRING)

        self.write_int(len(nbt_list.items))

        for item in nbt_list.items:
            if isinstance(item, NBTCompound):
                self._write_compound_payload(item.data)
            elif isinstance(item, NBTInt):
                self.write_int(item.value)
            elif isinstance(item, NBTString):
                self.write_string(item.value)

    def get_bytes(self):
        return self.buffer.getvalue()


class NBTCompound:
    def __init__(self, data=None):
        self.data = data or {}
    def __setitem__(self, key, value):
        self.data[key] = value
    def __getitem__(self, key):
        return self.data[key]

class NBTList:
    def __init__(self, items=None):
        self.items = items or []
    def append(self, item):
        self.items.append(item)

class NBTString:
    def __init__(self, value):
        self.value = value

class NBTInt:
    def __init__(self, value):
        self.value = value


# ============================================================================
# CONFIGURATION - Open Elevated Station
# ============================================================================

STATION_CONFIG = {
    "length": 32,           # Platform length (Z axis)
    "platform_width": 7,    # Island platform width (X axis)
    "track_width": 3,       # Track area width on each side (MTR needs 3 blocks)
    "height": 6,            # Floor to canopy (shorter for open feel)
    "canopy_overhang": 2,   # How far canopy extends over tracks
    "name": "mtr_elevated_station"
}

# Block palette - MTR mod blocks + vanilla for open elevated station
BLOCKS = {
    # Base structure
    "air": "minecraft:air",
    "support_beam": "minecraft:polished_deepslate",
    "floor_base": "minecraft:smooth_stone",

    # MTR Platform
    "platform": "mtr:platform",
    "platform_slab": "mtr:platform_slab",

    # MTR Canopy/Ceiling
    "canopy": "mtr:ceiling",
    "canopy_light": "mtr:ceiling_light",

    # MTR Pillars
    "pillar": "mtr:station_pole",

    # Railings (vanilla iron bars work well for open stations)
    "railing": "minecraft:iron_bars",

    # Platform edge
    "yellow_line": "minecraft:yellow_concrete",

    # Track bed
    "track_bed": "minecraft:gravel",

    # Support structure
    "beam": "minecraft:polished_deepslate",
    "support_pillar": "minecraft:polished_blackstone_bricks",

    # Decorative
    "bench_base": "minecraft:polished_andesite_slab",
    "light_post": "minecraft:chain",
    "lantern": "minecraft:lantern",
}


# ============================================================================
# STRUCTURE GENERATOR
# ============================================================================

class StationGenerator:
    def __init__(self, config):
        self.length = config["length"]
        self.platform_width = config["platform_width"]
        self.track_width = config["track_width"]
        self.height = config["height"]
        self.canopy_overhang = config["canopy_overhang"]
        self.name = config["name"]

        # Layout: [overhang] [track(3)] [edge(1)] [platform(7)] [edge(1)] [track(3)] [overhang]
        self.total_width = self.canopy_overhang + self.track_width + 1 + self.platform_width + 1 + self.track_width + self.canopy_overhang

        self.blocks = {}
        self.palette = []
        self.palette_map = {}

    def add_block(self, x, y, z, block_id, properties=None):
        if properties is None:
            properties = {}
        key = (block_id, tuple(sorted(properties.items())))
        if key not in self.palette_map:
            self.palette_map[key] = len(self.palette)
            self.palette.append((block_id, properties))
        self.blocks[(x, y, z)] = self.palette_map[key]

    def generate(self):
        print(f"Generating open elevated station: {self.total_width}x{self.height}x{self.length}")

        self._generate_support_structure()
        self._generate_platform()
        self._generate_railings()
        self._generate_canopy()
        self._generate_pillars()
        self._generate_benches()
        self._generate_lighting()

        print(f"Total blocks placed: {len(self.blocks)}")
        print(f"Palette size: {len(self.palette)}")

    def _generate_support_structure(self):
        """Generate the elevated support beams under the platform."""
        platform_start = self.canopy_overhang + self.track_width
        platform_end = platform_start + 1 + self.platform_width + 1

        # Support pillars at corners and intervals
        for z in [0, self.length // 2, self.length - 1]:
            # Left support
            self.add_block(platform_start, 0, z, BLOCKS["support_pillar"])
            # Right support
            self.add_block(platform_end, 0, z, BLOCKS["support_pillar"])

    def _generate_platform(self):
        """Generate the island platform."""
        platform_start = self.canopy_overhang + self.track_width + 1
        platform_end = platform_start + self.platform_width

        for z in range(self.length):
            # Yellow safety line - left
            self.add_block(platform_start - 1, 1, z, BLOCKS["yellow_line"])

            # Main platform surface
            for x in range(platform_start, platform_end):
                self.add_block(x, 1, z, BLOCKS["platform"])

            # Yellow safety line - right
            self.add_block(platform_end, 1, z, BLOCKS["yellow_line"])

        # Track beds (visual only, below platform level)
        left_track = self.canopy_overhang
        right_track = self.canopy_overhang + self.track_width + 1 + self.platform_width + 1 + 1

        for z in range(self.length):
            for i in range(self.track_width):
                self.add_block(left_track + i, 0, z, BLOCKS["track_bed"])
                self.add_block(right_track + i, 0, z, BLOCKS["track_bed"])

    def _generate_railings(self):
        """Generate safety railings along platform edges."""
        # Railing positions - on the yellow lines
        left_railing = self.canopy_overhang + self.track_width
        right_railing = self.canopy_overhang + self.track_width + 1 + self.platform_width + 1

        for z in range(self.length):
            # Railings at height 2 (waist height above platform)
            self.add_block(left_railing, 2, z, BLOCKS["railing"])
            self.add_block(right_railing, 2, z, BLOCKS["railing"])

        # End railings (close off the ends)
        for x in range(left_railing, right_railing + 1):
            self.add_block(x, 2, 0, BLOCKS["railing"])
            self.add_block(x, 2, self.length - 1, BLOCKS["railing"])

    def _generate_canopy(self):
        """Generate the canopy roof with overhang."""
        canopy_y = self.height - 1

        for x in range(self.total_width):
            for z in range(self.length):
                # Determine if this is a light position
                center = self.total_width // 2
                is_center_strip = abs(x - center) <= 1
                is_light_interval = z % 4 == 2

                if is_center_strip and is_light_interval:
                    self.add_block(x, canopy_y, z, BLOCKS["canopy_light"])
                else:
                    self.add_block(x, canopy_y, z, BLOCKS["canopy"])

    def _generate_pillars(self):
        """Generate support pillars from platform to canopy."""
        platform_center = self.total_width // 2

        # Main center pillars
        for z in range(4, self.length - 2, 8):
            for y in range(2, self.height - 1):
                self.add_block(platform_center, y, z, BLOCKS["pillar"])

        # Edge support pillars (at platform edges, less frequent)
        left_edge = self.canopy_overhang + self.track_width + 1
        right_edge = left_edge + self.platform_width

        for z in range(0, self.length, 16):
            for y in range(2, self.height - 1):
                self.add_block(left_edge, y, z, BLOCKS["pillar"])
                self.add_block(right_edge, y, z, BLOCKS["pillar"])

    def _generate_benches(self):
        """Generate bench seating along the platform."""
        platform_center = self.total_width // 2

        # Benches near pillars, alternating sides
        for i, z in enumerate(range(6, self.length - 4, 8)):
            offset = -2 if i % 2 == 0 else 2
            # Simple bench (slab)
            self.add_block(platform_center + offset, 2, z, BLOCKS["bench_base"])
            self.add_block(platform_center + offset, 2, z + 1, BLOCKS["bench_base"])

    def _generate_lighting(self):
        """Generate hanging lights/lanterns."""
        platform_center = self.total_width // 2

        # Lanterns hanging from canopy at intervals
        for z in range(2, self.length - 1, 6):
            # Chain + lantern on alternating sides
            for offset in [-3, 3]:
                x = platform_center + offset
                self.add_block(x, self.height - 2, z, BLOCKS["light_post"])
                self.add_block(x, self.height - 3, z, BLOCKS["lantern"])

    def to_nbt(self):
        palette_nbt = NBTList()
        for block_id, properties in self.palette:
            block_compound = NBTCompound({"Name": NBTString(block_id)})
            if properties:
                props = NBTCompound({k: NBTString(v) for k, v in properties.items()})
                block_compound["Properties"] = props
            palette_nbt.append(block_compound)

        blocks_nbt = NBTList()
        for (x, y, z), state in self.blocks.items():
            pos_list = NBTList([NBTInt(x), NBTInt(y), NBTInt(z)])
            block_compound = NBTCompound({
                "pos": pos_list,
                "state": NBTInt(state)
            })
            blocks_nbt.append(block_compound)

        structure = NBTCompound({
            "size": NBTList([NBTInt(self.total_width), NBTInt(self.height), NBTInt(self.length)]),
            "palette": palette_nbt,
            "blocks": blocks_nbt,
            "entities": NBTList(),
            "DataVersion": NBTInt(3700)
        })

        return structure

    def save(self, filename):
        structure = self.to_nbt()
        writer = NBTWriter()
        writer.write_compound_tag("", structure.data)
        raw_data = writer.get_bytes()

        with gzip.open(filename, 'wb') as f:
            f.write(raw_data)

        print(f"Saved: {filename} ({len(raw_data)} bytes)")


# ============================================================================
# MAIN
# ============================================================================

def main():
    print("=" * 60)
    print("MTR Open Elevated Station Generator v3")
    print("=" * 60)

    generator = StationGenerator(STATION_CONFIG)
    generator.generate()

    output_file = f"{STATION_CONFIG['name']}.nbt"
    generator.save(output_file)

    print("\n" + "=" * 60)
    print("Station Features:")
    print(f"  - Open elevated design (no PSDs)")
    print(f"  - Iron bar safety railings")
    print(f"  - MTR canopy roof with lighting")
    print(f"  - Hanging lanterns for ambiance")
    print(f"  - Bench seating")
    print(f"  - Central + edge support pillars")
    print(f"  Dimensions: {generator.total_width}W x {generator.height}H x {generator.length}L")
    print(f"  Track clearance: 3 blocks each side")
    print("=" * 60)


if __name__ == "__main__":
    main()
