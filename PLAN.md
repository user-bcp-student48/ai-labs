# Plan

## api — H-API-01 a H-API-05

Los hallazgos de api H-API-01 a H-API-07 figuran aprobados en `HALLAZGOS.md`. Este plan detalla H-API-01 a H-API-05; H-API-06 y H-API-07 quedan fuera de su alcance. `docs/ENDPOINTS.md` no existe; se toma como referencia el contrato observado allí y en `api/app/main.py`.

### Contrato y coordinaciones previas

- Mantener rutas, métodos HTTP, campos de entrada y nombres de campos de las respuestas exitosas observadas. No se propone crear ni cambiar endpoints.
- Los cambios de seguridad sí deben cerrar comportamientos vulnerables: solo consultar y crear datos del usuario autenticado, rechazar tokens no válidos y rechazar archivos fuera de la política acordada.
- La sustitución del formato Base64 por JWT requiere coordinar con el emisor del token fuera de este alcance: algoritmo, `issuer`, `audience`, clave y transición. No conservar un fallback que acepte el formato inseguro.
- Acordar antes de implementar las extensiones admitidas y el tamaño máximo de archivo; no están definidos en el contrato observado. No se han confirmado advisories ni aprobaciones externas.

### H-API-01 — Token Base64 con secreto embebido

- **Problema:** `legacy_identity` decodifica Base64 y compara un secreto constante; no verifica firma, emisor ni audiencia. Los errores de validación también incluyen detalles en la respuesta 401.
- **Cambio propuesto:** validar un JWT firmado con una biblioteca mantenida, algoritmo permitido explícitamente y claims de firma, `iss`, `aud` y `exp`. Cargar clave, emisor y audiencia desde configuración externa; construir la identidad usada por las rutas a partir de claims verificados. No aceptar el formato Base64 como alternativa. Evaluar y fijar una versión corregida de PyJWT en `api/requirements.txt`; se justifica por la validación criptográfica estandarizada que no ofrece Base64 ni la biblioteca estándar.
- **Archivos a tocar:** `api/app/main.py`, `api/requirements.txt`, `api/tests/test_api.py`.
- **Pruebas de caracterización antes de tocar esos archivos:** ampliar las pruebas de cliente para registrar que las rutas existentes requieren `Authorization: Bearer`, conservan sus códigos y formas de respuesta con una identidad válida y responden 401 ante token inválido. Añadir pruebas de aceptación del JWT acordado y rechazo de firma inválida, `issuer`/`audience` incorrectos y expiración; preparar el emisor de prueba con la misma configuración acordada.

### H-API-02 — Acceso y creación con `owner` controlado por el cliente

- **Problema:** `GET /api/tasks` y `GET /api/summary` usan el `owner` de la consulta si está presente; `POST /api/tasks` permite elegir `owner` en el cuerpo.
- **Cambio propuesto:** mantener rutas y campos observados, pero determinar siempre el propietario desde la identidad verificada. Un `owner` enviado por el cliente no cambia el ámbito de lectura ni el propietario de una tarea creada. Usar modelos Pydantic para tipar la entrada y preservar los campos de salida; no introducir reglas de privilegio por rol no documentadas.
- **Archivos a tocar:** `api/app/main.py`, `api/tests/test_api.py`.
- **Pruebas de caracterización antes de tocar esos archivos:** registrar respuestas y conteos de listado/resumen para el propietario autenticado y la creación sin `owner`. Añadir casos donde se envía otro `owner` en consulta y cuerpo; verificar que no se filtran tareas ajenas y que la tarea nueva queda asociada al usuario autenticado.

### H-API-03 — Concatenación de filtros e inserts SQL

