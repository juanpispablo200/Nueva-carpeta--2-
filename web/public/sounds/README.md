# Sonido de notificación

Coloca aquí un archivo MP3 llamado `notification.mp3` para las notificaciones de pedidos nuevos.

**Sugerencias de sonido:**
- Corto (1-2 segundos)
- Agradable y no molesto
- Volumen medio

**Dónde conseguir:**
- https://notificationsounds.com/
- https://freesound.org/ (buscar "notification")
- https://mixkit.co/free-sound-effects/notification/

O usa este comando para generar uno silencioso de prueba:
```bash
# Requiere ffmpeg instalado
ffmpeg -f lavfi -i "sine=frequency=800:duration=0.5" -af "volume=0.3" notification.mp3
```
