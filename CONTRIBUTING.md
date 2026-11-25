# Contribuir a CalcSuits

¡Gracias por tu interés en contribuir a **CalcSuits**! Esta guía describe cómo reportar problemas, proponer mejoras y enviar Pull Requests manteniendo la estabilidad y calidad del proyecto.

---

## 1. Reportar Issues

Antes de abrir un nuevo issue, revisa si ya existe uno similar. Al reportar un error o proponer una mejora, incluye:

- **Descripción clara** del problema o funcionalidad solicitada.
- **Pasos para reproducir** el comportamiento (en caso de bug).
- **Resultado esperado vs. resultado obtenido**.
- **Entorno:** versión de Android, modelo de dispositivo o emulador y rama/commit.
- **Capturas de pantalla o logs** (`logcat` / stacktrace) cuando apliquen.

---

## 2. Flujo para Pull Requests

1. Haz un **fork** del repositorio y clona tu copia local.
2. Crea una rama descriptiva desde `main` (por ejemplo: `fix/ohm-law-validation` o `feat/converter-pressure`).
3. Mantén tus cambios **acotados a un solo propósito**:
   - Respeta la arquitectura del proyecto (`com.calcsuits`, Clean Architecture + MVVM, Jetpack Compose + Material 3).
   - No mezcles cambios de UI/UX con refactorizaciones del motor matemático salvo necesidad técnica justificada.
   - Agrega o actualiza pruebas unitarias cuando modifiques lógica de cálculo, casos de uso o ViewModels.
4. Usa mensajes de commit claros y descriptivos (por ejemplo, estilo Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`).
5. Abre el **Pull Request** describiendo el problema resuelto, los cambios realizados y la evidencia de validación.

---

## 3. Validación obligatoria

Antes de abrir o actualizar un Pull Request, **debes** ejecutar y verificar en verde ambos comandos:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

- `assembleDebug` verifica que el proyecto completo, recursos, Room, Navigation y Hilt compilen sin errores.
- `testDebugUnitTest` verifica que toda la suite de pruebas unitarias (incluido el motor `MathExpressionEvaluator`) pase sin regresiones.

No se aceptarán Pull Requests que rompan la compilación o las pruebas unitarias.

---

## 4. Reglas de UI

### Ergonomía de scroll (regla global)

Toda pantalla de CalcSuits —presente y futura— debe respetar la siguiente regla de diseño:

> **Lo que da contexto a una función (pestañas o selector de modo, tarjetas de valor/entrada principal, resultado principal) permanece fijo arriba. Solo se desplaza el contenido secundario que sobra (listas largas, detalles, tablas). Las pantallas cuyo contenido cabe en la altura disponible no deben hacer scroll. Una pantalla NO debe envolverse entera en un único `verticalScroll` si tiene encabezado de contexto.**

Lineamientos de implementación:

- **Usa `CalcPinnedScaffold`** (`ui/components/CalcPinnedScaffold.kt`) en toda pantalla que tenga pestañas, selector de modo o tarjetas de contexto arriba y contenido secundario desplazable debajo. Evita repetir la combinación manual de `Scaffold` + `Column` + `verticalScroll`.
- **Conserva insets e IME**: `CalcPinnedScaffold` aplica `consumeWindowInsets(padding)` y `imePadding()` de forma consistente, y ofrece un fallback automático cuando la altura disponible es muy reducida (horizontal o teclado del sistema abierto) para que ningún control quede cortado ni inalcanzable.
- **Pantallas de teclado / canvas**: reparte la altura restante con `weight(1f)` por fila o área principal para evitar scroll en alturas estándar (`~640dp`–`900dp`), habilitando scroll únicamente sobre el bloque secundario/teclado cuando no cabe.
- **Excepciones válidas**: pantallas que son exclusivamente listas largas de secciones independientes sin encabezado de contexto (como **Ajustes**) o sheets/conversaciones con barra fija y lista interna (`LazyColumn`, como **Historial** y **Solucionador**).

---

## 5. Versionado y generación del AAB de release

### Cómo actualizar la versión en cada release

La versión de la app se define en un único lugar: `app/build.gradle.kts`, dentro de `android.defaultConfig`:

```kotlin
versionCode = 1
versionName = "1.0.0"
```

- **`versionCode`:** debe ser siempre un número entero estrictamente creciente en cada publicación (`1`, `2`, `3`, …). Google Play rechaza cualquier AAB cuyo `versionCode` sea menor o igual al de un lanzamiento anterior.
- **`versionName`:** sigue Versionado Semántico (`MAJOR.MINOR.PATCH`, por ejemplo `"1.0.0"`, `"1.0.1"`, `"1.1.0"`). La pantalla **Ajustes → Acerca de** lee este valor automáticamente desde `BuildConfig.VERSION_NAME`.

### Cómo generar el AAB de release

El paquete firmado para Google Play se genera exclusivamente mediante GitHub Actions usando el workflow `.github/workflows/release.yml`:

1. En GitHub, abre **Actions → Release → Run workflow** (o haz push de un tag con prefijo `v*`, por ejemplo `v1.0.0`).
2. El workflow ejecuta las pruebas unitarias (`testDebugUnitTest`), compila y firma el bundle (`bundleRelease`), verifica la firma con `jarsigner` y muestra la huella pública SHA-256 del certificado.
3. Al finalizar en verde, entra en la ejecución del workflow y descarga los artefactos (`release-aab` con el archivo `.aab` firmado y `release-mapping` con `mapping.txt`, disponibles durante 30 días).

> **Advertencia de seguridad:** el keystore de subida (`.jks` / `.keystore`) y sus contraseñas **jamás deben subirse al repositorio**, ni incluirse en commits, PRs, logs o artefactos. Toda la firma de producción depende únicamente de los secretos configurados en GitHub Actions (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`).

---

## 6. Licencia de las contribuciones

Al enviar una contribución a **CalcSuits**, aceptas que tu código se distribuya bajo la misma licencia del proyecto: **GNU General Public License v3.0 (GPL-3.0)** (ver [LICENSE](LICENSE)).
