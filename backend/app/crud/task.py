import uuid
from typing import Optional
from sqlalchemy.orm import Session
from app.models.task import Task
from app.schemas.task import TaskCreate, TaskUpdate, current_timestamp_ms


def get_tasks(db: Session, since: int = 0) -> list[Task]:
    """Retrieve all tasks updated after the specified Unix epoch timestamp in milliseconds.
    
    Includes soft-deleted tasks so Android clients can observe deletions during incremental pull.
    """
    return (
        db.query(Task)
        .filter(Task.updated_at > since)
        .order_by(Task.updated_at.asc())
        .all()
    )


def get_task_by_id(db: Session, task_id: str) -> Optional[Task]:
    """Retrieve a single task by its unique ID."""
    return db.query(Task).filter(Task.id == task_id).first()


def create_task(db: Session, task_in: TaskCreate) -> Task:
    """Create a new task record in the database."""
    task_id = task_in.id or str(uuid.uuid4())
    updated_at = task_in.updated_at if task_in.updated_at is not None else current_timestamp_ms()

    db_task = Task(
        id=task_id,
        title=task_in.title,
        is_completed=task_in.is_completed,
        is_deleted=task_in.is_deleted,
        updated_at=updated_at,
    )
    db.add(db_task)
    db.commit()
    db.refresh(db_task)
    return db_task


def update_task(db: Session, db_task: Task, task_in: TaskUpdate) -> Task:
    """Update an existing task record with new values and refresh updated_at."""
    update_data = task_in.model_dump(exclude_unset=True)

    for field, value in update_data.items():
        setattr(db_task, field, value)

    # If updated_at was not explicitly provided in the update payload, update to current time
    if "updated_at" not in update_data or update_data["updated_at"] is None:
        db_task.updated_at = current_timestamp_ms()

    db.add(db_task)
    db.commit()
    db.refresh(db_task)
    return db_task


def soft_delete_task(db: Session, db_task: Task) -> Task:
    """Mark a task as logically deleted and bump its updated_at timestamp."""
    db_task.is_deleted = True
    db_task.updated_at = current_timestamp_ms()
    db.add(db_task)
    db.commit()
    db.refresh(db_task)
    return db_task