- **Problema:** valores de filtros y campos de tarea se concatenan en las sentencias SQL.
- **Cambio propuesto:** usar placeholders `?` y parámetros enlazados en filtros, inserciones y lectura por `lastrowid`. Mantener las columnas, el orden de tareas y el formato de la respuesta observada.
- **Archivos a tocar:** `api/app/main.py`, `api/tests/test_api.py`.
- **Pruebas de caracterización antes de tocar esos archivos:** registrar el comportamiento normal de listado, creación y resumen; añadir casos con comillas y caracteres SQL en título y descripción para comprobar que se tratan como datos y se devuelven intactos, sin alterar la consulta.

### H-API-04 — Carga de archivos sin normalización ni límites

- **Problema:** se usa `file.filename` directamente para formar la ruta, se lee el archivo completo en memoria y no se valida extensión ni tamaño.
- **Cambio propuesto:** normalizar el nombre y asegurar que el destino permanezca en el directorio de cargas; validar una lista permitida de extensiones y el límite máximo acordados antes de guardar. Leer por bloques con límite para no cargar entradas ilimitadas en memoria. Mantener el endpoint, el campo multipart y los nombres de campos de la respuesta para archivos aceptados; usar errores HTTP genéricos para rechazos.
- **Archivos a tocar:** `api/app/main.py`, `api/tests/test_api.py`.
- **Pruebas de caracterización antes de tocar esos archivos:** registrar la carga válida existente de `requirements.txt`, su ubicación dentro del directorio configurado y la forma de respuesta. Añadir casos de nombre no seguro, extensión no permitida y tamaño por encima del límite acordado; comprobar rechazo y ausencia de escritura fuera del directorio.

### H-API-05 — Versiones fijadas de `urllib3` y `python-multipart`

- **Problema:** `api/requirements.txt` fija las versiones indicadas por el reto; no se ha verificado aquí si hay advisories aplicables ni cuál es la versión corregida.
- **Cambio propuesto:** revisar advisories para las versiones exactas y sus dependencias; actualizar solo los paquetes afectados a versiones corregidas compatibles, justificar el cambio y no silenciar alertas. No añadir dependencias en este paso salvo la biblioteca JWT justificada en H-API-01.
- **Archivos a tocar:** `api/requirements.txt` y, si las pruebas lo requieren, `api/tests/test_api.py`.
- **Pruebas de caracterización antes de tocar esos archivos:** registrar la respuesta normal de `GET /api/backend-health` mediante un servicio HTTP simulado y la carga multipart válida de `POST /api/requirements`, incluidos códigos y campos de respuesta. Tras actualizar, ejecutar estas pruebas y la suite existente para comprobar que el uso de ambas dependencias conserva el comportamiento observado.

### Registro de ejecución — api

Los IDs H-API-01 a H-API-05 estaban aprobados antes de implementar. La confirmación cubre los cambios de seguridad; no se proporcionaron parámetros ni acceso al emisor JWT y su interoperabilidad queda pendiente. La política aplicada para cargas es `.txt`, máximo 200 bytes. No se creó `docs/ENDPOINTS.md` ni `docs/TESTING.md`.

