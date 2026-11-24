# Borrador del formulario "Seguridad de los datos"

Verificado contra el código del repo (manifests, `build.gradle.kts`, ViewModels y servicios).
Es un borrador: tú eres quien responde ante Google. Revisa cada fila antes de enviar.

## Preguntas generales

| Pregunta | Respuesta | Evidencia |
|---|---|---|
| ¿Recopila o comparte datos del usuario? | Sí | Ver tablas |
| ¿Datos cifrados en tránsito? | Sí | HTTPS a Supabase/Firebase; `network_security_config.xml` sin tráfico en claro (ambas apps) |
| ¿Puede el usuario pedir que se eliminen sus datos? | Sí | Perfil → Eliminar mi cuenta + URL `https://<tu-dominio>/delete-account` |
| URL de política de privacidad | `https://<tu-dominio>/privacy` | `PrivacyPolicyPage.tsx` |
| Cuentas de usuario | Sí, correo y contraseña | `AuthViewModel` (Supabase Auth) |

## App cliente (`com.jagoo.app`)

| Categoría Play | Dato | ¿Se recopila? | ¿Se comparte con terceros? | Obligatorio | Finalidad | Evidencia |
|---|---|---|---|---|---|---|
| Información personal | Nombre | Sí | No (1) | Opcional | Funcionalidad, cuenta | `AuthViewModel.register`, `profiles.full_name` |
| Información personal | Correo electrónico | Sí | No (1) | Obligatorio | Cuenta, funcionalidad | `auth.signUpWith(Email)` |
| Información personal | Número de teléfono | Sí | No (1) | Opcional | Funcionalidad | `updateProfile` → `profiles.phone` |
| Información personal | Dirección | Sí | No (1) | Obligatorio para pedir | Funcionalidad (entrega) | `addresses`, `orders.delivery_address` |
| Información financiera | Historial de compras y monto en efectivo | Sí | No (1) | Obligatorio | Funcionalidad, contabilidad | `orders`, `order_items` |
| Fotos y videos | Fotos (comprobante de transferencia) | Sí, solo si se sube | No (1) | Opcional | Funcionalidad | `CartViewModel` → bucket `payment-proofs` |
| ID de dispositivo u otros | Token de notificaciones (FCM) | Sí | Sí, con Google (Firebase) como proveedor de servicio | Opcional | Funcionalidad (notificaciones) | `JagooFirebaseMessagingService` → `profiles.fcm_token` |
| Información de la app y rendimiento | Registros de fallos | Sí, solo en release | Sí, con Google (Firebase Crashlytics) como proveedor | Obligatorio | Análisis, estabilidad | Crashlytics (colección `true` solo en release) |
| Información de la app y rendimiento | Diagnósticos | Sí (Crashlytics) | Sí, igual que arriba | Obligatorio | Análisis | Ídem |
| Ubicación | (ninguna) | No | | | | `app` no declara permisos de ubicación |

La app cliente muestra en un mapa (Google Maps) la posición del repartidor. No lee la ubicación del cliente.
Las calificaciones y comentarios son contenido del usuario: declara "Otro contenido generado por el usuario" como recopilado (`ratings`).

(1) Play no considera "compartir" la transferencia a un proveedor de servicios que trata los datos
en tu nombre (Supabase como hosting/base de datos). Los restaurantes y repartidores reciben nombre y
dirección para cumplir el pedido; es parte de la funcionalidad principal. Si dudas, decláralo como
"compartido" con finalidad "Funcionalidad".

## App repartidor (`com.jagoo.driver`)

| Categoría Play | Dato | ¿Se recopila? | ¿Se comparte? | Obligatorio | Finalidad | Evidencia |
|---|---|---|---|---|---|---|
| Ubicación | Ubicación precisa | Sí, solo con entrega activa y en primer plano (servicio con notificación) | Se muestra al cliente del pedido (via Supabase) | Obligatorio para repartir | Funcionalidad | `DriverLocationService` (RPC `update_driver_location`, cada ~5 s) |
| Ubicación | Ubicación aproximada | Sí (se pide junto a la precisa) | Ídem | Ídem | Ídem | `ACCESS_COARSE_LOCATION` |
| Información personal | Nombre, correo, teléfono | Sí | No | Obligatorio | Cuenta | `profiles` |
| ID de dispositivo u otros | Token FCM | Sí | Google (proveedor) | Opcional | Notificaciones | `DriverFirebaseMessagingService` |
| Rendimiento | Fallos y diagnósticos | Sí, solo release | Google (proveedor) | Obligatorio | Análisis | Crashlytics |

No hay `ACCESS_BACKGROUND_LOCATION`: el seguimiento corre en un servicio de primer plano con tipo `location`.
En Play Console deberás completar la **declaración de servicio en primer plano (ubicación)** y explicar el caso de uso.

## Cambios de esta versión (verificados contra el código)

| Dato | ¿Se recopila? | ¿Se comparte? | Obligatorio | Finalidad | Evidencia |
|---|---|---|---|---|---|
| **Fecha de nacimiento** (Información personal → "Otra información") | Sí, solo si el cliente pide bebidas alcohólicas (+18). Se guarda una vez | No (1) | Opcional | Funcionalidad (verificar mayoría de edad) | `CartViewModel.submitBirthDate` → RPC `set_birth_date` → `profiles.birth_date` |
| Confirmación de mayoría de edad en el checkout | Sí, casilla obligatoria "soy mayor de 18 años y mostraré mi cédula"; el servidor rechaza el pedido sin ella (`EDAD_NO_CONFIRMADA`) | No | Obligatorio para alcohol | Funcionalidad | `CheckoutScreen`, `create_order(p_age_confirmed)` |
| Verificación de cédula al entregar | **No se guarda la cédula.** El repartidor la revisa en persona y solo se registra quién y cuándo confirmó (`age_verified_at/by`); sin esa confirmación no puede marcar el pedido como entregado | No | n/a | Funcionalidad | `confirm_age_verification` |
| Información de pago con tarjeta | **Ya no existe.** Jagoo no procesa ni guarda datos de tarjeta | n/a | n/a | n/a | Flujo Kushki eliminado (migración 20) |

**Medicamentos:** la categoría ofrece **solo productos de venta libre, sin receta**. La app y el panel no
piden, suben ni guardan recetas ni datos de salud. No declares datos de "Salud y estado físico".
**[REVISAR LEGALMENTE]** la venta de medicamentos de venta libre y de alcohol antes de publicar.

**Retención** (coincide con la política de privacidad, sección 9; la ejecuta `run_data_retention()`):
comprobante 365 días, datos personales del pedido 730 días, notificaciones 90 días,
ubicación del repartidor 24 horas.

**Eliminación de cuenta:** borra perfil (incluida la fecha de nacimiento), direcciones, favoritos,
notificaciones y los archivos de comprobantes; anonimiza los pedidos.

**Estado de hallazgos anteriores**
- A (tarjeta/Kushki): resuelto, el flujo se eliminó.
- B (permisos de fotos): se mantiene `GetContent`, sin permisos de almacenamiento. Solo se usa para el comprobante de transferencia.
- C (comprobantes en bucket público): resuelto. El bucket es privado, la app guarda la ruta (no una URL pública) y el panel pide URLs firmadas de 5 minutos.
- D (retención sin proceso): resuelto con la migración 23. Requiere habilitar `pg_cron`.