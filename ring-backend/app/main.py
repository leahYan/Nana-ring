import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.database import Base, engine
from app.api.v1.endpoints.health import router as health_router

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # create_all is idempotent — safe to call on every cold start.
    # Wrapped in try/except so a transient DB blip does not kill the process;
    # the app will still boot and serve requests, but DB ops will fail until
    # connectivity is restored.
    try:
        Base.metadata.create_all(bind=engine)
        logger.info("Database schema verified / tables created.")
    except Exception as exc:
        logger.warning(
            "Startup create_all failed — check DATABASE_URL. "
            "App will continue but DB operations will error until fixed. "
            f"Detail: {exc}"
        )
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
