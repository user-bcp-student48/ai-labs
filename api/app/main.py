from __future__ import annotations

from collections import Counter
import hashlib
import os
import sqlite3
from pathlib import Path
from pathlib import PurePosixPath, PureWindowsPath
from typing import Any

import jwt
import urllib3
from fastapi import Depends, FastAPI, File, Header, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field
from starlette.types import ASGIApp, Message, Receive, Scope, Send

JWT_ALGORITHM = "HS256"
JWT_SECRET_ENV = "AUTH_JWT_SECRET"
JWT_ISSUER_ENV = "AUTH_JWT_ISSUER"
JWT_AUDIENCE_ENV = "AUTH_JWT_AUDIENCE"
JWT_REQUIRED_CLAIMS = ("exp", "iss", "aud", "username", "role")
JWT_MIN_SECRET_BYTES = 32
UPLOAD_REQUEST_MAX_BYTES = 512
UPLOAD_REQUEST_TOO_LARGE_DETAIL = "Request body exceeds maximum size"
UPLOAD_MAX_BYTES = 200
ALLOWED_UPLOAD_EXTENSIONS = frozenset({".txt"})
NON_OPEN_TASK_STATUSES = frozenset({"DONE", "ARCHIVED"})


def verified_identity(authorization: str = Header(default="")) -> dict[str, str]:
    scheme, separator, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not separator or not token:
        raise HTTPException(status_code=401, detail="Invalid token")

    secret = os.getenv(JWT_SECRET_ENV)
    issuer = os.getenv(JWT_ISSUER_ENV)
    audience = os.getenv(JWT_AUDIENCE_ENV)
    if (
        not secret
        or len(secret.encode("utf-8")) < JWT_MIN_SECRET_BYTES
        or not issuer
        or not audience
    ):
        raise HTTPException(status_code=503, detail="Authentication is not configured")

    try:
        claims = jwt.decode(
            token,
            secret,
            algorithms=[JWT_ALGORITHM],
            audience=audience,
            issuer=issuer,
            options={"require": list(JWT_REQUIRED_CLAIMS)},
        )
    except jwt.PyJWTError:
        raise HTTPException(status_code=401, detail="Invalid token") from None

    username = claims.get("username")
    role = claims.get("role")
    if not isinstance(username, str) or not username.strip():
        raise HTTPException(status_code=401, detail="Invalid token")
    if not isinstance(role, str) or not role.strip():
        raise HTTPException(status_code=401, detail="Invalid token")
    return {"username": username, "role": role}


class TaskCreateRequest(BaseModel):
    title: str = ""
    description: str = ""
    status: str = "TODO"
    priority: str = "MEDIUM"
    owner: str | None = None


class TaskResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: int
    title: str
    description: str
    status: str
    owner: str
    priority: str
    created_at: str = Field(alias="createdAt")


class TaskSummaryResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    owner: str
    total: int
    by_status: dict[str, int] = Field(alias="byStatus")
    high_priority_open: int = Field(alias="highPriorityOpen")


class TaskRepository:
    def __init__(self, connection: sqlite3.Connection) -> None:
        self._connection = connection

    def list_for_owner(self, owner: str) -> list[sqlite3.Row]:
        rows = self._connection.execute(
            "SELECT id,title,description,status,owner,priority,created_at "
            "FROM tasks WHERE owner=? ORDER BY id",
            (owner,),
        ).fetchall()
        return rows

    def create(
        self, payload: TaskCreateRequest, owner: str
    ) -> sqlite3.Row:
        cursor = self._connection.execute(
            "INSERT INTO tasks(title,description,status,owner,priority,created_at) "
            "VALUES (?,?,?,?,?,datetime('now'))",
            (
                payload.title,
                payload.description,
                payload.status,
                owner,
                payload.priority,
            ),
        )
        self._connection.commit()
        row = self._connection.execute(
            "SELECT id,title,description,status,owner,priority,created_at "
            "FROM tasks WHERE id=?",
            (cursor.lastrowid,),
        ).fetchone()
        if row is None:
            raise RuntimeError("Created task could not be loaded")
        return row

    def summary_for_owner(self, owner: str) -> list[sqlite3.Row]:
        rows = self._connection.execute(
            "SELECT status,priority FROM tasks WHERE owner=?",
            (owner,),
        ).fetchall()
        return rows


