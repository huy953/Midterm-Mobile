from typing import Union
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.crud import task as crud_task
from app.schemas.task import ( TaskCreate, TaskUpdate, TaskDTO, SyncPayload, SyncResult)
from app.services.sync_service import sync_tasks

router = APIRouter(prefix="/tasks", tags=["Tasks"])


@router.get("", response_model=list[TaskDTO], summary="Incremental pull tasks")
def get_tasks_endpoint(
    since: int = Query(default=0, ge=0, description="Unix epoch timestamp in ms to filter updated records"),
    db: Session = Depends(get_db),
) -> list[TaskDTO]:
    """Retrieve all tasks updated after the specified `since` timestamp.
    
    Used by Android clients to perform incremental pull synchronization.
    Returns both active and soft-deleted tasks so local Room DB can reflect changes.
    """
    tasks = crud_task.get_tasks(db=db, since=since)
    return tasks

@router.get("/{task_id}", response_model=TaskDTO, summary="Get task by ID")
def get_task_by_id_endpoint(
    task_id: str,
    db: Session = Depends(get_db),
) -> TaskDTO:
    """Retrieve a single task by its unique ID."""
    db_task = crud_task.get_task_by_id(db=db, task_id=task_id)
    if not db_task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id '{task_id}' not found.",
        )
    return db_task


@router.post("/sync", response_model=SyncResult, summary="Batch push synchronization with LWW")
def sync_tasks_endpoint(
    payload: Union[SyncPayload, list[TaskDTO]],
    db: Session = Depends(get_db),
) -> SyncResult:
    """Synchronize a batch of tasks pushed from Android client.
    
    Accepts either `{"tasks": [...]}` or direct JSON array `[...]`.
    Applies Last-Write-Wins (LWW) conflict resolution based on `updated_at`.
    Idempotent: repeating the same payload produces no duplicate entries and no errors.
    """
    if isinstance(payload, SyncPayload):
        tasks = payload.tasks
    else:
        tasks = payload

    return sync_tasks(db=db, tasks=tasks)


@router.post("", response_model=TaskDTO, status_code=status.HTTP_201_CREATED, summary="Create a new task")
def create_task_endpoint(
    task_in: TaskCreate,
    db: Session = Depends(get_db),
) -> TaskDTO:
    """Directly create a new task.
    
    Accepts client-provided UUID or generates a new one.
    Accepts client-provided timestamp or assigns the current server timestamp.
    """
    created_task = crud_task.create_task(db=db, task_in=task_in)
    return created_task



@router.put("/{task_id}", response_model=TaskDTO, summary="Update an existing task")
def update_task_endpoint(
    task_id: str,
    task_in: TaskUpdate,
    db: Session = Depends(get_db),
) -> TaskDTO:
    """Update task details.
    
    Bumps `updated_at` to the provided value or current timestamp.
    """
    db_task = crud_task.get_task_by_id(db=db, task_id=task_id)
    if not db_task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id '{task_id}' not found.",
        )
    updated_task = crud_task.update_task(db=db, db_task=db_task, task_in=task_in)
    return updated_task


@router.delete("/{task_id}", response_model=TaskDTO, summary="Soft delete a task")
def delete_task_endpoint(
    task_id: str,
    db: Session = Depends(get_db),
) -> TaskDTO:
    """Logically delete a task (soft delete).
    
    Sets `is_deleted = True` and updates `updated_at` so the deletion propagates to clients.
    """
    db_task = crud_task.get_task_by_id(db=db, task_id=task_id)
    if not db_task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id '{task_id}' not found.",
        )
    deleted_task = crud_task.soft_delete_task(db=db, db_task=db_task)
    return deleted_task
