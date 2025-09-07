package net.rms.schempaste.util;

import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.rms.schempaste.SchemPaste;
import net.rms.schempaste.config.SchemPasteConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DynamicChunkManager {

    private static final ChunkTicketType<ChunkPos> SCHEMPASTE_TICKET = ChunkTicketType.create("schempaste", (pos1, pos2) -> Long.compare(ChunkPos.toLong(pos1.x, pos1.z), ChunkPos.toLong(pos2.x, pos2.z)));
    private static final long CAPACITY_LOG_THROTTLE_MS = 3000L;
    private final ServerWorld world;
    private final SchemPasteConfig config;
    private final ConcurrentHashMap<ChunkPos, ChunkInfo> loadedChunks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ChunkPos> unloadQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger currentlyLoaded = new AtomicInteger(0);
    private final AtomicLong lastCapacityLogMs = new AtomicLong(0L);


    public DynamicChunkManager(ServerWorld world, SchemPasteConfig config) {
        this.world = world;
        this.config = config;

    }


    public void tick() {
        cleanupUnusedChunks();
    }

    public boolean ensureChunkLoaded(ChunkPos chunkPos) {
        if (!config.enableDynamicChunkLoading) {
            return world.isChunkLoaded(chunkPos.x, chunkPos.z);
        }

        ChunkInfo info = loadedChunks.get(chunkPos);
        if (info != null) {
            info.updateLastAccess();
            return true;
        }


        if (config.maxLoadedChunks > 0 && currentlyLoaded.get() >= config.maxLoadedChunks) {

            boolean freed = evictOldestSync(chunkPos);
            if (!freed && currentlyLoaded.get() >= config.maxLoadedChunks) {
                long now = System.currentTimeMillis();
                long last = lastCapacityLogMs.get();
                if (now - last >= CAPACITY_LOG_THROTTLE_MS && lastCapacityLogMs.compareAndSet(last, now)) {
                    // SchemPaste.LOGGER.info("Capacity reached: waiting for eviction to free a slot ({}/{})",
                    //         currentlyLoaded.get(), config.maxLoadedChunks);
                }
                return false;
            }
        }


        if (loadChunk(chunkPos)) {
            ChunkInfo newInfo = new ChunkInfo(chunkPos);
            loadedChunks.put(chunkPos, newInfo);
            currentlyLoaded.incrementAndGet();


            // if (config.maxLoadedChunks > 0) {
            //     SchemPaste.LOGGER.debug("Loaded chunk {} ({}/{})", chunkPos, currentlyLoaded.get(), config.maxLoadedChunks);
            // } else {
            //     SchemPaste.LOGGER.debug("Loaded chunk {} (unlimited)", chunkPos);
            // }
            return true;
        }

        return false;
    }

    private boolean loadChunk(ChunkPos chunkPos) {

        try {
            world.getChunkManager().addTicket(SCHEMPASTE_TICKET, chunkPos, 32, chunkPos);
            return world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z);
        } catch (Exception e) {
            SchemPaste.LOGGER.warn("Failed to load chunk {}: {}", chunkPos, e.getMessage());
            return false;
        }
    }

    public void markChunkForUnload(ChunkPos chunkPos) {
        ChunkInfo info = loadedChunks.get(chunkPos);
        if (info != null) {

            if (!info.markedForUnload) {
                info.markForUnload(getDynamicUnloadDelayMs());
                unloadQueue.offer(chunkPos);
            }
        }
    }

    private void cleanupUnusedChunks() {
        final long start = System.nanoTime();
        final long budgetNanos = getCleanupBudgetNanos();
        long nowMs = System.currentTimeMillis();
        int processed = 0;


        ChunkPos chunkPos;
        while ((chunkPos = unloadQueue.poll()) != null) {
            ChunkInfo info = loadedChunks.get(chunkPos);
            if (info != null) {
                if (info.shouldUnload(nowMs)) {
                    if (unloadChunk(chunkPos)) {
                        loadedChunks.remove(chunkPos);
                        currentlyLoaded.decrementAndGet();
                        processed++;

                        // if (config.maxLoadedChunks > 0) {
                        //     SchemPaste.LOGGER.debug("Unloaded chunk {} ({}/{})",
                        //             chunkPos, currentlyLoaded.get(), config.maxLoadedChunks);
                        // } else {
                        //     SchemPaste.LOGGER.debug("Unloaded chunk {} (unlimited)", chunkPos);
                        // }
                    } else {

                        unloadQueue.offer(chunkPos);
                    }
                } else {

                    unloadQueue.offer(chunkPos);
                }
            }
            if (processed >= Math.max(1, getDynamicBatchSize())) break;
            if ((System.nanoTime() - start) >= budgetNanos) return;
        }


        int scanLimit = Math.max(1, Math.min(loadedChunks.size(), Math.max(1, getDynamicBatchSize()) * 4));
        int scanned = 0;
        for (var entry : loadedChunks.entrySet()) {
            if (scanned >= scanLimit) break;
            ChunkInfo info = entry.getValue();
            if (!info.markedForUnload && info.inactiveLongerThan(nowMs, getDynamicUnloadDelayMs())) {
                markChunkForUnload(entry.getKey());
            }
            scanned++;
            if ((System.nanoTime() - start) >= budgetNanos) return;
        }
    }

    private void forceCleanupOldChunks() {
        if (config.maxLoadedChunks <= 0) return;
        int target = Math.max(1, Math.min(Math.max(1, getDynamicBatchSize()), Math.max(1, config.maxLoadedChunks / 10)));
        var sortedChunks = loadedChunks.entrySet()
            .stream()
            .sorted((a, b) -> Long.compare(a.getValue().lastAccessTime.get(), b.getValue().lastAccessTime.get()))
            .limit(target)
            .toList();
        for (var entry : sortedChunks) {
            markChunkForUnload(entry.getKey());
        }
    }


    private int getDynamicBatchSize() {
        if (config.maxLoadedChunks <= 0) return 8;
        int base = Math.max(1, config.maxLoadedChunks / 8);
        if (base > 32) base = 32;
        return base;
    }

    private long getDynamicUnloadDelayMs() {
        if (config.maxLoadedChunks <= 0) return 5000L;
        int cur = Math.max(0, currentlyLoaded.get());
        int cap = Math.max(1, config.maxLoadedChunks);
        double util = Math.min(1.0, (double) cur / (double) cap);
        if (util >= 0.90) return 1000L;
        if (util >= 0.70) return 2500L;
        if (util >= 0.50) return 4000L;
        return 8000L;
    }

    private long getCleanupBudgetNanos() {
        int mainMs = (config.mainThreadBudgetMs > 0 ? config.mainThreadBudgetMs : 40);
        int baseMs = Math.max(5, Math.min(20, (int) Math.round(mainMs * 0.25)));
        if (config.maxLoadedChunks > 0) {
            int cur = Math.max(0, currentlyLoaded.get());
            int cap = Math.max(1, config.maxLoadedChunks);
            double util = Math.min(1.0, (double) cur / (double) cap);
            if (util >= 0.90) baseMs = Math.max(baseMs, 25);
        }
        return Math.max(0, baseMs) * 1_000_000L;
    }


    private boolean evictOldestSync(ChunkPos protect) {
        if (config.maxLoadedChunks <= 0) return true;
        ChunkPos oldest = null;
        long oldestTs = Long.MAX_VALUE;
        for (var e : loadedChunks.entrySet()) {
            if (e.getKey().equals(protect)) continue;
            long ts = e.getValue().lastAccessTime.get();
            if (ts < oldestTs) {
                oldestTs = ts;
                oldest = e.getKey();
            }
        }
        if (oldest == null) return false;
        if (unloadChunk(oldest)) {
            if (loadedChunks.remove(oldest) != null) {
                currentlyLoaded.decrementAndGet();
            }
            // SchemPaste.LOGGER.debug("Evicted oldest chunk {} to free capacity", oldest);
            return true;
        }
        return false;
    }

    private boolean unloadChunk(ChunkPos chunkPos) {
        try {
            world.getChunkManager().removeTicket(SCHEMPASTE_TICKET, chunkPos, 32, chunkPos);
            return true;
        } catch (Exception e) {
            SchemPaste.LOGGER.warn("Failed to unload chunk {}: {}", chunkPos, e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        // SchemPaste.LOGGER.info("Shutting down DynamicChunkManager, unloading {} chunks", loadedChunks.size());


        for (ChunkPos chunkPos : loadedChunks.keySet()) {
            unloadChunk(chunkPos);
        }

        loadedChunks.clear();
        currentlyLoaded.set(0);
    }

    public int getLoadedChunkCount() {
        return currentlyLoaded.get();
    }

    public int getMaxLoadedChunks() {
        return config.maxLoadedChunks;
    }

    public boolean isLoaded(ChunkPos pos) {
        return loadedChunks.containsKey(pos);
    }

    private static class ChunkInfo {
        final ChunkPos pos;
        final AtomicLong lastAccessTime = new AtomicLong(System.currentTimeMillis());
        final AtomicLong scheduledUnloadAt = new AtomicLong(Long.MAX_VALUE);
        volatile boolean markedForUnload = false;

        ChunkInfo(ChunkPos pos) {
            this.pos = pos;
        }

        void updateLastAccess() {
            lastAccessTime.set(System.currentTimeMillis());
        }

        void markForUnload(long baseDelayMs) {
            long now = System.currentTimeMillis();
            long jitter = baseDelayMs > 0 ? ThreadLocalRandom.current().nextLong(0, Math.max(1L, baseDelayMs / 2)) : 0L;
            markedForUnload = true;
            scheduledUnloadAt.set(now + Math.max(0L, baseDelayMs) + jitter);
        }

        boolean shouldUnload(long nowMs) {
            return markedForUnload && nowMs >= scheduledUnloadAt.get();
        }

        boolean inactiveLongerThan(long nowMs, long delayMs) {
            return (nowMs - lastAccessTime.get()) > Math.max(0L, delayMs);
        }
    }
}
