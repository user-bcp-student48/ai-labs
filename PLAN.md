# Plan

## auth

### Alcance y reglas

Se planificaron todos los hallazgos `Pendiente` de auth que existían en
`HALLAZGOS.md`: H-AUTH-01 a H-AUTH-07; tras la implementación y las pruebas,
los siete figuran `Resuelto` en la matriz. La revisión de cierre detectó y
agregó H-AUTH-08; su plan correctivo está documentado abajo. No existe `docs/ENDPOINTS.md`; el
contrato de referencia fue el comportamiento observado que quedó documentado
en `HALLAZGOS.md`, sin aprobación formal independiente del contrato.

**Aprobación humana:** el usuario aprobó explícitamente implementar H-AUTH-01,
H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06 y H-AUTH-07 en la
conversación del 2026-10-26. Esta aprobación autoriza trabajar esos puntos,
pero no resuelve las decisiones técnicas pendientes de cada uno ni certifica
pruebas, compatibilidad o revisión humana posterior.

**Decisiones confirmadas por el usuario para esta ejecución:** CORS se limita
al entorno de desarrollo; se usa `http://localhost:4200`, origen de la UI
documentado en README.md. Las contraseñas en texto claro se migran al hash al
iniciar sesión correctamente. Los tokens heredados se invalidan al cambiar al
token firmado. No se deben presentar estas decisiones como revisión humana del
diff.

Antes de modificar persistencia, tokens o estructura, agregar pruebas de
caracterización en `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.
Mantener `POST /auth/login`, los estados y el formato HTTP observado
(HTTP 400 para cuerpo/campos requeridos, HTTP 401 para credenciales no válidas,
HTTP 200 con `token`, `expiresIn` y `user`). Los cambios de seguridad pueden
requerir cambiar detalles internos —formato del token y almacenamiento de
contraseña—, pero no deben cambiar la forma de la respuesta sin decisión
explícita. La aprobación de alcance fue registrada por el usuario en la
conversación; no equivale a revisión humana independiente del diff.

La implementación debe mantener métodos con una sola responsabilidad,
separación Resource → Service → Repository, nombres descriptivos, guard clauses,
sin duplicación ni valores mágicos. No introducir secretos reales. No agregar
dependencias sin evidencia y justificación.

### H-AUTH-01 — Parametrizar las credenciales SQL

- **Hallazgo:** HALLAZGOS.md registra concatenación de `username` y `password`
  en la consulta de `LegacyDatabase`.
- **Problema:** entradas que contienen comillas u otros caracteres SQL se
  incorporan a la sentencia en lugar de ser valores; también puede fallar el
  login legítimo con caracteres especiales.
- **Cambio propuesto:** reemplazar `Statement` y la concatenación por una
  consulta con `PreparedStatement` que enlace explícitamente el username. Leer
  el hash de contraseña y verificarlo fuera de SQL. Mantener la selección de
  columnas, la semántica de no encontrado y el mapeo del usuario autenticado.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`;
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.
- **Caracterización antes del cambio:** agregar pruebas locales con credenciales
  ficticias válidas, inválidas y una cuenta de fixture cuyo nombre o contraseña
  contenga una comilla. Registrar el resultado actual; el login válido debe dar
  200, el inválido 401, y la entrada con comilla permite observar la diferencia
  entre concatenación y un valor enlazado. Después del cambio, el fixture con
  comilla debe autenticarse como dato literal cuando las credenciales coincidan.
- **Aislamiento de prueba:** se usa `AUTH_DB_URL=jdbc:sqlite:./target/auth-characterization.db`
  por ejecución, para no modificar la base de datos local del usuario.
- **Ejecución posterior (2026-10-26, H-AUTH-01):** con la caracterización de
  usuario ficticio con apóstrofo, `mvn "-Dquarkus.http.test-port=0" test`
  ejecutado desde `auth/` con `AUTH_DB_URL=jdbc:sqlite:./target/auth-characterization.db`
  — **PASS**, 9 pruebas, 0 fallos y 0 errores; el fixture se autentica como
  valor literal.

### H-AUTH-02 — Firmar el token y retirar el secreto embebido

- **Hallazgo:** HALLAZGOS.md registra contenido Base64 con un secreto literal,
  generado por `LegacyTokenUtil` y devuelto por el login.
- **Problema:** Base64 no firma ni cifra el contenido; las claims no tienen una
  verificación criptográfica observable en este componente.
