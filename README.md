# MIMI — App nativa Android (código fuente)

Hay dos formas de compilar el `.apk`. Si no tienes PC, usa la **Opción B (GitHub Actions)** —
todo se hace desde el navegador del celular y GitHub compila en la nube, gratis.

---

## Opción B: compilar sin PC, con GitHub Actions

1. **Crear el repositorio en GitHub** (desde el navegador del celular, con tu cuenta ya existente):
   - github.com → botón "+" → "New repository"
   - Nombre sugerido: `mimi-asistente`
   - Público o privado, cualquiera funciona
   - "Create repository"

2. **Subir los archivos manteniendo la estructura de carpetas.**
   La forma más confiable desde el celular es usar el editor web de GitHub (`github.dev`):
   - En tu repositorio recién creado, cambia la URL de `github.com` a `github.dev` (ejemplo: `github.dev/tu-usuario/mimi-asistente`)
   - Se abre un editor tipo VS Code en el navegador
   - Con el botón derecho (o el menú "..." en la barra lateral) → "New File" → escribe la ruta completa,
     por ejemplo `app/src/main/java/com/mimi/asistente/MainActivity.kt` — GitHub crea las carpetas automáticamente
   - Pega el contenido de cada archivo de este proyecto (los tienes en el zip que descomprimiste)
   - Repite para cada archivo: `build.gradle`, `settings.gradle`, `gradle.properties`, `app/build.gradle`,
     `app/src/main/AndroidManifest.xml`, los `.kt` de `app/src/main/java/com/mimi/asistente/`,
     los `.xml` de `app/src/main/res/layout/` y `app/src/main/res/values/`, y
     `.github/workflows/build.yml`
   - Ctrl+S (o el ícono de guardar) sube cada archivo directo al repositorio, sin necesidad de "commit" manual
   - **Alternativa más rápida si en algún momento consigues una PC o cabina un rato:** en la página normal de
     GitHub (no github.dev), "Add file" → "Upload files" permite arrastrar la carpeta `MimiApp` completa de una
     sola vez, respetando subcarpetas — mucho más rápido que archivo por archivo.

3. **Ejecutar la compilación:**
   - Pestaña "Actions" de tu repositorio
   - Verás el workflow "Compilar APK de MIMI" — clic en él
   - Botón "Run workflow" → "Run workflow" (de nuevo, para confirmar)
   - Espera unos 5-8 minutos (la primera vez tarda más porque descarga el SDK y el modelo de voz)

4. **Descargar el APK:**
   - Cuando el workflow termine con el ✅ verde, entra a esa ejecución
   - Al final de la página hay una sección "Artifacts" con un archivo `mimi-apk`
   - Descárgalo (es un `.zip` que contiene el `app-debug.apk` adentro) — se descarga directo en tu celular

5. **Instalar en el celular del adulto mayor:** igual que la Opción A, paso 6-7 más abajo.

*Nota:* este workflow descarga el modelo de voz de Vosk automáticamente en cada compilación —
no necesitas descargarlo tú ni copiarlo a mano como en la Opción A.

---

## Opción A: compilar con Android Studio en una PC/laptop

Proyecto de Android Studio completo y funcional. No viene compilado como `.apk`
porque generar ese binario requiere el SDK de Android y el modelo de voz de
Vosk, ninguno de los dos accesible desde el entorno donde escribí este código.
Compilarlo tú mismo toma unos 10-15 minutos la primera vez.

## Qué hace ya el código
- Escucha continua en segundo plano, incluso con pantalla bloqueada (servicio en primer plano)
- Reconocimiento de voz 100% offline con Vosk (sin internet, sin costo)
- Palabra de activación "Mimi" + comandos: pedir medicina, llamar a [nombre], me caí / emergencia, poner/pausar música
- Llamadas reales vía `Intent.ACTION_CALL`
- Control de música de cualquier app instalada vía señales de Media Session
- Botón físico de emergencia siempre visible en pantalla
- Reinicio automático del servicio si el celular se apaga y prende
- **Pantalla de Configuración de contactos**: un mismo `.apk` sirve para cualquier adulto mayor.
  Al visitarlo, abres MIMI → "Configurar contactos de confianza" → agregas nombre y número
  (marcando cuál es el de emergencia). Se guarda directo en ese celular, sin tocar código
  ni recompilar nada.

## Pasos para compilar el .apk

1. **Instalar Android Studio** (gratis): https://developer.android.com/studio

2. **Abrir este proyecto**: File → Open → seleccionar la carpeta `MimiApp`.
   Android Studio descargará automáticamente el Gradle y las librerías (necesita internet la primera vez).

3. **Descargar el modelo de voz en español**:
   - Ir a https://alphacephei.com/vosk/models
   - Descargar `vosk-model-small-es-0.42` (~40 MB, versión liviana, ideal para el piloto)
   - Descomprimir y renombrar la carpeta a `model-es`
   - Copiarla dentro de `app/src/main/assets/model-es`
   (Esta carpeta no está incluida en este proyecto porque el archivo pesa demasiado para entregarla aquí.)

4. **Compilar el APK** (ya no hace falta tocar código para los contactos — eso se hace después, en el celular de cada adulto mayor, paso 7):
   Build → Build App Bundle(s) / APK(s) → Build APK(s).
   El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

6. **Instalar en el celular de CADA adulto mayor** (mismo .apk para todos):
   - Copiar el `.apk` al celular (por USB, WhatsApp, o Google Drive)
   - Activar "Instalar apps de orígenes desconocidos" en Ajustes
   - Abrir el archivo `.apk` para instalar
   - Al abrir la app, aceptar los permisos de micrófono y llamadas

7. **Configurar los contactos de ESE adulto mayor, en su propio celular**:
   - Abrir MIMI → botón "⚙️ Configurar contactos de confianza"
   - Escribir nombre y número (con código de país, ej. +51987654321) de cada familiar
   - Marcar la casilla "Es el contacto de emergencia principal" en el que MIMI debe llamar si dice "me caí" o "ayuda"
   - Repetir este paso 7 en cada celular — es la única parte que cambia por persona; el .apk es siempre el mismo.

## Limitaciones que quedan pendientes (fase 2)
- El pedido de medicina solo se registra en el log (`requestMedicineOrder()`); falta conectarlo a un backend real (ej. Google Sheets, como en NutriCheck).
- El modelo de voz "small" reconoce bien comandos cortos, pero conviene probarlo con las voces reales de adultos mayores del piloto y ajustar las expresiones regulares en `CommandProcessor.kt` según los errores de transcripción que aparezcan.
- Si borras y reinstalas la app, se pierden los contactos guardados (viven en SharedPreferences, ligados a esa instalación).
