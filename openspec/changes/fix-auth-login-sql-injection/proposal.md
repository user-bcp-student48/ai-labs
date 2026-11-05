# Proposal

## Why

`LegacyDatabase.findByCredentials` arma el SQL de verificación de credenciales concatenando `username` y `password` directamente en la consulta. Un valor como `' OR '1'='1` altera la consulta y permite iniciar sesión sin conocer la contraseña real (Issue [#21](https://github.com/user-bcp-student48/ai-labs/issues/21), hallazgo `H-AUTH-01`, severidad alta). El cambio es urgente porque `POST /auth/login` es el punto de entrada de autenticación de todo el sistema (API y web dependen del token emitido aquí).

## What Changes

- `LegacyDatabase.findByCredentials` deja de concatenar `username` y `password` en el SQL; usa `PreparedStatement` con parámetros enlazados.
- Un intento de login con una carga de inyección SQL (por ejemplo `' OR '1'='1` en usuario o contraseña) debe resultar en credenciales no encontradas, no en una coincidencia accidental.
- No se agregan, quitan ni renombran campos de request/response de `POST /auth/login`; el contrato HTTP existente se conserva.

## Capabilities

### New Capabilities
- `auth-login`: comportamiento observable de `POST /auth/login` — verificación de credenciales, emisión de token y manejo de intentos inválidos (incluyendo entradas con sintaxis SQL), para el servicio `auth/`.

### Modified Capabilities
_(ninguna — no existen specs previas en `openspec/specs/` para este comportamiento; se crea como capability nueva)._

## Impact

- **Código afectado:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` (consulta SQL de `findByCredentials`). No se tocan `AuthResource.java`, `LoginRequest.java`, `UserRecord.java` ni `LegacyTokenUtil.java` salvo que una prueba de caracterización lo requiera.
- **Pruebas:** se añade un caso de inyección SQL en `AuthResourceTest` (componente `auth/`, ejecutado con `mvn test` desde `auth/`).
- **Contrato preservado:** `docs/ENDPOINTS.md` no documenta `POST /auth/login` (solo cubre la API FastAPI); el contrato de referencia para este endpoint es el código actual de `AuthResource.login` y la prueba existente `logsInWithWorkshopUser`, que deben seguir pasando sin cambios de forma.
- **Fuera de alcance:** cambios en `api/` o `web/`, cambios en el formato del token (`LegacyTokenUtil`), autorización por rol, y cualquier endpoint distinto de `POST /auth/login`.
