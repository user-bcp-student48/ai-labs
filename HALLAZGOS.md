# Hallazgos

## auth

`Pendiente` identifica un hallazgo que aún requiere corrección o verificación.
`Resuelto` indica que se aplicó el cambio y pasó la prueba local indicada en
PLAN.md; no equivale a revisión humana independiente ni a ejecución de CodeQL o
Dependency Review remotos.

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-AUTH-01 | auth | Las credenciales se concatenaban en la consulta SQL | Vulnerabilidad | **Resuelto:** la consulta busca por nombre con `PreparedStatement`; la contraseña se valida fuera del SQL y la prueba autentica un usuario ficticio cuyo nombre contiene una comilla como dato literal. | auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:57-65,70-82 | alta | Resuelto |
| H-AUTH-02 | auth | El token Base64 contenía un secreto embebido y no tenía firma | Vulnerabilidad | **Pendiente:** aunque el token actual usa HS256 y valida una longitud mínima, la configuración actual contiene un valor literal en `auth.token.secret`; no está externalizado como exige el plan. Debe externalizarse y rotarse antes del despliegue. | auth/src/main/java/com/bcp/workshop/auth/SignedTokenService.java:27-37,67-70; auth/src/main/resources/application.properties:4-5 | alta | Pendiente |
| H-AUTH-03 | auth | Las contraseñas se almacenaban y comparaban en texto plano | Vulnerabilidad | **Resuelto:** cuentas sembradas usan PBKDF2-HMAC-SHA256 con salt aleatorio; contraseñas heredadas se convierten tras autenticación correcta y el almacenamiento se actualiza con consulta parametrizada. | auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java:15-30,33-68; auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:68-82,90-100 | alta | Resuelto |
| H-AUTH-06 | auth | Dependencias directas y transitivas vulnerables en la versión señalada por el reto | Vulnerabilidad | **Resuelto para las coordenadas evaluadas:** el escaneo encontró CVE-2022-42889 en Commons Text `1.9`, CVE-2026-50559 en Quarkus HTTP `3.27.4` y hallazgos de Netty/Jackson en versiones transitivas resueltas. Se actualizaron Commons Text a `1.15.0` y Quarkus a `3.27.6.1`; el escaneo posterior de los componentes afectados no reportó CVE conocidos. CodeQL y Dependency Review remotos no se ejecutaron. | auth/pom.xml:13,49-50; resultados y alcance registrados en PLAN.md, sección H-AUTH-06 | alta | Resuelto |
| H-AUTH-08 | auth | Cuentas de demostración con credenciales conocidas se crean también en producción | Vulnerabilidad | **Resuelto tras revisión:** el sembrado queda activado solo en dev/test y desactivado por defecto/en prod. Si una base ya contiene los nombres demo, el repositorio también rechaza su autenticación cuando la opción está desactivada. | auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:30-31,37-46,52-54,103-110; auth/src/main/resources/application.properties:7-9; auth/src/test/java/com/bcp/workshop/auth/UserRepositoryTest.java:20-34 | alta | Resuelto |
| H-AUTH-04 | auth | El endpoint de error exponía detalles de excepciones | Vulnerabilidad | **Resuelto:** las respuestas inesperadas usan un mensaje genérico; el detalle se registra en el log del servidor sin agregarlo al cuerpo HTTP. | auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:40-43 | media | Resuelto |
| H-AUTH-05 | auth | CORS permitía cualquier origen | Vulnerabilidad | **Resuelto para el entorno aprobado:** desarrollo y pruebas permiten `http://localhost:4200`; producción requiere una lista externa `AUTH_CORS_ORIGINS`, sin wildcard. Una petición desde un origen no configurado recibe 403. | auth/src/main/resources/application.properties:11-13; auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:155-177 | media | Resuelto |
| H-AUTH-07 | auth | El recurso mezclaba validación, persistencia, lógica y construcción de respuesta | Refactor | **Resuelto:** `AuthResource` adapta HTTP, `AuthService` valida y orquesta, `UserRepository` persiste y los records de respuesta mapean la salida; se conservan endpoint, status y forma observada del JSON. | auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:16-49; auth/src/main/java/com/bcp/workshop/auth/AuthService.java:17-41; auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:1-3; auth/src/main/java/com/bcp/workshop/auth/AuthenticatedUser.java:1-3 | baja | Resuelto |
| H-AUTH-09 | auth | Contraseñas fijas de cuentas de demostración incluidas en el código | Configuración | **Resuelto localmente:** el sembrado requiere passwords configurados externamente; la configuración prod continúa sin sembrado. Fixtures de test quedan fuera del artefacto de producción. | auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:22-35,110-127; auth/src/test/java/com/bcp/workshop/auth/UserRepositoryTest.java:43-62; PLAN.md:H-AUTH-09 | media | Resuelto |

