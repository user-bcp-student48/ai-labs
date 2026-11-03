# Contrato HTTP — API FastAPI

Base URL local: `http://localhost:8000` (la guía de ejecución está en `README.md`). Este documento describe el comportamiento observado en el código actual; no se ha verificado interoperabilidad con un emisor JWT real.

## Contrato observado, acordado y riesgos pendientes

- **Observado:** rutas, campos y respuestas de las cinco operaciones documentadas a continuación, además de la autenticación y validación descritas aquí.
- **Acordado:** se preservan las rutas y formas de respuesta existentes. No hay valores de despliegue confirmados para issuer/audience ni evidencia de una prueba de integración con el emisor; no se presentan esos valores como acordados.
- **Riesgos pendientes:** un usuario puede reemplazar su propio archivo al volver a cargar el mismo nombre. Las cargas de usuarios distintos se aíslan en subdirectorios separados.

## Configuración y autenticación común

Todas las rutas requieren el header exacto en esta forma:

```http
Authorization: Bearer <JWT>
```

El token debe estar firmado con HS256 e incluir los claims `exp`, `iss`, `aud`, `username` y `role`. `username` y `role` deben ser strings no vacíos. La API compara issuer y audience con la configuración del proceso; exige que el secreto tenga al menos 32 bytes UTF-8. El código requiere estas variables y no les asigna valores por defecto:

| Variable | Requisito |
|---|---|
| `AUTH_JWT_SECRET` | Secreto HS256 de al menos 32 bytes UTF-8. |
| `AUTH_JWT_ISSUER` | Debe coincidir con `iss` del token. |
| `AUTH_JWT_AUDIENCE` | Debe coincidir con `aud` del token. |

La API no define autorización por rol: el claim `role` se valida, pero las operaciones de tareas limitan los datos por `username`. Los valores efectivos de issuer/audience y la obtención del JWT deben coordinarse con el emisor del entorno; no se pueden deducir de este repositorio.

| Variable opcional | Valor por defecto observado |
|---|---|
| `WORKSHOP_DB_PATH` | `./data/workshop.db` |
| `WORKSHOP_UPLOAD_DIR` | `./uploads` |
| `AUTH_HEALTH_URL` | `http://localhost:8080/q/health` |

Las referencias de implementación están en `api/app/main.py`: autenticación, líneas 16-61; modelos, 64-90; rutas, 221-275.

---

## GET /api/tasks

### 1. Qué hace

Devuelve las tareas asociadas al usuario autenticado.

### 2. Autenticación

Sí. Enviar `Authorization: Bearer <JWT>` con los claims y configuración de la sección común. Un header ausente, mal formado o con token no válido recibe 401; la configuración de autenticación ausente o inválida recibe 503.

### 3. Request

No acepta body.

| Campo/parámetro | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `Authorization` (header) | string, Bearer JWT | Sí | Token firmado y verificado por la API. |
| `owner` (query) | string | No | Se acepta por compatibilidad, pero se ignora. La identidad del token determina el propietario consultado. |

Ejemplo completo; el valor de autorización debe reemplazarse por un JWT válido emitido para el entorno:

```http
GET /api/tasks?owner=trainer HTTP/1.1
Host: localhost:8000
Authorization: Bearer <JWT válido>
```

### 4. Response exitosa

**200 OK**

| Campo | Tipo | Descripción |
|---|---|---|
| respuesta | array de objetos | Lista de tareas del `username` del token; puede estar vacía. |
| `id` | integer | Identificador de la tarea. |
| `title` | string | Título. |
| `description` | string | Descripción. |
| `status` | string | Estado almacenado. |
| `owner` | string | Propietario autenticado. |
| `priority` | string | Prioridad almacenada. |
| `createdAt` | string | Fecha/hora SQLite con formato `YYYY-MM-DD HH:MM:SS`. |

Ejemplo completo:

```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "id": 1,
    "title": "Refactor login",
    "description": "Separar responsabilidades en frontend",
    "status": "TODO",
    "owner": "student",
    "priority": "HIGH",
    "createdAt": "2026-11-02 17:00:00"
  }
]
```

