# Spec Delta

## Purpose

Define el comportamiento observable de `POST /auth/login` del servicio `auth/`: cómo se validan las credenciales recibidas, cómo se verifican contra el almacén de usuarios y qué respuesta recibe el cliente en cada caso, incluyendo entradas que intentan alterar la consulta de verificación.

## ADDED Requirements

### Requirement: Autenticación exitosa con credenciales válidas
El sistema SHALL responder 200 con `token`, `expiresIn` y `user.username` cuando `username` y `password` coinciden exactamente con un usuario existente.

#### Scenario: Usuario de workshop inicia sesión
- **WHEN** se envía `POST /auth/login` con `username=student` y `password=Workshop123!`
- **THEN** la respuesta es 200 e incluye `user.username=student` y un `token` no nulo

### Requirement: Validación de campos obligatorios
El sistema SHALL responder 400 cuando falte `username` o `password`, o cuando alguno sea una cadena vacía o solo espacios.

#### Scenario: Username ausente
- **WHEN** se envía `POST /auth/login` sin el campo `username`
- **THEN** la respuesta es 400 con un mensaje indicando que el username es requerido

#### Scenario: Password vacío
- **WHEN** se envía `POST /auth/login` con `password` igual a una cadena vacía o solo espacios
- **THEN** la respuesta es 400 con un mensaje indicando que el password es requerido

### Requirement: Rechazo de credenciales inválidas
El sistema SHALL responder 401 sin emitir `token` cuando `username` y `password` no coinciden con ningún usuario existente.

#### Scenario: Usuario desconocido
- **WHEN** se envía `POST /auth/login` con un `username` que no existe en el almacén de usuarios
- **THEN** la respuesta es 401 y el cuerpo no contiene `token`

#### Scenario: Contraseña incorrecta para usuario existente
- **WHEN** se envía `POST /auth/login` con `username=student` y un `password` distinto al almacenado
- **THEN** la respuesta es 401 y el cuerpo no contiene `token`

### Requirement: Verificación de credenciales resistente a inyección
La verificación de credenciales SHALL tratar `username` y `password` como valores literales de comparación, de modo que una entrada con sintaxis SQL no altere la condición de búsqueda ni produzca una autenticación no intencionada.

#### Scenario: Carga de inyección en el campo username
- **WHEN** se envía `POST /auth/login` con `username=' OR '1'='1` y cualquier `password`
- **THEN** la respuesta es 401, el cuerpo no contiene `token` y no se autentica como ningún usuario existente

#### Scenario: Carga de inyección en el campo password
- **WHEN** se envía `POST /auth/login` con `username=student` y `password=' OR '1'='1`
- **THEN** la respuesta es 401 y el cuerpo no contiene `token`
