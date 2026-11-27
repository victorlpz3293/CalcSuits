# Fase 4.2 — Implementación OCR Local (`TexTeller3-ONNX q4f16` con KV-Cache) y Gráfico 2D Contextual

**Fecha:** 27 de noviembre de 2026  
**Estado:** Implementación completada y validada en compilación, pruebas unitarias/Robolectric y verificación empírica de KV-cache en CPU x86_64. Pendiente únicamente el *smoke test* final en dispositivo físico Android ARM64.  
**Prerrequisito cerrado:** [`docs/FASE_4_1_EVALUACION_OCR_Y_GRAFICO_2D.md`](FASE_4_1_EVALUACION_OCR_Y_GRAFICO_2D.md)

---

## 1. Alcance y preservación de arquitectura

- **Calculadora científica principal intacta:** No se modificaron `CalculatorScreen`, `CalculatorViewModel`, `CalculateExpressionUseCase`, `CalculatorRepository`, `CalculatorDatabase`, `HistoryDao`, `MainScreen`, `NavGraph`, `Screen` ni `MathExpressionEvaluator.kt`.
- **OCR exclusivamente en el Solucionador (`AiAssistantScreen`):** Sin servicios de IA externos, sin API keys y sin transmisión de imágenes ni expresiones fuera del dispositivo.
- **Reutilización estricta:** Se reutiliza `MathExpressionEvaluator` y `GraphScreen.kt` (`ContextualFunctionGraphCanvas`), manteniendo Clean Architecture, MVVM, Hilt y Material 3.
- **Resultados históricos intactos:** No se alteró ningún JSON en `poc/ocr/results/`. No se incluyeron modelos `.onnx` ni binarios pesados en el repositorio.

---

## 2. Motor OCR local y resolución de los 5 requisitos técnicos

### 2.1 Artefactos fijados con KV-cache real (`TexTeller3-ONNX q4f16`)

Se integró el modelo oficial **`onnx-community/TexTeller3-ONNX`** (licencia **Apache-2.0**, compatible con GNU GPL-3.0), fijado al commit **`ed90c6164810242882ca90863bb85697a76f4841`**. Sus pesos del encoder y tokenizer son bit-idénticos a la versión evaluada en Fase 4.1 y añade el decoder fusionado con soporte real de `past_key_values` (`use_cache_branch`).

| Archivo | Tamaño (bytes) | SHA-256 fijado |
| --- | ---: | --- |
| `encoder_model_q4f16.onnx` | `49,751,260` | `c42190515ffcd4a728a1abee4dc8d4636b62f782fd823294d2861434a7f7d0eb` |
| `decoder_model_merged_q4f16.onnx` | `152,805,414` | `1961768786dfe44227c8b8ccd4951cb300eb546c89d64070ab282285c30cf9ca` |
| `tokenizer.json` | `1,370,259` | `ec4ca954798a092faf6fefcfa47fb5f85d76cdf6ab170b624ae1a683d53dae14` |
| `tokenizer_config.json` | `592,769` | `74c8bdcd769acae371f98980fe929be1172fab3df844a50159c61998a3832eea` |

**Total descargable bajo demanda:** `204,519,702 B` (~195.0 MB), guardado en almacenamiento privado `filesDir/ocr_models/texteller3_q4f16/`.

### 2.2 Verificación empírica de KV-cache real (`past_key_values`)

- **Compatibilidad de operadores ONNX:** Inspeccionados los 26 operadores únicos del grafo `decoder_model_merged_q4f16.onnx` (`MatMulNBits`, `If`, `Concat`, `Softmax`, `Erf`, etc.) — 100% soportados por `com.microsoft.onnxruntime:onnxruntime-android:1.20.0`.
- **Benchmark A/B real en CPU (`onnxruntime-node 1.20.1`, Linux x86_64)** sobre capturas reales de Fase 4.1 (tokens generados bit-idénticos entre ambos decoders):

| Muestra real | Preprocesamiento | Sin KV-cache | Con KV-cache real | Aceleración | Salida LaTeX |
| --- | --- | ---: | ---: | ---: | --- |
| Manuscrita (`20261127_141401`) | `borderMedian=131`, `inverted=false` | `4,693 ms` | **`1,822 ms`** | **2.58x** | `\[\sqrt{2xy+x}=0\]` (11 tokens) |
| Libro 2D (`IMG-20261127-WA0019`) | `borderMedian=254`, `inverted=false` | `19,527 ms` | **`4,939 ms`** | **3.95x** | 41 tokens completos sin truncamiento |
| Modo oscuro (`Screenshot_20261127_134814`) | `borderMedian=17`, **`inverted=true`** | `6,809 ms` | **`1,984 ms`** | **3.43x** | `\[x^{ \wedge}2-5x+6=0\]` → `x^2 - 5 * x + 6 = 0` |

