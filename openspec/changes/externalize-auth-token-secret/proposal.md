# Proposal

## Why

El Issue [#23 (HU-01)](https://github.com/user-bcp-student48/ai-labs/issues/23), originado en H-AUTH-02, identifica que la configuración versionada de auth contiene un secreto literal de firma. Mantenerlo en el repositorio expone una credencial capaz de firmar tokens; externalizarlo y registrar su rotación antes del despliegue reduce ese riesgo sin alterar el contrato que consumen los clientes.

## What Changes

- **Alcance:** auth requerirá un secreto de firma suministrado mediante configuración externa, rechazará el arranque si falta o no alcanza los 32 bytes mínimos y no expondrá su valor en respuestas o diagnósticos.
- **Alcance:** la preparación del despliegue deberá dejar evidencia de que el secreto fue rotado, sin registrar su valor.
- **Comportamiento que se preserva:** `POST /auth/login` mantiene su request y respuesta documentados; el JWT conserva HS256, los claims `sub`, `role` y `exp`, y la duración de 3600 segundos.
- **Fuera de alcance:** cambiar endpoints, formatos HTTP, algoritmo, claims, expiración, autenticación de usuarios o componentes `api/` y `web/`; elegir la fuente concreta de configuración por entorno; ejecutar ahora la rotación. La rotación sí deberá completarse y registrarse antes de desplegar.
- No se propone aquí una solución técnica ni se modifican código funcional o pruebas.

La evidencia local consultada y el baseline `docs/ENDPOINTS.md` confirman que hoy `auth/src/main/resources/application.properties` contiene un valor literal; esto resuelve para el alcance actual la discrepancia mencionada en las preguntas abiertas del Issue. La fuente concreta de configuración externa permanece pendiente para diseño.

## Capabilities

### New Capabilities
- `auth/token-signing`: suministro y validación del secreto de firma, preservación del contrato del token y evidencia de rotación previa al despliegue.

### Modified Capabilities
- Ninguna.

## Impact

- Componente `auth/`: configuración de `auth.token.secret`, validación del secreto y pruebas relacionadas.
- API pública: sin cambios al contrato `POST /auth/login` documentado en `docs/ENDPOINTS.md`.
- Despliegue: requiere evidencia de rotación sin divulgar el secreto; no se modifica documentación general ni la configuración de otros componentes en esta propuesta.