Los valores `id` y `createdAt` dependen de la base de datos y del momento de creación.

### 5. Errores

| Código | Cuándo ocurre | Ejemplo de cuerpo |
|---|---|---|
| 401 | Falta el Bearer token, su formato es inválido, o la firma/claims no se validan. | `{"detail":"Invalid token"}` |
| 503 | Falta configuración JWT, issuer/audience no están configurados o el secreto tiene menos de 32 bytes. | `{"detail":"Authentication is not configured"}` |
| 500 | Error interno no manejado al consultar SQLite. FastAPI/Starlette responde texto plano. | `Internal Server Error` |

### 6. Notas de seguridad

- El parámetro `owner` no amplía el acceso: se filtra siempre con el `username` verificado del token (`api/app/main.py:221-227`).
- No se aplican permisos adicionales según `role`.

---

## POST /api/tasks

### 1. Qué hace

Crea una tarea para el usuario autenticado.

### 2. Autenticación

Sí. Enviar `Authorization: Bearer <JWT>` con los claims y configuración de la sección común. Un header ausente, mal formado o con token no válido recibe 401; la configuración de autenticación ausente o inválida recibe 503.

### 3. Request

El body JSON es obligatorio; todos sus campos son opcionales y tienen los valores por defecto indicados.

| Campo/parámetro | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `Authorization` (header) | string, Bearer JWT | Sí | Token firmado y verificado por la API. |
| body | objeto JSON | Sí | Objeto `TaskCreateRequest`. |
| `title` | string | No | Título; por defecto `""`. |
| `description` | string | No | Descripción; por defecto `""`. |
| `status` | string | No | Estado; por defecto `"TODO"`. No se valida contra una enumeración. |
| `priority` | string | No | Prioridad; por defecto `"MEDIUM"`. No se valida contra una enumeración. |
| `owner` | string o null | No | Se acepta, pero se ignora. La tarea queda asociada al `username` del token. |

Ejemplo completo:

```http
POST /api/tasks HTTP/1.1
Host: localhost:8000
Authorization: Bearer <JWT válido>
Content-Type: application/json

{
  "title": "Review onboarding",
  "description": "Capture approval requirements",
  "status": "TODO",
  "priority": "HIGH",
  "owner": "trainer"
}
```

### 4. Response exitosa

**201 Created**

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | integer | Identificador generado por SQLite. |
| `title` | string | Título guardado. |
| `description` | string | Descripción guardada. |
| `status` | string | Estado guardado. |
| `owner` | string | `username` del token, no el valor enviado en el body. |
| `priority` | string | Prioridad guardada. |
| `createdAt` | string | Fecha/hora SQLite con formato `YYYY-MM-DD HH:MM:SS`. |

