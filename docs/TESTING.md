# Pruebas — API FastAPI

## Alcance y entorno

La suite usa `TestClient`, JWT de prueba aislados y bases/directorios temporales. Las llamadas al servicio externo de salud se simulan; no se certifica interoperabilidad con el emisor JWT ni disponibilidad de otros servicios. La fuente ejecutable es `api/tests/test_api.py`.

Cada fila indica el endpoint, el caso, su categoría y el resultado esperado documentado en `docs/ENDPOINTS.md`. Los IDs relacionan la prueba con el seguimiento de `HALLAZGOS.md`.

## Matriz de casos

| endpoint | caso | tipo | resultado esperado | ID / prueba |
|---|---|---|---|---|
| `GET /api/tasks` | Listar tareas sembradas del usuario | Camino feliz | 200; array con los campos de tarea documentados. | H-API-01/02; `test_lists_seeded_tasks` |
| `GET /api/tasks` | Usuario sin tareas | Borde | 200; `[]`. | H-API-02; `test_lists_empty_task_set` |
| `GET /api/tasks` | `owner=trainer` con token de `student` | Error/seguridad | 200; solo tareas de `student`; ninguna tarea ajena. | H-API-02; `test_owner_query_cannot_read_another_users_tasks` |
| `GET /api/tasks` | Token inválido | Error | 401; `{"detail":"Invalid token"}`. | H-API-01; `test_all_endpoints_reject_invalid_authentication[list-tasks]` |
| `GET /api/tasks` | Falta configuración JWT | Error | 503; `{"detail":"Authentication is not configured"}`. | H-API-01; `test_all_endpoints_fail_closed_without_jwt_configuration[list-tasks]` |
| `GET /api/tasks` | SQLite cerrado | Error | 500; texto `Internal Server Error`. | H-API-03; `test_task_database_failures_return_generic_server_error[list-tasks]` |
| `POST /api/tasks` | Crear tarea válida | Camino feliz | 201; respuesta con la tarea y `owner` del token. | H-API-01/02/03; `test_creates_task_and_summary`, `test_create_task_uses_authenticated_owner` |
| `POST /api/tasks` | Objeto JSON vacío | Borde | 201; defaults `title=""`, `description=""`, `status="TODO"`, `priority="MEDIUM"`. | H-API-03; `test_create_task_with_empty_object_uses_model_defaults` |
| `POST /api/tasks` | Body ausente o campo no string | Error | 422; detalle de validación Pydantic. | H-API-03; `test_create_task_requires_json_body`, `test_create_task_rejects_non_string_fields` |
| `POST /api/tasks` | Token inválido / configuración ausente | Error | 401 / 503 con los cuerpos comunes documentados. | H-API-01; `test_all_endpoints_reject_invalid_authentication[create-task]`, `test_all_endpoints_fail_closed_without_jwt_configuration[create-task]` |
| `POST /api/tasks` | SQLite cerrado | Error | 500; texto `Internal Server Error`. | H-API-03; `test_task_database_failures_return_generic_server_error[create-task]` |
| `POST /api/tasks` | Título y descripción con comillas | Borde/seguridad | 201; se conservan como datos y no alteran las consultas. | H-API-03; `test_task_queries_and_inserts_preserve_sql_characters` |
| `GET /api/summary` | Conteo de tareas del usuario | Camino feliz | 200; `owner`, `total`, `byStatus` y `highPriorityOpen` correctos. | H-API-02; `test_summary_counts_authenticated_user_tasks` |
| `GET /api/summary` | Usuario sin tareas | Borde | 200; total 0, `byStatus={}` y `highPriorityOpen=0`. | H-API-02; `test_summary_for_user_without_tasks_returns_zero_counts` |
| `GET /api/summary` | `owner=trainer` con token de `student` | Error/seguridad | 200; resumen limitado a `student`. | H-API-02; `test_owner_query_cannot_read_another_users_tasks` |
| `GET /api/summary` | Token inválido / configuración ausente | Error | 401 / 503 con los cuerpos comunes documentados. | H-API-01; `test_all_endpoints_reject_invalid_authentication[summary]`, `test_all_endpoints_fail_closed_without_jwt_configuration[summary]` |
| `GET /api/summary` | SQLite cerrado | Error | 500; texto `Internal Server Error`. | H-API-03; `test_task_database_failures_return_generic_server_error[summary]` |
| `POST /api/requirements` | Archivo `.txt` válido | Camino feliz | 201; nombre, ruta, tamaño y propietario en respuesta. | H-API-04/05; `test_uploads_requirement` |
| `POST /api/requirements` | Dos usuarios suben el mismo nombre | Borde/seguridad | 201 para ambos; rutas distintas bajo uploads y contenido de cada usuario intacto. | H-API-04; `test_same_upload_name_is_isolated_between_users` |
| `POST /api/requirements` | Archivo vacío / archivo de exactamente 200 bytes | Borde | 201; tamaño 0 / 200 bytes. | H-API-04; `test_upload_accepts_empty_file`, `test_upload_accepts_file_at_size_limit` |
| `POST /api/requirements` | Nombre sin extensión o extensión no permitida | Error | 400; `{"detail":"Unsupported file type"}`. | H-API-04; `test_upload_rejects_filename_without_extension`, `test_upload_rejects_non_txt_extension` |
| `POST /api/requirements` | Nombre con ruta | Error/seguridad | 400; `{"detail":"Invalid file name"}` y sin escritura fuera del directorio. | H-API-04; `test_upload_rejects_path_like_filename` |
| `POST /api/requirements` | Archivo mayor de 200 bytes | Error | 413; `{"detail":"File exceeds maximum size"}`. | H-API-04; `test_upload_rejects_file_over_limit` |
| `POST /api/requirements` | Falta la parte `file` | Error | 422; error de campo multipart requerido. | H-API-04; `test_upload_requires_file_part` |
| `POST /api/requirements` | Token inválido / configuración ausente | Error | 401 / 503 con los cuerpos comunes documentados. | H-API-01; `test_all_endpoints_reject_invalid_authentication[upload-requirement]`, `test_all_endpoints_fail_closed_without_jwt_configuration[upload-requirement]` |
| `POST /api/requirements` | Destino de almacenamiento no escribible | Error | 500; texto `Internal Server Error`. | H-API-04; `test_upload_storage_failure_returns_generic_server_error` |
| `GET /api/backend-health` | Servicio externo responde 200 | Camino feliz | 200; incluye `requestedBy`, status del servicio y body UTF-8. | H-API-05; `test_backend_health_preserves_response_contract` |
| `GET /api/backend-health` | Servicio externo responde 503 | Borde | HTTP 200; el JSON conserva `status=503` y el body externo. | H-API-05; `test_backend_health_preserves_upstream_error_status` |
| `GET /api/backend-health` | Error de transporte externo | Error observado | HTTP 200; `status=0` y `body` string con la excepción, según el contrato observado. | H-API-07; `test_backend_health_reports_transport_exception_shape` |
| `GET /api/backend-health` | Token inválido / configuración ausente | Error | 401 / 503 con los cuerpos comunes documentados. | H-API-01; `test_all_endpoints_reject_invalid_authentication[backend-health]`, `test_all_endpoints_fail_closed_without_jwt_configuration[backend-health]` |
| `GET /api/backend-health` | Error interno no manejado al serializar respuesta | Error | 500; texto `Internal Server Error`. | H-API-07; `test_backend_health_unhandled_response_error_is_generic` |

