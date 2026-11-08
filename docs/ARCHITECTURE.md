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
| Composite (news) | `CompositeNewsSource` | Several feeds behave as one `NewsSource`, merged in the order they are listed so the first feed wins. |
| Strategy (articles) | `ArticleEnricher` (`GoogleArticleEnricher`, `NONE`) | Interchangeable ways to give the AI more than a headline: download the article behind a Google link, or leave the item as it is. |
| Repository | `IncidentRepository` (`InMemoryIncidentRepository`, `JdbcIncidentRepository`) | Storage hidden behind an interface; swapped by `DB_URL`. |
| Facade | `MissionControl`, `IntelligenceService`, `AppContext` | One simple entry point over many collaborating classes. |
| Chain of Responsibility | `FallbackLlmClient` (a chain of `RetryingLlmClient` around `GeminiClient`) | A request goes to the first model; if its daily allowance is used up (`LlmException.isQuotaExhausted`), the next model in `LLM_FALLBACK_MODELS` gets it. Other failures are not passed along. |

## Key decisions

- **The AI proposes, rules decide.** Model output is validated and clamped (`Assessment`, `ResponseDecision`),
  prompts fence untrusted article text, and `VerifiedCommand` makes the deterministic `SafetyPolicy`
  the last word before any unit moves.
- **The operator has the last word on the plan.** `MissionControl.revise` compiles an edited decision
  again from the planner's own plan, so every change passes the same battery and range checks and the
  original can always be restored.
- **The AI reads the article, not just the headline.** `IntelligenceService` passes each news item through
  an `ArticleEnricher` before the analyzer sees it. `GoogleArticleEnricher` uses `GoogleNewsLinks` to find the
  real address behind a Google link (decoding it locally, or asking Google's own web service), downloads the
  page and `ArticleText` keeps the paragraphs. Every step fails softly: if anything goes wrong the item keeps
  its headline and short description, so a scan never fails because an article could not be fetched.
- **Real news before demo news.** `CompositeNewsSource` merges feeds in the order of `NEWS_SOURCES`, newest first
  within each feed, so demo stories cannot push real ones out of the one-item-per-topic scan.
- **A rescue needs a helicopter.** If the AI plans none while people are expected and one is ready,
  `LlmResponsePlanner` raises it to one and notes this in the plan.
- **Debriefs only claim what happened.** `MissionReport` carries the units sent and any operator change to the plan,
  `DebriefFacts` hands them to the AI with the counts, `LlmDebriefer` rejects an answer with no next-time items
  (one repair attempt, then the rules debrief) and drops claims about zero counts.
- **Deterministic simulation.** Fixed one-second ticks and a seeded random generator make runs repeatable.
- **Per-unit command lanes.** Commands for one unit run in order; different units run in parallel; a failed
  command cancels the rest of that unit's lane. `abort` cancels everything and brings units home.
- **No duplicate incidents.** Each incident has a normalised source key that is unique in the database,
  checked before insert and enforced by the database as a backstop.
- **Secrets stay local.** Keys come from `.env` or the environment, are sent in a header, never logged
  or printed, and `.env` is gitignored.
