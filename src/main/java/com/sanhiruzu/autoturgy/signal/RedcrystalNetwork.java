/* Redcrystal recomputes source contributions from zero rather than latching neighbors.
 * Loaded graphs and wire-source searches are bounded; vanilla-facing power is clamped. */
package com.sanhiruzu.autoturgy.signal;

import com.sanhiruzu.autoturgy.Content;
import com.sanhiruzu.autoturgy.common.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.registries.*;

public final class RedcrystalNetwork {
  public enum Variant {
    NORMAL,
    AMPLIFIED,
    DIM,
    DENSE,
    RESONATING,
    MERCURIAL
  }

  public static final Map<Variant, DeferredBlock<CrystalBlock>> BLOCKS =
      new EnumMap<>(Variant.class);
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalEntity>> TYPE;
  private static final ThreadLocal<Boolean> READING_SOURCE = ThreadLocal.withInitial(() -> false);

  public static void register() {
    for (Variant variant : Variant.values())
      BLOCKS.put(
          variant,
          Content.block(
              variant == Variant.NORMAL
                  ? "redcrystal"
                  : variant.name().toLowerCase(Locale.ROOT) + "_redcrystal",
              () -> new CrystalBlock(variant)));
    TYPE =
        Content.ENTITIES.register(
            "redcrystal",
            () ->
                BlockEntityType.Builder.of(
                        CrystalEntity::new,
                        BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new))
                    .build(null));
  }

  public static final class CrystalBlock extends DeviceBlock {
    public static final DirectionProperty ATTACHMENT = DirectionProperty.create("attachment");
    public final Variant variant;

    CrystalBlock(Variant variant) {
      this.variant = variant;
      registerDefaultState(defaultBlockState().setValue(ATTACHMENT, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(ATTACHMENT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
      BlockState state =
          super.getStateForPlacement(context)
              .setValue(ATTACHMENT, context.getClickedFace().getOpposite());
      return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      Direction attached = state.getValue(ATTACHMENT);
      BlockPos support = pos.relative(attached);
      return level.hasChunkAt(support)
          && level.getBlockState(support).isFaceSturdy(level, support, attached.getOpposite());
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        Direction side,
        BlockState neighbor,
        LevelAccessor level,
        BlockPos pos,
        BlockPos neighborPos) {
      return side == state.getValue(ATTACHMENT) && !state.canSurvive(level, pos)
          ? Blocks.AIR.defaultBlockState()
          : super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(
        BlockState state,
        net.minecraft.world.level.BlockGetter level,
        BlockPos pos,
        CollisionContext context) {
      return switch (state.getValue(ATTACHMENT)) {
        case DOWN -> Block.box(1, 0, 1, 15, 3, 15);
        case UP -> Block.box(1, 13, 1, 15, 16, 15);
        case NORTH -> Block.box(1, 1, 0, 15, 15, 3);
        case SOUTH -> Block.box(1, 1, 13, 15, 15, 16);
        case WEST -> Block.box(0, 1, 1, 3, 15, 15);
        case EAST -> Block.box(13, 1, 1, 16, 15, 15);
      };
    }

    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new CrystalEntity(p, s);
    }

    @Override
    protected int getSignal(
        BlockState state,
        net.minecraft.world.level.BlockGetter level,
        BlockPos pos,
        Direction side) {
      if (READING_SOURCE.get()) return 0;
      return level.getBlockEntity(pos) instanceof CrystalEntity crystal
              && crystal.sends(side.getOpposite())
          ? crystal.signal()
          : 0;
    }
  }

  public static final class CrystalEntity extends DeviceEntity implements DeviceBlock.Ticking {
    public CrystalEntity(BlockPos p, BlockState s) {
      super(TYPE.get(), p, s);
      settings[0] = 4095;
      settings[1] = 1;
      settings[2] = 7;
      settings[3] = 8;
    }

    public Variant variant() {
      return ((CrystalBlock) getBlockState().getBlock()).variant;
    }

    public String kind() {
      return "redcrystal";
    }

    public int controlCount() {
      return 9;
    }

    @Override
    public boolean controlVisible(int i) {
      return i != 6 && i != 7
          || i == 6 && variant() == Variant.DIM
          || i == 7 && variant() == Variant.DENSE;
    }

    @Override
    public boolean controlEditable(int i) {
      return controlVisible(i);
    }

    public boolean receives(Direction d) {
      return (settings[0] & 1 << (d.ordinal() * 2)) != 0;
    }

    public boolean sends(Direction d) {
      return (settings[0] & 2 << (d.ordinal() * 2)) != 0;
    }

    @Override
    public int comparator() {
      return signal();
    }

    public void configure(int i, int delta) {
      if (i < 6) {
        int shift = i * 2;
        int mode = Math.floorMod(((settings[0] >> shift) & 3) + delta, 4);
        settings[0] = (settings[0] & ~(3 << shift)) | mode << shift;
        changed();
      } else if (i == 6 && variant() == Variant.DIM) bounded(2, delta, 1, 15);
      else if (i == 7 && variant() == Variant.DENSE) bounded(3, delta, 1, 31);
      else if (i == 8) cycle(1, delta, 2);
    }

    public void serverTick() {
      if ((level.getGameTime() & 3) != 0) return;
      // Every discovered node records this tick, so one graph runs once per interval.
      if (lastSolved == level.getGameTime()) return;
      recompute(this);
    }

    private long lastSolved = Long.MIN_VALUE;

    public void setPower(int value) {
      output(value);
      progress = signal();
    }
  }

  public record Edge(CrystalEntity node, int distance, boolean outgoing) {}

  private static CrystalEntity crystal(Level level, BlockPos p) {
    return level.hasChunkAt(p) && level.getBlockEntity(p) instanceof CrystalEntity c ? c : null;
  }

  private static List<Edge> edges(CrystalEntity a) {
    Level level = a.getLevel();
    List<Edge> edges = new ArrayList<>();
    if (a.variant() == Variant.MERCURIAL
        && a.target != null
        && LoadedTargets.local(level, a.getBlockPos(), a.target, 1)) {
      com.sanhiruzu.autoturgy.transport.MirrorRoutes.resolve(level.getServer(), a.target)
          .ifPresent(
              route -> {
                Level destination = route.exit().getLevel();
                for (Direction direction : Direction.values()) {
                  CrystalEntity b =
                      crystal(destination, route.exit().getBlockPos().relative(direction));
                  if (b == null
                      || b.variant() != Variant.MERCURIAL
                      || b.target == null
                      || !b.target.dimension().equals(destination.dimension())
                      || !b.target.pos().equals(route.exit().getBlockPos())) continue;
                  Direction exit =
                      Direction.getNearest(
                          a.target.pos().getX() - a.getBlockPos().getX(),
                          a.target.pos().getY() - a.getBlockPos().getY(),
                          a.target.pos().getZ() - a.getBlockPos().getZ());
                  addEdge(edges, a, b, exit, direction.getOpposite(), 0);
                }
              });
    }
    for (Direction d : Direction.values()) {
      Direction attached = a.getBlockState().getValue(CrystalBlock.ATTACHMENT);
      CrystalEntity adjacent = crystal(level, a.getBlockPos().relative(d));
      if (adjacent != null
          && adjacent.getBlockState().getValue(CrystalBlock.ATTACHMENT) == attached
          && d.getAxis() != attached.getAxis()) addEdge(edges, a, adjacent, d, d.getOpposite(), 1);
      if (a.variant() == Variant.RESONATING) {
        for (int distance = 2; distance <= 15; distance++) {
          BlockPos p = a.getBlockPos().relative(d, distance);
          if (!level.hasChunkAt(p)) break;
          CrystalEntity b = crystal(level, p);
          if (b != null) {
            if (b.variant() == Variant.RESONATING) addEdge(edges, a, b, d, d.getOpposite(), 0);
            break;
          }
        }
      }
      if (a.settings[1] != 0 && d.getAxis() != attached.getAxis()) {
        CrystalEntity b = crystal(level, a.getBlockPos().relative(d).relative(attached));
        if (b != null
            && b.settings[1] != 0
            && b.getBlockState().getValue(CrystalBlock.ATTACHMENT) == d.getOpposite())
          addEdge(edges, a, b, d, attached.getOpposite(), 2);
      }
    }
    return edges;
  }

  private static void addEdge(
      List<Edge> edges,
      CrystalEntity a,
      CrystalEntity b,
      Direction exit,
      Direction entry,
      int loss) {
    boolean outgoing = a.sends(exit) && b.receives(entry);
    boolean incoming = a.receives(exit) && b.sends(entry);
    if (outgoing || incoming) edges.add(new Edge(b, loss, outgoing));
  }

  public static void recompute(CrystalEntity start) {
    Map<CrystalEntity, List<Edge>> graph = new LinkedHashMap<>();
    ArrayDeque<CrystalEntity> pending = new ArrayDeque<>();
    pending.add(start);
    while (!pending.isEmpty() && graph.size() < 512) {
      CrystalEntity node = pending.removeFirst();
      if (graph.containsKey(node)) continue;
      List<Edge> links = edges(node);
      graph.put(node, links);
      for (Edge edge : links) if (!graph.containsKey(edge.node())) pending.addLast(edge.node());
    }
    // Oversized graphs fail closed rather than retaining an unsolved boundary's old signal.
    if (!pending.isEmpty()) {
      graph
          .keySet()
          .forEach(
              c -> {
                c.lastSolved = c.getLevel().getGameTime();
                c.setPower(0);
              });
      return;
    }
    Map<CrystalEntity, Integer> power = new HashMap<>();
    record Work(CrystalEntity node, int signal) {}
    PriorityQueue<Work> queue =
        new PriorityQueue<>(Comparator.comparingInt(Work::signal).reversed());
    READING_SOURCE.set(true);
    try {
      for (CrystalEntity node : graph.keySet()) {
        int source = 0;
        for (Direction side : Direction.values())
          if (node.receives(side))
            source =
                Math.max(
                    source, external(node.getLevel(), node.getBlockPos().relative(side), side));
        if (source > 0) queue.add(new Work(node, transform(node, source)));
        power.put(node, 0);
      }
    } finally {
      READING_SOURCE.set(false);
    }
    while (!queue.isEmpty()) {
      Work work = queue.remove();
      if (work.signal() <= power.getOrDefault(work.node(), 0)) continue;
      power.put(work.node(), work.signal());
      for (Edge edge : graph.get(work.node())) {
        if (!edge.outgoing() || !graph.containsKey(edge.node())) continue;
        int propagated = Math.max(0, work.signal() - edge.distance());
        propagated = transform(edge.node(), propagated);
        if (propagated > power.get(edge.node())) queue.add(new Work(edge.node(), propagated));
      }
    }
    for (CrystalEntity node : graph.keySet()) {
      node.lastSolved = node.getLevel().getGameTime();
      node.setPower(power.get(node));
    }
  }

  private static int transform(CrystalEntity node, int source) {
    if (source <= 0) return 0;
    return switch (node.variant()) {
      case AMPLIFIED -> 31;
      case DIM -> Math.min(Math.clamp(node.settings[2], 1, 15), source);
      case DENSE -> source < Math.clamp(node.settings[3], 1, 31) ? 0 : source;
      default -> source;
    };
  }

  public static int transform(Variant variant, int source) {
    return source <= 0 ? 0 : variant == Variant.AMPLIFIED ? 31 : receive(variant, source);
  }

  public static int receive(Variant variant, int signal) {
    return switch (variant) {
      case DIM -> Math.min(7, signal);
      case DENSE -> signal < 8 ? 0 : signal;
      default -> signal;
    };
  }

  private static int external(Level level, BlockPos pos, Direction side) {
    if (!level.hasChunkAt(pos) || crystal(level, pos) != null) return 0;
    if (!(level.getBlockState(pos).getBlock() instanceof RedStoneWireBlock)) {
      int vanilla = level.getSignal(pos, side);
      if (vanilla > 0 && level.getBlockEntity(pos) instanceof DeviceEntity device)
        return device.internalSignal();
      return vanilla;
    }
    // Read the wire's real sources instead of its cached power, which may include us.
    Set<BlockPos> visited = new HashSet<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    queue.add(pos);
    int best = 0;
    while (!queue.isEmpty() && visited.size() < 256) {
      BlockPos p = queue.removeFirst();
      if (!visited.add(p)) continue;
      int loss =
          Math.abs(pos.getX() - p.getX())
              + Math.abs(pos.getY() - p.getY())
              + Math.abs(pos.getZ() - p.getZ());
      for (Direction d : Direction.values()) {
        BlockPos n = p.relative(d);
        if (!level.hasChunkAt(n) || crystal(level, n) != null) continue;
        BlockState s = level.getBlockState(n);
        if (s.getBlock() instanceof RedStoneWireBlock) queue.addLast(n);
        else best = Math.max(best, level.getSignal(n, d) - loss);
      }
    }
    return Math.clamp(best, 0, 15);
  }

  private RedcrystalNetwork() {}
}
