# Contratos HTTP

## auth

### Contrato observado en el código actual

Base local documentada: `http://localhost:8080` (README.md). La configuración
escucha en `127.0.0.1` por defecto y en `0.0.0.0` con el perfil prod
(`auth/src/main/resources/application.properties:1-3`). Los ejemplos de abajo
describen el componente auth; no implican que un token de ejemplo deba usarse
como credencial.

#### `GET /q/health`

1. **Qué hace:** permite a operaciones comprobar el estado de disponibilidad
   reportado por el servicio de autenticación.

2. **Autenticación:** no requiere autenticación ni header `Authorization`.

3. **Request:**

   | campo | tipo | obligatorio | descripción |
   |---|---|---|---|
   | Body | ninguno | no | Es una petición GET sin cuerpo JSON. |
   | `Accept` | header HTTP, texto | no | Puede indicar `application/json` para solicitar la representación JSON. |

   Ejemplo HTTP (no existe un documento JSON de request para este endpoint):

   ```http
   GET /q/health HTTP/1.1
   Host: localhost:8080
   Accept: application/json
   ```

4. **Response exitosa:** el endpoint SmallRye Health respondió HTTP 200 en la
   comprobación local documentada aquí. La respuesta observada fue:

   | campo | tipo | descripción |
   |---|---|---|
   | `status` | string | Estado global que reporta la extensión de health. |
   | `checks` | array de objetos | Comprobaciones registradas; en la observación actual está vacío. |

   ```json
   {
     "status": "UP",
     "checks": []
   }
   ```

   Evidencia: dependencia SmallRye Health en
   `auth/pom.xml:38-41`; comprobación local a `http://127.0.0.1:8080/q/health`
   respondió 200 y ese JSON. No hay una implementación propia de health en
   `auth/src/main/java`.

5. **Errores:** no se observó ni está definido en el código auth un cuerpo de
   error para este endpoint. No hay health checks personalizados en el
   componente. El status y cuerpo si una comprobación futura informa `DOWN`, o
   si la ruta no está disponible, quedan como pregunta abierta; no se asume un
   código ni una forma de respuesta.

6. **Notas de seguridad:** el endpoint publica únicamente el estado agregado
   y el array de checks observado vacío; no requiere token. La exposición de
   esta ruta fuera de la red de servicio depende del despliegue.

#### `POST /auth/login`

1. **Qué hace:** autentica a una persona del taller y, si las credenciales son
   válidas, entrega su identidad, rol y token de sesión firmado.

2. **Autenticación:** no requiere autenticación previa ni header
   `Authorization`. Requiere enviar `Content-Type: application/json`; la
   respuesta se produce como `application/json`.

3. **Request:**

   | campo | tipo | obligatorio | descripción |
   |---|---|---|---|
   | `username` | string | sí | Nombre de usuario. Debe estar presente y no ser vacío o solo espacios. El valor recibido se usa tal cual para buscar la cuenta. |
   | `password` | string | sí | Contraseña. Debe estar presente y no ser vacía o solo espacios. Se verifica contra el hash almacenado. |
   | `client` | string | no | Etiqueta del cliente. Se acepta en el request, pero actualmente no afecta la autenticación ni la respuesta. |

   Ejemplo JSON completo para la cuenta ficticia de desarrollo/pruebas:

   ```json
   {
     "username": "student",
     "password": "<valor configurado localmente; no incluido aquí>",
     "client": "angular-workshop"
   }
   ```

   Evidencia de los campos: `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java:3-7`.
   Validación: `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:36-42`.
   El sembrado de cuentas demo está habilitado en dev/test, no en prod:
   `auth/src/main/resources/application.properties:7-9`.
   En desarrollo, define `AUTH_DEMO_USERS_STUDENT_PASSWORD` y
   `AUTH_DEMO_USERS_TRAINER_PASSWORD` en el entorno antes de iniciar el backend.
   No hay contraseñas demo predeterminadas en la configuración versionada;
   las pruebas proporcionan valores fixture aislados.

4. **Response exitosa:** HTTP 200. Los campos se construyen en
   `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:3` y
   `auth/src/main/java/com/bcp/workshop/auth/AuthenticatedUser.java:3`.

   | campo | tipo | descripción |
   |---|---|---|
   | `token` | string | JWT compacto firmado, generado para esta autenticación. El valor cambia con el instante de expiración y no debe copiarse de un ejemplo. |
   | `expiresIn` | number entero | Duración del token en segundos: 3600. |
   | `user` | object | Identidad y atributos que devuelve el servicio. |
   | `user.username` | string | Nombre de usuario autenticado. |
   | `user.displayName` | string | Nombre visible escapado para HTML. |
   | `user.role` | string | Rol almacenado para el usuario, por ejemplo `STUDENT` o `TRAINER` en las cuentas de demostración. |
   | `debug` | string | Marcador actualmente devuelto por el flujo de login. |

   Ejemplo JSON con todos los campos. El valor de `token` es un marcador
   descriptivo, no un JWT utilizable; el servidor debe generar el token real:

   ```json
   {
     "token": "<JWT HS256 generado por el servidor>",
     "expiresIn": 3600,
     "user": {
       "username": "student",
       "displayName": "Workshop Student",
       "role": "STUDENT"
     },
     "debug": "authenticated-with-legacy-flow"
   }
   ```

   El contrato y la respuesta están cubiertos por
   `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:37-48`.