- **Cambio propuesto:** crear un token firmado con expiración y claims explícitos
  (identidad y rol), y cargar el secreto mediante configuración externa
  (`application.properties` con variable de entorno, nunca un valor real
  embebido). Preferir primitivas Java estándar si satisfacen el formato
  acordado; justificar cualquier dependencia adicional antes de agregarla.
  Mantener `token` como cadena opaca y conservar `expiresIn` en la respuesta.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`
  o su reemplazo con nombre de propósito; `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`;
  `auth/src/main/resources/application.properties`;
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.
- **Caracterización antes del cambio:** probar login válido e inválido y
  registrar la forma de respuesta, la presencia de token y `expiresIn`. No fijar
  los bytes del token como contrato HTTP; verificar el formato actual solo en
  pruebas internas de caracterización y luego reemplazar esa expectativa por
  verificación de firma, claims y expiración.
- **Decisión y ejecución (2026-10-26, H-AUTH-02):** el usuario aprobó invalidar
  tokens Base64 ya emitidos. `SignedTokenService` exige `AUTH_TOKEN_SECRET` con
  al menos 32 bytes; la configuración de test usa únicamente un valor ficticio.
  La prueba verifica algoritmo, tipo, claims, expiración y firma.
- **Revisión local:** se encontró que `application.properties` contiene un
  secreto predeterminado, por lo que sí existe fallback productivo si falta
  `AUTH_TOKEN_SECRET`. H-AUTH-02 vuelve a `Pendiente` en HALLAZGOS.md hasta
  retirar el valor por defecto y exigir configuración en producción. No se
  inspeccionaron consumidores fuera de auth; coordinar antes de desplegar,
  porque el cambio invalida tokens previos.
- **Corrección posterior a la revisión (2026-10-26):** se eliminó el valor
  predeterminado y la propiedad ahora requiere `AUTH_TOKEN_SECRET`; solo `%test`
  define una clave ficticia. `SignedTokenService` valida secreto y longitud
  durante inicialización CDI para que la aplicación falle al arrancar si la
  configuración es ausente o inválida. La prueba unitaria verifica que un
  secreto nulo es rechazado. H-AUTH-02 vuelve a `Resuelto` tras la suite de 20
  pruebas. La compatibilidad de consumidores de tokens anteriores sigue
  pendiente fuera del alcance.

### H-AUTH-03 — Hashear contraseñas con salt

- **Hallazgo:** HALLAZGOS.md registra contraseñas en claro en el esquema,
  registros iniciales y comparación de credenciales.
- **Problema:** una copia de la base revela las contraseñas y el esquema actual
  compara directamente el valor recibido con el almacenado.
- **Cambio propuesto:** almacenar hashes lentos con salt individual y comparar
  mediante una verificación constante del algoritmo seleccionado. El ejemplo
  BCrypt de `docs/GOOD_PRACTICES.md` es una opción; evaluar primero si se puede
  usar una opción ya disponible o una primitiva estándar adecuada. Justificar
  cualquier dependencia nueva. Definir una migración segura para filas
  existentes y para las cuentas de demostración; no reinterpretar hashes como
  contraseñas ni dejar la migración en un estado parcialmente aplicado.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`
  o repositorio extraído; nuevo servicio/ayudante de credenciales solo si es
  necesario; `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java` solo
  si cambia el límite de responsabilidades; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`;
  `auth/pom.xml` únicamente si la evaluación justifica una dependencia.
- **Caracterización antes del cambio:** probar que la cuenta ficticia existente
  inicia sesión con su contraseña correcta y que una incorrecta da 401. Registrar
  el comportamiento actual de inicialización/reutilización de la base local.
  Añadir pruebas para que, tras el cambio, un hash almacenado verifique la
  contraseña correcta y rechace la incorrecta sin exponerla en respuesta o logs.
- **Riesgo pendiente:** la migración perezosa solo convierte una contraseña
  heredada cuando su titular inicia sesión correctamente; cuentas que no
  vuelvan a iniciar sesión permanecen en claro hasta entonces.
- **Decisión y ejecución (2026-10-26, H-AUTH-03):** el usuario aprobó migrar al
  hash tras autenticación correcta. El test de login seguido de consulta local
  a SQLite verifica que la contraseña heredada queda en formato
  `pbkdf2-sha256$`; en el mismo comando de caracterización posterior, **PASS**
  (9 pruebas, 0 fallos y 0 errores).

### H-AUTH-04 — Respuestas de error genéricas y logging controlado

- **Hallazgo:** HALLAZGOS.md registra que el manejador HTTP devuelve mensaje y
  clase de excepción.
- **Problema:** el detalle de la excepción puede divulgar internals al cliente.
- **Cambio propuesto:** devolver un mensaje genérico y un código estable, y
  enviar el detalle al logger del servidor sin incluir credenciales ni secretos.
  Mantener la respuesta de credenciales inválidas según el contrato observado;
  distinguir errores internos de autenticación fallida.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`;
  `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` solo para
  reemplazar el manejo de error si corresponde; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.
