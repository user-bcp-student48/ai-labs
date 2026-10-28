# Spec Delta

## Purpose

Define los requisitos observables para configurar el secreto de firma de los tokens de autenticación y preservar el contrato existente de login y JWT.

## ADDED Requirements

### Requirement: El secreto de firma se suministra externamente
El servicio de autenticación MUST obtener el secreto de firma mediante configuración externa de runtime, y la configuración versionada de producción MUST NOT contener un secreto literal de producción.

#### Scenario: El servicio inicia con un secreto externo válido
- **WHEN** el servicio de autenticación inicia con un secreto suministrado externamente de al menos 32 bytes
- **THEN** el servicio inicia correctamente

#### Scenario: El arranque rechaza un secreto ausente o demasiado corto
- **WHEN** el servicio de autenticación inicia sin secreto de firma o con uno menor a 32 bytes
- **THEN** el arranque se rechaza y el valor del secreto no se divulga en los diagnósticos

### Requirement: El secreto de firma nunca se divulga
El servicio de autenticación MUST NOT devolver ni registrar el secreto de firma en respuestas HTTP o diagnósticos.

#### Scenario: La respuesta de login y los diagnósticos no revelan el secreto
- **WHEN** un login tiene éxito o se informa un error de configuración
- **THEN** ni la respuesta ni los diagnósticos contienen el secreto de firma

### Requirement: El contrato del token y del login permanece compatible
La externalización del secreto MUST NOT cambiar el request, la respuesta, los errores documentados de `POST /auth/login` ni el contrato JWT descrito en `docs/ENDPOINTS.md`.

#### Scenario: El login exitoso preserva la respuesta y el token documentados
- **WHEN** se envían credenciales válidas a `POST /auth/login` con una configuración externa de firma válida
- **THEN** la respuesta conserva HTTP 200 y los campos `token`, `expiresIn`, `user` (`username`, `displayName`, `role`) y `debug`
- **AND** `expiresIn` permanece en 3600 segundos
- **AND** el token sigue siendo un JWT HS256 con claims `sub`, `role` y `exp`, y su firma se valida con el secreto configurado

#### Scenario: Las credenciales inválidas preservan el error documentado
- **WHEN** se envían a `POST /auth/login` credenciales que no autentican
- **THEN** el endpoint conserva HTTP 401 y el comportamiento documentado del mensaje de credenciales inválidas

#### Scenario: Las credenciales requeridas preservan su validación
- **WHEN** el request no contiene `username` o `password` válido, o alguno está vacío o solo contiene espacios
- **THEN** el endpoint conserva HTTP 400 y el mensaje documentado para el campo inválido

### Requirement: La rotación se registra antes del despliegue
El servicio de autenticación MUST NOT considerarse listo para desplegar hasta que la rotación de su secreto de firma esté registrada sin incluir ni exponer el valor del secreto.

#### Scenario: La evidencia de rotación está disponible sin revelar el secreto
- **WHEN** se revisa la preparación del despliegue del servicio de autenticación
- **THEN** la rotación está registrada como completada
- **AND** la evidencia no contiene el valor del secreto de firma