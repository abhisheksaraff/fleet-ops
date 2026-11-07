# FleetOps architecture

## Overview

```
 news sources ──▶ IntelligenceService ──▶ IncidentRepository ◀── GUI / CLI
 (Google, Telegram,   (LLM verifies,        (memory or JDBC)         │
  demo)                dedupes, stores)                              ▼
                                                              MissionControl
                                       ResponsePlanner ◀──────  (facade)
                                       (LLM, rules fallback)         │
                                                                     ▼
                                    PlanCompiler ──▶ CommandQueue ──▶ SimulationEngine ──▶ World
                                    (safety-checked    (per-unit        (1 s ticks)        (fleet, zones,
                                     commands)          lanes)                              survivors)
```

`AppContext` builds the concrete objects from `.env`; `SimulationRig` builds a fleet plus
`MissionControl`. The GUI and the CLI both use these two classes, so they run the same simulation.

## Design patterns

| Pattern | Where | What it does here |
|---|---|---|
| State | `IncidentState`, `UnitState` | Lifecycle rules live in the state objects; an illegal transition (e.g. completing an unplanned incident) throws instead of silently succeeding. |
| Builder | `Incident.Builder` | Builds a validated incident step by step; it can also restore a stored incident with its saved state. |
| Composite | `FleetComponent`, `Fleet`, `Carrier`, `Unit` | A fleet, a rover with its drones and robodogs, and a single unit are all treated as one kind of component (`recall`, `hold`, `units`). |
| Factory | `UnitFactory`, `LlmClients` | Creates units (drones, helicopters, robodogs, rovers) with unique ids; picks the AI client from configuration. |
| Observer | `SimulationListener`, `SimulationEvent`, `MissionListener`, `CommandListener` | The engine and mission control announce events (survivor found, mission finished); the GUI log, CLI progress and dispatcher listen. |
| Strategy | `SearchStrategy` (`LawnmowerStrategy`, `SpiralStrategy`, `RadiusSweepStrategy`), `ResponsePlanner`, `MissionDebriefer`, `SafetyRule` | Interchangeable search patterns (drones fly lawnmower or spiral; robodogs walk radius sweeps), planners, debriefers and safety rules. |
| Command | `MissionCommand` (`FlyToCommand`, `SweepAreaCommand`, `PickUpCommand`, `ReturnToBaseCommand`, `AwaitCommand`), `CommandQueue` | Each action is an object that runs over several ticks, can fail or be cancelled, and is queued per unit. |
| Template Method | `AbstractMissionCommand`, `AbstractAppCommand` (CLI), `AbstractDebriefer` | The base class fixes the steps (start, step, finish; or open app, run, map errors to exit codes, close; or tidy a debrief's text); subclasses fill in one hook. |
| Decorator | `VerifiedCommand`, `RetryingLlmClient`, `ResilientPlanner`, `ResilientDebriefer` | Wrap an object to add behaviour: a safety check before a command, retries around an AI client, a rule-based fallback around the AI planner and the AI debriefer. |
| Adapter | `GeminiClient`, `OllamaClient`, `GoogleNewsRssSource`, `TelegramChannelSource` | Make outside services look like `LlmClient` / `NewsSource`. |
| Composite (news) | `CompositeNewsSource` | Several feeds behave as one `NewsSource`. |
| Repository | `IncidentRepository` (`InMemoryIncidentRepository`, `JdbcIncidentRepository`) | Storage hidden behind an interface; swapped by `DB_URL`. |
| Facade | `MissionControl`, `IntelligenceService`, `AppContext` | One simple entry point over many collaborating classes. |

## Key decisions

- **The AI proposes, rules decide.** Model output is validated and clamped (`Assessment`, `ResponseDecision`),
  prompts fence untrusted article text, and `VerifiedCommand` makes the deterministic `SafetyPolicy`
  the last word before any unit moves.
- **The operator has the last word on the plan.** `MissionControl.revise` compiles an edited decision
  again from the planner's own plan, so every change passes the same battery and range checks and the
  original can always be restored.
- **Deterministic simulation.** Fixed one-second ticks and a seeded random generator make runs repeatable.
- **Per-unit command lanes.** Commands for one unit run in order; different units run in parallel; a failed
  command cancels the rest of that unit's lane. `abort` cancels everything and brings units home.
- **No duplicate incidents.** Each incident has a normalised source key that is unique in the database,
  checked before insert and enforced by the database as a backstop.
- **Secrets stay local.** Keys come from `.env` or the environment, are sent in a header, never logged
  or printed, and `.env` is gitignored.