- **Caracterización antes del cambio:** añadir una prueba con dependencia
  sustituida/inyección de prueba que provoque una excepción no capturada por el
  repositorio. Registrar el status y cuerpo HTTP actual. Después, comprobar que
  el cliente recibe solo mensaje genérico y que el detalle queda en logging
  controlado, sin verificar secretos en logs.
- **Ejecución y riesgo residual:** en el código inicial, `LegacyDatabase`
  capturaba sus excepciones y devolvía `null`, por lo que el test de caracterización
  reprodujo los detalles expuestos inyectando una excepción que alcanzaba al
  manejador HTTP. `UserRepository` ahora propaga errores SQL como excepción
  interna y `AuthResource` registra el detalle y devuelve un mensaje genérico.

### H-AUTH-05 — Restringir CORS mediante configuración explícita

- **Hallazgo:** HALLAZGOS.md registra origen CORS permisivo en
  `application.properties`.
- **Problema:** cualquier origen de navegador queda permitido por la expresión
  actual; la lista de orígenes autorizados para el entorno no está acordada.
- **Cambio propuesto:** reemplazar la expresión global por orígenes permitidos
  explícitos y externalizables por entorno, manteniendo los métodos y headers
  que el servicio necesite. No inventar ni codificar un dominio de producción.
- **Archivos a tocar:** `auth/src/main/resources/application.properties`;
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` o prueba
  dedicada de configuración CORS dentro de `auth/src/test`.
- **Caracterización antes del cambio:** hacer una prueba local con un `Origin`
  ficticio y registrar el header CORS actual para preflight y/o respuesta
  observada. La prueba posterior debe verificar origen permitido y rechazado.
- **Decisión y ejecución (2026-10-26, H-AUTH-05):** el usuario confirmó que
  CORS debe limitarse a desarrollo. Se allowlisteó `http://localhost:4200`,
  origen documentado para la UI, en dev/test; producción requiere
  `AUTH_CORS_ORIGINS`. Las pruebas verifican origen permitido y denegado (403).

### H-AUTH-06 — Evaluación SCA de dependencias señaladas

- **Hallazgo inicial:** HALLAZGOS.md registró que las versiones en el reto
  necesitaban verificación; al comenzar el plan aún no se había confirmado un
  CVE ni que fueran vulnerables en el uso efectivo. La evaluación SCA
  subsecuente se registra abajo.
- **Problema:** actualizar a ciegas puede introducir incompatibilidades, pero
  ignorar advisories conocidos deja riesgo sin evaluar.
- **Cambio propuesto:** consultar advisories vigentes y el árbol efectivo de
  Maven para Quarkus y Commons Text; documentar coordenadas, versiones afectadas
  y alcance. Actualizar a una versión corregida compatible o retirar Commons
  Text si el uso se puede reemplazar de forma segura. No silenciar CodeQL,
  Dependency Review ni JaCoCo. Modificar dependencias únicamente con evidencia.
- **Archivos a tocar:** `auth/pom.xml` solo si la evaluación justifica cambio;
  si se retira Commons Text, `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`
  y su prueba para cubrir el escape HTML observado.
- **Caracterización antes del cambio:** ejecutar y registrar el test existente
  de login válido, incluyendo `user.displayName` y su representación, para
  establecer el comportamiento previo antes de tocar versiones o remover una
  biblioteca. Esto no sustituye el análisis de advisories ni el árbol Maven.
- **Ejecución SCA (2026-10-26, H-AUTH-06):** `mvn dependency:list` en `auth/`
  resolvió coordenadas. `appmod-cve-assessment` reportó CVE-2022-42889
  (CRITICAL) en `commons-text:1.9`; el escaneo de Quarkus/Netty/Jackson
  seleccionados reportó 36 hallazgos en 5 coordenadas afectadas, entre ellos
  CVE-2026-50559 (HIGH). Se actualizaron Commons Text a `1.15.0` y el BOM/
  plugin Quarkus a `3.27.6.1`; el árbol nuevo resolvió Netty `4.1.139.Final` y
  Jackson `2.21.7`. El escaneo posterior de esas coordenadas no reportó CVE.
  Informes generados durante la evaluación en `auth/target/`; su disponibilidad
  actual no se confirmó después de la limpieza de salida.
