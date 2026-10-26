# Estrategia y evidencia de pruebas

## auth

Alcance de esta ejecución: endpoints documentados para auth en
`docs/ENDPOINTS.md`. Las pruebas HTTP usan Quarkus `@QuarkusTest` y RestAssured;
la persistencia del componente usa SQLite local con base temporal bajo
`auth/target/`. No se requieren servicios remotos ni se lee/escribe la base de
desarrollo habitual.

### Matriz de casos de endpoint

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /q/health` | Consulta sin autenticación | Camino feliz | HTTP 200; `status` es `UP` y `checks` es una lista vacía, según el estado observado. |
| `GET /q/health` | Lista de checks vacía | Borde/estado mínimo observado | La respuesta conserva `checks: []`; no se inventan checks ni un estado degradado. |
| `GET /q/health` | Error | No definido en el contrato | No hay error documentado por el componente; no existe prueba de `DOWN` ni de ruta no disponible. Ver pendientes y propuestas. |
| `POST /auth/login` | Credenciales ficticias válidas | Camino feliz | HTTP 200 y JSON con `token`, `expiresIn`, `user` y `debug`; identidad/rol de `student` correctos. |
| `POST /auth/login` | Nombre de usuario con apóstrofo y credenciales correctas | Borde | HTTP 200; el nombre con carácter especial se trata como valor literal. Relacionado con H-AUTH-01. |
| `POST /auth/login` | `username` vacío o solo espacios | Borde y error 400 | HTTP 400, `{"message":"Username is required"}`. |
| `POST /auth/login` | `password` vacío o solo espacios | Borde y error 400 | HTTP 400, `{"message":"Password is required"}`. |
| `POST /auth/login` | Request JSON `null` | Error 400 | HTTP 400, `{"message":"Request is null"}`. |
| `POST /auth/login` | Campo `username` ausente | Error 400 | HTTP 400, `{"message":"Username is required"}`. |
| `POST /auth/login` | Campo `password` ausente | Error 400 | HTTP 400, `{"message":"Password is required"}`. |
| `POST /auth/login` | Usuario inexistente o password incorrecto | Error 401 | HTTP 401, `{"message":"Invalid user unknown-user or password"}`; el mensaje observado incluye el username. |
| `POST /auth/login` | `Origin` no permitido | Error CORS 403 | HTTP 403 y sin header `Access-Control-Allow-Origin`; el cuerpo no está definido ni se afirma. Relacionado con H-AUTH-05. |
| `POST /auth/login` | Excepción inesperada en el servicio | Error 500 | HTTP 500, `{"message":"Internal server error"}`; el detalle sintético de prueba no aparece en el cuerpo. Relacionado con H-AUTH-04. |

El contrato no especifica errores para `GET /q/health`; esa ausencia no se
convierte en una expectativa inventada. Para `POST /auth/login`, los casos
anteriores cubren cada respuesta de error descrita en `docs/ENDPOINTS.md`.

### Código de los tests

Las pruebas ejecutables están en
`auth/src/test/java/com/bcp/workshop/auth/`:

- `AuthResourceTest.java`: `reportsHealthWithoutAuthentication`,
  `logsInWithWorkshopUser`, `authenticatesQuotedCredentialAsLiteralValues`,
  `rejectsBlankUsername`, `rejectsEmptyUsername`, `rejectsBlankPassword`,
  `rejectsEmptyPassword`, `rejectsMissingUsername`,
  `rejectsMissingPassword`, `returnsBadRequestForNullRequestOverHttp`,
  `rejectsInvalidCredentials`, `rejectsUnconfiguredCorsOrigin`,
  `allowsDevelopmentCorsOrigin`, `returnsGenericErrorWhenAuthenticationFailsUnexpectedly`,
  `createsSignedTokenWithExplicitClaims` y
  `migratesLegacyPasswordToSaltedHashOnSuccessfulLogin`.
- `UserRepositoryTest.java`: `doesNotSeedOrAuthenticateDemoUsersWhenDisabled`
  comprueba que las cuentas demo no se crean ni autentican con la opción
  desactivada (H-AUTH-08).
- `SignedTokenServiceTest.java`: valida secreto ausente y de longitud
  insuficiente; `AuthResourceTest.createsSignedTokenWithExplicitClaims`
  comprueba firma, claims y expiración (H-AUTH-02).
- `PasswordHasherTest.java`: casos unitarios del hash usados como regresión
  asociada a H-AUTH-03.

El test de 500 simula la excepción del servicio con `QuarkusMock`, pero envía
una petición HTTP real a `/auth/login`. El detalle aparece en el log de prueba
porque el recurso lo registra; la aserción confirma que no se filtra en el
cuerpo HTTP.

### Comandos y ejecuciones

Los comandos se ejecutaron desde `auth/`, con `AUTH_DB_URL` apuntando a archivos
SQLite aislados dentro de `target/`. El puerto aleatorio de test evita depender
del puerto 8081.

| etapa | comando ejecutado | código de salida | resultado |
|---|---|---:|---|
| Baseline antes de ampliar pruebas | PowerShell: `$env:AUTH_DB_URL='jdbc:sqlite:./target/auth-docs-test.db'; mvn test '-Dquarkus.http.test-port=0'` | 0 | PASS: 20 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| Suite ampliada inicial | PowerShell: `$env:AUTH_DB_URL='jdbc:sqlite:./target/auth-endpoint-tests.db'; mvn test '-Dquarkus.http.test-port=0'` | 0 | PASS: 23 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| Verificación final con JaCoCo | PowerShell: `$env:AUTH_DB_URL='jdbc:sqlite:./target/auth-endpoint-final.db'; mvn verify '-Dquarkus.http.test-port=0'` | 0 | PASS: 25 pruebas, 0 fallos, 0 errores, 0 omitidas; compilación, empaquetado y reporte JaCoCo correctos. |

La verificación final reportó estas suites:

| suite | pruebas | fallos | errores | omitidas |
|---|---:|---:|---:|---:|
| `AuthResourceTest` | 16 | 0 | 0 | 0 |
| `PasswordHasherTest` | 6 | 0 | 0 | 0 |
| `SignedTokenServiceTest` | 2 | 0 | 0 | 0 |
| `UserRepositoryTest` | 1 | 0 | 0 | 0 |
| **Total** | **25** | **0** | **0** | **0** |

### Cobertura

Medición: JaCoCo Maven Plugin 0.8.12 durante `mvn verify`; métrica de líneas
tomada de `auth/target/site/jacoco/jacoco.xml`. La tabla resume el paquete de
producción de auth (las modificaciones de esta tarea fueron en tests, no en
código productivo).

| alcance/clase | líneas cubiertas | total de líneas | cobertura |
|---|---:|---:|---:|
| auth, paquete agregado | 149 | 159 | 93.7% |
| `AuthResource` | 20 | 20 | 100% |
| `AuthService` | 19 | 19 | 100% |
| `UserRepository` | 51 | 55 | 92.7% |
| `PasswordHasher` | 31 | 33 | 93.9% |
| `SignedTokenService` | 20 | 24 | 83.3% |

El porcentaje agregado de auth supera el objetivo de 85%. `SignedTokenService`
individualmente queda por debajo de 85%; no se cambió ni redujo ningún quality
gate para alcanzar el resultado agregado.

### Fallos, pendientes y pruebas propuestas

**Fallos:** no hubo fallos, errores ni pruebas omitidas en la verificación
final. La prueba sintética de error 500 genera de forma intencional una entrada
`ERROR` en el log para validar que el detalle no llega al cliente.

**Pendientes:**

- `GET /q/health`: el contrato actual no define el comportamiento HTTP de un
  estado `DOWN` ni de un servicio no disponible; no se probó ni se asumió.
- `POST /auth/login`: no se comprobó el cuerpo que Quarkus/JAX-RS devuelve ante
  JSON mal formado, media type no soportado, método incorrecto o ruta
  inexistente, porque el contrato no los especifica.
- La respuesta CORS 403 no tiene cuerpo fijado en el contrato; el test valida
  solo status y ausencia de `Access-Control-Allow-Origin`.
- La suite usa `%test` y no certifica el arranque/configuración de producción.
  `docs/ENDPOINTS.md` registra por separado el riesgo pendiente H-AUTH-02
  sobre el valor actual de la propiedad de secreto.
- No se ejecutaron CodeQL ni Dependency Review remotos.

**Pruebas propuestas:** acordar primero el contrato de health degradado y los
errores de transporte/framework. Después añadir pruebas HTTP para cada status
y cuerpo acordado, sin inventar sus expectativas antes de esa decisión.
