# Íconos de Jagoo: pasos en Android Studio (Image Asset)

Los SVG de partida están en `docs/play-store/icon-source/`. Son una copia mecánica del
`pathData` de `ic_launcher_foreground.xml` y `ic_launcher_background.xml`
(app = fondo verde #1A6B3C, driver = fondo verde #0F4D2A).

> Los SVG no se han visto renderizados. Ábrelos en el navegador antes de generar
> y confirma que el ícono se ve como esperas.

## Paso 1: abrir Image Asset

1. Abre el proyecto en Android Studio.
2. En el panel Project, selecciona el módulo (`app` o `driver`).
3. Menú **File > New > Image Asset**. En "Icon Type" deja **Launcher Icons (Adaptive and Legacy)**.
4. En el desplegable **Module** elige `app` (luego repite todo para `driver`).

## Paso 2: capas

- Pestaña **Foreground Layer**: Asset Type **Image**, Path = `docs/play-store/icon-source/app-foreground.svg`.
  Trim = No. Resize = 100%.
- Pestaña **Background Layer**: Asset Type **Image**, Path = `docs/play-store/icon-source/app-background.svg`.
- Para el driver usa `driver-foreground.svg` y `driver-background.svg`.

## Paso 3: opciones

- Pestaña **Options**: marca **Generate Legacy Icon**, **Generate Round Icon** y **Generate Google Play Store Icon**.
- Name: `ic_launcher`.
- Next, luego Finish. Si pregunta por sobrescribir los XML existentes (`ic_launcher.xml`, `ic_launcher_round.xml`), acepta.

Resultado esperado en cada módulo:

- `res/mipmap-mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi/ic_launcher.png` y `ic_launcher_round.png` (48/72/96/144/192 px).
- `src/main/ic_launcher-playstore.png` (512x512, 32-bit PNG).

## Paso 4: ícono de 512x512 para Play Console

Copia `app/src/main/ic_launcher-playstore.png` a `docs/play-store/icon-512.png`.
Para el driver, cópialo como `docs/play-store/icon-512-driver.png`.

Verifica en las propiedades del archivo: 512x512 px, PNG, máximo 1 MB.

## Paso 5: comprobar

1. `.\gradlew :app:compileDebugKotlin :driver:compileDebugKotlin`
2. Instala en un dispositivo o emulador y mira el ícono en el launcher (cuadrado, redondo y en Ajustes > Apps).

## Nota

Con `minSdk = 26`, todos los dispositivos soportados usan el ícono adaptativo
(`mipmap-anydpi-v26`). Los PNG de mipmap son un respaldo y no bloquean la
publicación. El archivo que Play Console exige es el de 512x512.

## Ícono monocromático (íconos con tema, Android 13+)
Los dos módulos ya incluyen `res/drawable/ic_launcher_monochrome.xml` (la silueta del
foreground en un solo color) y lo declaran como `<monochrome>` en
`mipmap-anydpi-v26/ic_launcher.xml` e `ic_launcher_round.xml`. Con `minSdk 26` el ícono
adaptativo con capas foreground, background y monochrome queda completo.

Si ejecutas Image Asset y pregunta por sobrescribir `ic_launcher.xml`, **vuelve a verificar**
que la línea `<monochrome ... />` sigue ahí (o actívala en la pestaña **Foreground Layer →
Monochrome** del asistente, si tu versión de Android Studio la ofrece).

Cómo comprobarlo: en un dispositivo Android 13+ activa **Ajustes → Fondo de pantalla y estilo →
Íconos con tema**. El ícono debe verse como una silueta reconocible. Si la silueta se ve
como una mancha, ajusta el dibujo del monocromático (solo importa el canal alfa).

## Resumen: pasos exactos para ambas apps y el ícono de 512 px
1. Android Studio → File → New → Image Asset → módulo `app`.
2. Foreground: `docs/play-store/icon-source/app-foreground.svg`. Background: `app-background.svg`.
3. Options: marcar Legacy, Round y Google Play Store icon. Name `ic_launcher`. Finish.
4. Copiar `app/src/main/ic_launcher-playstore.png` a `docs/play-store/icon-512.png` (512×512, PNG, ≤ 1 MB).
5. Repetir con el módulo `driver` y los SVG `driver-*.svg`; copiar su `ic_launcher-playstore.png` a `docs/play-store/icon-512-driver.png`.
6. Verificar `<monochrome>` (ver arriba) y compilar: `.\gradlew :app:compileDebugKotlin :driver:compileDebugKotlin`.