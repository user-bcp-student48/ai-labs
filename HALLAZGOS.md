# Hallazgos

## Contrato observado antes del cambio — api

`docs/ENDPOINTS.md` no existe; lo siguiente se deriva de `api/app/main.py` y no representa un contrato acordado:

- `GET /api/tasks` y `GET /api/summary` aceptan `owner` opcional y requieren una identidad obtenida de `Authorization`. La primera ruta responde tareas con `id`, `title`, `description`, `status`, `owner`, `priority` y `createdAt`; la segunda responde `owner`, `total`, `byStatus` y `highPriorityOpen`.
- `POST /api/tasks` acepta un cuerpo JSON y responde `201` con la tarea creada.
- `POST /api/requirements` acepta el campo multipart `file` y responde `201` con `owner`, `fileName`, `storedAt` y `size`.
- `GET /api/backend-health` requiere identidad y consulta `AUTH_HEALTH_URL` (por defecto, `http://localhost:8080/q/health`); responde `requestedBy`, `status` y `body`.
- El token observado es Base64 de `username|role|expiración|secreto`. El código compara el secreto estático y la expiración, pero no verifica firma, emisor ni audiencia.

## Comportamiento implementado — api

- Se conservan las rutas y formas de respuesta descritas arriba. La API ahora requiere JWT `HS256` con claims `username`, `role`, `iss`, `aud` y `exp`; la clave, el emisor y la audiencia se leen de variables de entorno.
- `POST /api/requirements` conserva el formulario y los campos de respuesta para archivos `.txt` de hasta 200 bytes; los demás se rechazan.

## Seguimiento — api

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-API-01 | api | Token Base64 con secreto embebido y sin validación de firma, emisor o audiencia | Vulnerabilidad | Resuelto: se valida JWT `HS256`, firma y claims requeridos; secretos y valores de issuer/audience no quedan en el código. La interoperabilidad con el emisor externo no se ejecutó en este alcance. | api/app/main.py:16-59 | alta | Resuelto |
| H-API-02 | api | Consultas por `owner` controladas por el cliente | Vulnerabilidad | Resuelto: listado, resumen y creación usan `identity["username"]`; el campo `owner` del cliente no amplía el acceso ni cambia el propietario de la tarea. | api/app/main.py:97-128,221-245 | alta | Resuelto |
| H-API-03 | api | Filtros e inserts SQL concatenan valores | Vulnerabilidad | Resuelto: los filtros, inserts y la lectura por ID usan placeholders y parámetros SQLite; los modelos Pydantic tipan las entradas y salidas de tareas. | api/app/main.py:81-128,221-244 | alta | Resuelto |
| H-API-04 | api | Carga de archivos usa el nombre recibido y no limita tamaño ni extensión | Vulnerabilidad | Resuelto: se almacena bajo un subdirectorio derivado con SHA-256 de la identidad verificada, aislando propietarios y manteniendo los campos de respuesta. `test_same_upload_name_is_isolated_between_users` falló antes y pasó después; diff local en `api/app/main.py` y `api/tests/test_api.py`. | api/app/main.py:254-268; api/tests/test_api.py:135-158 | alta | Resuelto |
| H-API-05 | api | Dependencias fijadas en versiones candidatas a revisión SCA | Vulnerabilidad | Resuelto para los paquetes directos: `urllib3` se actualizó a 2.8.0 y `python-multipart` a 0.0.22 según advisories; PyJWT 2.15.1 se añadió para JWT. No se ejecutó un escaneo integral de transitivas. | api/requirements.txt:4-6 | media | Resuelto |
| H-API-06 | api | Rutas, persistencia, reglas y serialización concentradas en un módulo | Refactor | La aplicación inicializa SQLite, transforma filas y define endpoints y reglas en el mismo archivo, dificultando aislar responsabilidades y probar cambios con seguridad. | api/app/main.py:29-56,67-125 | baja | Aprobado |
| H-API-07 | api | Tipado crudo, excepciones amplias y lógica anidada | Refactor | Las rutas aceptan y devuelven diccionarios sin modelos, hay capturas amplias y condicionales anidados; esto reduce la validación del contrato y puede devolver detalles internos de errores al cliente. | api/app/main.py:17,25-26,56,78,91-105,115-121 | baja | Aprobado |

