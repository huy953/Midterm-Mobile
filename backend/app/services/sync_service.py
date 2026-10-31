from sqlalchemy.orm import Session
from app.models.task import Task
from app.schemas.task import TaskDTO, SyncResult, current_timestamp_ms


def sync_tasks(db: Session, tasks: list[TaskDTO]) -> SyncResult:
    """Perform batch synchronization using the Last-Write-Wins (LWW) conflict resolution strategy.
    
    Algorithm details:
    1. For each incoming TaskDTO:
       a. Check if the task exists in the database by its UUID `id`.
       b. If not present:
          - Insert new record with incoming properties.
          - Mark ID as applied.
       c. If already present:
          - Compare `incoming.updated_at` with `existing.updated_at`.
          - If `incoming.updated_at > existing.updated_at`:
            Incoming record is newer (Last-Write-Wins). Overwrite existing record.
            Mark ID as applied.
          - If `incoming.updated_at == existing.updated_at`:
            Idempotent operation (re-delivered payload). Maintain consistent state.
            Mark ID as applied.
          - If `incoming.updated_at < existing.updated_at`:
            Server record is newer. Reject incoming stale record to preserve newer state.
            Mark ID as ignored.
    2. Commit transaction atomically.
    3. Return `SyncResult` containing applied IDs, ignored IDs, and current server timestamp.
    """
    applied_ids: list[str] = []
    ignored_ids: list[str] = []

    for task_dto in tasks:
        existing_task = db.query(Task).filter(Task.id == task_dto.id).first()

        if existing_task is None:
            # Case 1: New task from client
            now_ms = current_timestamp_ms()
            created_at = getattr(task_dto, "created_at", None) or task_dto.updated_at or now_ms
            new_task = Task(
                id=task_dto.id,
                title=task_dto.title,
                is_completed=task_dto.is_completed,
                is_deleted=task_dto.is_deleted,
                created_at=created_at,
                updated_at=task_dto.updated_at,
            )
            db.add(new_task)
            applied_ids.append(task_dto.id)
        else:
            # Case 2: Existing task - apply Last-Write-Wins
            if task_dto.updated_at > existing_task.updated_at:
                # Client has newer version: overwrite
                existing_task.title = task_dto.title
                existing_task.is_completed = task_dto.is_completed
                existing_task.is_deleted = task_dto.is_deleted
                existing_task.updated_at = task_dto.updated_at
                if not existing_task.created_at and getattr(task_dto, "created_at", None):
                    existing_task.created_at = task_dto.created_at
                db.add(existing_task)
                applied_ids.append(task_dto.id)
            elif task_dto.updated_at == existing_task.updated_at:
                # Idempotency: exact same timestamp, no-op but confirmed
                applied_ids.append(task_dto.id)
            else:
                # Server has newer version: reject stale write
                ignored_ids.append(task_dto.id)

    db.commit()

    return SyncResult(
        applied_ids=applied_ids,
        ignored_ids=ignored_ids,
        server_time=current_timestamp_ms(),
    )
