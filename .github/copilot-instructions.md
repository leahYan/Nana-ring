# Nana-Ring Project Guidelines

## Architecture

Nana-ring is a health tech POC with offline-first architecture: Ring → Android (Room SQLite) → FastAPI backend → Supabase PostgreSQL → React dashboard.

Key components and boundaries:

- **ring-android**: Kotlin + BLE SDK for data capture and local persistence
- **ring-backend**: FastAPI for batch validation and cloud storage
- **ring-web**: React dashboard for data visualization

Data flow: Ring → Phone (BLE) → Room SQLite → WorkManager batch sync → FastAPI → Supabase.

See [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) for detailed architecture.

## Build and Test

- Android: `./gradlew build` / `./gradlew test`
- Backend: `pip install -r requirements.txt` then `uvicorn main:app --reload`
- Web: `npm install` then `npm run dev`

Note: Backend requires local Supabase + Postgres setup. No docker-compose.yml yet.

See [docs/TEST_CASES.md](docs/TEST_CASES.md) for test scenarios.

## Conventions

- **Database Schema**: All metrics follow the pattern in [docs/ring_data_dictionary.csv](docs/ring_data_dictionary.csv). Use snake_case for tables/columns.
- **Timestamps**: Ring SDK sends Unix seconds—multiply by 1000 for DB (Unix ms).
- **Validation**: Strict ranges per metric (e.g., SpO2 70-100%, reject <70%). Allow ±60s clock skew.
- **Sync Deduplication**: Use sync_id for idempotency in WorkManager retries.
- **Blood Pressure**: SDK-derived from HR, not optically measured—disclose in UI.
- **JWT**: user_id extracted server-side from token, never from client.

See [docs/TECHNICAL_SOLUTION.md](docs/TECHNICAL_SOLUTION.md) for schema details.</content>
<parameter name="filePath">c:\Users\Leah\Desktop\Nana Ring\Github\Nana-ring\.github\copilot-instructions.md
