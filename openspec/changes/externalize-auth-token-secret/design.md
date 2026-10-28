# Design

## Context

Ver [proposal.md](proposal.md) para motivación y [specs/auth/token-signing/spec.md](specs/auth/token-signing/spec.md) para los requisitos observables. En `auth/`, `SignedTokenService` consume `auth.token.secret`, valida en el arranque que haya al menos 32 bytes UTF-8 y genera JWT HS256 con `sub`, `role` y `exp`; la expiración y `expiresIn` son 3600 segundos. Actualmente `application.properties` contiene un valor literal base y un secreto fixture en el perfil `%test`.

La configuración existente ya utiliza entradas `AUTH_*` para database y CORS. `AuthResourceTest` fija una clave de prueba y verifica respuesta, claims y firma; también caracteriza errores de credenciales. El cambio no requiere dependencias nuevas ni modificar el contrato HTTP documentado en `docs/ENDPOINTS.md`.

## Goals / Non-Goals

**Goals:**
- Obtener el secreto de los perfiles de runtime desde configuración externa, sin fallback secreto versionado.
- Fallar al arrancar si el secreto falta o es menor a 32 bytes, sin divulgar su valor.
- Entregar el mismo token y contrato HTTP para las mismas credenciales válidas.
- Mantener aislada la clave fixture del perfil de pruebas.

**Non-Goals:**
- Cambiar endpoints, algoritmo, claims, duración del token o lógica de autenticación.
- Añadir rotación automática, soporte simultáneo de varias claves o integración con un proveedor específico de secretos.
- Modificar `api/`, `web/` o documentos generales; la evidencia operativa de rotación se registra en el proceso de despliegue existente.

## Contratos protegidos

- `POST /auth/login` conserva request, status, response y mensajes documentados: éxito HTTP 200 con `token`, `expiresIn`, `user` (`username`, `displayName`, `role`) y `debug`; credenciales faltantes o vacías HTTP 400; credenciales inválidas HTTP 401; error inesperado HTTP 500.
- El token conserva `alg=HS256`, `typ=JWT`, claims `sub`, `role` y `exp`, y duración de 3600 segundos. La firma de tokens nuevos verifica con el secreto externo configurado.
- Ninguna respuesta o diagnóstico incluye el valor del secreto. La ruta de health y el comportamiento CORS no cambian.

## Componentes afectados

- `auth/src/main/resources/application.properties`: eliminar el valor base de runtime y referenciar `AUTH_TOKEN_SECRET` sin un valor secreto por defecto. El valor fixture de `%test` puede permanecer exclusivamente para pruebas.
- `auth/src/main/java/com/bcp/workshop/auth/SignedTokenService.java`: mantener la validación existente como condición de arranque y asegurar que la configuración que consume corresponda al secreto externo.
- `auth/src/test/java/com/bcp/workshop/auth/SignedTokenServiceTest.java` y `AuthResourceTest.java`: conservar y ampliar pruebas de validación, firma y contrato.
- No se modifican archivos de `api/`, `web/` ni documentación general.

## Decisions

1. **Usar `AUTH_TOKEN_SECRET` como entrada externa para runtime.** Quarkus/MicroProfile Config puede resolverla para `auth.token.secret`, en línea con las convenciones `AUTH_DB_URL` y `AUTH_CORS_ORIGINS` ya presentes. No se asignará un fallback para dev o prod: cada ejecución runtime debe proporcionar el valor. Alternativas consideradas: conservar un secreto literal, rechazado porque mantiene la exposición; añadir lectura de archivo o integración directa con un gestor de secretos, pospuesta porque exigiría infraestructura o dependencias que el Issue no requiere. La plataforma de cada entorno podrá inyectar la variable desde su mecanismo seguro.

2. **Validar al iniciar, no esperar al primer login.** `SignedTokenService` ya valida en su ciclo de inicio y al crear un token; se conserva el rechazo temprano para que una instancia mal configurada nunca parezca lista. La longitud se mide en bytes UTF-8 como hoy. Alternativa considerada: validar solo al firmar; se rechaza porque permitiría iniciar el servicio en un estado incapaz de autenticar.