5. **Respuestas de error:**

   | código | cuándo ocurre | ejemplo de cuerpo |
   |---|---|---|
   | 400 | El recurso recibe un request nulo. | `{"message":"Request is null"}` |
   | 400 | Falta `username`, es `null`, vacío o solo espacios. | `{"message":"Username is required"}` |
   | 400 | Falta `password`, es `null`, vacío o solo espacios. | `{"message":"Password is required"}` |
   | 401 | El usuario no existe o la contraseña no coincide. El mensaje incluye el `username` enviado. | `{"message":"Invalid user unknown-user or password"}` |
   | 403 | Se envía un `Origin` que no está permitido por CORS. Las pruebas verifican el status y que no se devuelve `Access-Control-Allow-Origin`; no fijan un cuerpo. El formato exacto queda como pregunta abierta. | **No definido por las pruebas/código auth; no se inventa un cuerpo.** |
   | 500 | Ocurre una excepción inesperada durante el procesamiento del login. | `{"message":"Internal server error"}` |

   Evidencia de status y cuerpos: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:25-49`;
   validaciones y mensaje de credenciales: `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:36-54`.
   La respuesta CORS 403 se comprueba en
   `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:155-164`.

   Errores previos a la ejecución del recurso (por ejemplo JSON mal formado,
   media type no soportado, método o ruta distintos) pueden ser generados por
   Quarkus/JAX-RS; auth no define su cuerpo en código y su status/cuerpo exactos
   no se han comprobado aquí. Quedan como pregunta abierta para una prueba de
   contrato HTTP.

6. **Notas de seguridad:**

   - El token lleva `alg=HS256`, `typ=JWT` y claims `sub`, `role` y `exp`;
     `exp` se fija a una hora del momento de emisión
     (`auth/src/main/java/com/bcp/workshop/auth/SignedTokenService.java:19-45`).
     El test comprueba formato, claims, vencimiento y firma
     (`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:123-151`).
   - **Riesgo pendiente en la configuración actual:** en
     `auth/src/main/resources/application.properties:4`, la propiedad
     `auth.token.secret` tiene un valor literal en vez de referenciar
     `AUTH_TOKEN_SECRET`. No se reproduce aquí. Esto contradice la afirmación
     de que el secreto está externalizado y debe corregirse/rotarse antes de
     desplegar; no asumir que el valor de la configuración es seguro.
   - Las contraseñas se verifican contra hashes PBKDF2 y nunca se incluyen en
     la respuesta (`auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:68-82`).
   - Las cuentas ficticias se crean solo con la opción habilitada en dev/test;
     prod la desactiva (`auth/src/main/resources/application.properties:7-9`).
     Aun con esa opción desactivada, el repositorio rechaza los nombres
     reservados `student` y `trainer`
     (`auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:52-54`).
   - `debug` se conserva porque forma parte de la respuesta actual; los
     consumidores no deberían depender de ese marcador como señal de seguridad.

### Contrato acordado

No se encontró una aprobación independiente registrada para un contrato
distinto del comportamiento observado. Este documento describe el código y las
pruebas actuales; no representa aprobación de integración humana.

### Riesgos y preguntas abiertas

- Externalizar y rotar el secreto de firma: la configuración actual contiene
  un valor literal (`application.properties:4`); por este motivo, H-AUTH-02
  figura `Pendiente` en `HALLAZGOS.md`.
- ¿Qué status y cuerpo debe ofrecer `/q/health` cuando una comprobación está
  degradada o la ruta no está disponible? No hay checks propios ni error
  observado.
- ¿Qué cuerpo exacto deben recibir los clientes ante CORS 403, JSON mal
  formado, media type no soportado, método no permitido o ruta inexistente?
  La implementación auth no fija todos esos cuerpos.
- El intento manual local de `POST /auth/login` expiró por timeout; no se
  presenta como comprobación exitosa. La respuesta exitosa se documenta a
  partir del DTO y de las aserciones de `AuthResourceTest`.
- El valor del token del ejemplo es un marcador, porque cada login genera un
  JWT con vencimiento dinámico; no se obtuvo ni se publica un token de sesión.
