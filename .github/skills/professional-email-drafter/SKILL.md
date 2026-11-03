---
name: professional-email-drafter
description: Redacta correos profesionales claros y concisos a partir del objetivo, la audiencia y los datos proporcionados, sin inventar hechos ni compromisos.
---

# Skill: Redacción de correos profesionales

## 1) Metadata
- **Skill ID:** professional-email-drafter
- **Version:** 0.1.0
- **Purpose:** Convertir un propósito y un conjunto de datos confirmados en un correo profesional listo para revisar y enviar.
- **Audience:** Personas que necesitan escribir a colegas, líderes, clientes o proveedores.
- **When to use:** Para solicitudes, seguimientos, actualizaciones, coordinación, agradecimientos y comunicaciones laborales rutinarias.
- **When NOT to use:** Para comunicaciones legales, disciplinarias o de crisis que requieran revisión especializada; no sustituye la aprobación de la persona responsable.
- **Sensitive inputs:** No incluyas contraseñas, tokens, datos personales innecesarios, información real de clientes ni contenido confidencial que no deba aparecer en un asistente.
- **Expected output:** Un correo en Markdown con asunto y cuerpo, más datos por confirmar solo cuando sean necesarios.

## 2) Inputs
Proporciona lo que tengas; los campos no relevantes pueden omitirse:

- **Objetivo del correo:**
- **Destinatario o audiencia:**
- **Relación o contexto:**
- **Hechos confirmados que deben incluirse:**
- **Acción o respuesta que se solicita:**
- **Fecha límite:** (solo si existe)
- **Tono deseado:** (por ejemplo, cordial, directo, formal)
- **Idioma:** (por defecto, el idioma de la solicitud; si no está claro, español)
- **Restricciones:** (longitud, contenido que no debe incluirse u otras preferencias)
- **Firma:** (opcional; texto exacto si debe agregarse)

## 3) Workflow
1. Identifica el propósito, la audiencia y la acción principal.
2. Usa únicamente los hechos, fechas, compromisos y nombres proporcionados por la persona usuaria.
3. Si falta información esencial para entender qué se necesita o a quién va dirigido, formula hasta tres preguntas concretas antes de redactar.
4. Si falta un dato no esencial, omítelo. Usa `[dato por confirmar]` solo cuando el correo no pueda ser útil sin ese dato.
5. Redacta el asunto y el cuerpo en el idioma y tono solicitados, priorizando claridad, cortesía y brevedad.
6. Revisa el borrador según las reglas de validación antes de devolverlo.

## 4) Output
Devuelve el siguiente formato. No agregues análisis interno ni variantes salvo que se soliciten.

**Asunto:** [asunto breve y específico]

**Correo:**

[Saludo]

[Propósito y contexto breve. Incluye la solicitud o el siguiente paso de forma explícita.]

[Cierre. Agrega la firma únicamente si fue proporcionada.]

**Por confirmar:** [incluir solo si quedan datos imprescindibles sin resolver; en caso contrario, omitir esta sección]

## 5) Constraints
- No inventes nombres, cargos, fechas, cifras, acuerdos, resultados, archivos adjuntos, políticas ni compromisos.
- No conviertas una sugerencia en una decisión acordada ni atribuyas opiniones a otras personas.
- No agregues urgencia, amenazas, disculpas o promesas que no estén en el input.
- Si no se indica el idioma, redacta en español. Conserva nombres de productos y términos técnicos proporcionados.
- Mantén el correo breve y fácil de escanear; amplíalo solo si el contexto o la persona usuaria lo requiere.
- No incluyas datos sensibles innecesarios. Si el input contiene credenciales o datos personales, no los repitas y señala que deben excluirse o redactarse.
- No agregues destinatarios, CC ni firma que no hayan sido proporcionados.
- Distingue claramente los hechos confirmados de los pendientes. No presentes hipótesis como hechos.

## 6) Validation Rules
La salida es válida si:
- Incluye un asunto y un cuerpo que corresponden al objetivo indicado.
- Expresa el contexto necesario sin añadir información no proporcionada.
- La acción o respuesta solicitada es explícita cuando aplica.
- Incluye una fecha límite únicamente si fue indicada por la persona usuaria.
- Mantiene un tono profesional y el idioma solicitado o predeterminado.
- No revela ni reproduce datos sensibles innecesarios.
- Marca lo imprescindible que falta en **Por confirmar** o pregunta por ello antes de redactar.

## 7) Evidence
Para distribuir o reutilizar esta skill, conserva:
- `SKILL.md` versionado.
- `examples/input.sample.md`.
- `examples/output.sample.md`.
- Una versión coherente en los metadatos de la skill y su historial de cambios.
