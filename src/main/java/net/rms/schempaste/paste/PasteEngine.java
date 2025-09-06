package net.rms.schempaste.paste;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.rms.schempaste.SchemPaste;
import net.rms.schempaste.config.PlacementConfig;
import net.rms.schempaste.config.SchemPasteConfig;
import net.rms.schempaste.litematica.LitematicFile;
import net.rms.schempaste.util.ChunkBoundsUtils;
import net.rms.schempaste.util.DynamicChunkManager;
import net.rms.schempaste.util.LayerRange;
import net.rms.schempaste.util.PositionUtils;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PasteEngine {
    private static final int QUEUE_SOFT_CAP_MULTIPLIER = 10;
    private static final double MIN_AVG_PER_BLOCK_MS = 0.1;
    private static final double MAX_AVG_PER_BLOCK_MS = 50.0;
    private static final double ESTIMATE_SMOOTHING_ALPHA = 0.3;
    private static final int MAX_SLOW_SAMPLES = 8;
    private static final double SLOW_SAMPLE_THRESHOLD_MS = 10.0;
    private final MinecraftServer server;
    private final SchemPasteConfig cfg;
    private final ExecutorService backgroundExecutor;
    private final Queue<BlockPlacementTask> mainThreadQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, PasteJob> activeJobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> chunkQueueCounts = new ConcurrentHashMap<>();
    private final DynamicChunkManager chunkManager;
    private final EnqueueRateLimiter enqueueLimiter = new EnqueueRateLimiter();
    private final AtomicInteger globalQueuedTasks = new AtomicInteger(0);
    private final ConcurrentHashMap<Long, String> chunkOwnerJob = new ConcurrentHashMap<>();
    private final java.util.Set<String> cancelledJobs = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong lastStatusLogMs = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong qNsGetState = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong qNsClearOldTe = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong qNsSetBlock = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong qNsApplyTe = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicInteger qCountPlaced = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger qCountWithTe = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger qCountSkipped = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger qCountClearedTe = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.ArrayDeque<SlowSample> qSlowSamples = new java.util.ArrayDeque<>();
    private volatile double lastTickMsTotal = 0.0;
    private volatile double lastTickMsChunks = 0.0;
    private volatile double lastTickMsQueue = 0.0;
    private volatile double lastTickMsProgress = 0.0;
    private volatile int lastDynamicBudget = 1;
    private volatile double avgPerBlockMsEstimate = 1.0;
    public PasteEngine(MinecraftServer server, SchemPasteConfig cfg) {
        this.server = server;
        this.cfg = cfg;
        this.backgroundExecutor = Executors.newFixedThreadPool(cfg.backgroundThreads, r -> {
            Thread t = new Thread(r, "SchemPaste-Background");
            t.setDaemon(true);
            return t;
        });
        this.chunkManager = new DynamicChunkManager(server.getWorld(World.OVERWORLD), cfg);
    }

    private static BlockRotation toRotation(PlacementConfig.Rotation r) {
        switch (r) {
            case CLOCKWISE_90:
                return BlockRotation.CLOCKWISE_90;
            case CLOCKWISE_180:
                return BlockRotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90:
                return BlockRotation.COUNTERCLOCKWISE_90;
            default:
                return BlockRotation.NONE;
        }
    }

    private static BlockMirror toMirror(PlacementConfig.Mirror m) {
        switch (m) {
            case LEFT_RIGHT:
                return BlockMirror.LEFT_RIGHT;
            case FRONT_BACK:
                return BlockMirror.FRONT_BACK;
            default:
                return BlockMirror.NONE;
        }
    }

    public void tick() {

        int dynamicBudget = computeDynamicBudget();
        lastDynamicBudget = dynamicBudget;
        enqueueLimiter.reset(Math.max(0, dynamicBudget));

        final long t0 = System.nanoTime();
        final long a0 = System.nanoTime();
        chunkManager.tick();
        final long a1 = System.nanoTime();
        processMainThreadQueue();
        final long a2 = System.nanoTime();
        updateProgress();
        final long t1 = System.nanoTime();


        lastTickMsChunks = (a1 - a0) / 1_000_000.0;
        lastTickMsQueue = (a2 - a1) / 1_000_000.0;
        lastTickMsProgress = (t1 - a2) / 1_000_000.0;
        lastTickMsTotal = (t1 - t0) / 1_000_000.0;

        maybeLogStatus();
    }

    private int computeDynamicBudget() {
        int budgetMs = (cfg.mainThreadBudgetMs > 0 ? cfg.mainThreadBudgetMs : 40);
        double est = Math.max(MIN_AVG_PER_BLOCK_MS,
                Math.min(MAX_AVG_PER_BLOCK_MS, avgPerBlockMsEstimate));
        long tokens = (long) Math.floor(budgetMs / Math.max(1e-3, est));
        return (int) Math.max(1L, tokens);
    }

    private void processMainThreadQueue() {
        int processed = 0;
        int maxPerTick = Math.max(1, lastDynamicBudget);
        final long start = System.nanoTime();
        final long budgetNanos = (long) ((cfg.mainThreadBudgetMs > 0 ? cfg.mainThreadBudgetMs : 40)) * 1_000_000L;


        ServerWorld worldTop = server.getWorld(World.OVERWORLD);
        boolean suppressed = false;
        if (cfg.suppressNeighborUpdates && worldTop != null) {
            try {
                net.rms.schempaste.util.WorldUtils.setShouldPreventBlockUpdates(worldTop, true);
                suppressed = true;
            } catch (Throwable t) {
            }
        }

        try {
            while (!mainThreadQueue.isEmpty() && processed < maxPerTick) {
                BlockPlacementTask task = mainThreadQueue.poll();
                if (task == null) break;

                try {
                    ServerWorld world = server.getWorld(World.OVERWORLD);
                    if (world != null) {

                        ChunkPos chunkPos = new ChunkPos(task.pos);
                        PasteJob job = activeJobs.get(task.jobId);

                        // Cancelled job or missing job: skip placement and clean counters
                        if (job == null || job.cancelled.get() || cancelledJobs.contains(task.jobId)) {
                            if (job != null) {
                                job.queuedBlocks.decrementAndGet();
                            }
                            decrementGlobalChunkCount(task.chunkPos);
                            globalQueuedTasks.decrementAndGet();
                            qCountSkipped.incrementAndGet();
                            processed++;
                            if ((System.nanoTime() - start) >= budgetNanos) {
                                break;
                            }
                            continue;
                        }
                        if (!chunkManager.ensureChunkLoaded(chunkPos)) {

                            mainThreadQueue.offer(task);
                            if (cfg.enableDynamicChunkLoading && cfg.maxLoadedChunks > 0) {
                                if (job != null && job.pausedByCap.compareAndSet(false, true)) {
                                    long now = System.currentTimeMillis();
                                    long last = job.lastStatusLogMs.get();
                                    if (now - last >= 3000 && job.lastStatusLogMs.compareAndSet(last, now)) {
                                        SchemPaste.LOGGER.info(
                                                "Paste paused: waiting for chunk to load (capacity reached) [job={}]",
                                                job.id.substring(0, Math.min(8, job.id.length())));
                                    }
                                }
                            }
                            break;
                        }
                        if (job != null && job.pausedByCap.compareAndSet(true, false)) {
                            long now = System.currentTimeMillis();
                            long last = job.lastStatusLogMs.get();
                            if (now - last >= 3000 && job.lastStatusLogMs.compareAndSet(last, now)) {
                                SchemPaste.LOGGER.info(
                                        "Paste resumed: chunk capacity available [job={}]",
                                        job.id.substring(0, Math.min(8, job.id.length())));
                            }
                        }


                        long tGet0 = System.nanoTime();
                        BlockState stateOld = world.getBlockState(task.pos);
                        long tGet1 = System.nanoTime();
                        long nsGet = (tGet1 - tGet0);
                        ReplaceBehavior replace = (job != null) ? job.replaceBehavior : ReplaceBehavior.WITH_NON_AIR;

                        if ((replace == ReplaceBehavior.NONE && !stateOld.isAir()) ||
                                (replace == ReplaceBehavior.WITH_NON_AIR && task.state.isAir())) {

                            if (job != null) {
                                job.queuedBlocks.decrementAndGet();
                            }

                            decrementGlobalChunkCount(task.chunkPos);
                            globalQueuedTasks.decrementAndGet();
                            qCountSkipped.incrementAndGet();
                            qNsGetState.addAndGet(nsGet);
                            processed++;

                            if ((System.nanoTime() - start) >= budgetNanos) {
                                break;
                            }
                            continue;
                        }


                        long nsClear = 0L;
                        if (stateOld.hasBlockEntity() && (!task.state.hasBlockEntity() || task.tileEntity != null)) {
                            long tC0 = System.nanoTime();
                            //#if MC < 12000
                            int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS | Block.SKIP_LIGHTING_UPDATES;
                            //#else
                            //$$ int flags = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE | net.minecraft.block.Block.SKIP_DROPS;
                            //#endif
                            world.setBlockState(task.pos, Blocks.BARRIER.getDefaultState(), flags);
                            long tC1 = System.nanoTime();
                            nsClear = (tC1 - tC0);
                            qCountClearedTe.incrementAndGet();
                        }


                        long tS0 = System.nanoTime();
                        //#if MC < 12000
                        int flagsPlace = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS | Block.SKIP_LIGHTING_UPDATES;
                        //#else
                        //$$ int flagsPlace = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE | net.minecraft.block.Block.SKIP_DROPS;
                        //#endif
                        boolean placedOk = world.setBlockState(task.pos, task.state, flagsPlace);
                        long tS1 = System.nanoTime();
                        long nsSet = (tS1 - tS0);
                        if (placedOk) {

                            long nsTe = 0L;
                            if (task.tileEntity != null) {
                                BlockEntity te = world.getBlockEntity(task.pos);
                                if (te != null) {
                                    NbtCompound nbt = task.tileEntity.copy();
                                    nbt.putInt("x", task.pos.getX());
                                    nbt.putInt("y", task.pos.getY());
                                    nbt.putInt("z", task.pos.getZ());

                                    long tT0 = System.nanoTime();
                                    try {
                                        te.readNbt(nbt);
                                        if (cfg.clearInventories && te instanceof Inventory) {
                                            ((Inventory) te).clear();
                                        }
                                    } catch (Exception e) {
                                        SchemPaste.LOGGER.warn("Failed to load BlockEntity data for {} @ {}", task.state, task.pos);
                                    }
                                    long tT1 = System.nanoTime();
                                    nsTe = (tT1 - tT0);
                                    qCountWithTe.incrementAndGet();
                                }
                            }

                            qNsGetState.addAndGet(nsGet);
                            qNsClearOldTe.addAndGet(nsClear);
                            qNsSetBlock.addAndGet(nsSet);
                            qNsApplyTe.addAndGet(nsTe);
                            qCountPlaced.incrementAndGet();

                            double getMs = nsGet / 1_000_000.0;
                            double clearMs = nsClear / 1_000_000.0;
                            double setMs = nsSet / 1_000_000.0;
                            double teMs = nsTe / 1_000_000.0;
                            double totalMs = getMs + clearMs + setMs + teMs;
                            if (totalMs >= SLOW_SAMPLE_THRESHOLD_MS) {
                                synchronized (qSlowSamples) {
                                    if (qSlowSamples.size() >= MAX_SLOW_SAMPLES) {
                                        qSlowSamples.removeFirst();
                                    }
                                    qSlowSamples.addLast(new SlowSample(task.pos, totalMs, getMs, clearMs, setMs, teMs));
                                }
                            }
                        }


                        if (job != null) {
                            job.placedBlocks.incrementAndGet();
                            job.queuedBlocks.decrementAndGet();
                        }

                        decrementGlobalChunkCount(task.chunkPos);
                        globalQueuedTasks.decrementAndGet();
                    }
                } catch (Exception e) {
                    SchemPaste.LOGGER.error("Error placing block at {}: {}", task.pos, e.getMessage());
                }
                processed++;

                if ((System.nanoTime() - start) >= budgetNanos) {
                    break;
                }
            }
        } finally {
            if (suppressed) {
                try {
                    net.rms.schempaste.util.WorldUtils.setShouldPreventBlockUpdates(worldTop, false);
                } catch (Throwable t) {
                }
            }
        }
    }

    private LayerRange buildLayerRangeFromConfig() {
        if (cfg == null) return null;
        String modeStr = cfg.defaultLayerMode == null ? "all" : cfg.defaultLayerMode.trim().toLowerCase();
        String axisStr = cfg.defaultLayerAxis == null ? "y" : cfg.defaultLayerAxis.trim().toLowerCase();

        net.rms.schempaste.util.LayerRange.LayerMode mode;
        switch (modeStr) {
            case "single_layer":
            case "single":
                mode = net.rms.schempaste.util.LayerRange.LayerMode.SINGLE_LAYER;
                break;
            case "all_above":
                mode = net.rms.schempaste.util.LayerRange.LayerMode.ALL_ABOVE;
                break;
            case "all_below":
                mode = net.rms.schempaste.util.LayerRange.LayerMode.ALL_BELOW;
                break;
            case "layer_range":
            case "range":
                mode = net.rms.schempaste.util.LayerRange.LayerMode.LAYER_RANGE;
                break;
            case "all":
            default:
                mode = net.rms.schempaste.util.LayerRange.LayerMode.ALL;
                break;
        }

        net.minecraft.util.math.Direction.Axis axis;
        switch (axisStr) {
            case "x":
                axis = net.minecraft.util.math.Direction.Axis.X;
                break;
            case "z":
                axis = net.minecraft.util.math.Direction.Axis.Z;
                break;
            case "y":
            default:
                axis = net.minecraft.util.math.Direction.Axis.Y;
                break;
        }

        if (mode == net.rms.schempaste.util.LayerRange.LayerMode.ALL) {
            return new LayerRange(mode, axis, 0, 0, 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        return new LayerRange(
                mode,
                axis,
                cfg.defaultLayerSingle,
                cfg.defaultLayerAbove,
                cfg.defaultLayerBelow,
                cfg.defaultLayerRangeMin,
                cfg.defaultLayerRangeMax
        );
    }

    private void updateProgress() {
        long now = System.currentTimeMillis();

        for (PasteJob job : activeJobs.values()) {

            if (now - job.lastProgressTime >= cfg.progressUpdateIntervalMs) {
                int placed = job.placedBlocks.get();
                int total = job.totalBlocks.get();
                if (total > 0 && placed < total && cfg.enableProgressMessages) {
                    int progress = (placed * 100) / total;
                    int lastPct = job.lastProgressPercent.get();
                    if (progress >= Math.max(0, lastPct + 1)) {
                        for (var p : server.getPlayerManager().getPlayerList()) {
                            p.sendMessage(net.minecraft.text.Text.of(
                                    "Paste progress: " + progress + "% (" + placed + "/" + total + ")"), true);
                        }
                        job.lastProgressPercent.set(progress);
                    }
                }
                job.lastProgressTime = now;
            }


            if (job.backgroundComplete.get() && job.queuedBlocks.get() == 0) {
                completeJob(job);
            }
        }
    }

    private void maybeLogStatus() {
        long now = System.currentTimeMillis();
        long last = lastStatusLogMs.get();
        if (now - last < 10_000L) return;
        if (!lastStatusLogMs.compareAndSet(last, now)) return;
        if (activeJobs.isEmpty()) return;

        java.util.List<StatusJob> jobs = new java.util.ArrayList<>(activeJobs.size());
        for (PasteJob job : activeJobs.values()) {
            String shortId = job.id.substring(0, Math.min(8, job.id.length()));
            jobs.add(new StatusJob(shortId, job.placedBlocks.get(), job.totalBlocks.get()));
        }


        double qMsGet = qNsGetState.getAndSet(0L) / 1_000_000.0;
        double qMsClear = qNsClearOldTe.getAndSet(0L) / 1_000_000.0;
        double qMsSet = qNsSetBlock.getAndSet(0L) / 1_000_000.0;
        double qMsTe = qNsApplyTe.getAndSet(0L) / 1_000_000.0;
        int qPlaced = qCountPlaced.getAndSet(0);
        int qWithTe = qCountWithTe.getAndSet(0);
        int qSkipped = qCountSkipped.getAndSet(0);
        int qCleared = qCountClearedTe.getAndSet(0);
        double qAvgPerBlock = qPlaced > 0 ? (qMsGet + qMsClear + qMsSet + qMsTe) / qPlaced : 0.0;
        if (qAvgPerBlock > 0.0) {
            double prev = avgPerBlockMsEstimate;
            double next = (1.0 - ESTIMATE_SMOOTHING_ALPHA) * prev + ESTIMATE_SMOOTHING_ALPHA * qAvgPerBlock;
            if (next < MIN_AVG_PER_BLOCK_MS) next = MIN_AVG_PER_BLOCK_MS;
            if (next > MAX_AVG_PER_BLOCK_MS) next = MAX_AVG_PER_BLOCK_MS;
            avgPerBlockMsEstimate = next;
        }

        java.util.List<SlowSample> slow;
        synchronized (qSlowSamples) {
            slow = new java.util.ArrayList<>(qSlowSamples);
            qSlowSamples.clear();
        }

        StatusSnapshot snap = new StatusSnapshot(
                jobs,
                chunkManager.getLoadedChunkCount(),
                SchemPaste.getTps(),
                SchemPaste.getAverageMspt(),
                SchemPaste.getLastTickMs(),
                lastTickMsChunks,
                lastTickMsQueue,
                lastTickMsProgress,
                lastTickMsTotal,
                qMsGet, qMsClear, qMsSet, qMsTe, qAvgPerBlock,
                qPlaced, qWithTe, qSkipped, qCleared, slow
        );

        backgroundExecutor.execute(() -> logStatus(snap));
    }

    private void logStatus(StatusSnapshot s) {

        double base = Math.max(0.0001, s.msTotal);
        double otherMs = Math.max(0.0, base - (s.msChunks + s.msQueue + s.msProgress));
        double pctChunks = Math.min(100.0, (s.msChunks / base) * 100.0);
        double pctQueue = Math.min(100.0, (s.msQueue / base) * 100.0);
        double pctProgress = Math.min(100.0, (s.msProgress / base) * 100.0);
        double pctOther = Math.max(0.0, 100.0 - (pctChunks + pctQueue + pctProgress));

        StringBuilder prog = new StringBuilder();
        prog.append("progress[");
        boolean first = true;
        for (StatusJob j : s.jobs) {
            int pct = (j.total > 0) ? (j.placed * 100) / j.total : 0;
            if (!first) prog.append(", ");
            prog.append("job ").append(j.idShort).append("=")
                    .append(pct).append("%(").append(j.placed).append("/").append(j.total).append(")");
            first = false;
        }
        prog.append("]");

        String queueDetail = String.format(
                java.util.Locale.ROOT,
                " queueDetail{get=%.1fms, clear=%.1fms, set=%.1fms, te=%.1fms; avgPerBlock=%.2fms; placed=%d, te=%d, skipped=%d, cleared=%d}",
                s.qMsGet, s.qMsClear, s.qMsSet, s.qMsTe, s.qAvgPerBlock, s.qPlaced, s.qWithTe, s.qSkipped, s.qCleared
        );

        String slowStr = "";
        if (s.slowSamples != null && !s.slowSamples.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(" slow[");
            int i = 0;
            for (SlowSample sm : s.slowSamples) {
                if (i++ > 0) sb.append(", ");
                sb.append("(").append(sm.x).append(",").append(sm.y).append(",").append(sm.z).append(") ");
                sb.append("total=").append(String.format(java.util.Locale.ROOT, "%.1fms", sm.totalMs));
                sb.append(" set=").append(String.format(java.util.Locale.ROOT, "%.1f", sm.setMs));
                sb.append(" te=").append(String.format(java.util.Locale.ROOT, "%.1f", sm.teMs));
                if (i >= MAX_SLOW_SAMPLES) break;
            }
            sb.append("]");
            slowStr = sb.toString();
        }

        SchemPaste.LOGGER.info(
                "Status: jobs={}, {}; chunks={}; TPS={}; MSPT={}; tick{{chunks={}ms({}%), queue={}ms({}%), progress={}ms({}%), other={}ms({}%))}}{}{}",
                s.jobs.size(),
                prog.toString(),
                s.chunksLoaded,
                String.format(java.util.Locale.ROOT, "%.1f", s.tps),
                String.format(java.util.Locale.ROOT, "%.1f", s.mspt),
                String.format(java.util.Locale.ROOT, "%.1f", s.msChunks),
                String.format(java.util.Locale.ROOT, "%.0f", pctChunks),
                String.format(java.util.Locale.ROOT, "%.1f", s.msQueue),
                String.format(java.util.Locale.ROOT, "%.0f", pctQueue),
                String.format(java.util.Locale.ROOT, "%.1f", s.msProgress),
                String.format(java.util.Locale.ROOT, "%.0f", pctProgress),
                String.format(java.util.Locale.ROOT, "%.1f", Math.max(0.0, otherMs)),
                String.format(java.util.Locale.ROOT, "%.0f", Math.max(0.0, pctOther)),
                queueDetail,
                slowStr
        );
    }

    private void completeJob(PasteJob job) {
        if (job.isCompleted.compareAndSet(false, true)) {
            activeJobs.remove(job.id);
            long duration = System.currentTimeMillis() - job.startTime;

            SchemPaste.LOGGER.info("Paste completed [job={}]: placed {} blocks in {} ms",
                    job.id.substring(0, Math.min(8, job.id.length())),
                    job.placedBlocks.get(), duration);

            for (var p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(net.minecraft.text.Text.of(
                        "Paste finished! Placed " + job.placedBlocks.get() + " blocks"), false);
            }


        }
    }

    public void enqueuePaste(LitematicFile file, PlacementConfig.Placement placement, ServerWorld world) {
        enqueuePaste(file, placement, world, null, -1, null);
    }

    public void enqueuePaste(LitematicFile file, PlacementConfig.Placement placement, ServerWorld world,
                             ReplaceBehavior replaceOverride, int rateOverride) {
        enqueuePaste(file, placement, world, replaceOverride, rateOverride, null);
    }

    public void enqueuePaste(LitematicFile file, PlacementConfig.Placement placement, ServerWorld world,
                             ReplaceBehavior replaceOverride, int rateOverride, LayerRange layerRange) {

        String jobId = UUID.randomUUID().toString();
        String name = file.name != null && !file.name.isEmpty() ? file.name : placement.fileName;
        ReplaceBehavior replace = replaceOverride != null ? replaceOverride : ReplaceBehavior.fromString(cfg.defaultReplace);


        if (layerRange == null) {
            layerRange = buildLayerRangeFromConfig();
        }

        PasteJob job = new PasteJob(jobId);
        job.replaceBehavior = replace;
        activeJobs.put(jobId, job);


        final LayerRange finalLayerRange = layerRange;
        CompletableFuture.runAsync(() -> {
            try {
                String posStr = placement.origin.position[0] + "," + placement.origin.position[1] + "," + placement.origin.position[2];
                for (var p : server.getPlayerManager().getPlayerList()) {
                    p.sendMessage(net.minecraft.text.Text.of("Start staged paste " + name + " @ " + posStr), false);
                }

                processLitematicFileBackground(world, file, placement, replace, finalLayerRange, job);

            } catch (Exception e) {
                SchemPaste.LOGGER.error("Error processing paste job {}", jobId, e);
                job.backgroundComplete.set(true);

                server.execute(() -> {
                    for (var p : server.getPlayerManager().getPlayerList()) {
                        p.sendMessage(net.minecraft.text.Text.of("Paste failed: " + name + " - " + e.getMessage()), false);
                    }
                });
            }
        }, backgroundExecutor);

        SchemPaste.LOGGER.info("Started staged paste job [{}]: {}", jobId.substring(0, 8), name);
    }

    private void processLitematicFileBackground(ServerWorld world, LitematicFile file, PlacementConfig.Placement placement,
                                                ReplaceBehavior replace, LayerRange layerRange, PasteJob job) {
        // Abort early if job was cancelled
        if (job.cancelled.get() || cancelledJobs.contains(job.id)) {
            return;
        }
        BlockPos origin = new BlockPos(placement.origin.position[0], placement.origin.position[1], placement.origin.position[2]);
        BlockRotation rotMain = toRotation(placement.rotation);
        BlockMirror mirMain = toMirror(placement.mirror);


        Map<String, RegionOverride> overrides = new java.util.HashMap<>();
        if (placement.subregions != null) {
            for (PlacementConfig.Subregion sr : placement.subregions) {
                BlockPos pos = new BlockPos(sr.position[0], sr.position[1], sr.position[2]);
                overrides.put(sr.name, new RegionOverride(pos, toRotation(sr.rotation), toMirror(sr.mirror)));
            }
        }


        int totalCount = 0;
        {
            for (Map.Entry<String, LitematicFile.Region> e : file.regions.entrySet()) {
                String regionName = e.getKey();
                LitematicFile.Region region = e.getValue();

                RegionOverride ov = overrides.get(regionName);

                Vec3i size = region.size;
                Vec3i absSize = new Vec3i(Math.abs(size.getX()), Math.abs(size.getY()), Math.abs(size.getZ()));

                BlockRotation rot = ov != null ? rotMain.rotate(ov.rotation) : rotMain;
                BlockMirror mir = mirMain;
                BlockMirror mirSub = ov != null ? ov.mirror : BlockMirror.NONE;
                if (mirSub != BlockMirror.NONE &&
                        (rotMain == BlockRotation.CLOCKWISE_90 || rotMain == BlockRotation.COUNTERCLOCKWISE_90)) {
                    mirSub = mirSub == BlockMirror.FRONT_BACK ? BlockMirror.LEFT_RIGHT : BlockMirror.FRONT_BACK;
                }

                BlockPos adjustedRelativePos = new BlockPos(
                        region.relativePos.getX() + (size.getX() < 0 ? size.getX() + 1 : 0),
                        region.relativePos.getY() + (size.getY() < 0 ? size.getY() + 1 : 0),
                        region.relativePos.getZ() + (size.getZ() < 0 ? size.getZ() + 1 : 0)
                );

                BlockPos regionBase = ov != null ? ov.worldPos :
                        origin.add(PositionUtils.getTransformedBlockPos(adjustedRelativePos, mir, rot));


                BlockPos.Mutable local = new BlockPos.Mutable();
                for (int ly = 0; ly < absSize.getY(); ly++) {
                    for (int lz = 0; lz < absSize.getZ(); lz++) {
                        for (int lx = 0; lx < absSize.getX(); lx++) {
                            local.set(lx, ly, lz);
                            BlockState state = region.stateAt(lx, ly, lz);


                            if (state.getBlock() == Blocks.STRUCTURE_VOID) {
                                continue;
                            }

                            if (state.isAir() && replace != ReplaceBehavior.ALL) {
                                continue;
                            }


                            BlockPos worldOffset = PositionUtils.getTransformedBlockPos(local, mirSub, rot);
                            BlockPos worldPos = regionBase.add(worldOffset);
                            if (!shouldPasteBlock(worldPos, layerRange)) {
                                continue;
                            }

                            totalCount++;
                        }
                    }
                }
            }
        }
        job.totalBlocks.set(totalCount);

        for (Map.Entry<String, LitematicFile.Region> entry : file.regions.entrySet()) {
            if (job.cancelled.get() || cancelledJobs.contains(job.id) || Thread.currentThread().isInterrupted()) {
                return;
            }
            String regionName = entry.getKey();
            LitematicFile.Region region = entry.getValue();
            RegionOverride ov = overrides.get(regionName);

            processRegionBackground(world, regionName, region, origin, rotMain, mirMain, ov, replace, layerRange, job);
        }

        job.backgroundComplete.set(true);
        SchemPaste.LOGGER.info("Background scheduling completed [job={}]", job.id.substring(0, 8));
    }

    private void processRegionBackground(ServerWorld world, String regionName, LitematicFile.Region region,
                                         BlockPos origin, BlockRotation rotMain, BlockMirror mirMain,
                                         RegionOverride regionOverride, ReplaceBehavior replace, LayerRange layerRange,
                                         PasteJob job) {
        // Check cancellation before heavy work
        if (job.cancelled.get() || cancelledJobs.contains(job.id)) {
            return;
        }
        Vec3i size = region.size;
        Vec3i absSize = new Vec3i(Math.abs(size.getX()), Math.abs(size.getY()), Math.abs(size.getZ()));

        BlockRotation rot = regionOverride != null ? rotMain.rotate(regionOverride.rotation) : rotMain;

        BlockMirror mir = mirMain;


        BlockMirror mirSub = regionOverride != null ? regionOverride.mirror : BlockMirror.NONE;
        if (mirSub != BlockMirror.NONE &&
                (rotMain == BlockRotation.CLOCKWISE_90 || rotMain == BlockRotation.COUNTERCLOCKWISE_90)) {
            mirSub = mirSub == BlockMirror.FRONT_BACK ? BlockMirror.LEFT_RIGHT : BlockMirror.FRONT_BACK;
        }


        BlockPos adjustedRelativePos = new BlockPos(
                region.relativePos.getX() + (size.getX() < 0 ? size.getX() + 1 : 0),
                region.relativePos.getY() + (size.getY() < 0 ? size.getY() + 1 : 0),
                region.relativePos.getZ() + (size.getZ() < 0 ? size.getZ() + 1 : 0)
        );

        BlockPos regionBase = regionOverride != null ? regionOverride.worldPos :
                origin.add(PositionUtils.getTransformedBlockPos(adjustedRelativePos, mir, rot));

        BlockPos.Mutable posMutable = new BlockPos.Mutable();


        int totalExamined = 0;
        int airSkipped = 0;
        int structureVoidSkipped = 0;
        int actuallyQueued = 0;


        Set<ChunkPos> chunkSet = ChunkBoundsUtils.getIntersectingChunks(absSize, regionBase, rot, mirSub);
        java.util.List<ChunkPos> chunks = new java.util.ArrayList<>(chunkSet);
        chunks.sort(java.util.Comparator
                .comparingInt((ChunkPos c) -> c.z)
                .thenComparingInt(c -> c.x));


        synchronized (job.chunkOrder) {
            for (ChunkPos c : chunks) {
                if (!job.chunkOrder.contains(c)) {
                    job.chunkOrder.add(c);
                }
            }
        }

        for (ChunkPos cpos : chunks) {
            if (job.cancelled.get() || cancelledJobs.contains(job.id) || Thread.currentThread().isInterrupted()) {
                return;
            }
            var inter = ChunkBoundsUtils.getChunkIntersection(absSize, regionBase, rot, mirSub, cpos);
            if (inter == null) continue;

            for (int y = inter.minY; y <= inter.maxY; y++) {
                for (int z = inter.minZ; z <= inter.maxZ; z++) {
                    for (int x = inter.minX; x <= inter.maxX; x++) {
                        if (job.cancelled.get() || cancelledJobs.contains(job.id) || Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        BlockPos worldPos = new BlockPos(x, y, z);


                        BlockPos rel = PositionUtils.getReverseTransformedBlockPos(worldPos.subtract(regionBase), mirSub, rot);
                        int lx = rel.getX();
                        int ly = rel.getY();
                        int lz = rel.getZ();
                        if (lx < 0 || ly < 0 || lz < 0 || lx >= absSize.getX() || ly >= absSize.getY() || lz >= absSize.getZ()) {
                            continue;
                        }

                        totalExamined++;
                        BlockState state = region.stateAt(lx, ly, lz);

                        if (state.getBlock() == Blocks.STRUCTURE_VOID) {
                            structureVoidSkipped++;
                            continue;
                        }


                        if (state.isAir() && replace != ReplaceBehavior.ALL) {
                            airSkipped++;
                            continue;
                        }

                        if (!shouldPasteBlock(worldPos, layerRange)) {
                            continue;
                        }


                        posMutable.set(lx, ly, lz);
                        NbtCompound teNBT = region.tileEntities.get(posMutable.toImmutable());


                        if (state.hasBlockEntity() && state.isOf(Blocks.CHEST) &&
                                mir != BlockMirror.NONE &&
                                !(state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) &&
                                cfg.fixChestMirror) {

                            Direction facing = state.get(ChestBlock.FACING);
                            Direction.Axis axis = facing.getAxis();
                            ChestType type = state.get(ChestBlock.CHEST_TYPE).getOpposite();

                            if (mir != BlockMirror.NONE && axis != Direction.Axis.Y) {
                                Direction facingAdj = type == ChestType.LEFT ?
                                        facing.rotateCounterclockwise(Direction.Axis.Y) :
                                        facing.rotateClockwise(Direction.Axis.Y);
                                BlockPos posAdj = new BlockPos(lx, ly, lz).offset(facingAdj);
                                NbtCompound adjNBT = region.tileEntities.get(posAdj);
                                if (adjNBT != null) {
                                    teNBT = adjNBT.copy();
                                }
                            }
                        }


                        if (mir != BlockMirror.NONE) {
                            state = state.mirror(mir);
                        }
                        if (mirSub != BlockMirror.NONE) {
                            state = state.mirror(mirSub);
                        }
                        if (rot != BlockRotation.NONE) {
                            state = state.rotate(rot);
                        }


                        int softCap = Math.max(1, lastDynamicBudget * QUEUE_SOFT_CAP_MULTIPLIER);
                        while (globalQueuedTasks.get() >= softCap) {
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            if (job.cancelled.get() || cancelledJobs.contains(job.id)) {
                                return;
                            }
                        }
                        enqueueLimiter.acquire();


                        ChunkPos taskChunk = cpos;
                        mainThreadQueue.offer(new BlockPlacementTask(worldPos, taskChunk, state, teNBT, job.id));
                        job.queuedBlocks.incrementAndGet();

                        long key = ChunkPos.toLong(taskChunk.x, taskChunk.z);
                        chunkQueueCounts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                        chunkOwnerJob.putIfAbsent(key, job.id);
                        globalQueuedTasks.incrementAndGet();
                        actuallyQueued++;
                    }
                }
            }
        }

        SchemPaste.LOGGER.info(
                "Region {} background pass [job={}]: scanned {}, skipped {} air, skipped {} structure_void, enqueued {}",
                regionName, job.id.substring(0, 8), totalExamined, airSkipped, structureVoidSkipped, actuallyQueued);


    }

    public void shutdown() {
        chunkManager.shutdown();
        backgroundExecutor.shutdown();
        try {
            if (!backgroundExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            backgroundExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void decrementGlobalChunkCount(ChunkPos chunk) {
        if (!cfg.enableDynamicChunkLoading || chunk == null) return;
        long key = ChunkPos.toLong(chunk.x, chunk.z);
        AtomicInteger cnt = chunkQueueCounts.get(key);
        if (cnt == null) return;
        int left = cnt.decrementAndGet();
        if (left <= 0) {
            chunkQueueCounts.remove(key);
            chunkOwnerJob.remove(key);
            server.execute(() -> chunkManager.markChunkForUnload(chunk));
        }
    }

    private boolean shouldPasteBlock(BlockPos pos, LayerRange layerRange) {
        if (layerRange == null || layerRange.getMode() == LayerRange.LayerMode.ALL) return true;
        return layerRange.isPositionWithinRange(pos);
    }

    private boolean shouldPasteEntity(Vec3d pos, LayerRange layerRange) {
        if (layerRange == null || layerRange.getMode() == LayerRange.LayerMode.ALL) return true;
        return layerRange.isPositionWithinRange((int) pos.x, (int) pos.y, (int) pos.z);
    }

    public String getChunkManagerStatus() {
        if (cfg.enableDynamicChunkLoading) {
            int max = chunkManager.getMaxLoadedChunks();
            if (max <= 0) {
                return String.format("ChunkManager: loaded %d (unlimited)",
                        chunkManager.getLoadedChunkCount());
            }
            return String.format("ChunkManager: %d/%d loaded",
                    chunkManager.getLoadedChunkCount(), max);
        }
        return "ChunkManager: disabled";
    }

    public int getActiveJobCount() {
        return activeJobs.size();
    }

    public int cancelAllJobs() {
        int count = 0;
        for (PasteJob job : activeJobs.values()) {
            if (job.cancelled.compareAndSet(false, true)) {
                cancelledJobs.add(job.id);
                count++;
            }
        }
        // Remove from active map to avoid stale progress/status
        for (String id : new java.util.ArrayList<>(activeJobs.keySet())) {
            PasteJob j = activeJobs.get(id);
            if (j == null) continue;
            if (j.cancelled.get()) {
                activeJobs.remove(id);
            }
        }
        return count;
    }

    private static final class SlowSample {
        final int x, y, z;
        final double totalMs, getMs, clearMs, setMs, teMs;

        SlowSample(BlockPos pos, double totalMs, double getMs, double clearMs, double setMs, double teMs) {
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
            this.totalMs = totalMs;
            this.getMs = getMs;
            this.clearMs = clearMs;
            this.setMs = setMs;
            this.teMs = teMs;
        }
    }

    private static final class StatusJob {
        final String idShort;
        final int placed;
        final int total;

        StatusJob(String idShort, int placed, int total) {
            this.idShort = idShort;
            this.placed = placed;
            this.total = total;
        }
    }

    private static final class StatusSnapshot {
        final java.util.List<StatusJob> jobs;
        final int chunksLoaded;
        final double tps;
        final double mspt;
        final double serverTickMs;
        final double msChunks;
        final double msQueue;
        final double msProgress;
        final double msTotal;

        final double qMsGet, qMsClear, qMsSet, qMsTe, qAvgPerBlock;
        final int qPlaced, qWithTe, qSkipped, qCleared;
        final java.util.List<SlowSample> slowSamples;

        StatusSnapshot(java.util.List<StatusJob> jobs, int chunksLoaded, double tps, double mspt,
                       double serverTickMs, double msChunks, double msQueue, double msProgress, double msTotal,
                       double qMsGet, double qMsClear, double qMsSet, double qMsTe, double qAvgPerBlock,
                       int qPlaced, int qWithTe, int qSkipped, int qCleared, java.util.List<SlowSample> slowSamples) {
            this.jobs = jobs;
            this.chunksLoaded = chunksLoaded;
            this.tps = tps;
            this.mspt = mspt;
            this.serverTickMs = serverTickMs;
            this.msChunks = msChunks;
            this.msQueue = msQueue;
            this.msProgress = msProgress;
            this.msTotal = msTotal;
            this.qMsGet = qMsGet;
            this.qMsClear = qMsClear;
            this.qMsSet = qMsSet;
            this.qMsTe = qMsTe;
            this.qAvgPerBlock = qAvgPerBlock;
            this.qPlaced = qPlaced;
            this.qWithTe = qWithTe;
            this.qSkipped = qSkipped;
            this.qCleared = qCleared;
            this.slowSamples = slowSamples;
        }
    }

    private static class BlockPlacementTask {
        final BlockPos pos;
        final ChunkPos chunkPos;
        final BlockState state;
        final NbtCompound tileEntity;
        final String jobId;

        BlockPlacementTask(BlockPos pos, ChunkPos chunkPos, BlockState state, NbtCompound tileEntity, String jobId) {
            this.pos = pos;
            this.chunkPos = chunkPos;
            this.state = state;
            this.tileEntity = tileEntity;
            this.jobId = jobId;
        }
    }

    private static class PasteJob {
        final String id;
        final AtomicInteger totalBlocks = new AtomicInteger(0);
        final AtomicInteger placedBlocks = new AtomicInteger(0);
        final AtomicInteger queuedBlocks = new AtomicInteger(0);
        final AtomicBoolean isCompleted = new AtomicBoolean(false);
        final AtomicBoolean backgroundComplete = new AtomicBoolean(false);
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final long startTime;
        final AtomicInteger lastProgressPercent = new AtomicInteger(-1);
        final java.util.List<ChunkPos> chunkOrder = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        final java.util.concurrent.ConcurrentLinkedQueue<ChunkPos> sequentialUnloadQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
        final AtomicBoolean pausedByCap = new AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicLong lastStatusLogMs = new java.util.concurrent.atomic.AtomicLong(0L);
        long lastProgressTime;
        ReplaceBehavior replaceBehavior = ReplaceBehavior.WITH_NON_AIR;

        PasteJob(String id) {
            this.id = id;
            this.startTime = System.currentTimeMillis();
            this.lastProgressTime = startTime;
        }
    }

    private static class EnqueueRateLimiter {
        private final AtomicInteger tokens = new AtomicInteger(0);

        void reset(int perTickBudget) {
            tokens.set(Math.max(0, perTickBudget));
        }

        void acquire() {
            for (; ; ) {
                int t = tokens.get();
                if (t > 0 && tokens.compareAndSet(t, t - 1)) return;
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static class RegionOverride {
        final BlockPos worldPos;
        final BlockRotation rotation;
        final BlockMirror mirror;

        RegionOverride(BlockPos worldPos, BlockRotation rotation, BlockMirror mirror) {
            this.worldPos = worldPos;
            this.rotation = rotation;
            this.mirror = mirror;
        }
    }
}