- **Límite:** el escaneo cubrió las dependencias directas señaladas por RETO.md
  y los componentes transitivos afectados identificados en el primer reporte,
  no todo el grafo de dependencias. No se ejecutaron CodeQL ni Dependency Review
  remotos.

### H-AUTH-07 — Separar responsabilidades del flujo de login

- **Hallazgo:** HALLAZGOS.md registra validación, búsqueda, token y respuesta
  concentrados en `AuthResource.login`.
- **Problema:** el método mezcla transporte HTTP y responsabilidades de reglas,
  persistencia y presentación; los condicionales anidados hacen más difícil
  probar y revisar cambios de seguridad.
- **Cambio propuesto:** mantener el Resource como adaptador HTTP; extraer
  validación/reglas y orquestación a un Service, y acceso a datos a un
  Repository. Mapear respuesta mediante DTO de salida propio. Cada método debe
  hacer una sola cosa, usar guard clauses y evitar duplicar validaciones,
  mensajes o construcción de respuestas. Mantener endpoint, status y formato
  observados.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`;
  nuevos `AuthService`, `UserRepository`/`LegacyDatabase` ajustado y DTO de
  respuesta bajo `auth/src/main/java/com/bcp/workshop/auth/`;
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y pruebas
  unitarias de los nuevos límites en `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización antes del cambio:** ampliar primero las pruebas HTTP para
  login válido, credenciales inválidas, cuerpo/campos vacíos y forma de
  respuesta; probar por separado los casos de base y token pertinentes a
  H-AUTH-01–04. Usar esas pruebas como regresión mientras se extraen capas.
- **Ejecución posterior (2026-10-26, H-AUTH-07):** `AuthResource` quedó como
  adaptador HTTP; `AuthService`, `UserRepository` y DTOs separan reglas,
  persistencia y salida. Las pruebas de caracterización verifican endpoint,
  status y JSON observado.

### H-AUTH-08 — Restringir las cuentas de demostración

- **Hallazgo:** la revisión de cierre observó que `UserRepository` sembraba
  `student` y `trainer` sin restricción de perfil; sus contraseñas conocidas
  podían emitir tokens en producción.
- **Cambio aplicado (2026-10-26):** `auth.demo-users.enabled` está desactivado
  por defecto y explícitamente en prod; dev/test lo activan para mantener el
  flujo documentado del laboratorio. Con el flag desactivado, el repositorio
  no siembra cuentas y rechaza esos nombres reservados incluso si quedaron en
  una base de datos previa.
- **Prueba:** `UserRepositoryTest` inicializa una base SQLite temporal con la
  opción desactivada, verifica que no crea usuarios y comprueba que una fila
  `trainer` preexistente con la contraseña conocida no autentica. `AuthResourceTest`
  conserva la comprobación del login ficticio en el perfil test.
- **Riesgo residual:** producción debe apuntar a su base de datos y establecer
  `AUTH_TOKEN_SECRET`; las cuentas de demo preexistentes no se borran para no
  destruir datos, pero quedan bloqueadas para login con esta configuración.

### Validación y decisiones pendientes

- **Revisión local de cierre (2026-10-26):** basada en `git diff main --
  auth/`, el estado de Git y lectura de archivos nuevos. H-AUTH-02 vuelve a
  `Pendiente` por la clave de firma predeterminada. Se registró H-AUTH-08 como
  `Pendiente`: las cuentas `student`/`trainer` se inicializan sin restricción
  de perfil. No se modificó código ni se volvieron a ejecutar pruebas durante
  esta revisión. El seguimiento y plan son archivos sin seguimiento, por lo
  que no tienen diff contra `main`.
- **Verificación documental posterior (2026-10-26, H-AUTH-02):** al preparar
  `docs/ENDPOINTS.md`, la lectura actual de `application.properties:4` mostró
  que `auth.token.secret` vuelve a estar configurado con un literal, aunque
  una lectura anterior durante la misma tarea lo mostraba referenciado a
  `AUTH_TOKEN_SECRET`. El archivo no figuraba modificado en `git status`; se
  documentó la configuración efectivamente leída, sin copiar su valor, y
  H-AUTH-02 se dejó `Pendiente`. No se editó código en este paso.
- **Corrección de los bloqueantes (2026-10-26, H-AUTH-02 y H-AUTH-08):** se
  retiró el fallback de secreto, se añadió validación de inicio y se restringió
  la creación/autenticación de cuentas demo por entorno. El primer intento de
  `UserRepositoryTest` falló porque `DriverManager` no había cargado el driver
  SQLite en una prueba JUnit independiente; se corrigió cargándolo explícitamente
  en la prueba. No se ocultó ese fallo: la repetición y la verificación final
  pasaron.
