package com.fleetops.command;

import com.fleetops.domain.fleet.Unit;
import com.fleetops.sim.SimulationEngine;
import com.fleetops.sim.TickTask;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Runs mission commands. Each unit has its own lane: commands for one unit run one after another,
 * while different units' lanes advance side by side in the same tick, so several drones can sweep
 * at once. Register the queue with {@link SimulationEngine#addTickTask} and it steps the command at
 * the front of every lane on each tick.
 *
 * <ul>
 *   <li>A command that finishes is removed and reported to the {@link CommandListener}s.
 *   <li>If a command fails, the rest of that unit's lane is cancelled (they depended on it); other
 *       units carry on.
 *   <li>{@link #abort} cancels everything and puts every unit on hold.
 * </ul>
 *
 * <p>Use the queue from the thread that runs the simulation.
 */
public final class CommandQueue implements TickTask {

    private static final System.Logger LOG = System.getLogger(CommandQueue.class.getName());

    private final Map<Unit, Deque<MissionCommand>> lanes = new LinkedHashMap<>();
    private final List<CommandResult> history = new ArrayList<>();
    private final List<CommandListener> listeners = new CopyOnWriteArrayList<>();

    /** Adds a command to the end of its unit's lane. */
    public void enqueue(MissionCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.isFinished()) {
            throw new IllegalArgumentException("Cannot queue a command that has already finished: " + command.describe());
        }
        lanes.computeIfAbsent(command.unit(), unit -> new ArrayDeque<>()).addLast(command);
    }

    public void enqueueAll(Collection<? extends MissionCommand> commands) {
        commands.forEach(this::enqueue);
    }

    public void addListener(CommandListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(CommandListener listener) {
        listeners.remove(listener);
    }

    /** Steps the front command of every lane. */
    @Override
    public void onTick(SimulationEngine engine) {
        // Work on a snapshot so a listener may enqueue new commands while we iterate.
        for (Deque<MissionCommand> lane : new ArrayList<>(lanes.values())) {
            MissionCommand head = lane.peekFirst();
            if (head == null) {
                continue;
            }
            CommandStatus status = head.step(engine);
            if (status.isFinished()) {
                lane.pollFirst();
                finish(head, engine.currentTick());
                if (status == CommandStatus.FAILED) {
                    cancelRemaining(lane, "an earlier command for " + head.unit().id() + " failed", engine.currentTick());
                }
            }
        }
        lanes.values().removeIf(Deque::isEmpty);
    }

    /**
     * Emergency abort: every queued and running command is cancelled, and every unit in the fleet
     * is put on hold. Held units can be sent home afterwards with a {@link ReturnToBaseCommand}.
     */
    public void abort(SimulationEngine engine) {
        long tick = engine.currentTick();
        for (Deque<MissionCommand> lane : new ArrayList<>(lanes.values())) {
            cancelRemaining(lane, "mission aborted", tick);
        }
        lanes.values().removeIf(Deque::isEmpty);
        engine.hold(engine.world().fleet());
    }

    /**
     * Cancels every unfinished command that matches the condition, wherever it is in its lane, and
     * reports each one to the listeners. Other commands (and other units' lanes) are left alone, which
     * is what lets one mission be stopped while another carries on.
     *
     * @return how many commands were cancelled
     */
    public int cancelWhere(Predicate<MissionCommand> condition, String reason, long tick) {
        Objects.requireNonNull(condition, "condition");
        int cancelled = 0;
        for (Deque<MissionCommand> lane : new ArrayList<>(lanes.values())) {
            for (MissionCommand command : new ArrayList<>(lane)) {
                if (condition.test(command)) {
                    lane.remove(command);
                    command.cancel(reason);
                    finish(command, tick);
                    cancelled++;
                }
            }
        }
        lanes.values().removeIf(Deque::isEmpty);
        return cancelled;
    }

    /** Number of commands that have not finished yet. */
    public int pendingCount() {
        return lanes.values().stream().mapToInt(Deque::size).sum();
    }

    /** True when no command is waiting or running. */
    public boolean isIdle() {
        return pendingCount() == 0;
    }

    /** Unfinished commands for one unit, front of the lane first. */
    public List<MissionCommand> pendingFor(Unit unit) {
        Deque<MissionCommand> lane = lanes.get(unit);
        return lane == null ? List.of() : List.copyOf(lane);
    }

    /** Every unfinished command, grouped by unit. */
    public List<MissionCommand> pending() {
        return lanes.values().stream().flatMap(Deque::stream).toList();
    }

    /** Outcomes of all finished commands, in the order they finished. */
    public List<CommandResult> history() {
        return List.copyOf(history);
    }

    private void cancelRemaining(Deque<MissionCommand> lane, String reason, long tick) {
        while (!lane.isEmpty()) {
            MissionCommand command = lane.pollFirst();
            command.cancel(reason);
            finish(command, tick);
        }
    }

    private void finish(MissionCommand command, long tick) {
        CommandResult result = new CommandResult(command, command.status(), command.detail().orElse(""), tick);
        history.add(result);
        for (CommandListener listener : listeners) {
            try {
                listener.onCommandFinished(result);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Command listener failed on " + result, e);
            }
        }
    }
}