## Código de los tests añadidos

Estos fragmentos corresponden al código añadido en `api/tests/test_api.py`; usan los imports, fixture JWT y funciones auxiliares definidos en ese mismo archivo. El resto de pruebas de camino feliz y caracterización ya existentes también permanece allí.

```python
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
    assert (upload_dir / "requirements.txt").read_bytes() == b""


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
    (upload_dir / "requirements.txt").mkdir()
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
```

## Ejecuciones y cobertura

Comando ejecutado desde `api/`:

```powershell
.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing
```

**Código de salida:** 0. **Resultado:** 47 passed, 97 warnings. Las advertencias son deprecaciones de `anyio` y del uso de `FastAPI.on_event` en `api/app/main.py:277`; no son fallos de pruebas.

| Métrica | Numerador / denominador | Resultado | Método |
|---|---:|---:|---|
| Código API bajo prueba (`app/main.py`) | 158 / 161 sentencias | 98.14% | Sentencias ejecutadas / sentencias detectadas por coverage.py. |
| Ramas de `app/main.py` | 21 / 24 ramas | 87.5% | Ramas cubiertas / ramas detectadas. |
| Cobertura combinada del módulo | 179 / 185 unidades | 96.76% (97% reportado) | Sentencias y ramas cubiertas / total de sentencias y ramas; pytest-cov con reporte de terminal. |
| Cobertura global de `app/` | 179 / 185 unidades | 96.76% (97% reportado) | Igual que el módulo: `app/main.py` contiene todas las sentencias de la aplicación; `app/__init__.py` no aporta sentencias ejecutables. |

