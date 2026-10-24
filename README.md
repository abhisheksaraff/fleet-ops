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
| `DB_URL` | Embedded H2 database URL | `jdbc:h2:./data/fleetops` |
| `MAP_TILES` | Map tile source | `osm` |

## Run

```bash
mvn test                                   # run all tests
mvn -q exec:java                           # start the GUI
mvn -q exec:java -Dexec.mainClass=com.fleetops.cli.FleetOpsCli -Dexec.args="config"   # CLI
```

In IntelliJ you can also run `FleetOpsGui` or `FleetOpsCli` directly from their `main` methods.

## Design

The core (incidents, fleet, simulation, commands, safety gate) will depend only on interfaces.
Vendors (Gemini, H2, the map library, news sources) sit behind adapters, and `AppContext` is the one
place that chooses which implementation to use, so any of them can be swapped through configuration.
