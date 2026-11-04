# FleetOps

LLM-driven multi-robot rescue mission control, built for EECS 3311 (Software Design).
The application reads disaster news, uses an AI agent to verify and plan a response, checks every
action with a deterministic safety gate, and shows drones, helicopters and survivors on a map.

FleetOps is a playful simulation: it is meant to show software-design patterns working together
(see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)), not to control real robots.

## How it works

1. **Scan** - news items (Google News, public Telegram channels, or a built-in offline demo set) are
   collected by topic: flood, famine, fire, earthquake. One new item per topic per scan.
2. **Verify** - the AI reads each item, decides whether it is a real incident and extracts structured
   data (place, latitude/longitude, severity, people affected). The answer is validated before use.
   Duplicates are detected, and incidents are saved without repeats.
3. **Plan** - the AI chooses how many drones, which search pattern and how many helicopters to send,
   based on severity and the units that are ready. If the AI is unavailable or answers badly, fixed
   rules take over.
4. **Safety check** - every command is checked by a deterministic safety gate (battery reserve,
   service area, unit readiness) before it runs. The AI cannot bypass it.
5. **Simulate** - a rover drives to the south edge of the incident square, drones sweep the square
   and reveal people, helicopters pick them up. The incident is marked completed when everyone is rescued.

The program works with no key and no network: the defaults use a mock AI and demo news.

## Prerequisites

- Java 21 or newer (`java -version`)
- Maven 3.9+ (IntelliJ IDEA bundles one; for the terminal, install it with `brew install maven`)

## Local configuration

Secrets live in a local `.env` file that is **gitignored**. Never commit a real API key.

```bash
cp .env.example .env
```

Then edit `.env`. The default `LLM_PROVIDER=mock` needs no key and no network. To use Gemini, set
`LLM_PROVIDER=gemini` and put your key in `LLM_API_KEY`.

| Key | Meaning | Default |
|---|---|---|
| `LLM_PROVIDER` | `mock`, `gemini` or `ollama` | `mock` |
| `LLM_API_KEY` | API key (required for `gemini`) | none |
| `LLM_MODEL` | Model name | provider default |
| `LLM_BASE_URL` | Ollama server URL | `http://localhost:11434` |
| `DB_URL` | JDBC URL of the incident database, or `memory` for no saving | `jdbc:h2:./data/fleetops` |
| `DB_USER` / `DB_PASSWORD` | Database login (only needed outside the embedded H2 default) | `sa` / empty |
| `NEWS_SOURCES` | `demo`, `google`, `telegram` (comma separated) | `demo` |
| `TELEGRAM_CHANNELS` | Public channel names, used when `telegram` is a source | none |
| `NEWS_GOOGLE_EDITION` | Google News language and country | `hl=en-CA&gl=CA&ceid=CA:en` |
| `NEWS_TOPICS` | `flood`, `famine`, `fire`, `earthquake` (blank = all) | all four |
| `MAP_TILES` | Map tile source | `osm` |

## Run

```bash
mvn -q compile                             # build (downloads dependencies the first time)
mvn -q exec:java                           # start the GUI
mvn -q exec:java -Dexec.mainClass=com.fleetops.cli.FleetOpsCli -Dexec.args="config"   # CLI
```

### Command line

Every command is run as `mvn -q exec:java -Dexec.mainClass=com.fleetops.cli.FleetOpsCli -Dexec.args="<command>"`.

| Command | What it does |
|---|---|
| `config` | Show the effective configuration (keys are never printed) |
| `scan [-t flood,fire] [-v]` | Read one new news item per topic, have the AI verify it, store new incidents |
| `incidents [-a] [-s STATE] [-t TYPE]` | List incidents (active ones unless `-a` or `-s`) |
| `show <id>` | Details of one incident |
| `run <id> [--dry-run] [-y] [--progress] [--abort-after S]` | AI plans the response, you approve it, the simulation plays out and prints a report |

`run` also takes `--rovers`, `--drones`, `--helicopters`, `--depot-km`, `--seed` and `--max-ticks`.
Exit codes: 0 ok, 1 configuration error, 2 bad usage, 3 incident not found, 4 failed or not completed.

Example session with the offline demo news:

```
scan
incidents
run 1 --progress
```

### Graphical interface

`mvn -q exec:java` opens the map window (OpenStreetMap tiles, so it needs internet access to show the map).

1. **Scan news** reads the configured feeds, has the AI verify them and adds incidents to the list and the map.
2. Click an incident square on the map (or a row in the list) to select it. Double-click a square, or press **Plan mission...**, to have the AI plan the response.
3. Read the plan and press **Launch**. The rover drives to the south edge of the square, drones sweep it (found people appear as red dots) and helicopters carry them out (rescued people turn green).
4. **Abort selected** / **Abort all** stop missions and bring the units home. **Reset fleet** rebuilds the fleet near the next incident you plan. The speed box sets how fast simulated time runs.

In IntelliJ you can also run `FleetOpsGui` or `FleetOpsCli` directly from their `main` methods.

## Design

The core (incidents, fleet, simulation, commands, safety gate, mission control) depends only on
interfaces. Vendors (Gemini/Ollama, H2, the map library, news sources) sit behind adapters, and
`AppContext` is the one place that chooses which implementation to use, so any of them can be
swapped through configuration:

| To change | Edit |
|---|---|
| AI vendor | `LLM_PROVIDER` in `.env`; a new vendor is one class implementing `LlmClient` plus one line in `LlmClients` |
| Database | `DB_URL` (and the driver dependency in `pom.xml`); the code only uses `java.sql` |
| News feeds | `NEWS_SOURCES`; a new feed is one class implementing `NewsSource` |
| Map tiles | `MapTiles` (only OpenStreetMap exists today) |

Package overview (`src/main/java/com/fleetops/`):

| Package | Role |
|---|---|
| `domain` | Incidents, survivors, geography and the fleet (rover, drones, helicopters) |
| `sim` | Fixed-step simulation engine, world and survivor placement |
| `command`, `strategy` | Mission commands with a per-unit command queue; search patterns |
| `safety` | Rules and the policy that checks commands |
| `news`, `ai`, `intel` | News sources, LLM clients, and the verify-and-store pipeline |
| `plan`, `mission` | AI/rule-based planning, and `MissionControl` which runs missions |
| `repository` | In-memory and JDBC incident storage |
| `app`, `config` | Configuration and the composition root |
| `cli`, `gui` | The two user interfaces |

The design patterns used, with the classes that implement them, are listed in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Troubleshooting

- **"LLM_PROVIDER=gemini needs LLM_API_KEY"** - put your key in `.env`, or use `LLM_PROVIDER=mock`.
- **Could not open the database** - run `mvn compile` once so Maven downloads the H2 driver, or set `DB_URL=memory`.
- **The map is grey** - tiles come from OpenStreetMap and need internet access. Incidents and units are still drawn.
- **Plan fails with "no drone is ready"** - the units are busy or low on battery; press **Reset fleet** (GUI) or start a new `run` (CLI), which begins with a fresh fleet.
- **Start fresh** - delete the `data/` folder (it holds the local database and is gitignored).

## Known limitations

- There are no automated tests in this repository yet; the code was checked with throwaway test programs.
- Live Gemini, Ollama, Google News and Telegram access depends on your keys, network and the
  services' current behaviour; the offline demo mode is the dependable path.
- The simulation is deliberately simple: straight-line flight, no wind or terrain, one search
  pattern per mission.
