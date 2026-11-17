 m# Jagoo Delivery - App de Delivery de Restaurantes

**Jagoo Delivery** es una aplicación móvil moderna desarrollada en Android nativo enfocada en el servicio de entrega a domicilio para restaurantes (hamburgueserías, pizzerías, cafeterías, comida típica, sushi, entre otros). 

La plataforma incluye una experiencia fluida para el cliente (exploración por categorías, personalización de platos con extras, carrito con regla de único restaurante, seguimiento de pedido en tiempo real) y la base para un panel de administración multi-tenant donde cada restaurante puede gestionar su menú y estado del negocio.

---

## 🛠️ Requisitos del Sistema e Instalación

Para abrir, compilar y ejecutar este proyecto en tu entorno local necesitas:

* **IDE Recomendado:** Android Studio (Versión Jellyfish 2023.3.1 / Koala 2024.1.1 o superior).
* **JDK:** Java Development Kit (JDK) 17.
* **Android SDK:** 
  * `compileSdk = 34`
  * `targetSdk = 34`
  * `minSdk = 26` (Android 8.0 Oreo o superior)
* **Dispositivo / Emulador:** Emulador con Android 8.0+ y Google Play Services configurado.

---

## 🚀 Cómo Compilar y Ejecutar

1. **Clonar o abrir el proyecto:**
   Abre Android Studio y selecciona `File -> Open`, luego navega hasta la carpeta raíz del proyecto (`Nueva carpeta (2)`).

2. **Sincronización de Gradle:**
   Deja que Android Studio descargue las dependencias y sincronice Gradle automáticamente (o ejecuta `./gradlew sync`).

3. **Ejecución en Emulador / Dispositivo:**
   * Selecciona el módulo `app` en la barra superior.
   * Selecciona tu dispositivo emulado o físico conectado vía ADB.
   * Haz clic en **Run 'app'** (`Shift + F10`).

4. **Compilación manual mediante Gradle CLI:**
   ```bash
   # Compilar el APK en modo debug
   ./gradlew app:assembleDebug
   ```

---

## 🏗️ Arquitectura y Tecnologías

El proyecto sigue una arquitectura **MVVM (Model-View-ViewModel)** reactiva con patrón Unidirectional Data Flow (UDF):

* **Lenguaje:** Kotlin
* **UI Framework:** **Jetpack Compose** con componentes **Material 3**.
* **Gestión de Estado:** `StateFlow` y `MutableStateFlow` con `collectAsState()` en Compose.
* **Navegación:** Jetpack Navigation Compose (`NavHost`, `composable`, `NavController`).
* **Carga de Imágenes:** **Coil Compose** con cacheado asíncrono y soporte para URLs remotas.
* **Tipografía:** Google Fonts con la familia tipográfica **Poppins**.
* **Persistencia:** Room Database local con soporte para semillas iniciales (Seed) y arquitectura preparada para integración con **Supabase** / **Firebase**.

---

## 📁 Estructura del Proyecto

```text
Nueva carpeta (2)/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           └── java/com/jagoo/app/
│               ├── MainActivity.kt           # Activity principal con Scaffold y NavGraph
│               ├── data/                     # Modelos de datos y repositorios de información
│               │   ├── RestaurantModels.kt   # Entidades: Restaurant, Dish, CartItem, Order, Option
│               │   └── RestaurantData.kt     # Semilla inicial con 7 restaurantes reales
│               ├── navigation/               # Definición de rutas y grafo de navegación
│               │   ├── Screen.kt             # Sealed class con las rutas de la app
│               │   └── NavGraph.kt           # NavHost y mapeo de composables
│               ├── ui/
│               │   ├── components/           # Componentes reutilizables (Headers, Cards, Dialogs)
│               │   │   ├── JagooHeader.kt
│               │   │   ├── JagooBottomBar.kt
│               │   │   ├── RestaurantCard.kt
│               │   │   ├── DishCard.kt
│               │   │   ├── DishDetailDialog.kt
│               │   │   ├── QuantitySelector.kt
│               │   │   └── SwipeToDeleteItem.kt
│               │   ├── screens/              # Pantallas principales
│               │   │   ├── HomeScreen.kt
│               │   │   ├── CategoryScreen.kt
│               │   │   ├── RestaurantDetailScreen.kt
│               │   │   ├── SearchScreen.kt
│               │   │   ├── CartScreen.kt
│               │   │   ├── OrderTrackingScreen.kt
│               │   │   ├── ProfileScreen.kt
│               │   │   ├── AdminDashboardScreen.kt
│               │   │   ├── AdminMenuScreen.kt
│               │   │   ├── OrderHistoryScreen.kt
│               │   │   └── AddressesScreen.kt
│               │   └── theme/                # Sistema de diseño (Color, Type, Shape, Spacing)
│               └── viewmodel/                # Lógica de negocio y manejo de estado
│                   ├── ProductViewModel.kt
│                   └── CartViewModel.kt
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

---

## ✨ Funcionalidades Principales

1. **Exploración de Restaurantes:** Filtros por categorías dinámicas (Hamburguesas, Pizza, Café, Sushi, Típica, etc.), calificación, tiempo de entrega y estado Abierto/Cerrado.
2. **Favoritos:** Botón de corazón para guardar restaurantes preferidos.
3. **Personalización de Platos:** Selección de cantidad, opciones/extras (ej. término de la carne, quesos adicionales) y notas especiales.
4. **Carrito de Único Restaurante:** Validación inteligente que alerta al usuario si intenta mezclar platos de distintos restaurantes.
5. **Seguimiento del Pedido:** Estado en tiempo real, mapa/línea de tiempo (Confirmado, Preparando, En camino, Entregado) y datos del repartidor.
6. **Panel Administrador de Restaurante:** Gestión del menú, creación y edición de platos, ajuste de precios, grupos de extras y estado del negocio.
7. **Gestión de Perfil:** Historial de pedidos realizados y administración de direcciones guardadas.
