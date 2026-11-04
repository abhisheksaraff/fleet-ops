# FleetOps

LLM-driven multi-robot rescue mission control, built for EECS 3311 (Software Design).
The application reads disaster news, uses an AI agent to verify and plan a response, checks every
action with a deterministic safety gate, and shows drones, helicopters and survivors on a map.

> Work in progress. This README grows with each stage of the project.

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
mvn test                                   # run all tests
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

In IntelliJ you can also run `FleetOpsGui` or `FleetOpsCli` directly from their `main` methods.

## Design

The core (incidents, fleet, simulation, commands, safety gate) will depend only on interfaces.
Vendors (Gemini, H2, the map library, news sources) sit behind adapters, and `AppContext` is the one
place that chooses which implementation to use, so any of them can be swapped through configuration.
