# Design

## Context

`POST /auth/login` (Quarkus, `auth/`) delega la verificación de credenciales en `LegacyDatabase.findByCredentials(username, password)`. Ese método arma el SQL concatenando ambos valores en un `Statement` plano sobre SQLite (JDBC `org.xerial:sqlite-jdbc`), sin *placeholders*. Cualquier valor que incluya comillas simples o palabras clave SQL cambia la condición `WHERE` evaluada, lo que permite construir una condición siempre verdadera (`' OR '1'='1`) y autenticar sin conocer la contraseña real. Ver [proposal.md](./proposal.md) - Why para el hallazgo (`H-AUTH-01`) y el detalle de la vulnerabilidad.

Restricciones relevantes para el diseño:
- `auth/` es Quarkus + JDBC puro (sin JPA/Hibernate ni capa de repositorio abstracta); la única vía de acceso a `legacy_users` es `LegacyDatabase`.
- La conexión se abre por invocación (`DriverManager.getConnection(DB_URL)`), no hay pool ni conexión compartida.
- `AuthResource.login` ya valida presencia de `username`/`password` (400) antes de llamar a `findByCredentials`; ese flujo no cambia.
- El token (`LegacyTokenUtil.create`) y el formato de respuesta de `AuthResource` son independientes del método de acceso a datos; no dependen de cómo se construye el SQL.

## Goals / Non-Goals

**Goals:**
- Eliminar la concatenación de `username`/`password` en el SQL de `findByCredentials`, usando parámetros enlazados (`PreparedStatement`).
- Mantener exactamente el mismo comportamiento observable de `POST /auth/login` para credenciales válidas e inválidas (ver `specs/auth-login/spec.md`).
- Verificar con una prueba automatizada que una carga de inyección ya no altera la condición de búsqueda.

**Non-Goals:**
- No se introduce un ORM, pool de conexiones, ni capa de repositorio nueva — el cambio se limita a la construcción de la sentencia dentro de `LegacyDatabase`.
- No se cambia el esquema de `legacy_users`, el almacenamiento de contraseñas en texto plano, ni el formato/firma del token (`LegacyTokenUtil`). Esos son hallazgos distintos, fuera del alcance de `H-AUTH-01`.
- No se agregan límites de intentos, *rate limiting* ni logging de auditoría.
- No se modifican `api/` ni `web/`.

## Contratos protegidos

- **`POST /auth/login` (request/response):** mismos campos de entrada (`username`, `password`, `client` ignorado) y de salida (`token`, `expiresIn`, `user.username`, `user.displayName`, `user.role`, `debug`); mismos códigos 200/400/401/500 descritos en `AuthResource.login` y en `specs/auth-login/spec.md`.
- **Prueba existente `AuthResourceTest.logsInWithWorkshopUser`:** debe seguir pasando sin modificar sus aserciones.
- **Usuarios semilla:** `student/Workshop123!` y `trainer/Trainer123!` siguen autenticando igual; `findByCredentials` debe seguir devolviendo `UserRecord` con los mismos campos (`id`, `username`, `display_name`, `role`).

## Componentes afectados

| Componente | Archivo | Cambio |
|---|---|---|
| `auth/` | `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` | Reemplazar `Statement` + SQL concatenado por `PreparedStatement` con parámetros enlazados en `findByCredentials`. |
| `auth/` (tests) | `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` | Agregar caso(s) de inyección SQL; conservar el caso existente sin cambios. |

No se tocan `AuthResource.java`, `LoginRequest.java`, `UserRecord.java`, `LegacyTokenUtil.java`, `application.properties`, ni código de `api/` o `web/`.

## Decisiones técnicas y alternativas consideradas

### Decisión: usar `PreparedStatement` con parámetros posicionales
Reemplazar `Statement.executeQuery(sql concatenado)` por `connection.prepareStatement("SELECT ... WHERE username=? AND password=?")` y enlazar `username`/`password` con `setString`.

**Alternativas consideradas:**
- *Escapar manualmente comillas en `username`/`password` antes de concatenar*: descartado — es frágil (depende de cubrir todos los caracteres/variantes de inyección) y no es la práctica estándar; `PreparedStatement` resuelve la clase completa de problema sin lógica de escape propia.
- *Introducir un framework de acceso a datos (JPA/Hibernate, jOOQ)*: descartado — excede el alcance de un fix puntual de inyección, agrega dependencias y superficie de cambio no relacionada con `H-AUTH-01`, y contradice el Non-Goal de no tocar el esquema/capa de acceso.
- *Mover la verificación a un procedimiento almacenado*: descartado — SQLite no soporta procedimientos almacenados de forma nativa; añadiría complejidad sin beneficio sobre `PreparedStatement`.