Ejemplo completo para el token del usuario `student`:

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": 4,
  "title": "Review onboarding",
  "description": "Capture approval requirements",
  "status": "TODO",
  "owner": "student",
  "priority": "HIGH",
  "createdAt": "2026-11-02 17:00:00"
}
```

El `id` y `createdAt` son valores dinámicos.

### 5. Errores

| Código | Cuándo ocurre | Ejemplo de cuerpo |
|---|---|---|
| 401 | Falta el Bearer token, su formato es inválido, o la firma/claims no se validan. | `{"detail":"Invalid token"}` |
| 503 | Falta configuración JWT, issuer/audience no están configurados o el secreto tiene menos de 32 bytes. | `{"detail":"Authentication is not configured"}` |
| 422 | Falta el body o un campo no tiene el tipo string requerido. Ejemplo de `title` numérico: | `{"detail":[{"type":"string_type","loc":["body","title"],"msg":"Input should be a valid string","input":42}]}` |
| 500 | Error interno no manejado al escribir o consultar SQLite. FastAPI/Starlette responde texto plano. | `Internal Server Error` |

### 6. Notas de seguridad

- El propietario lo establece la identidad JWT verificada; el campo `owner` del request no puede asignar tareas a otra persona (`api/app/main.py:229-235`).
- Los valores `status` y `priority` son strings libres; no hay autorización por rol ni validación de enumeraciones.

---

## GET /api/summary

### 1. Qué hace

Resume el número y estado de las tareas del usuario autenticado, además de contar sus tareas prioritarias abiertas.

### 2. Autenticación

Sí. Enviar `Authorization: Bearer <JWT>` con los claims y configuración de la sección común. Un header ausente, mal formado o con token no válido recibe 401; la configuración de autenticación ausente o inválida recibe 503.

### 3. Request

No acepta body.

| Campo/parámetro | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `Authorization` (header) | string, Bearer JWT | Sí | Token firmado y verificado por la API. |
| `owner` (query) | string | No | Se acepta por compatibilidad, pero se ignora. El resumen se calcula para el `username` del token. |

Ejemplo completo:

```http
GET /api/summary?owner=trainer HTTP/1.1
Host: localhost:8000
Authorization: Bearer <JWT válido>
```

### 4. Response exitosa

**200 OK**

| Campo | Tipo | Descripción |
|---|---|---|
| `owner` | string | `username` del token. |
| `total` | integer | Total de tareas del usuario. |
| `byStatus` | objeto de string a integer | Conteo agrupado por cada valor de `status`. |
| `highPriorityOpen` | integer | Tareas con `priority == "HIGH"` y `status` distinto de `"DONE"` y `"ARCHIVED"`. |

Ejemplo completo:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "owner": "student",
  "total": 2,
  "byStatus": {
    "TODO": 1,
    "IN_PROGRESS": 1
  },
  "highPriorityOpen": 1
}
```

### 5. Errores

| Código | Cuándo ocurre | Ejemplo de cuerpo |
|---|---|---|
| 401 | Falta el Bearer token, su formato es inválido, o la firma/claims no se validan. | `{"detail":"Invalid token"}` |
| 503 | Falta configuración JWT, issuer/audience no están configurados o el secreto tiene menos de 32 bytes. | `{"detail":"Authentication is not configured"}` |
| 500 | Error interno no manejado al consultar SQLite. FastAPI/Starlette responde texto plano. | `Internal Server Error` |

### 6. Notas de seguridad

- La consulta usa el `username` verificado; el parámetro `owner` no permite leer el resumen de otra identidad (`api/app/main.py:237-244`).
- `highPriorityOpen` excluye únicamente los estados exactos `"DONE"` y `"ARCHIVED"`; el código no normaliza mayúsculas/minúsculas.

---

## POST /api/requirements

### 1. Qué hace

Recibe y guarda un archivo de texto con un requerimiento.

### 2. Autenticación

Sí. Enviar `Authorization: Bearer <JWT>` con los claims y configuración de la sección común. Un header ausente, mal formado o con token no válido recibe 401; la configuración de autenticación ausente o inválida recibe 503.

### 3. Request

El request usa `multipart/form-data`.

| Campo/parámetro | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `Authorization` (header) | string, Bearer JWT | Sí | Token firmado y verificado por la API. |
| `file` (parte multipart) | archivo | Sí | Nombre simple con extensión `.txt` y máximo 200 bytes. La comparación de extensión no distingue mayúsculas/minúsculas; no se valida el MIME type. |

Ejemplo completo:

```bash
curl -X POST "http://localhost:8000/api/requirements" \
  -H "Authorization: Bearer <JWT válido>" \
  -F "file=@requirements.txt;type=text/plain"
```

### 4. Response exitosa

**201 Created**

| Campo | Tipo | Descripción |
|---|---|---|
| `owner` | string | `username` del token. |
| `fileName` | string | Nombre validado y guardado. |
| `storedAt` | string | Ruta absoluta resuelta del archivo en el servidor, dentro del subdirectorio opaco de la identidad; depende del directorio configurado y del sistema operativo. |
| `size` | integer | Tamaño del contenido guardado en bytes. |

