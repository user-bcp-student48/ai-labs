# Tasks

## 1. Caracterización previa al cambio funcional

- [ ] 1.1 Confirmar que `AuthResourceTest` caracteriza el login exitoso y los errores HTTP 400/401 documentados, y que verifica HS256, claims, expiración y firma con la fixture `%test`; ejecutar `mvn test` desde `auth/` y conservar el resultado como baseline.
- [ ] 1.2 Completar `SignedTokenServiceTest` para fijar el límite de 32 bytes UTF-8 y comprobar que el rechazo de secreto ausente o corto no incluye el valor del secreto; ejecutar `mvn -Dtest=SignedTokenServiceTest test` desde `auth/` antes de cambiar la configuración runtime.

## 2. Pruebas de aceptación de configuración externa

- [ ] 2.1 Añadir en `auth/` una prueba que compruebe que la configuración runtime versionada no contiene un secreto literal ni un fallback y que el secreto se resuelve desde `AUTH_TOKEN_SECRET`; verificar que detecta la configuración actual antes de implementar el cambio.
- [ ] 2.2 Añadir pruebas aisladas de arranque con perfil runtime no-test: secreto externo válido permite iniciar; secreto ausente o menor a 32 bytes impide iniciar; la salida de error no revela el valor. Ejecutar estas pruebas desde `auth/` y confirmar que cubren los tres casos.

## 3. Externalización incremental

- [ ] 3.1 En `auth/src/main/resources/application.properties`, retirar el secreto literal base y obtener `auth.token.secret` desde `AUTH_TOKEN_SECRET` sin fallback; conservar la clave fixture únicamente bajo `%test`. Verificar mediante la prueba de configuración de 2.1 que no queda un secreto runtime versionado.
- [ ] 3.2 Mantener la validación de inicio y firma existente para el valor externo; ejecutar desde `auth/` `mvn -Dtest=SignedTokenServiceTest,AuthResourceTest test` y comprobar que los casos de configuración y contrato pasan sin cambios en respuestas o tokens.

## 4. Verificación y preparación de despliegue

- [ ] 4.1 Ejecutar `mvn verify` desde `auth/`; confirmar todos los tests, la cobertura JaCoCo del código modificado de al menos 85% y que ninguna respuesta o salida diagnóstica expone el secreto.
- [ ] 4.2 Antes del despliegue, configurar `AUTH_TOKEN_SECRET` mediante el mecanismo seguro del entorno, completar la rotación y registrar su verificación en el checklist de despliegue existente sin incluir el valor; comprobar arranque y login con un JWT verificable.