- **Verificación posterior (2026-10-26, H-AUTH-02 y H-AUTH-08):** desde `auth/`,
  con `AUTH_DB_URL=jdbc:sqlite:./target/auth-blockers-verify.db`,
  `mvn verify "-Dproject.build.directory=target/blockers-verify" "-Dquarkus.http.test-port=0"`
  — **PASS**, 20 pruebas, 0 fallos, 0 errores, 0 omitidas; build y verify
  correctos. Suite: `AuthResourceTest` 11/11, `PasswordHasherTest` 6/6,
  `SignedTokenServiceTest` 2/2 y `UserRepositoryTest` 1/1. JaCoCo reportó
  150/158 líneas (94.9%) en auth, superior al umbral de 85%; `SignedTokenService`
  individualmente quedó en 20/24 (83.3%), mientras el conjunto de código auth
  cumple la meta global.
- **Artefacto de cobertura:** `auth/target/site/jacoco/index.html` y
  `auth/target/site/jacoco/jacoco.xml`.
- **H-AUTH-01–H-AUTH-07, caracterización previa (2026-10-26):** desde `auth/`,
  `mvn "-Dquarkus.http.test-port=0" test` con
  `AUTH_DB_URL=jdbc:sqlite:./target/auth-characterization.db` — **PASS**, 9
  pruebas, 0 fallos y 0 errores. La ejecución inicial sin puerto efímero falló
  porque el puerto de test 8081 estaba ocupado; la repetición con puerto 0 pasó.
  El caso local de usuario ficticio con comilla confirmó el comportamiento
  previo: HTTP 401 y error SQL registrado por el repositorio.
- **H-AUTH-01 y H-AUTH-03, validación posterior parcial (2026-10-26):** se
  repitió el mismo comando tras parametrizar la consulta y añadir el hash
  perezoso; **PASS**, 9 pruebas, 0 fallos y 0 errores. H-AUTH-02 y H-AUTH-04–07
  aún no se habían implementado al momento de esta ejecución.
- **Verificación completa posterior (2026-10-26, H-AUTH-01–H-AUTH-07):**
  `mvn clean verify "-Dquarkus.http.test-port=0"` — **PASS**, 18 pruebas,
  0 fallos, 0 errores, 0 omitidas. Como el `target/` de `auth/` estaba bloqueado
  por un proceso `quarkus:dev` preexistente (PID 16196), la verificación limpia
  se ejecutó en una copia temporal aislada de `auth/` bajo `auth/`; la copia se
  eliminó al terminar. No se detuvo el proceso externo. Intentar `mvn clean
  verify` directamente en `auth/` falló en el goal `clean` al no poder borrar
  `target/workshop-auth-backend-dev.jar`; esto no es un fallo de compilación ni
  de pruebas.
  Resultados por suite: `AuthResourceTest` 11/11,
  `PasswordHasherTest` 6/6 y `SignedTokenServiceTest` 1/1.
- **Verificación previa de la salida original (2026-10-26):** antes de la
  prueba limpia, `mvn "-Dquarkus.http.test-port=0" verify` en `auth/` también
  pasó con 18 pruebas, 0 fallos y 0 errores.
- **Cobertura (JaCoCo, código de producción modificado):** 133/143 líneas
  cubiertas (93.0% en conjunto), por encima del umbral de 85%. Coberturas por
  clase: `AuthResource` 100%, `AuthService` 100%, `UserRepository` 91.8%,
  `PasswordHasher` 93.9%, `SignedTokenService` 81.8%. El umbral se cumple en
  el conjunto modificado; la clase de token queda individualmente debajo de 85%.
- `python tools/lab.py test auth` no se ejecutó. CodeQL y Dependency Review
  remotos tampoco se ejecutaron, conforme a la restricción local.
- La meta indicada por AGENTS.md es 85% o superior para el código modificado;
  conservar los gates existentes y reportar cobertura con numerador,
  denominador y método, sin reducir umbrales.
- **Aprobaciones y decisiones registradas:** aprobación del usuario para
  H-AUTH-01–H-AUTH-07, CORS de desarrollo limitado a `http://localhost:4200`,
  migración perezosa de contraseñas heredadas tras autenticación exitosa e
  invalidación de tokens anteriores. Esto no equivale a revisión
  independiente. La compatibilidad de consumidores externos de tokens sigue
  pendiente por estar fuera del alcance.