| ID | Cambio y evidencia del diff | Caracterización antes del cambio | Prueba de corrección después del cambio |
|---|---|---|---|
| H-API-01 | Se reemplazó el parser Base64 por `verified_identity`, JWT HS256 y configuración externa; ver `api/app/main.py:16-59` y `api/requirements.txt:6`. | Las cuatro pruebas originales pasaron. Las nuevas pruebas de aceptación JWT no pasaron contra la implementación previa, que no aceptaba JWT; el resultado detallado por prueba no quedó conservado. | `test_rejects_jwt_with_invalid_claims_or_signature`, `test_rejects_bearer_header_without_scheme_separator`, `test_missing_jwt_configuration_fails_closed` y `test_short_jwt_secret_fails_closed`; suite completa final: 21 passed. No se probó el emisor real. |
| H-API-02 | Listado, resumen y creación derivan el propietario de la identidad verificada; ver `api/app/main.py:97-128,221-245`. | La copia temporal de la implementación original confirmó que las consultas permitían solicitar tareas de otro propietario y que la creación aceptaba el `owner` del cuerpo. | `test_owner_query_cannot_read_another_users_tasks` y `test_create_task_uses_authenticated_owner`; suite completa final: 21 passed. |
| H-API-03 | Se añadieron modelos Pydantic y consultas SQLite parametrizadas; ver `api/app/main.py:64-84,97-128,221-244`. | La copia temporal original confirmó que los valores de consulta e inserción podían alterar el SQL concatenado. No se conservó un resultado individual anterior por prueba. | `test_task_queries_and_inserts_preserve_sql_characters`; suite completa final: 21 passed. |
| H-API-04 | Se normaliza y valida el nombre/destino, se limita la lectura a 200 bytes y se permite `.txt`; ver `api/app/main.py:22-23,163-195,247-266`. | La copia temporal original confirmó que no se impedían rutas manipuladas, otras extensiones ni cargas superiores al límite. No se conservó un resultado individual anterior por prueba. | `test_upload_rejects_path_like_filename`, `test_upload_rejects_non_txt_extension`, `test_upload_rejects_file_over_limit` y `test_upload_accepts_file_at_size_limit`; suite completa final: 21 passed. |
| H-API-05 | `urllib3` se actualizó a 2.8.0, `python-multipart` a 0.0.22 y se añadió PyJWT 2.15.1; ver `api/requirements.txt:4-6`. | La suite original de cuatro pruebas pasó antes de actualizar dependencias. No quedó registrado un resultado de escaneo previo. | `test_backend_health_preserves_response_contract` y la carga multipart de la suite completa: 21 passed. `pip check`: sin dependencias rotas. La herramienta de escaneo disponible no admite Python, por lo que queda pendiente el análisis integral de dependencias transitivas. |

#### Resultados de validación

- Antes de modificar la implementación: las cuatro pruebas originales pasaron. Las pruebas nuevas de caracterización se ejecutaron antes del cambio y fallaron contra la implementación original en los comportamientos que requerían JWT; una copia temporal del código confirmó además los problemas de autorización, SQL y carga. No se conservaron los resultados individuales ni el comando exacto de esa ejecución, por lo que no se atribuyen cantidades por hallazgo.
- Después del cambio, ejecutado desde `api/`: `python -m pytest --cov=app --cov-report=term-missing` — **21 passed; 96% de cobertura**.
- `pip check` — sin dependencias rotas; las versiones instaladas coinciden con `api/requirements.txt`.
- Pylance no encontró errores sintácticos. El intérprete que tenía seleccionado Pylance era Python 3.14 y no resolvía FastAPI; las pruebas se ejecutaron con `api/.venv`, Python 3.13.15.
- La revisión de advisories identificó `urllib3` GHSA-gm62-xv2j-4w53/CVE-2025-66418 y advisories posteriores corregidos en 2.8.0; `python-multipart` GHSA-wp53-j4wj-2cfg/CVE-2026-24486 corregido en 0.0.22, con condiciones de configuración no observadas en esta API; PyJWT 2.15.1 contiene los fixes identificados en las fuentes consultadas. No se realizó un escaneo integral de transitivas.

#### Desviaciones y pendientes

- La compatibilidad con el emisor JWT no se verificó: no se leyeron otros componentes ni se recibieron valores técnicos del emisor. La API falla cerrada si falta `AUTH_JWT_SECRET`, `AUTH_JWT_ISSUER` o `AUTH_JWT_AUDIENCE`.
- El escáner de dependencias disponible no acepta Python. La actualización directa y `pip check` no sustituyen un escaneo integral de transitivas.
- Se conservaron las rutas y formas de respuesta observadas. Los rechazos de token inválido, acceso a propietario ajeno y archivos fuera de política son endurecimientos intencionales.

## Item: H-API-04 — Sobrescritura entre usuarios de archivos cargados