3. **Mantener una clave exclusiva de prueba en `%test`.** `AuthResourceTest` necesita una clave conocida para verificar criptográficamente el JWT; conservarla como fixture no la convierte en configuración de producción y permite pruebas deterministas. No se reutiliza fuera del perfil de pruebas. Alternativa considerada: requerir una variable externa también para ejecutar tests; se descarta para mantener el conjunto de pruebas autocontenido, sin reducir los requisitos de runtime.

4. **Rotación operativa antes del despliegue; sin doble clave.** El responsable del entorno genera e inyecta el nuevo valor por el mecanismo seguro disponible y registra la rotación sin guardar el valor en el repositorio ni en la evidencia. La rotación no será automática ni se añadirá un período de aceptación de claves anteriores, que ampliaría el alcance y el contrato de verificación.

## Estrategia incremental

1. Congelar la caracterización actual de login y JWT descrita abajo, usando solo la fixture `%test`.
2. Externalizar la configuración sin cambiar la validación de 32 bytes ni la serialización/firma del token; mantener el fixture test aislado.
3. Añadir comprobaciones de arranque para valor válido, ausente y menor al mínimo, y comprobar que mensajes y logs no contienen el valor.
4. Antes de desplegar, proveer y rotar el secreto en el entorno destino, revisar la evidencia sin el valor y comprobar arranque y login con un JWT verificable.

## Pruebas de caracterización previas

Antes de cambiar la configuración de runtime, ejecutar y mantener en `auth/` estas caracterizaciones:

- Login correcto: HTTP 200, campos de respuesta documentados, `expiresIn=3600`, claims `sub`/`role`/`exp`, header HS256 y firma verificable con la fixture de prueba.
- Login incorrecto: HTTP 401 y mensaje documentado; username/password ausentes o en blanco: HTTP 400 con sus mensajes actuales.
- Configuración del token: ausencia del secreto y secreto menor de 32 bytes son rechazados; añadir caso de exactamente 32 bytes para fijar el límite inferior.
- Una comprobación aislada de arranque demuestra que configuración runtime válida inicia y la ausente/corta falla antes de atender requests. La prueba debe inspeccionar salida de error para asegurar que nunca revela el valor.

Las pruebas existentes `AuthResourceTest` y `SignedTokenServiceTest` ya cubren parte de esta línea base; se deben ejecutar en el directorio `auth/` y completar los huecos antes de alterar el comportamiento de configuración.

## Risks / Trade-offs

- **Falta `AUTH_TOKEN_SECRET` en un entorno →** el arranque falla intencionalmente; configurar y verificar la variable antes de iniciar la nueva versión.
- **La rotación de una clave HS256 invalida la verificación de tokens firmados con la anterior →** coordinarla con el despliegue y prever reautenticación; no habilitar doble clave en este cambio.
- **Una clave fixture se copia accidentalmente a runtime →** limitarla explícitamente al perfil `%test` y revisar el perfil activo durante la comprobación previa al despliegue.
- **El entorno registra variables o diagnósticos sensibles →** inyectar el valor mediante el mecanismo de secretos de la plataforma, evitar imprimir configuración y comprobar que errores de arranque no incluyen el valor.

## Rollback

Si la versión nueva no inicia o falla la verificación de login, detener el despliegue y volver a la versión anterior junto con su secreto correspondiente, recuperado únicamente del almacenamiento seguro del entorno. No restaurar el secreto en Git. Los tokens emitidos con la clave nueva pueden dejar de verificarse tras volver a la anterior; aceptar reautenticación de esas sesiones o mantener el despliegue detenido hasta resolver la transición. El rollback no debe restablecer el secreto literal en configuración versionada.

## Open Questions

- Cada entorno debe decidir qué mecanismo seguro inyecta `AUTH_TOKEN_SECRET`; la interfaz de configuración queda fijada, pero no se prescribe un proveedor de secretos.