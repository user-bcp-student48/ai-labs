# Tasks

## 1. Pruebas de caracterización (antes del cambio funcional)

- [x] 1.1 En `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`, agregar un caso que envíe `username` inexistente con cualquier `password` y verificar 401 sin `token` en la respuesta; conservar sin cambios el caso existente `logsInWithWorkshopUser`.
- [x] 1.2 Agregar un caso con `username=student` y un `password` incorrecto, verificando 401 sin `token`.
- [x] 1.3 Agregar un caso con `username=' OR '1'='1` (y cualquier `password`), verificando 401 sin `token`.
- [x] 1.4 Agregar un caso con `username=student` y `password=' OR '1'='1`, verificando 401 sin `token`.
- [x] 1.5 Agregar un caso sin `username` y verificar 400 con `Username is required` (escenario «Username ausente» de la spec).
- [x] 1.6 Agregar un caso con `password` en blanco y verificar 400 con `Password is required` (escenario «Password vacío» de la spec). Las tareas 1.5 y 1.6 describen comportamiento ya existente y pasaron en verde desde su primera ejecución.

## 2. Confirmar el estado vulnerable (rojo)

- [x] 2.1 Ejecutar `mvn test` desde `auth/` contra el código actual (sin tocar `LegacyDatabase.java`) y confirmar que los casos de inyección (1.3 y 1.4) fallan, reproduciendo `H-AUTH-01`; registrar la salida de la ejecución como evidencia. Resultado real: falló solo 1.4 (`rejectsSqlInjectionInPassword`, esperaba 401 y obtuvo 200 con `token`); 1.3 devolvió 401 por la precedencia AND/OR del SQL concatenado, consistente con `design.md` y `specs/auth-login/spec.md`.

## 3. Implementación incremental

- [x] 3.1 En `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`, reemplazar el `Statement` con SQL concatenado de `findByCredentials` por un `PreparedStatement` parametrizado (`?` para `username` y `?` para `password`), sin modificar el resto del método (apertura de conexión, mapeo de `ResultSet` a `UserRecord`, manejo de excepciones).
- [x] 3.2 Verificar por lectura que no queda concatenación de `username`/`password` en el SQL de `findByCredentials` (revisión del diff del archivo).

## 4. Verificación final (verde) y validación de alcance

- [x] 4.1 Ejecutar `mvn test` desde `auth/` y confirmar que todos los casos de `AuthResourceTest` pasan, incluidos los casos de la sección 1 (1.1 a 1.6) y el caso preexistente `logsInWithWorkshopUser`. Resultado final: `mvn clean verify` con JDK 21 y `release=21` → 7 pruebas, 0 fallos; cobertura de `findByCredentials` según JaCoCo: líneas 12/13 (92,3 %), instrucciones 56/59 (94,9 %); la única línea sin cubrir es el `catch` con `printStackTrace`.
- [x] 4.2 Confirmar con `git status`/`git diff` (desde la raíz) que los cambios quedan limitados a `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` y `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`, sin modificaciones en `api/`, `web/` ni en los contratos de `docs/ENDPOINTS.md`. Alcance real del commit: esos dos archivos, `auth/src/test/resources/application.properties` (solo `quarkus.http.test-port`, infraestructura de test local), `HALLAZGOS.md` (seguimiento de `auth`) y `openspec/`. `auth/pom.xml` aparece modificado (`maven.compiler.release` 21→25) desde antes de este cambio y se excluye del commit.
