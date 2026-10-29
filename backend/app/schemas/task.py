import time
from typing import Optional
from pydantic import BaseModel, Field, ConfigDict


def current_timestamp_ms() -> int:
    """Return the current Unix epoch timestamp in milliseconds."""
    return int(time.time() * 1000)


class TaskBase(BaseModel):
    """Base schema containing common task fields."""
    title: str = Field(..., min_length=1, max_length=255, description="Task title")
    is_completed: bool = Field(default=False, description="Completion status flag")
    is_deleted: bool = Field(default=False, description="Soft delete flag")


class TaskCreate(BaseModel):
    """Schema for direct task creation (e.g. testing or manual insertion)."""
    id: Optional[str] = Field(
        default=None,
        description="Client-generated UUIDv4 string, or None for auto-generation",
    )
    title: str = Field(..., min_length=1, max_length=255, description="Task title")
    is_completed: bool = Field(default=False, description="Completion status flag")
    is_deleted: bool = Field(default=False, description="Soft delete flag")
    updated_at: Optional[int] = Field(
        default=None,
        description="Unix epoch timestamp in milliseconds (defaults to current time if omitted)",
    )


class TaskUpdate(BaseModel):
    """Schema for updating an existing task."""
    title: Optional[str] = Field(default=None, min_length=1, max_length=255)
    is_completed: Optional[bool] = Field(default=None)
    is_deleted: Optional[bool] = Field(default=None)
    updated_at: Optional[int] = Field(
        default=None,
        description="Unix epoch timestamp in milliseconds (defaults to current time if omitted)",
    )


class TaskDTO(BaseModel):
    """Data Transfer Object representing a task entity across client and server."""
    id: str = Field(..., description="Unique UUIDv4 identifier")
    title: str = Field(..., description="Task title")
    is_completed: bool = Field(default=False, description="Completion status flag")
    is_deleted: bool = Field(default=False, description="Soft delete flag")
    updated_at: int = Field(..., description="Unix epoch timestamp in milliseconds")

    model_config = ConfigDict(from_attributes=True)


class SyncPayload(BaseModel):
    """Payload pushed from client to server during two-way synchronization."""
    tasks: list[TaskDTO] = Field(
        default_factory=list,
        description="List of task DTOs with local updates",
    )


class SyncResult(BaseModel):
    """Result returned to client after performing batch Last-Write-Wins synchronization."""
    applied_ids: list[str] = Field(
        default_factory=list,
        description="Task IDs successfully inserted or overwritten",
    )
    ignored_ids: list[str] = Field(
        default_factory=list,
        description="Task IDs skipped because server record had a newer updated_at timestamp",
    )
    server_time: int = Field(
        default_factory=current_timestamp_ms,
        description="Current server Unix epoch timestamp in milliseconds",
    )