> **Nota técnica:** El tiempo en Android ARM64 con `XNNPACK+CPU` debe confirmarse en hardware físico con el APK generado (proyección desde Fase 4.1: `2.5–4.5 s` en fórmulas típicas gracias al factor `2.5x–3.9x` en decodificación y reducción de preprocesamiento por `inSampleSize`).

### 2.3 Resumen de los 5 requisitos técnicos implementados

1. **KV-cache real (`TexTellerOnnxEngine.kt`):** Inicializa 12 capas × 16 cabezas × 64 dimensiones (`past_key_values.{0..11}.{decoder,encoder}.{key,value}`), alternando `use_cache_branch=false` en el paso 0 (prefill) y `use_cache_branch=true` en pasos `>= 1` alimentando `present.*` clonados, cerrando determinísticamente cada `OrtSession.Result` y `OnnxTensor` intermedio. Límite por defecto: `80` tokens con guardia de repetición.
2. **Decodificación segura con `inSampleSize` (`OcrImageDecoder.kt`):** Doble pasada (`inJustDecodeBounds = true`), cálculo de potencia de 2 limitando el lado máximo a `<= 1024 px` antes del reescalado a `448×448`, límites duros (`<= 16,384 px`, `<= 100 MP`) y `Bitmap.recycle()` inmediato en `finally`.
3. **Inversión automática de polaridad (`TexTellerImagePreprocessor.kt`):** Calcula mediana de luminancia del borde perimetral en `O(w + h)` mediante histograma de 256 niveles; invierte automáticamente (`255 - lum`) cuando `borderMedian < 128` (pantallas en modo oscuro o pizarras oscuras).
4. **Liberación determinista de memoria nativa (`LocalOcrRepository.kt`, `SolverViewModel.kt`, `AiAssistantScreen.kt`):** `OrtSession.close()` garantizado al salir del Solucionador (`DisposableEffect` → `onScreenDisposed()`), en `ViewModel.onCleared()`, tras cancelación de inferencia y por *timeout* de inactividad de `60 s`.
5. **Confirmación editable obligatoria respaldada por AST (`MathIrNormalizer.kt`, `SolverViewModel.kt`, `SolverOcrPanels.kt`):** La salida OCR entra siempre en `OcrUiState.PendingConfirmation` con campo editable validado en vivo mediante AST (`MathIrNode`), preservando precedencia (`-2^2` → `-(2^2)`, `(-2)^2` → `(-2)^2`, `2^{3^2}` → `2^(3^2)` y `^{\wedge}` → `^`). Nunca evalúa ni grafica sin confirmación explícita del usuario.

### 2.4 Seguridad, permisos y privacidad

