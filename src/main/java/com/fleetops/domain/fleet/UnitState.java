package com.fleetops.domain.fleet;

/**
 * What a {@link Unit} is currently doing, implemented with the State pattern.
 *
 * <p>Each constant decides which events are legal and where they lead; the {@link Unit} just
 * delegates to its current state. Events a state does not support throw
 * {@link IllegalStateException}.
 *
 * <p>{@link #recall()} and {@link #hold()} are safe to broadcast to a whole fleet, so they never
 * throw: a state that has nothing to do simply stays as it is.
 *
 * <pre>
 *   IDLE ──dispatch──▶ EN_ROUTE ──arrive──▶ OPERATING
 *     ▲                   │                     │
 *     │                   └──── recall ─────────┤
 *     └──dock── RETURNING ◀─────────────────────┘
 *
 *   hold: EN_ROUTE / OPERATING / RETURNING ──▶ HOLDING ──recall──▶ RETURNING
 * </pre>
 */
public enum UnitState {

    /** Docked at base (or on its carrier) and ready for work. */
    IDLE {
        @Override
        public UnitState dispatch() {
            return EN_ROUTE;
        }

        @Override
        public UnitState recall() {
            return IDLE;
        }

        @Override
        public UnitState hold() {
            return IDLE;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    },

    /** Travelling to its work site. */
    EN_ROUTE {
        @Override
        public UnitState arrive() {
            return OPERATING;
        }

        @Override
        public UnitState recall() {
            return RETURNING;
        }

        @Override
        public UnitState hold() {
            return HOLDING;
        }
    },

    /** At work: sweeping an area, or extracting survivors. */
    OPERATING {
        @Override
        public UnitState recall() {
            return RETURNING;
        }

        @Override
        public UnitState hold() {
            return HOLDING;
        }
    },

    /** Heading back to base. */
    RETURNING {
        @Override
        public UnitState dock() {
            return IDLE;
        }

        @Override
        public UnitState recall() {
            return RETURNING;
        }

        @Override
        public UnitState hold() {
            return HOLDING;
        }
    },

    /** Stopped in place after an emergency abort; waits to be recalled. */
    HOLDING {
        @Override
        public UnitState recall() {
            return RETURNING;
        }

        @Override
        public UnitState hold() {
            return HOLDING;
        }
    };

    /** The unit was given a job and sets off. */
    public UnitState dispatch() {
        throw illegal("dispatch");
    }

    /** The unit reached its work site. */
    public UnitState arrive() {
        throw illegal("arrive");
    }

    /** The unit finished its work (or was recalled) and heads home. */
    public UnitState recall() {
        throw illegal("recall");
    }

    /** The unit reached base and is ready again. */
    public UnitState dock() {
        throw illegal("dock");
    }

    /** Emergency stop. */
    public UnitState hold() {
        throw illegal("hold");
    }

    /** True only for a unit that can be given new work right now. */
    public boolean isAvailable() {
        return false;
    }

    private IllegalStateException illegal(String event) {
        return new IllegalStateException("Cannot " + event + " a unit that is " + this);
    }
}
