from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.database import engine, Base
from app.routers import tasks_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan event to create database tables on startup."""
    Base.metadata.create_all(bind=engine)
    yield


app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description="Backend synchronization engine for Offline-First Android applications using Last-Write-Wins.",
    lifespan=lifespan,
)

# CORS configuration to allow Android emulator (10.0.2.2), local devices, and frontends
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS if settings.CORS_ORIGINS else ["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API routers
app.include_router(tasks_router)


@app.get("/health", tags=["Health"])
def health_check() -> dict[str, str]:
    """Health check endpoint to verify backend service readiness."""
    return {"status": "healthy", "service": settings.PROJECT_NAME}


@app.get("/", tags=["Health"])
def root() -> dict[str, str]:
    """Root endpoint welcoming clients."""
    return {
        "message": f"Welcome to {settings.PROJECT_NAME}",
        "docs_url": "/docs",
        "health_url": "/health",
    }