**Por qué `PreparedStatement` con JDBC puro:** es el cambio mínimo y localizado al método afectado, usa la misma librería JDBC ya presente (`sqlite-jdbc`), y es la forma estándar reconocida de prevenir inyección SQL (CWE-89) sin alterar la forma de la consulta, los nombres de columnas devueltas ni el contrato de `UserRecord`.

### Decisión: mantener la comparación exacta de contraseña en texto plano
El fix no introduce hashing de contraseñas. Es una decisión de alcance: `H-AUTH-01` es específicamente sobre inyección SQL, no sobre almacenamiento de contraseñas (ese es un hallazgo distinto, no reportado aquí). Cambiarlo ahora alteraría el esquema de `legacy_users` y el flujo de verificación, fuera del alcance aprobado en `proposal.md`.

## Estrategia incremental

1. **Caracterizar el comportamiento actual** (ver sección siguiente) agregando pruebas antes de tocar `LegacyDatabase`, para tener una red de seguridad que detecte cualquier regresión de contrato.
2. **Confirmar en rojo** que la prueba de inyección falla contra el código actual (demuestra que el hallazgo es reproducible y que la prueba lo detecta).
3. **Aplicar el cambio mínimo** en `findByCredentials`: construir la sentencia con `?` y enlazar los dos parámetros con `PreparedStatement#setString`, sin tocar el resto del método (manejo de conexión, mapeo de `ResultSet` a `UserRecord`, manejo de excepciones).
4. **Confirmar en verde** que todas las pruebas de `AuthResourceTest` pasan (`mvn test` desde `auth/`), incluida la de inyección y la preexistente de login válido.
5. No se combinan en el mismo paso cambios de comportamiento con cambios de estructura; el único cambio de comportamiento es que las entradas con sintaxis SQL dejan de autenticar incorrectamente.

## Pruebas de caracterización necesarias antes de modificar comportamiento crítico

Antes de modificar `findByCredentials`, `AuthResourceTest` debe cubrir (algunas ya existen, otras se agregan en este cambio):

1. **Ya existe — login válido (`logsInWithWorkshopUser`):** `student`/`Workshop123!` → 200, `user.username=student`, `token` no nulo. Sirve de caracterización del camino feliz; debe seguir pasando igual después del fix.
2. **Nueva — credenciales inválidas simples:** usuario inexistente o password incorrecta → 401, sin `token`. Caracteriza el camino de rechazo antes de introducir el fix, para asegurar que `PreparedStatement` no cambie este comportamiento.
3. **Nueva — inyección en `username`:** `username=' OR '1'='1` con cualquier `password` → debe fallar (401) tanto antes (documentando el hallazgo, útil como prueba que reproduce `H-AUTH-01`) como, tras el fix, seguir devolviendo 401 de forma consistente.
4. **Nueva — inyección en `password`:** `username=student`, `password=' OR '1'='1` → 401 sin `token`.

Estas pruebas se escriben primero (o se confirma que ya existen) y se ejecutan contra el código actual para registrar el comportamiento real antes de tocar `LegacyDatabase`, conforme al contrato de agentes para `auth/` (punto 3 de `AGENTS.md`).

## Riesgos / Trade-offs

- **[Riesgo] Un escenario de prueba de inyección podría pasar "por accidente" si la query de prueba no reproduce fielmente el patrón vulnerable.** → Mitigación: ejecutar la prueba de inyección contra el código actual antes del fix y confirmar que falla (rojo), luego confirmar que pasa después del fix (verde).
- **[Riesgo] `PreparedStatement` reutiliza el mismo patrón de apertura de conexión por invocación; no se cambia el manejo de conexiones.** → Sin mitigación adicional necesaria: está fuera de alcance y no empeora con este cambio.
- **[Trade-off] No se corrige el almacenamiento de contraseñas en texto plano ni se agrega protección contra fuerza bruta.** → Aceptado conscientemente: está fuera del alcance de `H-AUTH-01`; documentado como fuera de alcance en `proposal.md`.
- **[Riesgo] Cambiar el tipo de `Statement` a `PreparedStatement` podría afectar el manejo de excepciones si cambia el tipo de error lanzado por el driver SQLite.** → Mitigación: conservar el mismo bloque `try/catch` genérico (`Exception`) que ya existe en `findByCredentials`, sin asumir tipos de excepción más específicos.

## Rollback

El cambio queda contenido en un único método de un único archivo (`LegacyDatabase.findByCredentials`) más la prueba agregada en `AuthResourceTest`. Si tras desplegar se detecta una regresión:
- Revertir el commit del cambio en `auth/` (`git revert`) restaura la versión anterior de `LegacyDatabase.java` y `AuthResourceTest.java` sin afectar `api/` ni `web/`, ya que no hay migraciones de esquema ni cambios de datos involucrados.
- No se requiere rollback de datos: `legacy_users` no cambia de forma ni de contenido con este fix.
