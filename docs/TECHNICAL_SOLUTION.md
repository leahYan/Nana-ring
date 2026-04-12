# Technical Implementation Specification

## 1. Database Schema (PostgreSQL via Supabase)

All time-series tables (e.g., `ring_heart_rate`, `ring_spo2`) must implement the following standard columns:

- `id`: UUID (Primary Key, Default: uuid_generate_v4())
- `user_id`: UUID (Foreign Key to users table)
- `timestamp`: BIGINT (Unix milliseconds provided by the Android SDK)
- `value`: NUMERIC/INTEGER (The actual metric reading)
- `source`: VARCHAR (e.g., 'manual_trigger', 'continuous_monitoring')
- `created_at`: TIMESTAMPTZ (Default: now())

## 2. Backend Strategy (FastAPI)

- **ORM:** SQLAlchemy for database interactions.
- **Validation:** Pydantic models must match the Android Room entity structures exactly.
- **Deployment:** A `docker-compose.yml` file should define the FastAPI service and an Nginx reverse proxy for the Hostinger VPS.

## 3. Frontend Strategy (React)

## Framework:\*\* Vite + React + TypeScript.

- **State Management:** React Query (TanStack Query) for efficient data fetching from the VPS.
- **Styling:** Tailwind CSS for a clean, "humanised" health dashboard interface.

## 4. Authentication & Multi-Tenancy (Multi-User POC)

- **Isolation Strategy**: Every record is hard-linked to a user_id. The backend enforces a WHERE user_id = current_user filter on all requests unless the requester has an is_admin flag.

- **Auth Flow**: - No public registration or login UI for the POC.

- **Provisioning**: A backend utility script (generate_tokens.py) creates unique JWTs for the 5 testers.

- **Android Integration**: The JWT is manually entered into the Android app's configuration settings.

- **Admin Access**: Admins can view all users' data via a target_user_id query parameter on the dashboard endpoints.

## 5. Data Ingestion (Sync Engine)

- **Endpoint**: POST /data/batch

- **Idempotency**: Implement ON CONFLICT (id, user_id) DO NOTHING. This allows the Android app to safely retry failed syncs without duplicating health data.

- **Clock Skew**: Validate timestamps within a 24-hour window (lenient for travel/time-zones) but strictly reject metrics outside clinical ranges (e.g., SpO2 < 70%).
