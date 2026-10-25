package com.fleetops.domain.fleet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The root of the fleet Composite: a group of {@link FleetComponent}s (carriers with their drones,
 * helicopters, even other fleets). Operations such as {@link #recall()} or {@link #hold()} are
 * passed down to every member, and queries such as {@link #units()} gather results from all of them.
 */
public final class Fleet implements FleetComponent {

    private final String id;
    private final List<FleetComponent> members = new ArrayList<>();

    public Fleet(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public List<FleetComponent> members() {
        return List.copyOf(members);
    }

    /**
     * Adds a member to the fleet.
     *
     * @throws IllegalArgumentException if adding it would create a cycle or give two units the same id
     */
    public void add(FleetComponent member) {
        Objects.requireNonNull(member, "member");
        if (member == this || (member instanceof Fleet nested && nested.containsComponent(this))) {
            throw new IllegalArgumentException("Adding " + member.id() + " to " + id + " would create a cycle");
        }
        Set<String> knownIds = new HashSet<>();
        units().forEach(unit -> knownIds.add(unit.id()));
        for (Unit unit : member.units()) {
            if (!knownIds.add(unit.id())) {
                throw new IllegalArgumentException("Fleet " + id + " already contains a unit with id " + unit.id());
            }
        }
        members.add(member);
    }

    /** Removes a member; returns false if it was not part of this fleet. */
    public boolean remove(FleetComponent member) {
        return members.remove(member);
    }

    @Override
    public List<Unit> units() {
        return members.stream().flatMap(member -> member.units().stream()).toList();
    }

    @Override
    public void recall() {
        members.forEach(FleetComponent::recall);
    }

    @Override
    public void hold() {
        members.forEach(FleetComponent::hold);
    }

    private boolean containsComponent(FleetComponent target) {
        for (FleetComponent member : members) {
            if (member == target || (member instanceof Fleet nested && nested.containsComponent(target))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Fleet[" + id + ", " + unitCount() + " units]";
    }
}
