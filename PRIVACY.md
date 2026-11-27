# Política de Privacidad — CalcSuits

**Última actualización:** noviembre de 2026

En **CalcSuits** respetamos tu privacidad. Esta política explica de forma clara qué datos maneja la aplicación y cómo utiliza el acceso a Internet.

---

## 1. Datos que recopilamos

**CalcSuits no recopila, transmite ni almacena datos personales en servidores propios ni de terceros.**

En particular, la aplicación:
- No solicita registro, inicio de sesión, correo electrónico ni datos de identificación.
- No integra SDKs de analítica, seguimiento (*tracking*), telemetría ni redes de publicidad.
- No recopila ubicación, contactos, fotos, archivos ni identificadores publicitarios del dispositivo.

---

## 2. Almacenamiento local en tu dispositivo

Toda la información generada mientras usas CalcSuits permanece exclusivamente en tu dispositivo:
- **Historial de cálculos:** guardado localmente mediante Room (SQLite).
- **Preferencias de usuario** (tema, idioma, precisión decimal, unidad angular, vibración): guardadas localmente mediante Jetpack DataStore.
- **Caché de tasas de cambio:** las últimas tasas consultadas y su fecha de actualización se almacenan localmente en DataStore para permitir el uso sin conexión.
- **Modelo OCR local (opcional, bajo demanda):** si decides activar el escaneo de fórmulas dentro del Solucionador, los archivos del modelo `TexTeller3-ONNX` (~195 MB) se guardan en el almacenamiento privado de la app (`filesDir/ocr_models/`) tras verificar su integridad SHA-256. Todas las inferencias OCR se ejecutan 100% offline en tu dispositivo. Las fotos capturadas temporalmente en `cacheDir/ocr_tmp/` se eliminan automáticamente al finalizar, cancelar o reiniciar la app.

La copia de seguridad automática de Android en la nube está desactivada (`android:allowBackup="false"`). Puedes eliminar tu historial en cualquier momento desde **Ajustes → Limpiar historial de cálculos**, o borrar todos los datos locales (incluido el modelo OCR si lo descargaste) desinstalando la aplicación o limpiando su almacenamiento en los ajustes del sistema.

---

## 3. Acceso a Internet (usos estrictamente limitados)

CalcSuits declara el permiso `android.permission.INTERNET` y lo utiliza **únicamente** en dos casos opcionales:

1. **Tasas de cambio (sección Moneda):** al consultar o actualizar divisas desde **ExchangeRate-API** (`https://open.er-api.com`) por HTTPS.
2. **Descarga única bajo demanda del modelo OCR local (sección Solucionador):** solo si pulsas descargar el modelo `TexTeller3-ONNX` desde Hugging Face (`https://huggingface.co/onnx-community/TexTeller3-ONNX`) por HTTPS con verificación estricta SHA-256. Una vez descargado, el reconocimiento OCR funciona completamente sin conexión.

- CalcSuits **nunca** envía tus imágenes, fotografías, fórmulas matemáticas ni datos personales a servidores propios ni de terceros.
- Como en cualquier solicitud HTTPS estándar en Internet, el proveedor del servicio puede recibir metadatos técnicos básicos de conexión (como tu dirección IP) sujetos a sus políticas ([ExchangeRate-API](https://www.exchangerate-api.com), [Hugging Face](https://huggingface.co/privacy)).
- Si prefieres usar la app sin Internet, la calculadora, el solucionador determinista, el graficador 2D contextual y el OCR (una vez descargado el modelo) funcionan 100% sin conexión.

---

## 4. Permisos del dispositivo

- **`android.permission.CAMERA` (opcional y bajo demanda):** se solicita únicamente cuando pulsas el botón de cámara dentro del **Solucionador** para fotografiar una fórmula matemática. Si lo deniegas, puedes seguir usando la app normalmente o seleccionar imágenes con el **Android Photo Picker** del sistema (que no requiere permisos de almacenamiento).
- **Sin permisos amplios de almacenamiento:** CalcSuits nunca solicita `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES` ni `READ_MEDIA_VIDEO`. Tampoco accede a micrófono, ubicación ni contactos.

---

## 5. Privacidad de menores

Dado que CalcSuits no recopila datos personales de ningún tipo, es segura para usuarios de todas las edades.

---

## 6. Cambios a esta política

Si en futuras versiones se incorporan funciones opcionales que impliquen nuevos permisos o servicios de red, esta política se actualizará antes de su publicación indicando la nueva fecha de vigencia.

---

## 7. Contacto

Si tienes preguntas sobre esta Política de Privacidad o sobre el tratamiento de datos en CalcSuits, puedes abrir un *issue* en el repositorio oficial del proyecto o consultar la documentación en [README.md](README.md).

---

# Privacy Policy — CalcSuits (English Summary)

**Last updated:** November 2026

- **No personal data collection:** CalcSuits does not collect, sell, share, or transmit personal data, images, formulas, analytics, crash telemetry, or advertising identifiers, and requires no account.
- **On-device storage & local OCR:** Calculation history (Room), preferences (DataStore), cached currency exchange rates (DataStore), and the optional on-demand local OCR model (`TexTeller3-ONNX`, verified via SHA-256 in private `filesDir`) stay exclusively on your device (`android:allowBackup="false"`). Temporary camera captures in `cacheDir/ocr_tmp/` are deleted automatically after processing, cancellation, or app restart.
- **Limited HTTPS usage:** `INTERNET` is used solely to fetch public exchange rates from **ExchangeRate-API** (`https://open.er-api.com`) and, only if requested by the user, for the one-time HTTPS download of the offline `TexTeller3-ONNX` model from Hugging Face. No user images or expressions are ever uploaded.
- **Least-privilege permissions:** `CAMERA` is requested strictly on demand when tapping the camera scan button inside the Solver. Gallery selection uses the zero-permission Android Photo Picker (`PickVisualMedia`). CalcSuits never requests storage, location, microphone, or contacts permissions.