- **Aprobación humana:** aprobada explícitamente por el usuario el 2026-11-03 para el aislamiento de cargas por identidad descrito en este apartado.
- **Item de `HALLAZGOS.md`:** H-API-04, componente `api`, estado actual `Pendiente`. Las validaciones de nombre, extensión y tamaño existen, pero todos los usuarios escriben bajo el mismo directorio y un nombre repetido reemplaza el archivo previo (`api/app/main.py:247-266`).
- **Problema:** dos identidades distintas que carguen el mismo nombre pueden sobrescribir el contenido de la otra. La identidad autenticada aparece en la respuesta, pero no aísla el destino en disco.
- **Cambio propuesto:** mantener `POST /api/requirements`, el campo multipart `file`, los estados y la forma de respuesta. Guardar cada archivo bajo un subdirectorio propio de la identidad autenticada, derivado de un digest SHA-256 de `identity["username"]` para que el claim no se use directamente como segmento de ruta. Mantener el nombre de archivo validado dentro de ese subdirectorio. Esto preserva el reemplazo del mismo nombre por el mismo usuario, pero evita colisiones entre usuarios. No añadir dependencias. Actualizar la nota de almacenamiento de `docs/ENDPOINTS.md` sin alterar nombres de campos ni el contrato HTTP.
- **Archivos a tocar:** `api/tests/test_api.py` (primero, prueba de caracterización), `api/app/main.py` (después, implementación), `docs/ENDPOINTS.md` y `docs/TESTING.md` (documentar la nueva ubicación y la prueba), `HALLAZGOS.md` (marcar H-API-04 `Resuelto` solo al verificar la corrección y las pruebas).
- **Prueba de caracterización antes de tocar la implementación:** crear una app temporal y, usando tokens válidos de dos usuarios distintos, subir para ambos el mismo nombre `.txt` con contenidos diferentes. Comprobar que ambos uploads responden con la forma actual, que `storedAt` identifica destinos distintos, que ambos archivos conservan su propio contenido y que los destinos permanecen bajo el directorio de uploads. Ejecutar primero contra la implementación actual para confirmar el fallo por colisión; solo después modificar `api/app/main.py`.
- **Pruebas de regresión:** conservar las pruebas actuales de traversal, extensión, tamaño y carga válida; ejecutar desde `api/` la suite completa con cobertura y confirmar al menos 85% según el umbral vigente. Registrar comando, código de salida, cobertura y cualquier limitación real en `docs/TESTING.md`.
- **Contrato y criterio de cierre:** no cambiar rutas, método, código de éxito ni campos (`owner`, `fileName`, `storedAt`, `size`). `storedAt` continuará siendo una ruta absoluta, ahora dentro del subdirectorio de la identidad. No cambiar el estado del hallazgo ni afirmar aprobación antes de que la prueba de colisión falle antes y pase después de la implementación.
- **Ejecución:** antes del cambio, `.\.venv\Scripts\python.exe -m pytest -q tests/test_api.py::test_same_upload_name_is_isolated_between_users` desde `api/` terminó con código 1: la prueba falló porque ambas identidades recibieron la misma ruta `uploads/requirements.txt`. Después del cambio, la misma prueba pasó (código 0, 1 passed); las cinco pruebas enfocadas de carga pasaron (código 0, 5 passed). La primera regresión completa detectó cuatro aserciones aún ligadas a la ruta plana; se actualizaron para comprobar `storedAt` y la ubicación por identidad, y la suite final pasó: 48 passed, cobertura de sentencias 162/166 (97.59%), ramas 22/26 (84.62%), cobertura combinada 184/192 (95.83%). Comando: `.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing`, ejecutado desde `api/`.
- **Resultado/diff local:** `api/app/main.py` deriva y valida el subdirectorio a partir de SHA-256 del username verificado; `api/tests/test_api.py` comprueba rutas separadas, contención bajo uploads y preservación de ambos contenidos. Se actualizaron `docs/ENDPOINTS.md` y `docs/TESTING.md`; H-API-04 pasó a `Resuelto` en `HALLAZGOS.md`.
