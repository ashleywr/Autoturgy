# Autoturgy

An original magical automation addon for Minecraft **1.21.1**, NeoForge
**21.1.262**, Java **21** and Thaumaturge **1.0.4**. Version **1.0.0** covers the fifteen
survival gameplay families described below, with explicit modern adaptations.

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
- Golem linking bell, reversible inhibitor, physical-delivery taskmaster and golem workbench.
- Paid Thaumostatic Spires, weighted Netherminds, dimensional creature lures and Siren navigation.
- Finite experience stones and movable concentrate jars, real throwing pearls, Curios rings, portable wand crafting and preserved advanced node jarring.
- Luminous amber masonry, sliver paving alternatives and deterministic fishing infusions.
- Original textures, models, host research/magical recipes and searchable field notes.

All listed devices perform their described function. Real host golems walk,
collect actual indexed items, carry them in host hands and deliver through
validated reservations. Automated checks use the actual installed host and dependencies.
Final release-client and isolated full-pack acceptance are recorded separately.
No claim of exact historical constants or every creative catalogue item is made.

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
selections persist when contents change; native aspect icons have localized hover names. Count mode emits
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
Real linked host golems now fulfill this worker interface. Dispersers claim their
own memory and receive explicit worker leases before routing to the current
paid tome's selected output. Ordinary containers respect their selected side.

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
Their XML report defaults to `../validation/phase4a/gametest.xml` (override with
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
public reference omits numbers. The phase-three frozen client review observed
actual tank capacity/intake, paid boiler output, bound locus eyes, soup/milk
conservation, natural vishroom growth, real mirror deliveries and host research
pages. Final 1.0.0 client and full-pack acceptance are separate from these earlier observations.

## Host golems and workbench

Attune the **Golem Linking Bell** to a placed inhibitor, taskmaster or workbench,
then use it on one of your own Thaumaturge golems. One device owns each link;
at most six golems link to a device. Sneak-use the bell on a device to clear its
links, or on another block to clear attunement. Normal client entity interaction
is supported. Finish existing host tasks and empty hands before assigning work.
Death and ordinary host pickup retire metadata without fulfilling the order;
the host drops its real hands. Deferred notices survive unloaded device chunks
and retire when that placement loads or has been replaced.
Linking and work require the same dimension. Portal travel detaches device links;
already carried items drop safely in the arrival dimension and demand stays open.

An **Inhibitor** sits above one to four consecutive, actually powered host
fetters. Modern radii are 8/16/24/32 blocks. Its menu chooses linked exemptions
or linked-only targets. Only its owner's golems are affected; combat takes
precedence. Reversible goals preserve external NoAI and ordinary host goals.
Overlapping inhibitors keep pausing until the last applicable device stops,
unloads, breaks or loses range. Candidates use local chunk buckets and a bounded
4096-device inspection budget per golem.

Bind a **Taskmaster** to a functional Inventarium Core using a crystalline eye.
It requires its own real memory slot and the core's cluster-expanded link range.
Linked workers need their host home within five blocks. Host 1.0.4 fill/empty
eligibility comes directly from its registered seal trait requirements: both
exclude Clumsy. Fine arms provide Deft; claw/breaker/dart arms carry Clumsy.
Smart heads permit advanced workbench patterns. No legacy animation-core item
is invented. Golems use their actual navigation, movement speed and hands.

Paid tome requests claim physical source slots. Workers walk within two blocks
of the source/destination block surface, revalidate sided capabilities, collect
real items and acknowledge only actual delivery. Full, absent or unloaded targets
retain hands. Cancellation returns surplus; a removed/full source produces a safe
physical drop. Reloaded custody never re-reserves its carried quantity. A narrow
public home adapter suppresses the host's first-load teleport during carried
journeys, then restores the saved home/radius. Host seal tasks cannot steal addon
custody, including during a parts/selector rebuild. No destination is force-loaded.

The **Golem's Workbench** provides nine ghost slots per enabled pattern, eighteen
real ingredient slots and nine product slots. Link a host Empty-compatible golem.
Basic workers handle a 2×2 bounding grid or one repeated ingredient; Smart workers
handle full mixed 3×3 recipes. Only real registered ordinary crafting recipes
work. The front routes products to a sided neighbor; bottom automation extracts
products, other faces access ingredients. A powered fetter directly underneath
pauses crafting while output still moves. Default noningredient remainders drop;
Order retains them where space permits. Ghosts never become physical items.

Use one removable upgrade on each cardinal side. Sneak-use that side with an
empty hand or host wand to recover it. Air uses actual move speed for a faster
interval at increased fatigue. Earth uses the greater of host material damage
and actual attack damage, improving rate and endurance. Fire adds one ghost
pattern page each, up to two extra pages. Removing Fire retains dormant patterns
and every real ingredient. Entropy is unique: the real first ingredient slot
identifies a ghost placeholder; other stored ingredients substitute only when
a registered recipe yields the nominal product or an explicitly permitted filter
product. Invalid substitutions consume nothing.

Modern Water adaptation improves resting recovery next to actual water. Work
starts at forty ticks, bounded to at least four after upgrades. Base effort is
four; each Air adds three, Entropy adds six and Earth subtracts two (minimum one).
Fatigue stops work at 100 and is capped at 120. Recovery is one point each second,
plus three per Water side when touching water. At fatigue twenty or above, one
actual unbraced Fabrico essentia within a 5×3×5 box restores forty points. Actual
source blocking and output-face permissions apply. These are modern defaults.

Phase-four-a automation covers fully initialized real host entities, strict
walking/hand conservation, two competing workers, cancellation/full output,
saved custody without a teleport, source-side revocation, death and host pickup,
genuine separate Nether-core chunk unload/death/reload, inhibition overlap and
host-goal restoration, real recipes/remainders and every upgrade. Release-client and saved-world checks are tracked separately from this automated evidence.

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


## Paid constructs and lures

The field notes now contain original structure diagrams, port mappings and every
cost. These are balanced modern constants where public historical descriptions
lack values; no recovered exact percentages or costs are claimed. Complete the
actual host research and use a charged host wand to start. Empty-hand/menu Start
cannot bypass research or payment. Use a jar on a configured face to feed up to8
real essentia; sneak-use to recover8. Host tubes respect output/brace permissions.
Buffers hold64/aspect,256 total, and remain in the dropped inactive controller.

Spire: chiseled quartz bottom, two to six pylons plus one controller in any order,
actual host thaumium cap, air and sky. Start costs100 Aer centivis. Horizontal
radius8+2/pylon has unlimited vertical extent;1 Aer essentia per successful batch
adds8 radius and1 Volatus doubles the pylon-count transfer rate. Every delivered
Potentia costs1 actual owned Potentia plus1 Aer relay centivis. The updated actual
jar is installed in the equipped host harness. Player processing rotates a bounded
64-player budget. Unsuitable/full harnesses and unpaid transfers consume nothing.

Nethermind: center of a5x5 runed-obsidian/rune platform,500 Ignis wand centivis to
start. Legal loaded targets lie in a9x9x9 volume starting two below the center.
Rock/Sea/Crystal/Light/Soul/Pebble yield netherrack/lava/quartz ore/glowstone/soul
sand/gravel. Pebble gravel is an explicit modern adaptation. Base weights4 Rock
and1 Sea gain4 per matching rune. Four TOTAL type runes exclude absent types;
matching discounts apply once. Material costs: Terra2 Rock/Pebble; Ignis4 Sea;
Vitreus4 Crystal; Lux4 Light; Spiritus4 Soul, discounted1 with a matching rune.
Each operation adds Ignis1 essentia and5 Ignis relay centivis. Celerity caps at12,
reduces interval200 by15/rune to20 ticks, and its first four multiply all
per-operation costs by1+count (max5). Blocks/fluids/containers/entities are safe.

Dimensional Lure: at least four total creature runes, never mixed with block
runes. Lower center6–10 below the Nethermind,8–10 with Ghasts; clear center line,
5x5 perimeter of BASIC Runed Obsidian below. Start LOWER first (200 Perditio
wand centivis), then upper. Actual vanilla piglin analogue/blaze/ghast/wither
skeleton/magma cube and host Firebat/Ignis Wisp use rune-count weights. Upper
cost Ignis2 plus3 creature aspect; lower Alienis2+Potentia1. Creature aspects:
Exanimis piglin/skeleton; Ignis blaze; Volatus ghast; Bestia cube/bat; Auram wisp.
Both ends additionally pay5 relay centivis (upper Ignis/lower Perditio), with the
same Celerity multiplier. Space/liquid/height/peaceful/16-local-mob gates precede
payment. Blaze extra chance1/8 adds at most one real blaze and8 instability.
At instability20+, piglin anger chance1/4 lasts200 ticks. Four corner totem
pillars(+/-3,+/-3) contribute up to4 each; equal nonzero heights add4 stability.
Nitor atop pillars/below cardinal upper arms adds2 each. Instability gain is
max(0,4+2*Celerity-stability), decays1/second, and stops both ends at80.

Siren: vertical line of two actual host obsidian totems, counted up to16 above
and16 below, extending4 blocks beyond the column. Start costs100 Ordo centivis.
An eligible winning attraction second costs1 actual Potentia for radius12;
1 additional Desiderium extends to24. Installed host has no Fames: Desiderium is
the explicit modern mapping. Empty periods have no upkeep. Strongest column
wins, then radius/coordinates. Ordinary navigation excludes bosses/golems/slimes,
combat, leashed mobs and seated pets. Blocked100-tick, arrived40-tick or200-tick
attraction gives600 ticks per-lure resistance. Endermen can safely step at most4
blocks nearer through the ordinary ender-teleport event. Disable/unload releases
movement without rewriting targets, home or AI.

Shared golem reach measures two physical surfaces, within2 blocks and a clear
loaded interaction line. Actual reachable adjacent paths use accuracy0 to avoid
stopping a node short. The observed client carried-save position is replayed with
real host hands and save/load. Final saved-world client acceptance is separate.

Automated construct checks use the actual installed host. Its1.0.4 HARNESS_JAR
stores a copied ItemStack, violating NeoForge IDE-only component validation; the
test source set narrowly admits that host class. No production suppression or
host modification is included. Release-client and full-pack evidence is recorded separately.


## Experience and portable tools

Amnesiac Stones hold 2,000 XP points. Click to remove one level, or sneak-click
for five. Fractional progress scales to the destination level using an integer
floor; all removed points enter the stone, and limited room takes only what fits.
Standing on the solid stone drains ten points every four ticks. The real brain jar
above collects from its buffer; otherwise the buffer releases collectible orbs.
Sneak-use the brain itself with an empty hand to empty it. Used or thrown experience
bottles yield exactly nine points once, exceeding the ordinary average of seven.

Soul Stones hold 10,000 points and exchange with neighboring Amnesiac/brain
assemblies according to their actual balances. Their own comparator reads their
own fullness; an Amnesiac comparator reads connected stones and brains once each,
with a bounded 512-position traversal. Soul Stones drip ten points per second into
an empty unlabeled warded jar one or two below. An open Item Grate passes the drip,
a closed grate or solid block stops it, and open air without a jar loses one point
per second. Concentrate jars retain their sole balance when broken and placed.
Phials withdraw/refill/drink exactly 100 points; ordinary bottles spend 20 points
to produce a normal Bottle o' Enchanting that cannot refill concentrate. Stone
breaks and explosions release half their buffer once and destroy the remainder.

XP transfers reconstruct actual level/progress instead of trusting the lifetime
counter. Exact addon balances survive float rounding and reload, and explicitly
synchronize the client HUD. Player reconstruction supports levels through
200,000,000, below Minecraft's native integer XP-width overflow; invalid external
levels/progress are refused without rewriting them. Explicit point reconstruction
saturates at that documented support bound. Pure long arithmetic has a wider
1,400,000,000-level bound. These large bounds do not change ordinary survival play.

Avarice/Miser Pearls are thrown entities with a 2.5-block collection radius and
80-tick maximum flight. Collected loose stacks move directly into the thrower's
real inventory; overflow stays loose. Avarice has a 64-stack operation bound;
its shatter chance is min(1536,quantity+16*stacks)/2048 and damage chance is
min(768,quantity+8*stacks)/1024, with bounded 1–8 damage. A surviving pearl returns
once; a shattered one returns one fragment. Miser never harms/shatters, accepts
at most ten loose stacks, and each manual throw pays 25 actual Ordo centivis from
a carried wand. A dispenser is a calibrated launch with no manual vis cost and
returns collected items and its one pearl to its own inventory, dropping safe
remainders. Fragments gently attract existing idle, unnavigating Endermen within
24 blocks as the approved modern affinity adaptation. Mercatus Amalgam's actual
Desiderium value gives Pech a value of 24: one gift can domesticate a wild Pech,
and subsequent gifts enter its real hoard.

Equip Lesser/full Ender Disjunction Rings in real Curios ring slots. They deal
3/6 damage within 6/12 blocks of either endpoint of an ender-creature teleport.
A second ring adds half its own strength; later rings add nothing. One wearer
pays 25 Perditio centivis per affected target/event, with no free unpaid effect.
The normal Runic Shielding augmentation accepts both rings.

Socket a Crafting Focus into a real wand. Right-use in air, or press the
configurable Call Crafting Focus key, period (.) by default, from the hotbar.
Opening costs 25 Ordo centivis. The portable menu uses actual ordinary and arcane
recipes, real inputs/remainders and host research/payment rules. Primal icons
show wand costs, counts show required crystals, and the Aura label shows the
ordinary generic recipe cost paid from the player's loaded current chunk.
Failed transactions roll back that real aura. Each arcane craft additionally
costs 25 Ordo centivis; a staff can craft ordinary recipes only. Leftovers return
on close, invalid wand changes, death and full inventories without duplication.

Paid cumulative infusion upgrades provide Gratis, then Storage, then Transposing
as an explicit modern replacement for unavailable legacy focus upgrade hooks.
Gratis removes addon opening/operation fees. Storage also retains real grid and
crystal inputs inside the physical focus. Transposing includes both and swaps
whole focus stacks from inventory or a carried/worn real Focus Pouch, returning
the previous focus to its source slot. Upgrades preserve contents and refuse
redundant/lower tiers. Installed host transactions, sockets, pouches and data
components remain authoritative; no host modification is included.

Advanced Node Jarring consumes the normal shell with warded glass and a 3×3
warded silverwood-log lid. Use an unfocused wand on an accessible warded lid log,
with Advanced Node Jarring research and 70 vis of every primal. Instant capture
preserves type, modifier and current/base/original aspects through actual host
jar drop, placement and release. Energized capture is explicitly refused before
payment because ordinary host jar semantics do not preserve that state safely.
Only intrinsic node data travels; spatial relay and unrelated ownership state
never travels with it.

Glow Amber and its bricks emit light 15. Existing paid sliver refining remains
unchanged; Practical Magic adds paid sliver paving alternatives. Fishing infusions
apply Luck of the Sea III or Lure III to an unenchanted rod while preserving its
name, wear and other components. Enchanted rods are explicitly refused rather
than having their enchantments replaced. Existing milk/mushroom/vishroom fluid
and food functionality remains available. All numerical costs/rates above are
modern defaults where the historical public descriptions omit constants.

## Final integration and tested support

Unlinked Tally Boxes read the adjacent inventory behind their output face. A bound
Remote Comparator directly above a Tally or Vis Reader bridges its loaded target;
an explicit copied eye overrides that arrangement. Vis Readers also read actual
jars, crucibles, nodes and wand holders directly above, retaining Locus pool
discovery. Same-dimension, distance, sided-access and cycle guards apply.

Clear Tally lenses are the unmodified box. Unbound and Yield are paid physical
items: install on an empty box, sneak-use empty-handed to recover, and recover
the same component-bearing lens on break. The menu cannot create lenses. Aggregate
Count retains analog thresholds; All Quantities and Any Quantity compare each
written sample against actual stock. Whitelists require at least the quantity;
blacklists require less. Empty lists satisfy neither quantity rule.

Greedy observations use separate long counts, including deep storage above 2^32.
Each loaded direction transfers at most 1,728 items per ten-tick visit. Earned
shares defer across full/refusing destinations and subsequent ticks. External
withdrawals reduce outstanding entitlement; a missing source or reload resets
observations and entitlement without treating existing stock as a new deposit.
Fractional percentage credit persists. Inventarium stock travels as full positive
64-bit counts with readable abbreviated rows and exact hover counts.

Construct ports, Vis selection, Locus/reader totals and mixed field-note prose use
the installed host's registered aspect icons and localized hover names. No host
textures are bundled. Scribe contents expose Reset Type and Local/Whole System;
golem controls show link, fetter/radius or core/memory status; fluid screens name
their current contents. Upgraded Crafting Focus tooltips identify active upgrades
and occupied stored-input slots while retaining normal wand/pouch compatibility.

The tested development coordinates are Minecraft 1.21.1, NeoForge 21.1.262,
Thaumaturge 1.0.4 and Curios 9.5.1+1.21.1 on Java 21. The installed host's other
required dependencies are pinned in the outer release manifest. Newer dependency
versions allowed by metadata have not been independently qualified. Build with
Gradle 8.8: `./gradlew build check`; the cumulative actual-host suite uses
`./gradlew runGameTestServer`. GameTest code/resources are excluded from the JAR.

The Thinking Cap and Infinity Jar were creative/helper content and are excluded
from this survival recreation. Phenomenon's historical role remains undefined
by the public behavioral reference and is explicitly excluded. Duplicate lit/off
states and repeated rune metadata names are consolidated into modern functional
states; both Memory Block names map to one physical memory device. No decorative
survival substitute or claim of exact item-for-item parity is introduced.

<!-- ashley-release-information -->

## My builds

This is my original project. Original project code is offered under the [MIT License](LICENSE). Third-party material retains its own license and attribution.

Provided **AS IS**, without warranty of any kind. Maintenance, updates and support are best effort; compatibility with every modpack is not guaranteed.

### Downloads and installation

Download a runtime `.jar` from [GitHub Releases](https://github.com/ashleywr/Autoturgy/releases). Use the Minecraft version and loader named in that release and install its required dependencies. Do not install `-sources`, `-dev`, `-slim`, `-common` or test JARs as the mod.

Close Minecraft, back up your world, and place the matching runtime JAR in the instance's `mods/` directory. Replace older versions of this same mod rather than loading duplicates. For content mods, install matching versions on the server and its clients unless the release explicitly says client-only or server-only.

### Building and reporting problems

Use Java 21 and the checked-in Gradle wrapper:

```powershell
.\gradlew.bat build
```

Build outputs are under `build/libs/` or each loader subproject's `build/libs/`. Some projects require the local development dependencies described above; those dependencies are not included in the download.

For a problem report, include the exact release filename, Minecraft and loader versions, relevant mod versions, `logs/latest.log`, and a crash report if one was written. Remove private information before sharing logs. Remove the JAR to uninstall; worlds containing this mod's blocks, entities or world generation may require restoring a backup.