Ejemplo completo para un archivo de dos bytes:

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "owner": "student",
  "fileName": "requirements.txt",
  "storedAt": "/configured-upload-dir/<owner-key>/requirements.txt",
  "size": 2
}
```

La ruta del ejemplo es ilustrativa; la respuesta contiene la ruta absoluta de la instalación.

### 5. Errores

| Código | Cuándo ocurre | Ejemplo de cuerpo |
|---|---|---|
| 400 | Nombre vacío o con separadores/ruta, o extensión distinta de `.txt`. | `{"detail":"Invalid file name"}` o `{"detail":"Unsupported file type"}` |
| 401 | Falta el Bearer token, su formato es inválido, o la firma/claims no se validan. | `{"detail":"Invalid token"}` |
| 413 | El archivo excede 200 bytes. | `{"detail":"File exceeds maximum size"}` |
| 422 | Falta la parte multipart `file`. | `{"detail":[{"type":"missing","loc":["body","file"],"msg":"Field required","input":null}]}` |
| 503 | Falta configuración JWT, issuer/audience no están configurados o el secreto tiene menos de 32 bytes. | `{"detail":"Authentication is not configured"}` |
| 500 | Error interno no manejado al guardar el archivo. FastAPI/Starlette responde texto plano. | `Internal Server Error` |

### 6. Notas de seguridad

- Se rechazan rutas, extensiones no permitidas y archivos mayores de 200 bytes (`api/app/main.py:163-191,247-258`).
- El nombre se conserva dentro de un subdirectorio determinado por SHA-256 del `username` verificado; el username no se usa como segmento de ruta. Usuarios diferentes quedan aislados. El mismo usuario puede reemplazar su carga anterior si repite el nombre.
- `storedAt` revela la ruta absoluta de almacenamiento del servidor.

---

## GET /api/backend-health

### 1. Qué hace

Consulta el endpoint de salud del servicio de autenticación configurado y devuelve el resultado junto con la identidad que solicitó la consulta.

### 2. Autenticación

Sí. Enviar `Authorization: Bearer <JWT>` con los claims y configuración de la sección común. Un header ausente, mal formado o con token no válido recibe 401; la configuración de autenticación ausente o inválida recibe 503.

### 3. Request

No acepta body ni parámetros de query.

| Campo/parámetro | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `Authorization` (header) | string, Bearer JWT | Sí | Token firmado y verificado por la API. |

Ejemplo completo:

```http
GET /api/backend-health HTTP/1.1
Host: localhost:8000
Authorization: Bearer <JWT válido>
```

### 4. Response exitosa

**200 OK**

| Campo | Tipo | Descripción |
|---|---|---|
| `requestedBy` | string | `username` del token. |
| `status` | integer | Código HTTP recibido del servicio consultado. |
| `body` | string | Cuerpo recibido, decodificado como UTF-8. |

Ejemplo completo cuando el servicio configurado responde 200:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "requestedBy": "student",
  "status": 200,
  "body": "{\"status\":\"UP\"}"
}
```

### 5. Errores

| Código | Cuándo ocurre | Ejemplo de cuerpo |
|---|---|---|
| 401 | Falta el Bearer token, su formato es inválido, o la firma/claims no se validan. | `{"detail":"Invalid token"}` |
| 503 | Falta configuración JWT, issuer/audience no están configurados o el secreto tiene menos de 32 bytes. | `{"detail":"Authentication is not configured"}` |
| 500 | Error interno no manejado fuera de la llamada externa. FastAPI/Starlette responde texto plano. | `Internal Server Error` |

Si la llamada HTTP externa lanza una excepción, la ruta la captura y sigue respondiendo **200 OK**, con `status: 0` y `body` igual al texto de la excepción:

```json
{
  "requestedBy": "student",
  "status": 0,
  "body": "<texto de la excepción>"
}
```

El texto depende de la excepción concreta.
Si el servicio externo responde con un código 4xx o 5xx sin lanzar una excepción, la API también devuelve HTTP 200 y coloca ese código externo en el campo `status`.

### 6. Notas de seguridad

- La URL se obtiene de `AUTH_HEALTH_URL`; si falta, se usa `http://localhost:8080/q/health` (`api/app/main.py:269-271`).
- La petición externa usa timeout de 3 segundos. Los fallos externos no se traducen a un status HTTP de error; se representan en el JSON con `status: 0`.
- El texto crudo de la excepción se incluye en `body` (`api/app/main.py:274-275`); no debe tratarse como mensaje seguro para mostrar a usuarios finales.