- **Descarga bajo demanda segura (`OcrModelManager.kt`, `Sha256Verifier.kt`):** Lista blanca estricta de URLs HTTPS fijadas por commit, descarga a `.part` con cálculo SHA-256 en flujo, rechazo y borrado inmediato ante cualquier corrupción o cancelación, y reverificación SHA-256 obligatoria antes de abrir sesiones ONNX.
- **Permisos mínimos verificados en APK (`aapt2 dump permissions`):**
  - `android.permission.INTERNET`
  - `android.permission.CAMERA` (solicitado bajo demanda solo al pulsar el botón de cámara; manejo de denegación temporal y permanente con acceso a Ajustes y alternativa directa de Galería)
  - **Cero** permisos amplios de almacenamiento (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`): la galería usa `ActivityResultContracts.PickVisualMedia`.
- **Limpieza garantizada de archivos temporales (`OcrTempFileManager.kt`, `CalcSuitsApp.kt`):** Las fotos temporales se guardan únicamente en `cacheDir/ocr_tmp/` vía `FileProvider` privado y se borran al finalizar, cancelar, fallar o al iniciar la app.

---

## 3. Gráfico 2D contextual (sin reincorporar al menú lateral)

- **`GraphExpressionAdapter.kt`:**
  - Adapta funciones `f(x)` y ecuaciones `LHS = RHS` → `(LHS) - (RHS)` (`isEquation = true`).
  - Oculta el botón contextual en expresiones no graficables (constantes, porcentajes, expresiones/ecuaciones multivariables).
  - Validación multipunto segura de dominio (corrige el defecto `x = 1.234` de Fase 4.1: permite `sqrt(-x)`, `ln(-x)`, `asin(x)`, `acos(x)`, `1/(x-1.234)` mientras sigue rechazando errores reales de sintaxis).
  - Pre-muestreo `sampleCurve(...)` ejecutado en `remember(...)` fuera de `Canvas.onDraw`.
- **`GraphScreen.kt` y `SolverOcrPanels.kt`:**
  - Extrae `ContextualFunctionGraphCanvas` reutilizado por `GraphScreen` y el `ModalBottomSheet` contextual del Solucionador.
  - Sincronización bidireccional `RAD`/`DEG` con `SettingsDataStore` en `SolverViewModel`, `AiAssistantScreen` y `ContextualGraphSheet`.
  - Áreas táctiles mínimas de `48.dp` en botones de zoom, reinicio, cambio angular, cámara, galería, confirmación y cierre.

---

## 4. Validación y artefacto entregable

### 4.1 Comandos ejecutados y resultados

| Verificación | Comando | Resultado |
| --- | --- | --- |
| Compilación Android | `compile_applet` | **PASS** (`BUILD SUCCESSFUL`) |
| Suite completa unit + Robolectric | `gradle :app:testDebugUnitTest --rerun-tasks` | **PASS — 156/156 tests (0 fallos, 0 errores, 0 omitidos)** |
| Empaquetado APK debug | `gradle :app:assembleDebug` | **PASS — `app/build/outputs/apk/debug/app-debug.apk` (`58 MB`)** |
| Auditoría de permisos APK | `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk` | **PASS — solo `INTERNET` y `CAMERA` (0 permisos de almacenamiento)** |

**Desglose de pruebas (`156` totales):**
- **136 pruebas existentes (regresión 100% verde):** `CalculatorViewModelTest` (5), `MathExpressionEvaluatorTest` (10), `CalculateLoanUseCaseTest` (27), `FinanceViewModelTest` (21), `Ipv4SubnettingEngineTest` (14), `ConvertUnitsUseCaseTest` (12), `ProgrammerViewModelTest` (9), `CalculateEngineeringUseCaseTest` (8), `ConverterViewModelTest` (7), `CurrencyRepositoryImplTest` (7), `ConvertBaseUseCaseTest` (6), `EngineeringViewModelTest` (6), `ThemePreferenceMigrationTest` (3), `ScreenNavigationTest` (1).
- **20 pruebas nuevas de Fase 4.2 (100% verde):**
  - `MathIrNormalizerTest` (5): precedencia `-2^2` vs `(-2)^2` y `2^(3^2)` contra `MathExpressionEvaluator` real, fracciones/raíces/trig LaTeX, variante modo oscuro `^{\wedge}`, porcentajes/multivariables y errores sintácticos.
  - `OcrSecurityAndPreprocessingTest` (8, Robolectric SDK 35 + `GraphicsMode.NATIVE`): verificación SHA-256 válida/corrupta/faltante/malformada, lista blanca HTTPS, decodificador ByteLevel BPE, cálculo `inSampleSize` y límites de dimensión, decodificación real de PNG grande y rechazo de archivo corrupto, inversión automática de polaridad en fondo oscuro vs papel blanco, limpieza de archivos temporales en `cacheDir/ocr_tmp/`, y ciclo de vida de `LocalOcrRepository` (borrado en `finally`, liberación por inactividad y cancelación).
  - `SolverViewModelTest` (3): confirmación obligatoria sin evaluación automática + edición AST, borrado de captura cancelada + liberación nativa en `onScreenDisposed()`, sincronización `RAD`/`DEG` y visibilidad contextual del gráfico 2D.
  - `GraphExpressionAdapterTest` (4): validación multipunto (`sqrt(-x)`, `ln(-x)`, `asin(x)`, `acos(x)`, `1/(x-1.234)`), rechazo de sintaxis inválida, adaptación `f(x)`/`LHS=RHS` y filtrado de no graficables, y diferenciación real `RAD` vs `DEG`.

### 4.2 APK entregable para prueba en teléfono

- **Ruta:** `app/build/outputs/apk/debug/app-debug.apk`
- **Tamaño:** `58 MB` (`arm64-v8a` + `x86_64`, sin pesos del modelo empaquetados)
- **SHA-256:** `08daec1131848f98bb959f5fc089694d02bc7d14f7d78567faf1bd0ea7c1278a`

### 4.3 Validado localmente vs pendiente en hardware físico Android ARM64

- **Validado localmente:** Compilación completa, 156/156 pruebas unitarias y Robolectric (incluyendo Skia nativo para `BitmapFactory` y `Canvas`), auditoría de permisos del APK y aceleración real KV-cache (`2.58x–3.95x`) en `onnxruntime-node 1.20.1` sobre fotos reales manuscritas, impresas y en modo oscuro.
- **Pendiente de *smoke test* en teléfono físico ARM64:** Descarga HTTPS real (~195 MB) del modelo desde la UI del Solucionador, medición de latencia real en ARM64 (`XNNPACK+CPU`) con el decoder KV-cache en el APK final y captura real con cámara física.
