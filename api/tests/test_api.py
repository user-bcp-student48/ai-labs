import asyncio
import base64
import hashlib
import hmac
import json
import time
from pathlib import Path
from typing import Any
from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient

from app.main import UploadRequestBodyLimitMiddleware, create_app

TEST_JWT_KEY = b"0123456789abcdef0123456789abcdef"
TEST_ISSUER = "ai-labs-api-tests"
TEST_AUDIENCE = "ai-labs-api"
TOKEN_LIFETIME_SECONDS = 3600
TEST_MULTIPART_BOUNDARY = "ai-labs-test-boundary"


@pytest.fixture(autouse=True)
def configure_test_jwt(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AUTH_JWT_SECRET", TEST_JWT_KEY.decode())
    monkeypatch.setenv("AUTH_JWT_ISSUER", TEST_ISSUER)
    monkeypatch.setenv("AUTH_JWT_AUDIENCE", TEST_AUDIENCE)


def _encode_segment(value: dict[str, object]) -> str:
    encoded = json.dumps(value, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(encoded).rstrip(b"=").decode()


def token(
    username: str = "student",
    role: str = "STUDENT",
    *,
    issuer: str = TEST_ISSUER,
    audience: str = TEST_AUDIENCE,
    expires_at: int | None = None,
    signing_key: bytes = TEST_JWT_KEY,
) -> str:
    header = _encode_segment({"alg": "HS256", "typ": "JWT"})
    claims: dict[str, object] = {
        "username": username,
        "role": role,
        "iss": issuer,
        "aud": audience,
        "exp": expires_at if expires_at is not None else int(time.time()) + TOKEN_LIFETIME_SECONDS,
    }
    payload = _encode_segment(claims)
    unsigned_token = f"{header}.{payload}"
    signature = hmac.new(signing_key, unsigned_token.encode(), hashlib.sha256).digest()
    encoded_signature = base64.urlsafe_b64encode(signature).rstrip(b"=").decode()
    return f"{unsigned_token}.{encoded_signature}"


def headers(username: str = "student", role: str = "STUDENT") -> dict[str, str]:
    return {"Authorization": f"Bearer {token(username, role)}"}


def multipart_body_of_size(total_size: int, file_content: bytes) -> bytes:
    boundary = TEST_MULTIPART_BOUNDARY.encode()
    file_part = (
        b"--"
        + boundary
        + b'\r\nContent-Disposition: form-data; name="file"; filename="requirements.txt"\r\n'
        + b"Content-Type: text/plain\r\n\r\n"
        + file_content
        + b"\r\n"
    )
    padding_part = (
        b"--"
        + boundary
        + b'\r\nContent-Disposition: form-data; name="padding"\r\n\r\n'
    )
    closing_boundary = b"\r\n--" + boundary + b"--\r\n"
    padding_size = total_size - len(file_part + padding_part + closing_boundary)
    if padding_size < 0:
        raise ValueError("Requested multipart body size is too small")
    return file_part + padding_part + b"x" * padding_size + closing_boundary


ENDPOINT_REQUESTS = (
    pytest.param("GET", "/api/tasks", {}, id="list-tasks"),
    pytest.param("POST", "/api/tasks", {"json": {}}, id="create-task"),
    pytest.param("GET", "/api/summary", {}, id="summary"),
    pytest.param(
        "POST",
        "/api/requirements",
        {"files": {"file": ("requirements.txt", b"R1", "text/plain")}},
        id="upload-requirement",
    ),
    pytest.param("GET", "/api/backend-health", {}, id="backend-health"),
)


def test_lists_seeded_tasks(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 200
    assert len(response.json()) == 2
    assert set(response.json()[0]) == {
        "id",
        "title",
        "description",
        "status",
        "owner",
        "priority",
        "createdAt",
    }


def test_creates_task_and_summary(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        created = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Add tests", "description": "Improve coverage", "status": "TODO", "priority": "HIGH"},
        )
        summary = client.get("/api/summary", headers=headers())
    assert created.status_code == 201
    assert created.json()["owner"] == "student"
    assert set(created.json()) == {
        "id",
        "title",
        "description",
        "status",
        "owner",
        "priority",
        "createdAt",
    }
    assert summary.json()["total"] == 3
    assert set(summary.json()) == {"owner", "total", "byStatus", "highPriorityOpen"}


def test_uploads_requirement(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"Login must be accessible", "text/plain")},
        )
    assert response.status_code == 201
    stored_file = Path(response.json()["storedAt"])
    assert stored_file.is_relative_to(upload_dir.resolve())
    assert stored_file.exists()
    assert response.json()["fileName"] == "requirements.txt"
    assert response.json()["size"] == len(b"Login must be accessible")
    assert stored_file.read_bytes() == b"Login must be accessible"
    assert set(response.json()) == {"owner", "fileName", "storedAt", "size"}


def test_same_upload_name_is_isolated_between_users(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        student_upload = client.post(
            "/api/requirements",
            headers=headers(username="student"),
            files={"file": ("requirements.txt", b"student requirement", "text/plain")},
        )
        trainer_upload = client.post(
            "/api/requirements",
            headers=headers(username="trainer", role="TRAINER"),
            files={"file": ("requirements.txt", b"trainer requirement", "text/plain")},
        )

    assert student_upload.status_code == 201
    assert trainer_upload.status_code == 201
    student_path = Path(student_upload.json()["storedAt"])
    trainer_path = Path(trainer_upload.json()["storedAt"])
    assert student_path != trainer_path
    assert student_path.is_relative_to(upload_dir.resolve())
    assert trainer_path.is_relative_to(upload_dir.resolve())
    assert student_path.read_bytes() == b"student requirement"
    assert trainer_path.read_bytes() == b"trainer requirement"


def test_rejects_invalid_token(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers={"Authorization": "Bearer invalid"})
    assert response.status_code == 401


@pytest.mark.parametrize(
    "invalid_token",
    [
        token(issuer="unexpected-issuer"),
        token(audience="unexpected-audience"),
        token(expires_at=int(time.time()) - 1),
        token(signing_key=b"different-test-key"),
        token(username=""),
        token(role=""),
    ],
)
def test_rejects_jwt_with_invalid_claims_or_signature(
    invalid_token: str, tmp_path: Path
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get(
            "/api/tasks",
            headers={"Authorization": f"Bearer {invalid_token}"},
        )
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid token"


def test_owner_query_cannot_read_another_users_tasks(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get(
            "/api/tasks",
            params={"owner": "trainer"},
            headers=headers(),
        )
        summary = client.get(
            "/api/summary",
            params={"owner": "trainer"},
            headers=headers(),
        )
    assert response.status_code == 200
    assert {task["owner"] for task in response.json()} == {"student"}
    assert summary.json()["owner"] == "student"
    assert summary.json()["total"] == 2


def test_create_task_uses_authenticated_owner(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/tasks",
            headers=headers(),
            json={
                "title": "Owned task",
                "description": "Identity sets the owner",
                "owner": "trainer",
            },
        )
    assert response.status_code == 201
    assert response.json()["owner"] == "student"


def test_task_queries_and_inserts_preserve_sql_characters(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    user_headers = headers(username="O'Connor")
    with TestClient(app) as client:
        created = client.post(
            "/api/tasks",
            headers=user_headers,
            json={"title": "O'Reilly", "description": "A user's requirement"},
        )
        tasks = client.get("/api/tasks", headers=user_headers)
        summary = client.get("/api/summary", headers=user_headers)
    assert created.status_code == 201
    assert created.json()["title"] == "O'Reilly"
    assert created.json()["description"] == "A user's requirement"
    assert tasks.status_code == 200
    assert tasks.json()[0]["owner"] == "O'Connor"
    assert summary.status_code == 200
    assert summary.json()["owner"] == "O'Connor"


def test_upload_rejects_path_like_filename(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("../outside.txt", b"requirement", "text/plain")},
        )
    assert response.status_code == 400
    assert response.json() == {"detail": "Invalid file name"}
    assert not (tmp_path / "outside.txt").exists()


def test_upload_rejects_non_txt_extension(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.csv", b"requirement", "text/csv")},
        )
    assert response.status_code == 400
    assert response.json() == {"detail": "Unsupported file type"}


def test_upload_rejects_file_over_limit(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"x" * 201, "text/plain")},
        )
    assert response.status_code == 413
    assert response.json() == {"detail": "File exceeds maximum size"}


def test_backend_health_preserves_response_contract(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    response_from_backend = Mock(status=200, data=b'{"status":"UP"}')
    http_pool = Mock()
    http_pool.request.return_value = response_from_backend
    monkeypatch.setattr("app.main.urllib3.PoolManager", lambda: http_pool)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())
    assert response.status_code == 200
    assert response.json() == {
        "requestedBy": "student",
        "status": 200,
        "body": '{"status":"UP"}',
    }


def test_rejects_bearer_header_without_scheme_separator(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get(
            "/api/tasks",
            headers={"Authorization": "not-a-bearer-token"},
        )
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid token"


def test_missing_jwt_configuration_fails_closed(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    monkeypatch.delenv("AUTH_JWT_AUDIENCE")
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 503
    assert response.json()["detail"] == "Authentication is not configured"


def test_upload_accepts_request_at_body_limit(tmp_path: Path) -> None:
    content = b"x" * 200
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    body = multipart_body_of_size(512, content)
    request_headers = {
        **headers(),
        "Content-Type": f"multipart/form-data; boundary={TEST_MULTIPART_BOUNDARY}",
    }

    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=request_headers,
            content=body,
        )

    assert len(body) == 512
    assert response.status_code == 201
    assert response.json()["size"] == 200
    assert Path(response.json()["storedAt"]).read_bytes() == content


def test_upload_rejects_request_over_body_limit(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    body = multipart_body_of_size(513, b"valid requirement")
    request_headers = {
        **headers(),
        "Content-Type": f"multipart/form-data; boundary={TEST_MULTIPART_BOUNDARY}",
    }

    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=request_headers,
            content=body,
        )

    assert response.status_code == 413
    assert response.json() == {"detail": "Request body exceeds maximum size"}
    assert not any(path.is_file() for path in upload_dir.rglob("*"))


def test_upload_body_limit_counts_chunks_without_content_length() -> None:
    request_messages = [
        {"type": "http.request", "body": b"x" * 256, "more_body": True},
        {"type": "http.request", "body": b"x" * 257, "more_body": False},
    ]
    received_body = bytearray()
    sent_messages: list[dict[str, Any]] = []
    scope = {
        "type": "http",
        "method": "POST",
        "path": "/api/requirements",
        "headers": [],
    }

    async def receive() -> dict[str, Any]:
        return request_messages.pop(0)

    async def send(message: dict[str, Any]) -> None:
        sent_messages.append(message)

    async def downstream_app(scope, receive, send) -> None:
        while True:
            message = await receive()
            received_body.extend(message.get("body", b""))
            if not message.get("more_body", False):
                break
        await send({"type": "http.response.start", "status": 200, "headers": []})
        await send({"type": "http.response.body", "body": b"ok"})

    async def invoke_middleware() -> None:
        middleware = UploadRequestBodyLimitMiddleware(downstream_app)
        await middleware(scope, receive, send)

    asyncio.run(invoke_middleware())

    response_start = next(
        message for message in sent_messages if message["type"] == "http.response.start"
    )
    response_body = b"".join(
        message.get("body", b"")
        for message in sent_messages
        if message["type"] == "http.response.body"
    )
    assert response_start["status"] == 413
    assert json.loads(response_body) == {
        "detail": "Request body exceeds maximum size"
    }
    assert len(received_body) == 256


def test_upload_accepts_file_at_size_limit(tmp_path: Path) -> None:
    content = b"x" * 200
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", content, "text/plain")},
        )
    assert response.status_code == 201
    assert response.json()["size"] == len(content)
    assert Path(response.json()["storedAt"]).read_bytes() == content


def test_short_jwt_secret_fails_closed(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    monkeypatch.setenv("AUTH_JWT_SECRET", "test-only-short-key")
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 503
    assert response.json()["detail"] == "Authentication is not configured"


@pytest.mark.parametrize(("method", "path", "request_kwargs"), ENDPOINT_REQUESTS)
def test_all_endpoints_reject_invalid_authentication(
    method: str,
    path: str,
    request_kwargs: dict[str, Any],
    tmp_path: Path,
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.request(
            method,
            path,
            headers={"Authorization": "Bearer invalid"},
            **request_kwargs,
        )
    assert response.status_code == 401
    assert response.json() == {"detail": "Invalid token"}


@pytest.mark.parametrize(("method", "path", "request_kwargs"), ENDPOINT_REQUESTS)
def test_all_endpoints_fail_closed_without_jwt_configuration(
    method: str,
    path: str,
    request_kwargs: dict[str, Any],
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    monkeypatch.delenv("AUTH_JWT_AUDIENCE")
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.request(
            method,
            path,
            headers=headers(),
            **request_kwargs,
        )
    assert response.status_code == 503
    assert response.json() == {"detail": "Authentication is not configured"}


def test_lists_empty_task_set(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    app.state.connection.execute("DELETE FROM tasks WHERE owner=?", ("student",))
    app.state.connection.commit()
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 200
    assert response.json() == []


def test_create_task_with_empty_object_uses_model_defaults(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post("/api/tasks", headers=headers(), json={})
    assert response.status_code == 201
    assert response.json()["title"] == ""
    assert response.json()["description"] == ""
    assert response.json()["status"] == "TODO"
    assert response.json()["priority"] == "MEDIUM"
    assert response.json()["owner"] == "student"


def test_create_task_requires_json_body(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post("/api/tasks", headers=headers())
    assert response.status_code == 422
    assert response.json() == {
        "detail": [
            {
                "type": "missing",
                "loc": ["body"],
                "msg": "Field required",
                "input": None,
            }
        ]
    }


def test_create_task_rejects_non_string_fields(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": 42},
        )
    assert response.status_code == 422
    assert response.json() == {
        "detail": [
            {
                "type": "string_type",
                "loc": ["body", "title"],
                "msg": "Input should be a valid string",
                "input": 42,
            }
        ]
    }


def test_summary_for_user_without_tasks_returns_zero_counts(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    app.state.connection.execute("DELETE FROM tasks WHERE owner=?", ("student",))
    app.state.connection.commit()
    with TestClient(app) as client:
        response = client.get("/api/summary", headers=headers())
    assert response.status_code == 200
    assert response.json() == {
        "owner": "student",
        "total": 0,
        "byStatus": {},
        "highPriorityOpen": 0,
    }


def test_summary_counts_authenticated_user_tasks(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/summary", headers=headers())
    assert response.status_code == 200
    assert response.json() == {
        "owner": "student",
        "total": 2,
        "byStatus": {"TODO": 1, "IN_PROGRESS": 1},
        "highPriorityOpen": 1,
    }


def test_upload_accepts_empty_file(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"", "text/plain")},
        )
    assert response.status_code == 201
    assert response.json()["size"] == 0
    assert Path(response.json()["storedAt"]).read_bytes() == b""


def test_upload_rejects_filename_without_extension(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements", b"R1", "text/plain")},
        )
    assert response.status_code == 400
    assert response.json() == {"detail": "Unsupported file type"}


def test_upload_requires_file_part(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post("/api/requirements", headers=headers())
    assert response.status_code == 422
    assert response.json() == {
        "detail": [
            {
                "type": "missing",
                "loc": ["body", "file"],
                "msg": "Field required",
                "input": None,
            }
        ]
    }


def test_upload_storage_failure_returns_generic_server_error(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    owner_directory = upload_dir / hashlib.sha256(b"student").hexdigest()
    (owner_directory / "requirements.txt").mkdir(parents=True)
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"R1", "text/plain")},
        )
    assert response.status_code == 500
    assert response.text == "Internal Server Error"


@pytest.mark.parametrize(
    ("method", "path", "request_kwargs"),
    [
        ("GET", "/api/tasks", {}),
        ("POST", "/api/tasks", {"json": {}}),
        ("GET", "/api/summary", {}),
    ],
)
def test_task_database_failures_return_generic_server_error(
    method: str,
    path: str,
    request_kwargs: dict[str, Any],
    tmp_path: Path,
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    app.state.connection.close()
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.request(
            method,
            path,
            headers=headers(),
            **request_kwargs,
        )
    assert response.status_code == 500
    assert response.text == "Internal Server Error"


def test_backend_health_preserves_upstream_error_status(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    http_pool = Mock()
    http_pool.request.return_value = Mock(status=503, data=b'{"status":"DOWN"}')
    monkeypatch.setattr("app.main.urllib3.PoolManager", lambda: http_pool)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())
    assert response.status_code == 200
    assert response.json() == {
        "requestedBy": "student",
        "status": 503,
        "body": '{"status":"DOWN"}',
    }


def test_backend_health_reports_transport_exception_shape(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    http_pool = Mock()
    http_pool.request.side_effect = RuntimeError("test transport failure")
    monkeypatch.setattr("app.main.urllib3.PoolManager", lambda: http_pool)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())
    assert response.status_code == 200
    assert response.json()["status"] == 0
    assert isinstance(response.json()["body"], str)


def test_backend_health_unhandled_response_error_is_generic(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    http_pool = Mock()
    http_pool.request.return_value = Mock(status=object(), data=b"ok")
    monkeypatch.setattr("app.main.urllib3.PoolManager", lambda: http_pool)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.get("/api/backend-health", headers=headers())
    assert response.status_code == 500
    assert response.text == "Internal Server Error"