No se modificó código de implementación; por ello, no se calculó cobertura diferencial del diff. La cobertura del módulo API y de ramas supera el umbral del 85%. El reporte marca como no cubiertas las sentencias 126, 165 y 255 de `api/app/main.py`; quedan como propuestas los casos de fila de creación no recuperable, nombre vacío y destino resuelto fuera del directorio.

## Fallos, pendientes y pruebas propuestas

### Fallos o riesgos detectados

- **H-API-04 — sobrescritura de cargas:** este riesgo se corrigió el 2026-11-03 dentro del alcance aprobado. Los subdirectorios derivados de la identidad impiden que usuarios distintos reemplacen entre sí archivos del mismo nombre; el mismo usuario conserva la semántica de reemplazo de su propio archivo.
- **H-API-07 — detalle de excepciones:** `test_backend_health_reports_transport_exception_shape` registra el contrato observado (`status=0`, `body` string), no valida que sea seguro exponer el texto. El endpoint devuelve `str(error)` (`api/app/main.py:274-275`), contrario a `docs/GOOD_PRACTICES.md`; requiere corrección independiente.

### Pendientes

- La matriz no prueba interoperabilidad con el emisor JWT real ni valores de issuer/audience de despliegue.
- No se ejecutó un escaneo SCA de dependencias transitivas para H-API-05.
- La cobertura global no certifica por sí sola el diff.

### Pruebas propuestas

- Definir primero la política de colisión (rechazo, nombre único o almacenamiento por usuario) para H-API-04; luego añadir una prueba que compruebe que una carga no sustituye el archivo de otro usuario.
- Añadir casos de nombre vacío y de destino simbólico resuelto fuera del directorio; el entorno Windows puede requerir permisos para crear symlinks.
- Simular el caso de `TaskRepository.create` que no recupera la fila insertada, para cubrir la sentencia 126.
- Para H-API-07, acordar primero una respuesta genérica de error y después cambiar la prueba de contrato para exigir que no se filtre `str(exception)`.

## Ejecución de regresión — H-API-04 (2026-11-03)

- Caracterización antes de implementar, desde `api/`: `.\.venv\Scripts\python.exe -m pytest -q tests/test_api.py::test_same_upload_name_is_isolated_between_users` — **código 1**; 1 failed. Ambas respuestas señalaban la misma ruta `uploads/requirements.txt`, demostrando la colisión observada.
- Después del cambio, prueba enfocada: `.\.venv\Scripts\python.exe -m pytest -q tests/test_api.py::test_same_upload_name_is_isolated_between_users` — **código 0**; 1 passed.
- Regresión de cargas: `.\.venv\Scripts\python.exe -m pytest -q tests/test_api.py::test_uploads_requirement tests/test_api.py::test_same_upload_name_is_isolated_between_users tests/test_api.py::test_upload_accepts_file_at_size_limit tests/test_api.py::test_upload_accepts_empty_file tests/test_api.py::test_upload_storage_failure_returns_generic_server_error` — **código 0**; 5 passed.
- Suite final, desde `api/`: `.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing` — **código 0**; 48 passed, 99 advertencias deprecadas.

| Métrica posterior al cambio | Numerador / denominador | Resultado | Método |
|---|---:|---:|---|
| Sentencias `app/main.py` | 162 / 166 | 97.59% | coverage.py, sentencias cubiertas / sentencias detectadas. |
| Ramas `app/main.py` | 22 / 26 | 84.62% | coverage.py, ramas cubiertas / ramas detectadas. |
| Cobertura combinada del módulo | 184 / 192 | 95.83% (96% reportado) | coverage.py, sentencias y ramas cubiertas / total de unidades detectadas. |
| Cobertura global de `app/` | 184 / 192 | 95.83% (96% reportado) | Igual que el módulo: `app/main.py` contiene la implementación de `app/`. |

El umbral de 85% se supera en cobertura de sentencias y combinada; la cobertura de ramas sola queda en 84.62%, y se registra sin alterar gates. La suite emite advertencias deprecadas de `anyio` y `FastAPI.on_event`.

## Documentos consultados

- `README.md`: ejecución del componente API y comando de pruebas documentado.
- `RETO.md`: hallazgos sembrados de API.
- `docs/GOOD_PRACTICES.md`: criterios de tipos, autenticación, autorización, archivos y errores.
- `docs/ENDPOINTS.md`: rutas, contratos, errores y riesgos observados.
- `HALLAZGOS.md` y `PLAN.md`: IDs, estados y alcance de API.
- `api/app/main.py` y `api/tests/test_api.py`: implementación y pruebas existentes.
