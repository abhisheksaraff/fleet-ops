package com.fleetops.debrief;

import com.fleetops.mission.MissionStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fixed rules that turn mission numbers into a debrief. They are the whole author when no AI is
 * available, and the offline AI stand-in answers with them too, so a demo without a key still shows a
 * sensible debrief. They also decide whether a piece of advice from an AI could actually be followed.
 */
final class DebriefRules {

    private static final Pattern UNIT_COUNT = Pattern.compile(
            "(\\b(?:one|two|three|four|five|six|\\d+)\\b)\\s+(more\\s+|additional\\s+|fewer\\s+)?(drone|helicopter|robodog)s?\\b(\\s+fewer)?");
    private static final Pattern OLD_NUMBER = Pattern.compile("(?s).*\\b(?:instead of|rather than|from|than)\\s*$");

    private DebriefRules() {}

    static Debrief write(DebriefFacts f, String writtenBy, boolean ai) {
        return new Debrief(summary(f), wentWell(f), problems(f), nextTime(f), writtenBy, ai);
    }

    static String summary(DebriefFacts f) {
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

    static List<String> nextTime(DebriefFacts f) {
        List<String> out = new ArrayList<>();
        boolean moreDrones = f.maxDrones() == 0 || f.drones() < f.maxDrones();
        boolean moreHelicopters = f.maxHelicopters() == 0 || f.helicopters() < f.maxHelicopters();
        boolean fewerDrones = f.drones() > Math.max(1, f.minDrones());
        if (f.foundNotRescued() > 0) {
            out.add(moreHelicopters
                    ? "Send more helicopters so everyone who is found can be picked up."
                    : "Plan the incident again: all helicopters were used and some people who were found could not be picked up.");
        }
        if (f.notFound() > 0) {
            out.add(moreDrones
                    ? "Send more drones, or try the other search pattern, to cover the area sooner."
                    : "Try the other search pattern: every ready drone was already sent.");
        }
        if (f.status() == MissionStatus.ABORTED && f.rescued() < f.people()) {
            out.add("Plan the incident again to reach the people who are left.");
        }
        if (!f.operatorChange().isEmpty()) {
            out.add("Have the planner choose the operator's numbers from the start, so the first plan can be launched as it is.");
        }
        if (out.isEmpty() && f.people() > 0 && f.rescued() == f.people()) {
            out.add(fewerDrones
                    ? "The same fleet finished the job; try " + count(f.drones() - 1, "drone") + " instead of " + f.drones()
                            + " to see if it still finishes in time."
                    : "The same fleet finished the job; use it again for a similar incident.");
        }
        if (out.isEmpty()) {
            out.add("Use the same fleet again for a similar incident and compare the result.");
        }
        return out;
    }

    // ---- checking advice from an AI ----------------------------------------------------------------

    /**
     * Whether a "next time" suggestion could be followed with this fleet: it must not ask for a number of
     * units that was already sent, fewer drones than the area needs, or more units than there are.
     */
    static boolean advicePossible(String item, DebriefFacts f) {
        String text = item.toLowerCase(Locale.ROOT);
        if (text.contains("closer base") || text.contains("closer depot") || text.contains("base closer")) {
            return false;
        }
        Matcher m = UNIT_COUNT.matcher(text);
        while (m.find()) {
            if (OLD_NUMBER.matcher(text.substring(0, m.start())).matches()) {
                continue; // "... instead of 4 drones": the number that was sent, not the suggestion
            }
            int n = number(m.group(1));
            String unit = m.group(3);
            String how = m.group(2) == null ? "" : m.group(2).strip();
            boolean fewer = how.equals("fewer") || m.group(4) != null;
            boolean more = how.equals("more") || how.equals("additional");
            int sent = sent(f, unit);
            int target = more ? sent + n : fewer ? sent - n : n;
            if (!allowed(f, unit, target, sent)) {
                return false;
            }
        }
        for (String unit : List.of("drone", "helicopter", "robodog")) {
            int sent = sent(f, unit);
            if (Pattern.compile("\\b(?:fewer|less|reduce|cut)\\b[^.]{0,25}\\b" + unit + "s?\\b").matcher(text).find() && sent <= low(f, unit)) {
                return false;
            }
            int high = high(f, unit);
            if (Pattern.compile("\\b(?:more|additional|extra)\\b[^.]{0,25}\\b" + unit + "s?\\b").matcher(text).find() && high > 0 && sent >= high) {
                return false;
            }
        }
        return true;
    }

    private static boolean allowed(DebriefFacts f, String unit, int target, int sent) {
        int high = high(f, unit);
        return target != sent && target >= low(f, unit) && (high == 0 || target <= high);
    }

    private static int low(DebriefFacts f, String unit) {
        return switch (unit) {
            case "drone" -> Math.max(1, f.minDrones());
            case "helicopter" -> 1;
            default -> 0;
        };
    }

    private static int high(DebriefFacts f, String unit) {
        return switch (unit) {
            case "drone" -> f.maxDrones();
            case "helicopter" -> f.maxHelicopters();
            default -> f.maxRobodogs();
        };
    }

    private static int sent(DebriefFacts f, String unit) {
        return switch (unit) {
            case "drone" -> f.drones();
            case "helicopter" -> f.helicopters();
            default -> f.robodogs();
        };
    }

    private static int number(String word) {
        return switch (word) {
            case "one" -> 1;
            case "two" -> 2;
            case "three" -> 3;
            case "four" -> 4;
            case "five" -> 5;
            case "six" -> 6;
            default -> Integer.parseInt(word);
        };
    }

    // ---- wording ------------------------------------------------------------------------------------

    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
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