def task_response(row: sqlite3.Row) -> TaskResponse:
    return TaskResponse(
        id=row["id"],
        title=row["title"],
        description=row["description"],
        status=row["status"],
        owner=row["owner"],
        priority=row["priority"],
        created_at=row["created_at"],
    )


def task_summary(owner: str, rows: list[sqlite3.Row]) -> TaskSummaryResponse:
    status_counts = Counter(row["status"] for row in rows)
    high_priority_open = sum(
        row["priority"] == "HIGH" and row["status"] not in NON_OPEN_TASK_STATUSES
        for row in rows
    )
    return TaskSummaryResponse(
        owner=owner,
        total=len(rows),
        by_status=dict(status_counts),
        high_priority_open=high_priority_open,
    )


def normalize_upload_filename(filename: str | None) -> str:
    if not filename:
        raise HTTPException(status_code=400, detail="Invalid file name")

    normalized_name = Path(filename).name
    if (
        normalized_name != filename
        or PurePosixPath(filename).name != filename
        or PureWindowsPath(filename).name != filename
    ):
        raise HTTPException(status_code=400, detail="Invalid file name")

    extension = Path(normalized_name).suffix.lower()
    if extension not in ALLOWED_UPLOAD_EXTENSIONS:
        raise HTTPException(status_code=400, detail="Unsupported file type")
    return normalized_name


async def read_upload_limited(file: UploadFile) -> bytes:
    content = bytearray()
    while True:
        remaining_bytes = UPLOAD_MAX_BYTES + 1 - len(content)
        chunk = await file.read(remaining_bytes)
        if not chunk:
            return bytes(content)
        content.extend(chunk)
        if len(content) > UPLOAD_MAX_BYTES:
            raise HTTPException(status_code=413, detail="File exceeds maximum size")


class UploadRequestBodyTooLarge(Exception):
    pass


class UploadRequestBodyLimitMiddleware:
    def __init__(self, app: ASGIApp) -> None:
        self._app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if (
            scope["type"] != "http"
            or scope["method"] != "POST"
            or scope["path"] != "/api/requirements"
        ):
            await self._app(scope, receive, send)
            return

        content_length = next(
            (
                value
                for name, value in scope["headers"]
                if name.lower() == b"content-length"
            ),
            None,
        )
        if content_length is not None:
            declared_length = content_length.lstrip(b"0") or b"0"
            maximum_length = str(UPLOAD_REQUEST_MAX_BYTES).encode()
            if declared_length.isdigit() and (
                len(declared_length) > len(maximum_length)
                or (
                    len(declared_length) == len(maximum_length)
                    and declared_length > maximum_length
                )
            ):
                await self._reject(scope, receive, send)
                return

        received_bytes = 0

        async def receive_with_limit() -> Message:
            nonlocal received_bytes
            message = await receive()
            if message["type"] == "http.request":
                received_bytes += len(message.get("body", b""))
                if received_bytes > UPLOAD_REQUEST_MAX_BYTES:
                    raise UploadRequestBodyTooLarge
            return message

        try:
            await self._app(scope, receive_with_limit, send)
        except UploadRequestBodyTooLarge:
            await self._reject(scope, receive, send)

    async def _reject(self, scope: Scope, receive: Receive, send: Send) -> None:
        response = JSONResponse(
            status_code=413,
            content={"detail": UPLOAD_REQUEST_TOO_LARGE_DETAIL},
        )
        await response(scope, receive, send)


