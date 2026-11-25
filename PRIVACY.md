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

La copia de seguridad automática de Android en la nube está desactivada (`android:allowBackup="false"`). Puedes eliminar tu historial en cualquier momento desde **Ajustes → Limpiar historial de cálculos**, o borrar todos los datos locales desinstalando la aplicación o limpiando su almacenamiento en los ajustes del sistema.

---

## 3. Acceso a Internet (único uso)

CalcSuits declara únicamente el permiso `android.permission.INTERNET` y lo utiliza **exclusivamente** cuando accedes a la sección **Moneda** (o pulsas el botón de actualizar tasas) para descargar tipos de cambio públicos desde **ExchangeRate-API** (`https://open.er-api.com`) a través de HTTPS.

- CalcSuits no envía datos personales ni identificadores de usuario en dicha consulta.
- Como en cualquier solicitud HTTP estándar en Internet, el proveedor del servicio de tasas puede recibir información técnica básica de conexión (como tu dirección IP) sujeta a los términos y política de privacidad de [ExchangeRate-API](https://www.exchangerate-api.com).
- Si no tienes conexión o prefieres usar la app sin Internet, todos los módulos —incluido el conversor de monedas usando la última caché o tasas de referencia— siguen funcionando sin interrupciones.

---

## 4. Permisos del dispositivo

CalcSuits no solicita permisos sensibles ni de tiempo de ejecución (no accede a cámara, micrófono, ubicación, contactos ni almacenamiento externo).

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

- **No personal data collection:** CalcSuits does not collect, sell, share, or transmit personal data, analytics, crash telemetry, or advertising identifiers, and requires no account.
- **On-device storage only:** Calculation history (Room), preferences (DataStore), and cached currency exchange rates (DataStore) stay exclusively on your device (`android:allowBackup="false"`). You can clear history from **Settings** or remove all data by clearing app storage or uninstalling the app.
- **Single Internet usage:** The `INTERNET` permission is used solely to fetch public exchange rates over HTTPS from **ExchangeRate-API** (`https://open.er-api.com`) when using or refreshing the Currency converter. No personal or account data is sent by CalcSuits; standard network metadata (such as IP address) is subject to [ExchangeRate-API](https://www.exchangerate-api.com) terms.
- **No sensitive permissions:** CalcSuits requests no runtime permissions (no camera, microphone, location, contacts, or storage access).
