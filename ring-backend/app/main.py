from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.database import Base, engine
from app.api.v1.endpoints.health import router as health_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Create all tables that don't exist yet on every cold start.
    # Supabase already has the schema once migrations run, but create_all
    # is idempotent and safe to call repeatedly.
    Base.metadata.create_all(bind=engine)
    yield


app = FastAPI(
    title="Nana Ring — Health API",
    version="1.0.0",
    description=(
        "Receives batched health metrics from the Android ring bridge, "
        "stores them in Supabase, and serves them to the React dashboard."
    ),
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],   # tighten to VPS + Vercel domain before production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health_router, prefix="/api/v1")
