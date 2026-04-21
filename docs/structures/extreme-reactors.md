# Extreme Reactors 2 — Multiblock Reference

Mod: `bigreactors` (ExtremeReactors2, namespace `bigreactors`)
Companion: ZeroCore2

---

## Reactor (Fission)

### Tiers

| Tier        | Casing block                        | Fuel rod                              | Glass                              | Control rod                              |
|-------------|-------------------------------------|---------------------------------------|------------------------------------|------------------------------------------|
| Basic       | `bigreactors:basic_reactorcasing`   | `bigreactors:basic_reactorfuelrod`    | `bigreactors:basic_reactorglass`   | `bigreactors:basic_reactorcontrolrod`    |
| Reinforced  | `bigreactors:reinforced_reactorcasing` | `bigreactors:reinforced_reactorfuelrod` | `bigreactors:reinforced_reactorglass` | `bigreactors:reinforced_reactorcontrolrod` |

### Structure rules

- **Minimum size**: 3×3×3 (exterior). Practical minimum is 5×5×5 for any interior.
- **Maximum size**: 32×32×32 (exterior).
- **All 12 edges** must be solid casing.
- **Top and bottom faces** must be solid casing (no glass).
- **Side faces**: interior (non-edge) blocks may be glass or casing. Glass cannot be on edge rows/columns.
- **Control rods** go on the TOP face directly above each fuel rod column. They replace casing on the top face.

### Interior blocks

| Block                                      | Purpose                                         | Placement                        |
|--------------------------------------------|-------------------------------------------------|----------------------------------|
| `bigreactors:reinforced_reactorfuelrod`    | Holds fuel; generates heat                      | Full-height columns (Y interior) |
| `bigreactors:graphite_block`               | Moderator — converts fast neutrons, boosts RF   | Fill non-fuel interior positions |
| `bigreactors:ludicrite_block`              | Better moderator (end-game)                     | Same as graphite                 |
| `bigreactors:cyanite_block`               | Moderator (cyanite)                             | Same as graphite                 |

### Accessory face blocks (replace casing on any non-edge face)

| Block                                                  | Purpose                       |
|--------------------------------------------------------|-------------------------------|
| `bigreactors:reinforced_reactorcontroller`             | Required — activate/monitor   |
| `bigreactors:reinforced_reactorpowertapfe_active`      | RF power output (active push) |
| `bigreactors:reinforced_reactorpowertapfe_passive`     | RF power output (passive pull)|
| `bigreactors:reinforced_reactorsolidaccessport`        | Insert/extract fuel/waste     |
| `bigreactors:reinforced_reactorfluidaccessport`        | Fluid fuel/coolant port       |
| `bigreactors:reinforced_reactorredstoneport`           | Redstone control              |
| `bigreactors:reinforced_reactorcomputerport`           | CC/OC2 computer access        |
| `bigreactors:reinforced_reactorchargingportfe`         | Charge items in inventory     |

### Fuel rod layout patterns (interior cross-section)

Interior is (exterior - 2) in each dimension. For a 7×7×7 reactor the interior cross-section is 5×5.

**+ pattern (9 rods, balanced)** — center row + center column:
```
. . F . .
. . F . .
F F F F F
. . F . .
. . F . .
```
Graphite fills all `.` positions. 9 control rods on top face above each rod.

**2×2 grid (4 rods)** — sparse, good for early game:
```
. . . . .
. F . F .
. . . . .
. F . F .
. . . . .
```

### Known examples built

| Location (world W3)         | Dimensions | Pattern  | Notes                                 |
|-----------------------------|------------|----------|---------------------------------------|
| (-1722, 63, -1148) center   | 7×7×7      | + (9 rod)| Reinforced, graphite moderator, glass sides |

---

## Turbine

TODO — build example first.

Block names follow same `basic_turbine*` / `reinforced_turbine*` pattern.
