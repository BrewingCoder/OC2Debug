# Mekanism — Induction Matrix (Energy Storage)

Mod: `mekanism`
Source: https://wiki.aidancbrady.com/wiki/Induction_Matrix

---

## What it is

Endgame energy storage multiblock. Capacity = sum of all Induction Cells inside. 
Max I/O rate = sum of all Induction Providers inside. At least 1 of each is required to operate.

---

## Structure rules

- **Minimum exterior**: 3×3×3 (interior 1×1×1 — fits 1 block, needs at least 1 cell + 1 provider so minimum useful is 4×3×3)
- **Maximum interior**: 16×16×16 (exterior 18×18×18)
- Must be a rectangular prism (any aspect ratio)
- Tiers do NOT need to match — mix freely

### Placement rules by position

| Position              | Valid blocks                                                              |
|-----------------------|---------------------------------------------------------------------------|
| **Edges** (12 lines)  | `mekanism:induction_casing` only                                          |
| **Face interiors**    | `mekanism:induction_casing`, `mekanism:induction_port`, reactor glass, structural glass |
| **Interior**          | `mekanism:induction_cell` (any tier), `mekanism:induction_provider` (any tier), air |

---

## All blocks

### Shell

| Block                        | Purpose                                      |
|------------------------------|----------------------------------------------|
| `mekanism:induction_casing`  | Structural — required on all edges, valid everywhere on shell |
| `mekanism:induction_port`    | Energy I/O — place on any non-edge face position |

### Interior — Cells (storage capacity)

| Block                               | Capacity     |
|-------------------------------------|--------------|
| `mekanism:basic_induction_cell`     | 8 GFE        |
| `mekanism:advanced_induction_cell`  | 64 GFE       |
| `mekanism:elite_induction_cell`     | 512 GFE      |
| `mekanism:ultimate_induction_cell`  | 4 TFE        |

### Interior — Providers (I/O throughput)

| Block                                   | Rate          |
|-----------------------------------------|---------------|
| `mekanism:basic_induction_provider`     | 256 MFE/t     |
| `mekanism:advanced_induction_provider`  | 2 GFE/t       |
| `mekanism:elite_induction_provider`     | 16 GFE/t      |
| `mekanism:ultimate_induction_provider`  | 128 GFE/t     |

---

## Port configuration

Ports are placed as casing during the build, then configured **after** the matrix forms:
- Right-click a port with a **Configurator** to toggle Input / Output
- Need at least 1 input port and 1 output port for energy to flow
- Matrix "sparks with redstone particles" when the last block is placed and the structure validates

---

## Sizing guide

| Interior dims | Exterior dims | Interior slots | Max cells (all ultimate) | Max storage  |
|---------------|---------------|----------------|--------------------------|--------------|
| 1×1×2         | 3×3×4         | 2              | 1 cell + 1 provider      | 4 TFE        |
| 2×2×2         | 4×4×4         | 8              | 7 cells + 1 provider     | 28 TFE       |
| 4×4×4         | 6×6×6         | 64             | 63 cells + 1 provider    | 252 TFE      |
| 8×8×8         | 10×10×10      | 512            | 511 cells + 1 provider   | ~2 PFE       |
| 16×16×16      | 18×18×18      | 4096           | max config               | ~16 PFE      |

---

## Build approach (for automated builds)

1. Fill exterior shell with `mekanism:induction_casing` (entire bounding box, then hollow interior)
2. Fill interior with desired mix of cells and providers
3. Replace desired face (non-edge) positions with `mekanism:induction_port`
4. Matrix validates on last block placed — redstone particle spark confirms
5. Configure ports with Configurator (input/output)

---

## Known examples (world W3)

| Location (exterior corners)                        | Dims  | Interior          | Notes                  |
|----------------------------------------------------|-------|-------------------|------------------------|
| (-1725,62,-1161) to (-1719,66,-1155)               | 7×5×7 | 5×3×5 = 75 slots  | Shell complete, unfilled |
