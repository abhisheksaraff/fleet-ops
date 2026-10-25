package com.fleetops.sim;

import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.Survivor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

/**
 * Scatters hidden survivors at random positions inside an incident's area.
 *
 * <p>Pass a seeded generator (for example {@code new Random(42)}) to get exactly the same survivors
 * every run, which keeps demos and tests repeatable. The number of survivors follows the incident's
 * estimated people, clamped to a range so the map stays readable.
 *
 * <p>Use one spawner for the whole world: survivor ids are numbered per spawner, and the world
 * rejects two survivors with the same id.
 */
public final class SurvivorSpawner {

    public static final int DEFAULT_MIN_COUNT = 3;
    public static final int DEFAULT_MAX_COUNT = 20;

    private final RandomGenerator random;
    private final int minCount;
    private final int maxCount;
    private final AtomicInteger sequence = new AtomicInteger();

    public SurvivorSpawner(RandomGenerator random) {
        this(random, DEFAULT_MIN_COUNT, DEFAULT_MAX_COUNT);
    }

    public SurvivorSpawner(RandomGenerator random, int minCount, int maxCount) {
        if (minCount < 1 || maxCount < minCount) {
            throw new IllegalArgumentException("Need 1 <= minCount <= maxCount, got " + minCount + " and " + maxCount);
        }
        this.random = Objects.requireNonNull(random, "random");
        this.minCount = minCount;
        this.maxCount = maxCount;
    }

    /** How many survivors an incident gets: its estimated people, kept within the min/max range. */
    public int countFor(Incident incident) {
        return Math.max(minCount, Math.min(maxCount, incident.estimatedPeople()));
    }

    /** Creates the survivors for an incident; ids ({@code S_1}, {@code S_2}, ...) are unique per spawner. */
    public List<Survivor> spawn(Incident incident) {
        int count = countFor(incident);
        List<Survivor> survivors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            survivors.add(new Survivor("S_" + sequence.incrementAndGet(), incident.area().randomPoint(random)));
        }
        return survivors;
    }
}
