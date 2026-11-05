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

## Contrato observado antes del cambio — auth

`docs/ENDPOINTS.md` no documenta `POST /auth/login`; lo siguiente se deriva de `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`, `LegacyDatabase.java` y `LegacyTokenUtil.java`, y no representa un contrato acordado:

- `POST /auth/login` acepta `username`, `password` y `client` opcional. Responde 200 con `token`, `expiresIn` (3600 fijo) y `user.username`, `user.displayName`, `user.role` cuando las credenciales coinciden contra `legacy_users`.
- `username`/`password` ausentes o vacíos responden 400 con `message`. Credenciales que no coinciden responden 401 con `message`, incluyendo el `username` recibido.
- Errores no controlados responden 500 con `message` y `exception` (nombre de la clase de la excepción).
- El token es Base64 de `username|role|expiración|secreto`; el secreto está fijo en código (`LegacyTokenUtil.SECRET`) y no hay verificación de firma.
- Las credenciales se almacenan y comparan en texto plano en SQLite (`legacy_users.password`).

## Seguimiento — auth

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-AUTH-01 | auth | SQL de login concatena username/password | Vulnerabilidad | `findByCredentials` arma el SQL con concatenación de strings; una entrada con sintaxis SQL (por ejemplo `' OR '1'='1`) puede alterar la condición de búsqueda y autenticar sin conocer la contraseña real. Resuelto: la consulta usa `PreparedStatement` con parámetros; `rejectsSqlInjectionInPassword` falló antes (200) y pasa después (401). Issue #21 (campo Hallazgo origen = H-AUTH-01). | auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32-46 | alta | Resuelto |
| H-AUTH-02 | auth | Contraseñas almacenadas en texto plano | Vulnerabilidad | La tabla `legacy_users` guarda `password` sin hash ni salt; una lectura de la base expone credenciales reutilizables directamente, sin necesidad de romper ninguna protección adicional. Issue #22. | auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:22-24 | alta | Confirmado |
| H-AUTH-03 | auth | Token sin firma criptográfica y secreto embebido | Vulnerabilidad | El token es Base64 de campos separados por `\|` más un secreto constante en código; Base64 no aporta integridad (no es firma) y el secreto no es verificable, rotable ni externalizable. Issue #24. | auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8,15 | alta | Confirmado |
| H-AUTH-04 | auth | Respuesta 500 expone mensaje y clase de excepción | Vulnerabilidad | El manejador de errores de `/auth/login` devuelve `error.getMessage()` y `error.getClass().getName()` al cliente, filtrando detalles internos de implementación ante fallos no controlados. Issue #23. | auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:54-57 | media | Confirmado |