### Flujo y contrato observado antes de los cambios (historial Discovery)

Las rutas y líneas de esta subsección describen el código leído durante el
Discovery inicial; `LegacyDatabase.java` y `LegacyTokenUtil.java` se retiraron
al refactor. Para la evidencia vigente, consultar las filas Resuelto y el
recorrido posterior.

`docs/ENDPOINTS.md` no existe en el workspace, así que no hay contrato
acordado disponible para comparar. El contrato siguiente es solo lo observado
en el código antes de la implementación y queda pendiente de confirmación:

1. `POST /auth/login` recibe un `LoginRequest`; rechaza un cuerpo nulo, un
   usuario vacío o una contraseña vacía con HTTP 400
   (auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:20-33).
2. La ruta entrega usuario y contraseña a
   `LegacyDatabase.findByCredentials`
   (auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:35).
3. La base usa `AUTH_DB_URL` o, si no está definido,
   `jdbc:sqlite:./data/workshop.db`; inicializa `legacy_users` y agrega los
   usuarios de demostración `student` y `trainer`
   (auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:15,22-24).
   La búsqueda concatena ambas credenciales en SQL y ejecuta la cadena con
   `Statement` (auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:31-35).
4. Si no hay usuario, la ruta responde HTTP 401. Si lo encuentra, crea un
   token con nombre, rol y vencimiento, y responde HTTP 200 con `token`,
   `expiresIn`, `user` y `debug`
   (auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:36-50;
   auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:12-15).
5. Las excepciones que escapan de la ruta producen HTTP 500 con mensaje y clase
   de excepción (auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:53-58).
   Una excepción dentro de la búsqueda de base de datos se captura allí y se
   convierte en `null` (auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:37-43).

No se ejecutaron pruebas durante este Discovery. La prueba existente solo
comprueba el login satisfactorio del usuario ficticio
`student` (auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:12-22);
faltan pruebas de caracterización para credenciales inválidas, campos vacíos,
entrada con comilla y errores de persistencia.

### Flujo implementado y verificación local

`POST /auth/login` conserva el endpoint y los formatos JSON observados en el
Discovery. `AuthResource` trata el cuerpo nulo y traduce errores a HTTP;
`AuthService` valida credenciales, consulta el repositorio, genera el token y
construye los DTOs de salida
(auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:23-49;
auth/src/main/java/com/bcp/workshop/auth/AuthService.java:17-41).
`UserRepository` usa consultas parametrizadas y, tras autenticar una contraseña
heredada, guarda su hash PBKDF2 con salt
(auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:39-93;
auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java:24-68).
El token JWT firmado incluye `sub`, `role` y `exp`; `AUTH_TOKEN_SECRET` no
tiene valor predeterminado y el servicio falla al inicializar si falta o no
alcanza la longitud mínima. Solo el perfil test define una clave ficticia
(auth/src/main/java/com/bcp/workshop/auth/SignedTokenService.java:27-37,67-70;
auth/src/main/resources/application.properties:4-5).
Las cuentas de demostración se siembran solo cuando
`auth.demo-users.enabled` está activado; dev/test lo activan y prod lo desactiva.
Además, con la opción desactivada se rechaza la autenticación de los nombres
reservados incluso si ya existen en la base
(auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:30-31,37-54,103-110;
auth/src/main/resources/application.properties:7-9).

La verificación local posterior a estos cambios está registrada en PLAN.md:
20 pruebas pasaron y JaCoCo midió 150/158 líneas (94.9%) en el paquete auth.
CodeQL y Dependency Review remotos no se ejecutaron.
