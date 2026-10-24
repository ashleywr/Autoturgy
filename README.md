# Autoturgy

An original magical automation addon for Minecraft **1.21.1**, NeoForge
**21.1.262**, Java **21** and Thaumaturge **1.0.4**. This is a development preview
of a broader recreation; it does not claim complete historical feature parity.

Inspired by [Automagy 0.28.2 by Tuhljin](https://www.curseforge.com/minecraft/mc-mods/automagy).
Autoturgy is an original unofficial recreation; no original code or assets are reused.

## Current devices

- Ordinary and magical hourglasses, analog inverter.
- Surface-mounted normal, amplified, dim, dense and resonating redcrystals.
- Crystalline and entity eyes, remote comparator, vis reader, tally box,
  Signum Entia, editable ghost-sample runic filters and two tally lenses.
- Hungry/finical maws, greedy/eager/tenacious physical chests.
- Inventarium core, paid scribes, mixed-eye oculus, dedicated memory,
  enhanced bookshelves and placed requisition tomes.
- Melding/dispersing mirror adapters, hungry hand mirror and mercurial redcrystal.
- Thirsty tank, nine physical face glyphs, milk and two soup fluids.
- Fueled alchemical boiler, real host alembics, validated essentia locus and aggregator.
- Original textures, models, host research/magical recipes and searchable field notes.

All listed devices perform their described function. Future machines are not
registered as decorative substitutes. **Actual golem collection and delivery are
pending**: requests reserve real source references but never move or create items
on their own. Dispersing mirrors expose a tested explicit worker-custody contract
for that later integration. No inert taskmaster is registered. Golems, constructs,
experience and portable equipment belong to subsequent phases.

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

## Storage and logistics

Place maws against an inventory face. Hungry absorbs nearby loose items;
Finical applies an attached filter. Committed insertion leaves overflow loose.
The maws have attached plate outlines and no solid collision.

The three chests have 27 real slots, persistent contents and ordinary manual
access. Tenacious protects one of each component variant from automation,
including vanilla hoppers, while allowing manual removal. Greedy watches new
neighbor deposits every ten ticks. Its stick menu configures acquisition faces,
fair percentage share, own-stock target or neighbor reserve. It refuses hoppers
below and reciprocal greedy acquisition; one-way sharing and bottom barrels work.
Fractional shares persist so repeated single deposits remain fair.

Eager trades with the placer and authorized users when opened; sneaking bypasses
trade. Apply a player-bound entity eye to authorize, or sneak-apply it to revoke.
A whitelist restocks the player toward sample quantities, deposits excess and
returns nonlisted chest items. A blacklist deposits nonlisted player items and
returns listed chest surplus while retaining the configured quantities there.
Full destinations preserve remainders. A stick opens chest controls.

Build an Inventarium as **amber → host Ordo crystal → core**, vertically upward.
At least eight eligible shelves anywhere within the radius-three cube complete
the documented modern arrangement. Each basic/enchanting/ensorcelled shelf adds
81/216/486 slots; the enhanced shelves also grant 4/8 ordinary shelves of enchanting
power. The modern index budget is capped at 16,384 slots. Entire containers must
fit. Only extractable contents visible through the selected capability face count.
Physical containers, including both halves of a double chest, are deduplicated.

Bind a core eye to each scribe. One baseline scribe plus one per nearby Ordo
crystal is allowed, within 10+8 blocks per crystal. The first container directly
behind the scribe selects its default type. A scribe normally scans a 7×7×7 cube
once a second, paying one actual Ordo centivis through a host relay per scan.
Use a stick for inventory-face controls; sneak-click to forget its type.
Empty-hand right-click opens local or whole-system contents. An oculus above
the scribe holds five eye links. Each self-targeted eye independently extends
scan radius by one; other eyes add container types. These may be mixed. An
empty-hand click removes its last eye. Nothing force-loads chunks.

Each placed tome or dispersing mirror needs its own physical memory block.
Eligible memory is two or three below the core, within three horizontally, and
touches amber. Persistent memory reservations cannot be shared by two devices.
Place a tome and attach a core-bound eye. Its range follows the same crystal
allowance, and it continuously needs an Ordo relay at one centivis per second.
The live server catalogue searches and pages every indexed identity, displays
stock/reserved/wanted counts separately, and sorts by name or stock. Large
values abbreviate in rows; tooltips retain exact totals. Queries are bounded
and rate limited; requests validate the current menu, range, power and revision.

Plus/minus changes only your own orders. Shift changes a stack, Ctrl multiplies
by ten, and Alt permits demand above stock. A ghost sample or Shift-clicked
inventory stack permits out-of-stock identities without consuming real items.
Zero cancels. Orders and worker custody leases persist; reduction and cancellation
retain recovery obligations for already carried items. The core or its shelves
can feed a tally directly or through a remote comparator; a placed tome tallies
its own actual demand.

Modern safety bounds are 256 active/recovering orders per core, one million
requested items per order, and 512 worker leases of at most 4,096 items each.
The index is bounded at 16,384 slots. These are implementation limits rather
than claims about undocumented historical constants.

The tome's **Mirrors** button selects input/output eye configuration. Close the
menu and attach an eye aimed at a mirror within 16 blocks. Its distinct real input
slot passes actual stacks through the input route; a refusal keeps the stack in
that slot. The output location names the actual mirror near this tome.

Melding uses an eye bound to a real paired host entrance. Hand, hopper and loose
input emerge from the paired exit, including across dimensions only when both
ends are loaded and reciprocal. Dispersing binds a core eye, consumes one memory
slot, and accepts only explicit persisted worker-custody tokens. It serves multiple
tomes and queues their actual delivered items at each specified output mirror
or melding adapter. Ordinary matching items are still unsolicited and are refused.
This worker interface is tested, but automatic host-golem fulfillment is pending.

Hungry Hand Mirror sneak-binds an existing paired entrance. Use an opposite-hand
filter to copy list one, or sneak-use for list two. Automatic mode, toggled by
sneak-use in air, considers only explicitly listed types: whitelists send listed
items; blacklist quantities preserve a reserve and send surplus. Empty lists send
nothing automatically. Ordinary use can send a real opposite-hand stack manually.
Mercurial crystals beside both mirror endpoints carry directional internal power
through the loaded pair. Source-free mirror cycles turn off.

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
Their XML report defaults to `../validation/phase3/gametest.xml` (override with
`-PtestPhase=phase2`, or another evidence phase); JUnit XML is
in `build/test-results/test/`. Test results establish their covered behaviors,
not full runtime or pack compatibility. Client and full-pack acceptance are
tracked separately in the outer feature matrix.

Magical recipes use actual host research gates and arcane/infusion/crucible costs.
The ordinary inverter, ordinary hourglass, guide, soup and milk conversion crafts
remain intentionally mundane. All earlier magical material-recipe bypasses were
replaced. Complete the original Autoturgy category in the host research interface.
Unknown historical timing constants use the documented modern defaults above.

Source namespace `com.sanhiruzu.autoturgy` is first-party. The implementation,
art and prose are original; no historical mod classes or assets are included.


## Fluids and essentia

The **Thirsty Tank** holds 16 buckets, plus 16 per Reservoir glyph. Apply one
physical glyph per face; sneak-use that face with a stick or host wand to remove
it. Removal refuses if the smaller capacity would strand fluid. Empty-hand use
shows read-only glyph, fluid and capacity data. Menu integers use two vanilla
16-bit property words, preserving full 32-bit amounts and existing thresholds.

The tank follows connected matching fluid paths within 16 blocks and 256 visited
positions, once per 20 ticks, collecting one bucketable source. Adjacent water
sources remain renewable. Exposed rain adds 25 mB per collection. A source is
checked against actual acceptance first; if source-removal callbacks change the
intake gate, its paid fluid remains in persisted recovery custody. Breaking the
tank drops one filled tank preserving all fluid, glyphs and recovery custody.

Void destroys compatible overflow; Consumption drains 100 mB per collection per
glyph. Siphoning pushes and Envy pulls up to 1000 mB through their face.
Temperance requires redstone for intake; Preservation retains 1 mB. Guzzler
reduces collection intervals, capped at 2 ticks. Bovine disables world absorption
and milks adult cows in a four-block directional area; every cow persists its
own shared 1200-tick cooldown across tanks and reloads.

Water bottles and mushroom/vishroom stew bowls exchange 250 mB, preserving return
containers. Milk uses NeoForge's standard fluid identity. Craft an ordinary milk
bucket into a **Pouring Milk Bucket**: use normally to drink and cure effects,
or sneak-use to place a stable, bucketable milk source. This explicit modern
source does not spread. Mushroom and vishroom soup have flowing blocks and
placeable buckets. Vishroom stew uses the real host vishroom, provides little
food, nausea/hunger and one temporary warp. Source immersion adds nausea and one
temporary warp per 200 ticks; source random ticks can grow actual vishrooms on
legal nearby surfaces within two blocks.

The **Alchemical Boiler** accepts real fuel and fluid. Stack up to five actual
host alembics above it. Whole 1000 mB batches take 100 fuel ticks, accelerated by
up to four correctly attached host bellows; alumentum uses its real burn time.
The actual host aspect index determines yield. Bucket fluids subtract every
empty-bucket aspect; soup uses four servings minus their bowl aspects. Unknown
or empty net yields produce nothing. Full output stalls before consuming fluid
or new fuel. Paid pending output persists if an alembic changes. Thaumaturge
1.0.4 intentionally refuses alembic conduit/storage intake, so this producer uses
its public furnace-side `IAspectContainer.fill` API, including its remainder.
Breaking the boiler destroys fluid/pending essentia and drops unused fuel.

The **Essentia Locus** uses this documented modern construct: center lapis in a
3×3 pool of eight water sources, a full 5×5 silverwood-plank outer ring at that
height, an Aqua crystal cluster above lapis and the locus above the cluster.
Insert a loaded, in-range jar/reservoir eye. One eye floods connected real
containers, initially 12 within 16 blocks. Up to eight extra Aqua clusters
adjacent to the wood ring add eight containers, one eye and eight range each.
Empty-hand use removes the last actual eye; sneak-empty-hand use opens status.
Changes invalidate immediately; no chunks are loaded. Vis readers under any
pool water square or readers bound to the locus report actual totals and labeled
per-aspect capacities, including empty labeled jars. A tally counts essentia
units there, filtering with actual component-bearing host crystal samples.

Craft the **Essentia Aggregator** with a real Aqua cluster and replace the
central cluster directly below the locus. Its original model visibly embeds that
cluster; all other construct resources remain required. It owns no essentia.
Actual host storage, conduit, legacy transport and infusion-source capabilities
delegate extraction to the original monitored jars. Blocked/braced sources and
wrong output sides refuse extraction. Its zero suction/minimum threshold allows
an actual downstream tube pull; host infusion and essentia mirrors use their real
source discovery path. Reservoirs expose conduit/aspect source rather than a
storage capability in host 1.0.4, so a small adapter delegates their real methods.

These capacities, shapes, costs and rates are modern defaults wherever the
public reference omits numbers. New fluid/research/menu visuals still need the
parent's frozen-JAR client review; later feature families and full-pack acceptance
remain pending.

## Extending progression

Register functional content and original assets first. Add recipe JSON under
`data/autoturgy/recipe/`, using installed-host schemas: arcane shaped recipes
carry `pattern`, `key`, `crystals`, `vis`, `research: {entry}` and `result`;
infusion recipes carry `catalyst`, `components`, real `aspects`, `instability`,
the same research gate and result. Component-aware primal crystal ingredients
use `neoforge:components` with `thaumaturge:crystal_aspect`, never legacy shard IDs.
Original research entries under `data/autoturgy/thaumaturge/research_entry/`
belong to `autoturgy:automation`, have valid namespaced parents and normal host
obtain/knowledge stages. Add the recipe IDs to the entry's final stage and the
original translated title/text to the language file. Preserve the graph's
reachability; do not make an ingredient require the machine it is used to craft.
Do not add a parallel ordinary crafting recipe for a magically gated device.
