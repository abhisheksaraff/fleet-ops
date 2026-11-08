package com.fleetops.debrief;

import com.fleetops.mission.MissionStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * The fixed rules that turn mission numbers into a debrief. They are the whole author when no AI is
 * available, and the offline AI stand-in answers with them too, so a demo without a key still shows a
 * sensible debrief.
 */
final class DebriefRules {

    private DebriefRules() {}

    static Debrief write(DebriefFacts f, String writtenBy, boolean ai) {
        return new Debrief(summary(f), wentWell(f), problems(f), nextTime(f), writtenBy, ai);
    }

    private static String summary(DebriefFacts f) {
        String time = minutes(f.minutes());
        if (f.people() == 0) {
            return "The mission ended after " + time + " with nobody reported in the area.";
        }
        if (f.rescued() == f.people()) {
            return f.people() == 1
                    ? "The one person in the area was rescued in " + time + "."
                    : "All " + f.people() + " people were rescued in " + time + ".";
        }
        if (f.rescued() == 0) {
            return "Nobody was rescued: the mission " + ended(f.status()) + " after " + time + " with "
                    + f.people() + " " + people(f.people()) + " in the area.";
        }
        String stopped = f.status() == MissionStatus.ABORTED ? ", then the mission was stopped" : "";
        return f.rescued() + " of " + f.people() + " " + people(f.people()) + " were rescued in " + time + stopped + ".";
    }

    private static List<String> wentWell(DebriefFacts f) {
        List<String> out = new ArrayList<>();
        if (f.rescued() > 0) {
            out.add(f.rescued() + " " + people(f.rescued()) + " reached safety.");
        }
        if (f.robodogs() > 0 && f.rescued() > 0) {
            out.add(f.robodogs() + (f.robodogs() == 1 ? " robodog" : " robodogs") + " searched the ground near the drop-off alongside " + f.drones() + " drones.");
        }
        if (f.people() > 0 && f.rescued() == f.people()) {
            out.add("Nobody was left behind.");
        } else if (f.rescued() > 0 && f.foundNotRescued() == 0) {
            out.add("Everyone who was found was also picked up.");
        }
        return out;
    }

    private static List<String> problems(DebriefFacts f) {
        List<String> out = new ArrayList<>(f.notes());
        if (!f.operatorChange().isEmpty()) {
            out.add("The operator had to change the plan: " + f.operatorChange() + ".");
        }
        if (out.isEmpty() || (out.size() == 1 && !f.operatorChange().isEmpty())) {
            if (f.foundNotRescued() > 0) {
                out.add(f.foundNotRescued() + " " + people(f.foundNotRescued()) + " " + was(f.foundNotRescued()) + " found but not picked up.");
            }
            if (f.notFound() > 0) {
                out.add(f.notFound() + " " + people(f.notFound()) + " " + was(f.notFound()) + " never found.");
            }
            if (out.isEmpty() && f.status() != MissionStatus.COMPLETED) {
                out.add("The mission " + ended(f.status()) + " before it could finish.");
            }
        }
        return out;
    }

    private static List<String> nextTime(DebriefFacts f) {
        List<String> out = new ArrayList<>();
        if (f.foundNotRescued() > 0) {
            out.add("Send more helicopters, or a base closer to the area, so everyone who is found can be picked up.");
        }
        if (f.notFound() > 0) {
            out.add("Send more drones, or try the other search pattern, to cover the area sooner.");
        }
        if (f.status() == MissionStatus.ABORTED && f.rescued() < f.people()) {
            out.add("Plan the incident again to reach the people who are left.");
        }
        if (!f.operatorChange().isEmpty()) {
            out.add("Have the planner choose the operator's numbers from the start, so the first plan can be launched as it is.");
        }
        if (out.isEmpty() && f.people() > 0 && f.rescued() == f.people()) {
            out.add(f.drones() > 0
                    ? "The same fleet size worked (" + f.drones() + " drones, " + f.helicopters() + " helicopters); try one drone fewer to see if it still finishes in time."
                    : "The same fleet size worked; use it again for an incident like this.");
        }
        if (out.isEmpty()) {
            out.add("Plan the incident again with more drones and helicopters if people are left.");
        }
        return out;
    }

    private static String minutes(double minutes) {
        long whole = Math.max(1, Math.round(minutes));
        return whole + (whole == 1 ? " minute" : " minutes");
    }

    private static String people(int count) {
        return count == 1 ? "person" : "people";
    }

    private static String was(int count) {
        return count == 1 ? "was" : "were";
    }

    /** How a mission ended, as a verb phrase: "ended", "was aborted" or "ended incomplete". */
    private static String ended(MissionStatus status) {
        return switch (status) {
            case ABORTED -> "was aborted";
            case INCOMPLETE -> "ended incomplete";
            default -> "ended";
        };
    }
}
