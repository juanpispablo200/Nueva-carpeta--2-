# Fase 4 — Instrucciones para completar en Android Studio

## ¿Por qué necesitas abrir Android Studio?

Esta máquina tiene un problema de certificado SSL que impide descargar
dependencias nuevas desde la terminal. Android Studio tiene su propio
manejo de SSL y descarga todo correctamente.

---

## Paso 1 — Pegar las claves de Supabase

Abre el archivo `local.properties` (está en la raíz del proyecto) y reemplaza
los placeholders con tus valores reales de Supabase:

```
supabase.url=https://TUPROYECTO.supabase.co
supabase.anon.key=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

¿Dónde los encuentro?
1. Ve a https://supabase.com → tu proyecto
2. Clic en el ícono de Settings (engranaje) → API
3. Copia "Project URL" y "anon public key"

---

## Paso 2 — Sincronizar en Android Studio

1. Abre Android Studio
2. File → Open → selecciona la carpeta "Nueva carpeta (2)"
3. Android Studio detecta los cambios en build.gradle.kts y muestra
   una barra amarilla que dice "Gradle files have changed"
4. Haz clic en **"Sync Now"**
5. Espera que termine (descarga ~50 MB de Supabase + Ktor)
6. Si no aparece la barra amarilla: Tools → Gradle → "Sync Project with Gradle Files"

---

## Paso 3 — Verificar que compiló bien

Una vez que el Sync termina sin errores:
- En la barra inferior verás "BUILD SUCCESSFUL" o "Gradle sync finished"
- Puedes correr la app en un emulador o dispositivo físico

---

## ¿Qué se descarga?

| Librería | Para qué sirve |
|---|---|
| `supabase-kt auth` | Login y registro con email/contraseña |
| `supabase-kt postgrest` | Leer y escribir en la base de datos |
| `supabase-kt realtime` | Recibir cambios del pedido en vivo |
| `ktor-client-android` | Motor HTTP que usa Supabase internamente |
| `kotlinx-serialization-json` | Convertir JSON a clases Kotlin |

---

## ¿Qué sigue después?

Una vez que Android Studio sincronice sin errores, avísame y continuamos
con la **Fase 5 — Login y registro de usuarios**.
