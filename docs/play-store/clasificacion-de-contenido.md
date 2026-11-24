# Guía del cuestionario de clasificación de contenido (IARC)

Categoría de partida en Play Console: **"Referencia, noticias o educación"** no aplica; elige
**"Utilidad, productividad, comunicación u otra"**. Responde con honestidad: Google puede revisar.

| Pregunta | Respuesta sugerida | Motivo |
|---|---|---|
| Violencia | No | Sin contenido violento |
| Contenido sexual o desnudez | No | |
| Lenguaje soez | No | |
| Sustancias controladas (drogas, alcohol, tabaco) | **Sí: alcohol** (venta de bebidas alcohólicas en licorerías) | Marca "referencias o venta de alcohol". La app exige fecha de nacimiento ≥ 18 y el repartidor verifica la cédula. **[REVISAR LEGALMENTE]** |
| Medicamentos | **Sí** (farmacias: medicamentos con receta y de venta libre) | Play pide declarar si la app vende o facilita medicamentos. Exige receta aprobada por la farmacia. **[REVISAR LEGALMENTE]**: puede exigir licencias o declaraciones adicionales |
| Juegos de azar o dinero simulado | No | |
| Contenido generado por usuarios | **Sí** (calificaciones con comentarios) | `ratings`. Menciona que hay moderación posible (Términos, sección 8) |
| Interacción entre usuarios | No hay chat entre usuarios. Los comentarios se ven en calificaciones | Confirma que no hay mensajería |
| Comparte ubicación del usuario | **Sí en Jagoo Driver** (con el cliente del pedido). **No** en la app cliente | `DriverLocationService` |
| Compras digitales | No: se venden bienes físicos (comida) | Los bienes físicos no usan Google Play Billing |
| Acceso sin restricciones a internet | No | No hay navegador embebido propio (los enlaces legales abren el navegador del sistema) |

Resultado esperado: clasificación baja (PEGI 3 / Everyone / Todos), sujeta a lo que marques en alcohol y contenido de usuarios.

## Otras declaraciones obligatorias del Play Console

- **Público objetivo:** 18+ o "todos los públicos salvo menores de 13". La política de privacidad dice "no dirigido a menores de 13". Si eliges menores de 13 como público objetivo, se activan los requisitos de Familias: no lo hagas.
- **Anuncios:** No contiene anuncios (no hay SDK de ads en el repo).
- **App de noticias / salud / finanzas / gobierno:** No.
- **Acceso a la app:** requiere cuenta. Entrega credenciales de prueba a revisión (usuario cliente). Para Driver, entrega un repartidor de prueba, porque el login lo crea el admin.
- **ID de publicidad:** no se usa (no hay SDK de ads ni de analytics con ese ID). Responde "No".
- **Servicio en primer plano (Driver):** tipo ubicación; explica "seguimiento de la entrega activa, visible con notificación persistente"; sube un video corto demostrándolo.
- **Eliminación de cuenta:** URL `https://<tu-dominio>/delete-account` y mención de la opción dentro de la app.


## Impacto de alcohol y medicamentos
- La clasificación IARC subirá (esperable: PEGI 18 / Mayores de 18 en algunas regiones por la venta de alcohol). Si prefieres una clasificación baja, lanza primero **solo restaurantes y tiendas** y habilita licorerías después con otra ficha.
- Play tiene políticas propias sobre alcohol y medicamentos: revisa "Política de contenido restringido" y la de "Salud" antes de enviar. Es posible que pidan documentación del negocio.
- Público objetivo: **18+** (no incluir menores de 13 evita Familias).
- Pago: ya no hay tarjeta; solo efectivo y transferencia (bienes físicos, no usan Google Play Billing).