## Candidatos de discovery — api

### H-API-08 — El límite del archivo se comprueba después del parseo multipart
- Estado: Aprobado; implementación local completada, pendiente de revisión humana.
- Evidencia: `api/app/main.py:182-190` limita la lectura a 200 bytes; la ruta declara `file: UploadFile = File(...)` en `api/app/main.py:248-250` y aplica el límite desde el handler. La prueba `test_upload_rejects_file_over_limit` verifica el rechazo de un archivo de 201 bytes, pero no el consumo de recursos al procesar un cuerpo mucho mayor.
- Impacto: el límite protege el archivo escrito, pero no demuestra que el tamaño total de la solicitud se limite antes de que el parser multipart la procese; una carga grande podría consumir memoria o almacenamiento temporal.
- Prioridad propuesta: Media.
- Contrato potencialmente afectado: `POST /api/requirements`; el tamaño máximo declarado es 200 bytes por archivo.
- Cómo validarlo: enviar cargas multipart progresivamente mayores que 200 bytes a través del servidor desplegado y medir recursos temporales; verificar que el límite de solicitud se aplique antes de aceptar el cuerpo completo.
- Evidencia de implementación: `api/app/main.py` limita el cuerpo completo a 512 bytes antes del parseo multipart; `api/tests/test_api.py` valida los cuerpos de 512 y 513 bytes, y cuerpos transmitidos en fragmentos sin `Content-Length`. `python tools/lab.py test api`: 51 pruebas pasaron; cobertura 96.22%.

### H-API-09 — Los campos de tarea aceptan valores y longitudes sin restricciones de dominio
- Estado: CANDIDATO
- Evidencia: `TaskCreateRequest` declara `title`, `description`, `status` y `priority` como `str` sin cotas ni enumeraciones en `api/app/main.py:65-70`; `task_summary` considera abiertos los estados distintos de `DONE` y `ARCHIVED` en `api/app/main.py:150-154`.
- Impacto: valores arbitrarios o cadenas extensas pueden persistirse; estados no previstos entran en `byStatus` y se contabilizan como abiertos, lo que puede distorsionar el resumen y degradar su utilidad.
- Prioridad propuesta: Media.
- Contrato potencialmente afectado: cuerpo de `POST /api/tasks` y agregados `byStatus` / `highPriorityOpen` de `GET /api/summary`.
- Cómo validarlo: caracterizar solicitudes con estado/prioridad desconocidos y cadenas largas; comprobar aceptación, persistencia y conteos del resumen frente a los valores de dominio que acuerde el contrato.

### H-API-10 — La conexión SQLite se comparte entre solicitudes sin aislamiento explícito
- Estado: CANDIDATO
- Evidencia: `create_app` crea una sola conexión con `check_same_thread=False` en `api/app/main.py:194-200` y la entrega al repositorio en `api/app/main.py:201`; los métodos del repositorio ejecutan y confirman operaciones sobre esa conexión compartida en `api/app/main.py:98-140`. No se observa un bloqueo o una conexión por solicitud.
- Impacto: operaciones concurrentes pueden intercalarse sobre el estado transaccional de la misma conexión, causando fallos intermitentes o acoplando commits de solicitudes distintas.
- Prioridad propuesta: Media.
- Contrato potencialmente afectado: disponibilidad y consistencia de `POST /api/tasks`, `GET /api/tasks` y `GET /api/summary` bajo concurrencia.
- Cómo validarlo: ejecutar solicitudes concurrentes de creación y lectura sobre la misma instancia; revisar errores 5xx, filas duplicadas/perdidas y consistencia de los resúmenes bajo carga repetida.
