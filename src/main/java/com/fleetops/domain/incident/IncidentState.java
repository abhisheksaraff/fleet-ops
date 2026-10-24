package com.fleetops.domain.incident;

/**
 * Lifecycle of an {@link Incident}, implemented with the State pattern.
 *
 * <p>Each constant is a state object that decides, through its own overrides, which lifecycle events
 * are legal and which state they lead to. Events the state does not override throw
 * {@link IllegalStateException}, so an illegal transition (for example completing an incident that
 * was never planned) can never silently succeed. {@link Incident} simply delegates to its current
 * state instead of using {@code switch} statements.
 *
 * <pre>
 *   NEW ──verify──▶ VERIFIED ──plan──▶ PLANNED ──start──▶ IN_PROGRESS ──complete──▶ COMPLETED
 *    │                 ▲                  │                    │
 *    ├─reject─▶ REJECTED                  └──────cancel────────┴─▶ back to VERIFIED
 *    └─markDuplicate─▶ DUPLICATE
 * </pre>
 *
 * Being an enum, a state is a singleton and is stored in the database by its name.
 */
public enum IncidentState {

    /** Freshly extracted from a feed; not yet checked. */
    NEW {
        @Override
        public IncidentState verify() {
            return VERIFIED;
        }

        @Override
        public IncidentState reject() {
            return REJECTED;
        }

        @Override
        public IncidentState markDuplicate() {
            return DUPLICATE;
        }
    },

    /** Confirmed as a real, locatable disaster; waiting for a response plan. */
    VERIFIED {
        @Override
        public IncidentState plan() {
            return PLANNED;
        }
    },

    /** A response plan exists and is waiting for operator approval. */
    PLANNED {
        @Override
        public IncidentState start() {
            return IN_PROGRESS;
        }

        @Override
        public IncidentState cancel() {
            return VERIFIED;
        }
    },

    /** Units are deployed and rescuing survivors. */
    IN_PROGRESS {
        @Override
        public IncidentState complete() {
            return COMPLETED;
        }

        @Override
        public IncidentState cancel() {
            return VERIFIED;
        }
    },

    /** Everyone in the area has been rescued. */
    COMPLETED {
        @Override
        public boolean isTerminal() {
            return true;
        }
    },

    /** The report was not a real or locatable disaster. */
    REJECTED {
        @Override
        public boolean isTerminal() {
            return true;
        }
    },

    /** The same disaster was already recorded. */
    DUPLICATE {
        @Override
        public boolean isTerminal() {
            return true;
        }
    };

    /** The report was confirmed as real. */
    public IncidentState verify() {
        throw illegal("verify");
    }

    /** The report was judged not to be a real, locatable disaster. */
    public IncidentState reject() {
        throw illegal("reject");
    }

    /** The report matches an incident that is already recorded. */
    public IncidentState markDuplicate() {
        throw illegal("mark as duplicate");
    }

    /** A response plan was generated. */
    public IncidentState plan() {
        throw illegal("plan");
    }

    /** The operator approved the plan and units were dispatched. */
    public IncidentState start() {
        throw illegal("start");
    }

    /** All survivors were rescued. */
    public IncidentState complete() {
        throw illegal("complete");
    }

    /** The plan was rejected or the mission aborted; the incident can be planned again. */
    public IncidentState cancel() {
        throw illegal("cancel");
    }

    /** True when no further events are possible. */
    public boolean isTerminal() {
        return false;
    }

    private IllegalStateException illegal(String event) {
        return new IllegalStateException("Cannot " + event + " an incident that is " + this);
    }
}