def create_app(db_path: str | None = None, upload_dir: str | None = None) -> FastAPI:
    selected_db = db_path or os.getenv("WORKSHOP_DB_PATH", "./data/workshop.db")
    selected_uploads = upload_dir or os.getenv("WORKSHOP_UPLOAD_DIR", "./uploads")
    Path(selected_db).parent.mkdir(parents=True, exist_ok=True)
    Path(selected_uploads).mkdir(parents=True, exist_ok=True)

    connection = sqlite3.connect(selected_db, check_same_thread=False)
    connection.row_factory = sqlite3.Row
    task_repository = TaskRepository(connection)
    connection.execute("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL, owner TEXT NOT NULL, priority TEXT NOT NULL, created_at TEXT NOT NULL)")
    count = connection.execute("SELECT COUNT(*) AS total FROM tasks").fetchone()["total"]
    if count == 0:
        connection.execute("INSERT INTO tasks(title,description,status,owner,priority,created_at) VALUES ('Refactor login','Separar responsabilidades en frontend','TODO','student','HIGH',datetime('now'))")
        connection.execute("INSERT INTO tasks(title,description,status,owner,priority,created_at) VALUES ('Review API','Revisar seguridad de endpoints','IN_PROGRESS','student','MEDIUM',datetime('now'))")
        connection.execute("INSERT INTO tasks(title,description,status,owner,priority,created_at) VALUES ('Trainer notes','Preparar retrospectiva','TODO','trainer','LOW',datetime('now'))")
        connection.commit()

    app = FastAPI(title="Workshop Requirements API", version="0.1.0")
    app.state.connection = connection
    app.state.upload_dir = selected_uploads
    app.add_middleware(UploadRequestBodyLimitMiddleware)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.get("/api/tasks", response_model=list[TaskResponse])
    def list_tasks(
        owner: str | None = Query(default=None),
        identity: dict[str, str] = Depends(verified_identity),
    ) -> list[TaskResponse]:
        rows = task_repository.list_for_owner(identity["username"])
        return [task_response(row) for row in rows]

    @app.post("/api/tasks", status_code=201, response_model=TaskResponse)
    def create_task(
        payload: TaskCreateRequest,
        identity: dict[str, str] = Depends(verified_identity),
    ) -> TaskResponse:
        row = task_repository.create(payload, identity["username"])
        return task_response(row)

    @app.get("/api/summary", response_model=TaskSummaryResponse)
    def summary(
        owner: str | None = Query(default=None),
        identity: dict[str, str] = Depends(verified_identity),
    ) -> TaskSummaryResponse:
        owner_identity = identity["username"]
        rows = task_repository.summary_for_owner(owner_identity)
        return task_summary(owner_identity, rows)

    @app.post("/api/requirements", status_code=201)
    async def upload_requirement(
        file: UploadFile = File(...),
        identity: dict[str, str] = Depends(verified_identity),
    ) -> dict[str, Any]:
        normalized_filename = normalize_upload_filename(file.filename)
        upload_root = Path(selected_uploads).resolve()
        owner_directory = upload_root / hashlib.sha256(
            identity["username"].encode("utf-8")
        ).hexdigest()
        owner_directory.mkdir(parents=True, exist_ok=True)
        if owner_directory.resolve() != owner_directory:
            raise HTTPException(status_code=400, detail="Invalid file name")
        target = (owner_directory / normalized_filename).resolve()
        if target.parent != owner_directory:
            raise HTTPException(status_code=400, detail="Invalid file name")
        try:
            content = await read_upload_limited(file)
            target.write_bytes(content)
        finally:
            await file.close()
        return {
            "owner": identity["username"],
            "fileName": normalized_filename,
            "storedAt": str(target),
            "size": len(content),
        }

    @app.get("/api/backend-health")
    def backend_health(identity: dict[str, str] = Depends(verified_identity)) -> dict[str, Any]:
        url = os.getenv("AUTH_HEALTH_URL", "http://localhost:8080/q/health")
        try:
            response = urllib3.PoolManager().request("GET", url, timeout=3.0)
            return {"requestedBy": identity["username"], "status": response.status, "body": response.data.decode("utf-8")}
        except Exception as error:
            return {"requestedBy": identity["username"], "status": 0, "body": str(error)}

    @app.on_event("shutdown")
    def close_connection() -> None:
        connection.close()

    return app


app = create_app()
