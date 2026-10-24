# Autoturgy

An original magical automation addon for Minecraft **1.21.1**, NeoForge
**21.1.262**, Java **21** and Thaumaturge **1.0.4**. This is a foundation preview
of a broader recreation; it does not claim complete historical feature parity.

Inspired by [Automagy 0.28.2 by Tuhljin](https://www.curseforge.com/minecraft/mc-mods/automagy).
Autoturgy is an original unofficial recreation; no original code or assets are reused.

## Current devices

- Ordinary and magical hourglasses, analog inverter.
- Surface-mounted normal, amplified, dim, dense and resonating redcrystals.
- Crystalline and entity eyes, remote comparator, vis reader, tally box,
  Signum Entia, editable ghost-sample runic filters and two tally lenses.
- Original textures, models, crafting recipes and searchable field notes.

All listed devices perform their described function. Future machines are not
registered as decorative substitutes. Storage, Inventarium, mirrors, fluids,
golems, constructs, experience and equipment belong to subsequent development
phases. Mercurial redcrystal awaits the mirror route implementation.

## Using the workshop

Craft **Autoturgy Field Notes** with a book, redstone and an amethyst shard in
a vertical column. Right-click the book to search its original guide text.
Scroll long pages. Standard recipe viewers display every crafting recipe.

Place a device with its marked front facing you. Its output is the front and
clock/inverter input is the rear. Right-click empty-handed to open controls.
Clicking a clock also starts it; sneak-click starts it without opening controls.
Its menu has explicit Start and Cancel buttons. Use a comparator for progress.

Ordinary clocks run 5–60 seconds in five-second steps. Magic clocks support
whole seconds up to one hour, five output modes, four signal trigger modes,
repeat, restart and falling-edge cancellation. Ignore mode ignores redstone
and waits for manual activation. Constant variants stop output when stopped.
Loaded ticks advance time; unloading pauses it, with elapsed state preserved.

Place redcrystals against a sturdy face. They route along that surface and
around the corners of a shared supporting block. Configure each world-direction
face as Off, Input, Output or Both. Normal edges lose one strength; wrapped
corners lose two. Amplified crystals raise any positive incoming value to 31;
dim caps and dense thresholds are adjustable. Resonating crystals transmit
straight through solids up to 15 blocks without distance loss. Vanilla outputs
clamp to 15, while the graph keeps extended strength. Graphs are bounded at
512 crystals and source-free cycles turn off.

Sneak-use a crystalline eye on a target face, then use it on a reader. Links
copy without consuming eyes. Readers accept only loaded targets within 20
blocks in the same dimension. The selected side controls capability access.
An entity eye binds any entity type, including vehicles, or a player identity; sneak-use it in air to
bind yourself, then apply it to Signum Entia.

The vis reader uses public Thaumaturge contracts for jars, crucibles, nodes,
infusion demand, wand-bearing workbenches and recharge pedestals. Node extrema
compare current/base fullness, including depleted base aspects. Wand automatic
capacity comes from its actual parts and measurements use centivis. Aspect
selections persist as stable IDs; the menu names those IDs. Count mode emits
the number of aspect types directly, ignores capacity/selection and preserves
extended values for redcrystal. Infusion demand and unknown storage types need
a manual capacity for normalized amount measurements. Invalid targets emit zero
even with inversion enabled. Reads never drain magic.

Open a runic filter to edit ghost samples. Cursor items are never consumed.
Left-click with an empty cursor clears; right-click with an empty cursor raises
quantity. Shift-click an inventory stack to add a sample. Toggle blacklist to
convert while preserving settings. Components, damage, shared item tags and
matching mod are separate options. Sneak-use with a second filter in the off
hand to copy everything. Apply a filter to a tally without consuming it.

Tally reads extractable real items from the selected face. Minimum and
full-strength thresholds control its vanilla output. Chains use a 16-hop cycle
guard. Unbound lenses count loose items near the target. Yield lenses sample
normal and silk-touch alternatives with an appropriate tool, without breaking
the block. Predictions use an isolated deterministic loot RNG. Dynamic and
random loot is sampled in those two contexts rather than exhaustively enumerated.
Signum counts entities in a box shaped by its six immediately adjacent
faces, with type/player and adult/child selection. The default box is 11 blocks
wide on each axis. Blocking one adjacent side suppresses that direction and
extends the opposite side to ten blocks; blocking both ends suppresses that
axis. Adjacent glass and replaceable blocks are ignored. Walls farther away do
not alter the box. A modern half-span control can reduce the default five.
Age options apply to living entities; vehicles and other nonliving entities
remain countable in every age mode. The player-identity control switches between
the bound UUID and any player. Raw counts up to 255 remain available to
redcrystal while vanilla output uses the configured full-strength threshold.

## Build and verification

Place legally obtained Thaumaturge 1.0.4, Curios 9.5.1 and Lithostitched 1.8.1
JARs in the wrapper's `dependencies/` directory. Third-party binaries are not
distributed or included inside this repository's release JAR.

```powershell
.\gradlew.bat build check
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient
```

Client, server and GameTest worlds live in the wrapper's `validation/` folder.
GameTests use a separate source set and are excluded from the release JAR.
Their XML report is written to `../validation/phase1/gametest.xml`; JUnit XML is
in `build/test-results/test/`. Test results establish their covered behaviors,
not full runtime or pack compatibility. Client and full-pack acceptance are
tracked separately in the outer feature matrix.

Current recipes are deliberately disclosed interim material recipes. Host
research and magical recipe costs are pending the recipe integration phase.
Unknown historical timing constants use the documented modern defaults above.

Source namespace `com.sanhiruzu.autoturgy` is first-party. The implementation,
art and prose are original; no historical mod classes or assets are included.
