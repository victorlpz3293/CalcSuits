# CALCSUITS — FASE 4.1
## Evaluación Técnica y Prueba de Concepto OCR Local + Auditoría del Antiguo Gráfico 2D

- **Proyecto:** CalcSuits (`com.calcsuits`, Kotlin + Jetpack Compose + Material 3 + Clean Architecture + MVVM + Hilt)
- **Licencia del proyecto:** GNU GPL-3.0 (`LICENSE`, `THIRD_PARTY_NOTICES.md`)
- **Referencia de repositorio solicitada:** `victorlpz3293/CalcSuits`, rama `main`, commit `dbbacbe273ecf2a3adf0e9ea5b39f7e8e716c783`
- **Verificación Git en este entorno:** `[PENDIENTE / BLOQUEADO POR ENTORNO]` — el workspace local del contenedor no contiene directorio `.git` (`ls .git` inexistente), por lo que el SHA de commit y la rama activa no son verificables localmente mediante comandos Git.
- **Estado de la Fase 4.1:** **PARCIAL / ABIERTA** (consolidación documental y POC de contenedor CPU completadas; permanecen bloqueadas las mediciones en Android ARM64 físico, escritura manuscrita real con cámara y validación en dispositivo real antes de autorizar la Fase 4.2).
- **Control de alcance:** Ningún archivo de producción (`app/`, `gradle/`, `settings.gradle.kts`, `build.gradle.kts`, `PRIVACY.md`, `README.md`, `ROADMAP.md`, `THIRD_PARTY_NOTICES.md`) fue modificado. No se integró OCR ni Gráfico 2D en producción.

---

## 1. Clasificación de Evidencias y Advertencia Metodológica

Para evitar mezclar mediciones reales con estimaciones o propuestas, todas las afirmaciones de este documento siguen cuatro etiquetas estrictas:

| Etiqueta | Significado |
|---|---|
| `[EJECUTADO / MEDIDO EN CONTENEDOR CPU]` | Medición empírica real ejecutada en este contenedor Linux `x86_64` (4 vCPU, gVisor 4.19.0) con artefactos JSON persistidos e inalterados en `poc/ocr/results/`. **NO equivale a rendimiento en Android ARM64 físico.** |
| `[DEFINIDO / PROPUESTO]` | Especificación técnica, código/XML o diseño arquitectónico documentado para revisión previa a Fase 4.2; **no aplicado** en producción. |
| `[DOCUMENTAL / ESTIMADO]` | Datos obtenidos de repositorios oficiales, Maven Central, Hugging Face o literatura técnica, sin ejecución local en Android. |
| `[PENDIENTE / BLOQUEADO POR ENTORNO]` | Prueba requerida para el cierre definitivo de la decisión técnica que no puede ejecutarse en un contenedor sin dispositivo Android ARM64, cámara física, ADB ni `.git`. |

> **Advertencia crítica sobre plataformas:** Todas las latencias (`ms`) y consumos de RAM (`RSS MB`) medidos en la POC corresponden a ejecución CPU en contenedor `x86_64` bajo Node.js (`tesseract.js` WASM y `onnxruntime-node` C++ CPU). **No deben interpretarse como tiempos ni memorias de Android ARM64** (donde intervienen XNNPACK/NNAPI, límites de memoria por proceso Android, throttling térmico y diferencias de arquitectura CPU).

---

## 2. Metodología y Reproducibilidad de la POC OCR

### 2.1 Expresiones obligatorias y diseño del dataset

Se evaluaron las **7 expresiones matemáticas obligatorias** definidas para la Fase 4.1:

| ID | Expresión visual | Forma canónica CalcSuits | LaTeX esperado | Comportamiento Solver esperado |
|---|---|---|---|---|
| `expr_01_arith_mul` | `125 + 35 × 2` | `125 + 35 * 2` | `125 + 35 \times 2` | Evaluar → `195` |
| `expr_02_div_dec` | `12.75 / 0.5` | `12.75 / 0.5` | `\frac{12.75}{0.5}` | Evaluar → `25.5` |
| `expr_03_poly_sq` | `x² + 5x + 6` | `x^2 + 5 * x + 6` | `x^{2} + 5x + 6` | Variable libre (graficable) |
| `expr_04_sqrt` | `√144 + 5` | `sqrt(144) + 5` | `\sqrt{144} + 5` | Evaluar → `17` |
| `expr_05_grouped_frac` | `(3x + 2) / 5` | `(3 * x + 2) / 5` | `\frac{3x + 2}{5}` | Variable libre (graficable) |
| `expr_06_linear_eq` | `2x + 5 = 15` | `2 * x + 5 = 15` | `2x + 5 = 15` | Resolver ecuación lineal → `x = 5` |
| `expr_07_percent_es` | `15% de 250` | `15% de 250` | `15\% \text{ de } 250` | Resolver porcentaje → `37.5` |

Cada motor ejecutado se evaluó sobre **88 imágenes** en total:
- **67 imágenes base** (`poc/ocr/generate_dataset.py`):
  - `49` impresas lineales (`7` expresiones × `7` variantes: `printed_clean_sans`, `printed_serif`, `printed_large` [78 pt], `printed_mono_small` [28 pt], `printed_low_light`, `printed_tilted`, `printed_blur_noise`).
  - `4` impresas en maquetación matemática 2D (`printed_2d_typeset`: fracción vertical `12.75 / 0.5`, superíndice elevado `x² + 5x + 6`, radical con vínculum `√144 + 5`, fracción vertical agrupada `(3x + 2) / 5`).
  - `14` **manuscritas sintéticas** (`handwritten_synth_clear` y `handwritten_synth_difficult` con trazos perturbados, inclinación y líneas de cuaderno). **Etiquetadas explícitamente como sintéticas**: no sustituyen fotografías reales de escritura humana capturadas con cámara.
- **21 imágenes preprocesadas localmente** (`poc/ocr/preprocess_local.py`):
  - Aplicado sobre las 3 variantes degradadas (`printed_low_light`, `printed_blur_noise`, `handwritten_synth_difficult`): conversión a gris, `auto-level`, `contrast-stretch 1%x1%`, umbral adaptativo local (`-lat 25x25+10%`), `despeckle`, recorte con `fuzz 10%` y borde blanco.

### 2.2 Dependencias, versiones fijadas y hashes SHA-256

**Entorno de ejecución aislado (`/tmp/poc_ocr_env`, 100 % fuera de Gradle y `app/`):**
- Sistema: `Linux x86_64`, `4 vCPU`, kernel `4.19.0-gvisor`
- Node.js: `v22.23.4`
- Python: `3.11.2`
- ImageMagick: `6.9.11-60 Q16 x86_64`
- Paquetes npm exactos (`package-lock.json` en `/tmp/poc_ocr_env`):
  - `tesseract.js@5.1.1`
  - `onnxruntime-node@1.20.1` (instalado con `--onnxruntime-node-install-cuda=skip`)
  - `@huggingface/transformers@3.4.0`
  - `pngjs@7.0.0`

**Revisiones fijadas y hashes SHA-256 de TODOS los artefactos consumidos (pesos, tokenizadores y JSON auxiliares):**

| Motor | Repositorio / Revisión fijada | Archivo | Tamaño | SHA-256 |
|---|---|---|---:|---|
| Tesseract 5 LSTM | `cdn.jsdelivr.net/npm/@tesseract.js-data/eng/4.0.0_best_int` (`tesseract.js@5.1.1`) | `eng.traineddata` (descomprimido) | `4.96 MB` (`5,199,098 B`) | `5dc5d8d640a212c9d6184921ba103b186f50e0fed9ee716c53e6b312b400d747` |
| RapidLaTeXOCR | `RapidAI/RapidLaTeXOCR@v0.0.0` (Release ID `112346722`, 2023-07-15; export de `lukas-blecher/LaTeX-OCR@1781514fb8c92ea9f94057295fdae0e683f4648e`) | `image_resizer.onnx` | `37.16 MB` | `e0b075c39700f64d50400f39c8fc186bbb3b5d84d31864008313f376603aca9d` |
| RapidLaTeXOCR | mismo release `v0.0.0` | `encoder.onnx` | `84.88 MB` | `01bf5dc25539ca0cd5b1bd29296ea495977a6ba5f629dc4178277809d26e5e7d` |
| RapidLaTeXOCR | mismo release `v0.0.0` | `decoder.onnx` | `48.59 MB` | `bd695497bf1b22279b7626f5916c79226e1e244c84355f8da7edfd2d921d0072` |
| RapidLaTeXOCR | mismo release `v0.0.0` | `tokenizer.json` | `0.02 MB` | `1dc27b18d6a518d0d5ff3f4bb7bd98521fe80ad39e5b2a246d4109f1bb9d5019` |
| Xenova/texify (`q8`) | `Xenova/texify@98b3e3d88921ae91525d116d8d79a8402e5b5e4e` | `onnx/encoder_model_quantized.onnx` | `~90 MB` | `302452c132a82b1c70389f0f646952586afa20c3f9a16b517e40600c49eb8f23` |
| Xenova/texify (`q8`) | misma revisión | `onnx/decoder_model_merged_quantized.onnx` | `~216 MB` | `ed067534a996c818708d0d377b94ace32b9738f0d7a0c4ef635e4fa3d28653ce` |
| Xenova/texify (`q8`) | misma revisión | `tokenizer.json` | — (`306.46 MB` total en caché) | `02c318d9cfa95bf323371762b8f838a82709530274d36dba6eca880f0add6cc4` |
| Xenova/texify (`q8`) | misma revisión | `config.json` | auxiliar | `e1c4e95573d80a1816d8d1e00fcc9f71961226369877b4fd392708fe7d2e9e23` |
| Xenova/texify (`q8`) | misma revisión | `preprocessor_config.json` | auxiliar | `b2fc6ff0098ac8e6af6e688bfc28188db883c99a48afa60e8555faeedc0c45e4` |
| Xenova/texify (`q8`) | misma revisión | `generation_config.json` | auxiliar | `0fb8980236d664d2851ed5939b435ecc6fe0e32ee08b83e75cb1743db0cfe10e` |
| Xenova/texify (`q8`) | misma revisión | `tokenizer_config.json` | auxiliar | `fd4d94f8b9dbb7deeb3a3ef084ca0e16c43d45774a180730b7e9cfd6359a074b` |
| TexTeller-ONNX (`q4f16`) | `onnx-community/TexTeller-ONNX@9727784d91d7f8437dc7140941c4335284ce075e` | `onnx/encoder_model_q4f16.onnx` | `47.45 MB` | `c42190515ffcd4a728a1abee4dc8d4636b62f782fd823294d2861434a7f7d0eb` |
| TexTeller-ONNX (`q4f16`) | misma revisión | `onnx/decoder_model_q4f16.onnx` | `145.13 MB` | `edebc4c90596d13313382eecfeef89f0041efe7bfa4e89391d5b5dbabab25100` |
| TexTeller-ONNX (`q4f16`) | misma revisión | `tokenizer.json` | `0.50 MB` | `ec4ca954798a092faf6fefcfa47fb5f85d76cdf6ab170b624ae1a683d53dae14` |
| TexTeller-ONNX (`q4f16`) | misma revisión | `tokenizer_config.json` | `0.57 MB` | `74c8bdcd769acae371f98980fe929be1172fab3df844a50159c61998a3832eea` |

**Integridad pre-carga y ejecución 100 % offline verificada (`poc/ocr/test_offline_integrity.mjs` — `6/6 PASS`):**
- Los 4 runners (`run_onnx_math_poc.mjs`, `run_texteller_direct_poc.mjs`, `run_rapidlatexocr_poc.mjs`, `run_tesseract_poc.mjs`) verifican el SHA-256 de **cada archivo consumido** (pesos y configuraciones JSON auxiliares) **antes** de instanciar `OrtSession`, `pipeline()`, `AutoTokenizer` o `createWorker()`.
- Rechazan archivos corruptos (1 byte alterado), ausentes, hashes vacíos/inválidos, variables de entorno de bypass (`HF_MODEL_REVISION`, `HF_ENDPOINT`) y archivos shadow no versionados en `env.localModelPath`.
- Ejecución real **100 % offline** probada con `globalThis.fetch`, `http.request` y `https.request` bloqueados (`OFFLINE_NETWORK_BLOCKED`, `0` llamadas salientes) tanto en `TexTeller-ONNX (q4f16)` (`2 * x + 5 = 15`) como en `Tesseract 5` (`125 + 35 x 2`).

**Corrección de reproducibilidad determinista del dataset:**
- Se corrigieron dos fuentes reales de no-determinismo en `poc/ocr/generate_dataset.py`:
  1. Se sustituyó `hash((expr["id"], difficult))` (dependiente de `PYTHONHASHSEED`) por semillas derivadas mediante `SHA-256` a partir de `DATASET_SEED = 20261127`.
  2. Se añadió `-seed <int>` determinista antes de `+noise Gaussian` en ImageMagick y `-strip -define png:exclude-chunk=date,time` en la escritura PNG.
- Verificado automáticamente en `poc/ocr/test_ocr_validation.mjs`: `PYTHONHASHSEED=1` vs. `PYTHONHASHSEED=42` produce **67/67 PNG idénticos bit a bit** cubriendo **10 resoluciones reales** (`440x220` a `880x220`).

---

## 3. Resultados Verificables de la POC OCR

Todos los resultados siguientes provienen directamente de los **8 archivos JSON íntegros** en `poc/ocr/results/` (con `texteller_q4f16_base.json` [67/67] y `texteller_q4f16_preprocessed.json` [21/21] ejecutados directamente sobre el dataset determinista con verificación SHA-256 pre-carga activa):

| Artefacto JSON | Registros | SHA-256 |
|---|---:|---|
| `poc/ocr/results/tesseract_base.json` | `67/67` | `057708833d4d5ed4c21402c662a3b68d188c11834afc5502fd47d38c3e025f8a` |
| `poc/ocr/results/tesseract_preprocessed.json` | `21/21` | `e063d713f85a5ea38d26785edec0ce4ce56220dc4d1a1aa0a105ab1ed5e85b0d` |
| `poc/ocr/results/rapidlatexocr_base.json` | `67/67` | `e944703d54bf89906e64cace608f1a096161742822b4c13fa88bbcac110ccd9a` |
| `poc/ocr/results/rapidlatexocr_preprocessed.json` | `21/21` | `8a890c8fb91a175b15db4c210a51516c2cfcc59ebcd01b466685d484a3b63dc5` |
| `poc/ocr/results/texify_q8_base.json` | `67/67` | `7dc978a421786403e1420e96a7b52ab599ea4225620f3e7dcc86ded99e98c859` |
| `poc/ocr/results/texify_q8_preprocessed.json` | `21/21` | `08661d3a2c21f2ea38b2d39384b37c2010b6e1dfacade632c73edde43ad16505` |
| `poc/ocr/results/texteller_q4f16_base.json` | `67/67` | `96897adfd337ed2e059d81ed0bcd55d6da2f2d27ce82f870273d758222959cd6` |
| `poc/ocr/results/texteller_q4f16_preprocessed.json` | `21/21` | `148864121050b599fe1b9103bd3ae7650cb9992ef85736cceef715de1f2a27bf` |

### 3.1 Tabla comparativa general (`[EJECUTADO / MEDIDO EN CONTENEDOR CPU]` vs. `[DOCUMENTAL]`)

| Motor | Licencia / Compatibilidad GPL-3.0 | Peso modelo / runtime | Exactitud base (`67` img) | Impreso lineal (`49`) | 2D maquetado (`4`) | Manuscrito sintético (`14`) | Latencia base `min / p50 / p95 / max` (ms, CPU x86_64) | Pico RSS (MB, CPU x86_64) |
|---|---|---|---:|---:|---:|---:|---|---:|
| **Tesseract 5 LSTM** (`eng`, WASM) | Apache-2.0 — **Compatible** | `4.96 MB` (`eng.traineddata`) | `30/67` (**44.8 %**) | `24/49` (49.0 %) | `0/4` (0.0 %) | `6/14` (42.9 %) | `20 / 58 / 151 / 256` | `228` |
| **RapidAI/RapidLaTeXOCR** (Pix2Tex ONNX FP32) | MIT (código); pesos arXiv/LaTeX requieren cautela de redistribución | `170.66 MB` FP32 (+ ~13–15 MB/ABI ORT) | `20/67` (**29.9 %**) | `15/49` (30.6 %) | `3/4` (75.0 %) | `2/14` (14.3 %) | `283 / 607 / 9120 / 14201` | `483` |
| **Xenova/texify (`q8`)** (Transformers.js ONNX) | Código GPL-3.0; pesos **CC-BY-SA-4.0** (fricción con GPL-3.0) | `306.46 MB` caché ONNX q8 | `54/67` (**80.6 %**) | `38/49` (77.6 %) | `4/4` (100.0 %) | `12/14` (85.7 %) | `826 / 1134 / 3293 / 4401` | `1404` |
| **TexTeller-ONNX (`q4f16`)** (ONNX directo, `BOS=0`) | **Apache-2.0** (código y pesos) — **Compatible GPL-3.0** | `192.57 MB` ONNX (+ `17.63 MB` `arm64-v8a` ORT `1.20.0`) | `59/67` (**88.1 %**) | `41/49` (83.7 %) | `4/4` (100.0 %) | `14/14` (100.0 %) | `4248 / 5632 / 11951 / 18280` | `918` |
| **Google ML Kit Text Rec. v2** `[DOCUMENTAL / BLOQUEADO]` | **Propietaria** (ML Kit ToS prohíbe ingeniería inversa → **conflicto GPL-3.0**) | ~4–10 MB bundle / Play Services `[DOC]` | No ejecutado | No ejecutado | OCR lineal (sin estructura 2D) `[DOC]` | No ejecutado | No medido | No medido |

> **Nota sobre tamaño de `onnxruntime-android` `[VERIFICADO EN BINARIO AAR]`:** `com.microsoft.onnxruntime:onnxruntime-android:1.20.0` (MIT) pesa `26.58 MB` multi-ABI en Maven Central; su binario `arm64-v8a` pesa **`16.76 MB`** (`libonnxruntime.so`) + **`0.87 MB`** (`libonnxruntime4j_jni.so`) = `17.63 MB` sin comprimir (el APK ARM64 aislado `poc/ocr/android/app/build/outputs/apk/debug/app-debug.apk` pesa **`21 MB`** total). Como comparación, `onnxruntime-android:1.32.0` pesa `56.59 MB` multi-ABI (`34.63 MB` solo `arm64-v8a/libonnxruntime.so`).
> **Nota sobre `Tesseract eng+equ`:** `equ.traineddata` (~2.15 MB) solo existe para Tesseract 3 legacy (OEM 0), no para los modelos LSTM de Tesseract 4/5.

### 3.2 Métricas granulares sobre el dataset base (`67` imágenes, `[EJECUTADO / MEDIDO EN CONTENEDOR CPU]`)

| Motor | Símbolos (`67`) | Exponentes (`10`) | Fracciones (`20`) | Raíces (`10`) | Agrupación (`10`) |
|---|---:|---:|---:|---:|---:|
| **Tesseract 5 LSTM** | `50/67` (74.6 %) | `0/10` (0.0 %) | `10/20` (50.0 %) | `0/10` (0.0 %) | `5/10` (50.0 %) |
| **RapidAI/RapidLaTeXOCR** | `39/67` (58.2 %) | `1/10` (10.0 %) | `9/20` (45.0 %) | `3/10` (30.0 %) | `3/10` (30.0 %) |
| **Xenova/texify (`q8`)** | `64/67` (95.5 %) | `9/10` (90.0 %) | `18/20` (90.0 %) | `4/10` (40.0 %) | `10/10` (100.0 %) |
| **TexTeller-ONNX (`q4f16`)** | `66/67` (**98.5 %**) | `10/10` (**100.0 %**) | `18/20` (**90.0 %**) | `8/10` (**80.0 %**) | `9/10` (**90.0 %**) |

### 3.3 Desglose por variante (`67` base) y efecto del preprocesamiento local (`21` degradadas)

| Variante (`n`) | Tesseract 5 | RapidLaTeXOCR | Texify `q8` | TexTeller `q4f16` |
|---|---:|---:|---:|---:|
| `printed_clean_sans` (`7`) | `5/7` (71.4 %) | `3/7` (42.9 %) | `6/7` (85.7 %) | `7/7` (100.0 %) |
| `printed_serif` (`7`) | `3/7` (42.9 %) | `2/7` (28.6 %) | `7/7` (100.0 %) | `7/7` (100.0 %) |
| `printed_large` (`7`) | `5/7` (71.4 %) | `3/7` (42.9 %) | `6/7` (85.7 %) | `7/7` (100.0 %) |
| `printed_mono_small` (`7`) | `5/7` (71.4 %) | `0/7` (0.0 %) | `4/7` (57.1 %) | `6/7` (85.7 %) |
| `printed_tilted` (`7`) | `4/7` (57.1 %) | `2/7` (28.6 %) | `5/7` (71.4 %) | `7/7` (100.0 %) |
| `printed_2d_typeset` (`4`) | `0/4` (0.0 %) | `3/4` (75.0 %) | `4/4` (100.0 %) | `4/4` (100.0 %) |
| `handwritten_synth_clear` (`7`) | `5/7` (71.4 %) | `2/7` (28.6 %) | `6/7` (85.7 %) | `7/7` (100.0 %) |
| `printed_low_light` (`7`) — **Antes → Después** | `0% → 71.4%` (`+71.4 pp`) | `0% → 71.4%` (`+71.4 pp`) | `57.1% → 71.4%` (`+14.3 pp`) | `71.4% → 100.0%` (`+28.6 pp`) |
| `printed_blur_noise` (`7`) — **Antes → Después** | `28.6% → 57.1%` (`+28.6 pp`) | `71.4% → 14.3%` (`-57.1 pp`) | `85.7% → 57.1%` (`-28.6 pp`) | `28.6% → 0.0%` (`-28.6 pp`) |
| `handwritten_synth_difficult` (`7`) — **Antes → Después** | `14.3% → 28.6%` (`+14.3 pp`) | `0% → 0%` (`0.0 pp`) | `85.7% → 0.0%` (`-85.7 pp`) | `100.0% → 0.0%` (`-100.0 pp`) |
| **Total 21 degradadas — Antes → Después** | `14.3% → 52.4%` (`+38.1 pp`) | `23.8% → 28.6%` (`+4.8 pp`) | `76.2% → 42.9%` (`-33.3 pp`) | `66.7% → 33.3%` (`-33.3 pp`) |

**Hallazgo empírico clave sobre preprocesamiento local:**
- La normalización de iluminación/contraste benefició a **los 4 motores** en `printed_low_light` (llevando a `TexTeller` de `71.4 %` a **`100.0 %`**, a `Tesseract` de `0 %` a `71.4 %`, y reduciendo la latencia media de `RapidLaTeXOCR` de `9,393 ms` a `462 ms`).
- En cambio, la **binarización adaptativa dura (`-lat 25x25+10%`)** perjudicó severamente a los modelos neuronales ViT en `printed_blur_noise` (`28.6 % → 0.0 %`) y `handwritten_synth_difficult` (`100.0 % → 0.0 %`) porque convierte ruido gaussiano y líneas azules de cuaderno en trazos negros de alto contraste.
- **Conclusión técnica de preprocesamiento:** En Android debe aplicarse únicamente **estiramiento suave de exposición/contraste en escala de grises** (ya implementado y probado en `poc/ocr/android/.../TexTellerImagePreprocessor.kt`), evitando binarización adaptativa dura para TexTeller.

### 3.4 Aclaración técnica de candidatos específicos

1. **Relación técnica real `RapidAI/RapidLaTeXOCR` vs. `lukas-blecher/LaTeX-OCR` (`pix2tex`):**
   - `lukas-blecher/LaTeX-OCR` (PyTorch, licencia MIT) es el proyecto upstream original (`pix2tex`, entrenado mayormente sobre ~100K fórmulas Computer Modern/arXiv).
   - `RapidAI/RapidLaTeXOCR` (licencia MIT en código) es un wrapper y exportación ONNX directa del commit `1781514fb8c92ea9f94057295fdae0e683f4648e` de `pix2tex` (`v0.0.0`, 4 artefactos, `170.66 MB` FP32).
   - En la POC real (`run_rapidlatexocr_poc.mjs`), logró `75.0 %` (3/4) en 2D tipográfico, pero falló en texto en español (`0/9` en `15% de 250`), fuentes pequeñas (`0/7` en `printed_mono_small`) y manuscrito sintético (`14.3 %`), obteniendo `29.9 %` global.
2. **Viabilidad demostrada de `onnx-community/TexTeller-ONNX` mediante ONNX Runtime directo:**
   - El pipeline genérico de `@huggingface/transformers` (`pipeline("image-to-text")`) falla porque el repositorio en Hugging Face omite `preprocessor_config.json`.
   - En `poc/ocr/run_texteller_direct_poc.mjs` y en la implementación Kotlin Android `poc/ocr/android/` se demostró que **`TexTeller-ONNX` SÍ es 100 % ejecutable mediante ONNX Runtime directo** sin Transformers.js ni Node.js, usando el preprocesamiento oficial (`448x448` gris, `mean = 0.9545467`, `std = 0.15394445`), decodificación autoregresiva con `BOS = 0` (`<s>`) y decodificador BPE `ByteLevel` puro en Kotlin (`ByteLevelBpeDecoder.kt`).
   - **Cuello de botella crítico detectado:** Los archivos ONNX publicados en `onnx-community/TexTeller-ONNX` tienen `use_cache: false` (el decoder solo acepta `["input_ids", "encoder_hidden_states"]`, sin KV-cache). Esto elevó la latencia en CPU `x86_64` a `p50 = 5,632 ms` (promedio `6,317 ms`, máximo `18,280 ms`). Para despliegue móvil interactivo será imprescindible **re-exportar TexTeller a ONNX con KV-cache (`past_key_values`)** y medir en dispositivo ARM64 físico.

### 3.5 Separación explícita: errores de motor OCR vs. errores de normalizador y casos adversos

| Motor (dataset base `67`) | Aciertos totales (`PASS`) | Fallos totales | Atribución verificada en `poc/ocr/results/*_base.json` |
|---|---:|---:|---|
| **Tesseract 5 LSTM** | `30` | `37` | **100 % atribuibles al motor OCR:** no reconoce exponentes (`0/10`), radicales (`0/10`) ni diseño 2D (`0/4`); `5` salidas vacías, `23` errores de lectura/parseo por caracteres corruptos, `5` texto corrupto y `4` discrepancias semánticas. |
| **RapidAI/RapidLaTeXOCR** | `20` | `47` | **Principalmente motor OCR:** `17` texto/símbolos corruptos, `7` alucinaciones largas, `10` discrepancias semánticas (`\times` por `x`, omisión de `de` en `15% de 250`) y `13` fallos de estructura/parseo por salida LaTeX malformada. |
| **Xenova/texify (`q8`)** | `54` | `13` | **Motor OCR:** `5` alucinaciones (`\pi^0`, `**A**`, `\chi^2`, `\begin{array}`), `4` errores de alcance en radicales (`\sqrt{144+5}` en lugar de `\sqrt{144}+5`), `4` lecturas corruptas (`12.75 10.5`, `V144 + 5`). |
| **TexTeller-ONNX (`q4f16`)** | `59` | `8` | **Separación crítica demostrada:** de los `8` fallos en `texteller_q4f16_base.json`, **5 son envoltorios decorativos LaTeX** (`\boxed{\begin{array}{c}...\end{array}}` / `\mbox{\tt ...}` en `printed_blur_noise` y `printed_low_light`) que el limpiador AST rechaza conservadoramente aunque el texto matemático interno fue leído bien, y solo **3 de 67 (`4.5 %`) son errores reales del motor OCR** (`printed_mono_small/expr_04_sqrt` → `144+5`, `printed_low_light/expr_04_sqrt` → `v144+5`, `printed_blur_noise/expr_02_div_dec` → `\begin{array}{c|c}\hline...` detenido por guarda de 5 repeticiones). La lectura matemática bruta correcta del motor fue `64/67` (**95.5 %**). |

**Casos adversos / reconocimiento incorrecto ejecutados offline (`poc/ocr/test_ocr_validation.mjs` — `PASS`):**
- Imagen completamente blanca (`blank_white.png`): TexTeller emitió `\[\vdots\]` → rechazado por AST (`UNKNOWN_MACRO(\vdots)`, `ast_ok=false`, `ENGINE_ERROR_CORRUPTED_TEXT`).
- Ruido monocromático impulsivo (`garbage_noise.png`): TexTeller emitió `\[b=1,2, \cdots a\]` → rechazado por AST (`Unexpected token after equation: ,`, `ast_ok=false`, `ENGINE_OR_PARSE_ERROR`).
- Texto no matemático (`"HELLO WORLD"`): TexTeller emitió `\[\mathsf{HELLO}\;\mathsf{WORLD}\]` → rechazado por AST (`IDENT(HELLO)`, `ast_ok=false`, `ENGINE_ERROR_CORRUPTED_TEXT`).

### 3.6 Compatibilidad Real Android ARM64 y POC Android Aislada (`poc/ocr/android/`)

1. **Verificación binaria real del runtime Android (`com.microsoft.onnxruntime:onnxruntime-android:1.20.0`, licencia MIT):**
   - Inspección directa del AAR oficial de Maven Central (`26.58 MB` multi-ABI):
     - `jni/arm64-v8a/libonnxruntime.so`: **`16.76 MB`** (`17,571,160` bytes)
     - `jni/arm64-v8a/libonnxruntime4j_jni.so`: **`0.87 MB`** (`915,968` bytes)
   - Símbolos verificados dentro del binario ELF `arm64-v8a/libonnxruntime.so`: **`MatMulNBits`**, `DynamicQuantizeLinear`, `QLinearMatMul`, `LayerNormalization`, `SkipLayerNormalization`, `Attention`, **`XnnpackExecutionProvider`**, **`NnapiExecutionProvider`**, `Cast`, `Gelu`.
   - Inspección directa de los protobufs de `TexTeller-ONNX (q4f16)` (`encoder_model_q4f16.onnx`, `decoder_model_q4f16.onnx`): usan dominio `com.microsoft:MatMulNBits` + operadores ONNX estándar (`Conv`, `Softmax`, `Gather`, `Cast`, `Add`, `Mul`, `Div`, `Sqrt`, `Erf`) — **100 % presentes en `onnxruntime-android:1.20.0` `arm64-v8a`**.
   - El tokenizador `tokenizer.json` (`BPE` + `ByteLevel`, `11,500` tokens) se decodifica en **Kotlin puro** (`ByteLevelBpeDecoder.kt`) usando `org.json.JSONObject` + tabla inversa GPT-2/RoBERTa, eliminando por completo Node.js y Transformers.js en Android.
2. **POC Android ARM64 aislada compilada y probada (`poc/ocr/android/`, 100 % desacoplada de `app/`):**
   - Proyecto Gradle independiente (`com.calcsuits.poc.ocr.arm64`, `minSdk = 24`, `compileSdk = 36`, `abiFilters += "arm64-v8a"`).
   - **Compilación real ejecutada:** `gradle -p poc/ocr/android assembleDebug testDebugUnitTest` → **`BUILD SUCCESSFUL`**, **`10/10` pruebas unitarias JVM PASS** (`Sha256VerifierTest`, `ByteLevelBpeDecoderTest`, `TexTellerImagePreprocessorTest`).
   - **APK ARM64 generado:** `poc/ocr/android/app/build/outputs/apk/debug/app-debug.apk` (**`21 MB`**, contiene exclusivamente `lib/arm64-v8a/libonnxruntime.so` y `libonnxruntime4j_jni.so`).
   - **Permisos verificados con `aapt2 dump permissions`:** **CERO `android.permission.INTERNET`**, **CERO permisos de almacenamiento** (usa Photo Picker y almacenamiento privado de la app).
   - **Guía y script de pruebas en teléfono físico:** `poc/ocr/android/README_DEVICE_TEST.md` y `poc/ocr/android/push_models_to_device.sh`.
3. **Qué permanece bloqueado en Android ARM64 `[PENDIENTE / BLOQUEADO POR ENTORNO]`:**
   - Latencia real por imagen (`p50`/`p95`), tiempo de carga en frío, pico de memoria nativa (`Debug.getNativeHeapAllocatedSize()`), throttling térmico en gama media/baja y precisión sobre fotos reales de cuaderno con cámara Android (requiere dispositivo físico ARM64 y ADB, ausentes en el contenedor).

---

## 4. Representación Matemática Intermedia (`MathIrNode` AST) y Contraste Real JVM

Se implementó en `poc/ocr/math_ir_ast.mjs` una representación intermedia real basada en AST (`NumberNode`, `VariableNode`, `UnaryOpNode`, `BinaryOpNode`, `FractionNode`, `RootNode`, `PowerNode`, `GroupNode`, `EquationNode`, `PercentOfNode`, `FunctionCallNode`), un lexer + parser descendente recursivo y un serializador consciente de precedencia hacia la sintaxis real de `MathExpressionEvaluator`.

### 4.1 Defecto latente de precedencia detectado en `MathExpressionEvaluator.kt`

Inspección directa de `app/src/main/java/com/calcsuits/domain/usecase/MathExpressionEvaluator.kt` (líneas 40–49 y 203):
```kotlin
private val precedence = mapOf(
    "+" to 1, "-" to 1,
    "*" to 2, "/" to 2, "%" to 2,
    "^" to 3,
    "u-" to 4
)
```
Dado que el menos unario (`u-`) tiene precedencia `4` (superior a `^` con precedencia `3`), `MathExpressionEvaluator.evaluate("-2^2")` devuelve **`+4.0`** en lugar de **`-4.0`**.

**Solución adoptada en el serializador AST sin modificar producción:**
- `UnaryOp("-", Power(a, b))` se serializa explícitamente como `-(a^b)` → evaluado como **`-4.0`**.
- `Power(Group(UnaryOp("-", a)), b)` se serializa como `(-a)^b` → evaluado como **`+4.0`**.
- Se preservan asociatividad derecha de potencias (`2^3^2` = `512` vs. `(2^3)^2` = `64`), multiplicación implícita (`5x` → `5 * x`, `2(3+4)` → `2 * (3 + 4)`), alcance de raíces (`\sqrt{144} + 5` → `sqrt(144) + 5` vs. `\sqrt{144 + 5}` → `sqrt(144 + 5)`), raíces enésimas (`\sqrt[3]{27}` → `27^(1 / 3)`) y agrupación en fracciones (`\frac{3x+2}{5}` → `(3 * x + 2) / 5`).

**Validación ejecutada (`poc/ocr/test_math_ir_ast.mjs`):**
- `31/31` pruebas unitarias del AST superadas.
- `24/24` verificaciones JVM reales ejecutadas en solo lectura contra las clases compiladas en `app/build/tmp/kotlin-classes/debug` (`com.calcsuits.domain.usecase.MathExpressionEvaluator`), confirmando tanto el riesgo en crudo (`evaluate("-2^2") == 4.0`) como la seguridad de la salida serializada (`evaluate("-(2^2)") == -4.0`).

---

## 5. Auditoría Técnica Basada en Código Real del Antiguo Gráfico 2D (`GraphScreen.kt`) y Compatibilidad con Solucionador (`AiAssistantScreen.kt`)

### 5.1 Capacidades realmente implementadas en `GraphScreen.kt` (verificadas línea por línea)

Archivo auditado: `app/src/main/java/com/calcsuits/features/graph/ui/screens/GraphScreen.kt` (588 líneas; compila limpiamente, retirado de `NavGraph.kt` y `Screen.kt`).

- **Motor de renderizado:** `Canvas` nativo de Jetpack Compose + `android.graphics.Paint` (0 dependencias externas de gráficas).
- **Motor matemático:** reutiliza `MathExpressionEvaluator` directamente (`domain/usecase/MathExpressionEvaluator.kt`).
- **Capacidades realmente presentes en el código:**
  - Graficado de **una única función** `f(x)` (`var expression`, sin soporte actual de múltiples curvas superpuestas).
  - Interacciones implementadas: **arrastre/paneo** (`detectDragGestures`), **inspección táctil de punto** `(x, f(x))` (`detectTapGestures`) y **3 botones** de zoom acercar / alejar / reiniciar vista (`FilledTonalIconButton`). **No implementa pinch-to-zoom** (`detectTransformGestures`).
  - Detección básica de discontinuidades verticales (`abs(screenY - prevY) < h * 1.5f`), rejilla adaptativa con pasos `1 / 2 / 5 × 10^k`, ejes coordenados y etiquetas numéricas.
  - Presets de ejemplo (`sin(x)`, `x^2 - 4`, `cos(x) * x`, `1/x`, `ln(x)`, `x^3 - 3x`) y teclado matemático rápido.
  - Estado local autocontenido (sin Hilt ni navegación acoplada): viable para extraer el lienzo como componente embebible `ContextualFunctionGraphCard` / `FunctionGraphSheet`.

### 5.2 Limitaciones reales e incompatibilidades Solver ↔ Gráfico 2D (verificadas en código)

1. **Validación sintáctica fija en `x = 1.234` (`GraphScreen.kt:122`):**
   - Valida la expresión únicamente evaluando `x = 1.234`, rechazando funciones reales válidas cuyo dominio excluye `1.234` (p. ej. `sqrt(-x)`, `ln(-x)`, `asin(x)`, `acos(x)`).
   - **Cambio mínimo requerido:** sustituir por validación léxica/AST + muestreo multi-punto sobre el rango visible `[xMin, xMax]` permitiendo subintervalos fuera de dominio (`NaN`).
2. **Inconsistencia angular `RAD` / `DEG` / `SettingsDataStore`:**
   - `GraphScreen.kt:89` fija estado local `useRadians = true`.
   - `AiAssistantScreen.kt:104` instancia `MathExpressionEvaluator(useRadians = false)` fijo.
   - `SettingsDataStore.kt` persiste la preferencia global `useRadians`.
   - **Cambio mínimo requerido:** inicializar ambos desde `SettingsDataStore.settings.useRadians` con chip explícito `RAD`/`DEG`.
3. **Brechas directas de compatibilidad entre Solucionador (`AiAssistantScreen.kt`) y `GraphScreen.kt`:**
   - **Ecuaciones con `=`** (`2x + 5 = 15`, `x^2 - 5x + 6 = 0`): fallan directamente en `GraphScreen` porque `MathExpressionEvaluator` rechaza `=` (`InvalidCharacter("=")`). Requieren reescritura `LHS = RHS` → `f(x) = (LHS) - (RHS)` (como ya hace `AiAssistantScreen.kt:488` internamente) o trazar `y = LHS` y `y = RHS` si se habilita soporte multi-curva.
   - **Funciones con variable libre `x` sin `=`** (`x^2 + 5x + 6`, `(3x + 2) / 5`): sí grafican en `GraphScreen`, pero **actualmente fallan en `AiAssistantScreen.kt:445`** porque `solveDeterministic` evalúa con `variableX = null` (`MathError.UnknownIdentifier("x")`). El Solucionador necesita reconocer expresiones `f(x)` graficables sin exigir `=`.
   - **Normalización asimétrica:** `AiAssistantScreen.kt:419` normaliza `² -> ^2` y `, -> .`, mientras `GraphScreen.kt` no aplica normalización previa.
   - **Expresiones no graficables** (`15% de 250`, aritmética pura sin variable `x`): la acción contextual **"Ver gráfico 2D"** debe permanecer oculta.
4. **Evaluación síncrona en hilo UI dentro de `Canvas`:**
   - `GraphScreen.kt` calcula entre `360` y `900` evaluaciones `BigDecimal` síncronas dentro de `onDraw` por frame de redibujo/paneo. Conviene cachear muestras por `(expression, xMin, xMax, useRadians, widthPx)` fuera de `onDraw`.
5. **Accesibilidad y recursos huérfanos:**
   - Los 3 botones de zoom/reset tienen tamaño `40.dp` (inferior al mínimo táctil de `48.dp` de Material 3).
   - Cadena huérfana `drawer_sub_graphs` en `strings.xml` (`values`, `values-en`, `values-es`) y referencias obsoletas en `README.md` y `ROADMAP.md`.

### 5.3 Factibilidad de invocación contextual desde Solucionador (`[DEFINIDO / PROPUESTO]`, no implementado)

- **Viable con cambios mínimos y acotados** (sin restaurar Gráfico 2D en el menú lateral):
  1. Extraer el lienzo de `GraphScreen.kt` a un componente reutilizable `FunctionGraphCanvas(expression, useRadians, modifier)` sin barra superior ni campo de texto redundante.
  2. Añadir un clasificador puro `GraphabilityAdapter` que reciba la expresión confirmada del Solucionador: oculte el botón en constantes/porcentajes, acepte `f(x)` directamente y transforme `LHS = RHS` → `(LHS) - (RHS)` (marcando raíces `f(x) = 0`).
  3. Corregir los 5 puntos de §5.2 (`x = 1.234`, `SettingsDataStore.useRadians`, soporte `f(x)` en `AiAssistantScreen`, cacheo fuera de `onDraw`, botones `48.dp` + `detectTransformGestures` opcional).

---

## 6. Propuestas Exactas de Seguridad, Permisos y Privacidad (`[DEFINIDO / PROPUESTO — NO APLICADO]`)

Todas las propuestas siguientes cumplen con **Google Play Developer Program Policy** (uso obligatorio de Android Photo Picker sin permisos amplios de almacenamiento) y la arquitectura 100 % local de CalcSuits. **No se han aplicado a ningún archivo de producción.**

### 6.1 Cambios propuestos en `AndroidManifest.xml`

```xml
<!-- Permiso mínimo necesario solo si el usuario elige captura directa con cámara -->
<uses-permission android:name="android.permission.CAMERA" />
<!-- La cámara NO es obligatoria: tablets/Chromebooks sin cámara siguen pudiendo instalar y usar galería -->
<uses-feature android:name="android.hardware.camera" android:required="false" />

<!-- PROHIBIDO agregar READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE ni READ_MEDIA_IMAGES:
     la selección de galería debe usar Android Photo Picker (cero permisos). -->

<application ...>
    <!-- FileProvider estrictamente acotado al subdirectorio privado de caché temporal OCR -->
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.ocr.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
</application>
```

### 6.2 Archivo propuesto `app/src/main/res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path
        name="ocr_tmp"
        path="ocr_tmp/" />
</paths>
```

### 6.3 Flujo propuesto de permisos, captura y ciclo de vida de archivos temporales

1. **Galería sin permisos (`ActivityResultContracts.PickVisualMedia`):**
   - Invocar `PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)`. No solicita ningún permiso en tiempo de ejecución ni en manifiesto.
2. **Cámara en tiempo de ejecución (`ActivityResultContracts.RequestPermission`):**
   - Solicitar `Manifest.permission.CAMERA` únicamente cuando el usuario pulse "Escanear con cámara" (nunca al iniciar la app).
   - **Denegación temporal:** mostrar explicación breve y ofrecer botón alternativo "Elegir imagen de galería".
   - **Denegación permanente** (`!shouldShowRequestPermissionRationale(CAMERA)` tras rechazo): mostrar diálogo con acción directa a `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` para el paquete de la app, manteniendo la opción de galería siempre funcional.
3. **Almacenamiento privado y borrado garantizado:**
   - Guardar la captura temporal exclusivamente en `File(context.cacheDir, "ocr_tmp/scan_<uuid>.jpg")` (fuera de almacenamiento compartido y nunca indexado por `MediaStore`).
   - Eliminar el archivo temporal en bloque `try { ... } finally { tempFile.delete() }` inmediatamente tras completar o cancelar la inferencia OCR.
   - Ejecutar limpieza preventiva de `cacheDir/ocr_tmp/` en el arranque de la aplicación (recuperación ante cierres abruptos del proceso).

### 6.4 Cláusulas propuestas para `PRIVACY.md` (`[DEFINIDO / PROPUESTO — NO APLICADO]`)

**Español:**
> **Reconocimiento óptico de fórmulas (OCR local):** Cuando utilizas la cámara o seleccionas una imagen para escanear una expresión matemática, el procesamiento se realiza íntegramente en tu dispositivo. CalcSuits no envía imágenes, capturas ni expresiones reconocidas a servidores externos ni a servicios de IA en la nube. Las imágenes temporales se almacenan únicamente en la caché privada de la aplicación durante el análisis y se eliminan automáticamente al finalizar o cancelar el escaneo. *(Nota: si en una fase futura se habilita la descarga opcional bajo demanda del modelo OCR, esta descargará únicamente los archivos del modelo sin transmitir datos personales ni imágenes del usuario).*

**English:**
> **On-device Formula Optical Character Recognition (Local OCR):** When you use the camera or pick an image to scan a mathematical expression, all image processing and recognition run strictly on your device. CalcSuits never uploads images, captures, or recognized expressions to external servers or cloud AI services. Temporary captures are stored exclusively in the app's private cache directory during recognition and are deleted automatically as soon as scanning completes or is canceled. *(Note: if optional on-demand OCR model download is enabled in a future release, only static model weight files are downloaded and no user images or personal data are transmitted).*

---

## 7. Inventario Arquitectónico de CalcSuits (Solo lectura)

| Componente | Ubicación (`app/src/main/java/com/calcsuits/...`) | Responsabilidad | Estado actual | Reutilizable tal cual | Requiere adaptación futura | Riesgos / Dependencias |
|---|---|---|---|---|---|---|
| `AppModule` | `di/AppModule.kt` | Provisión Hilt de Room, DAO, repositorios, OkHttp/Retrofit y casos de uso | Activo | Sí | Agregar bindings de motor OCR / normalizador AST en Fase 4.2+ | Mantener módulos OCR aislados en módulo/paquete dedicado |
| `MainActivity` / `CalcSuitsApp` | `MainActivity.kt`, `CalcSuitsApp.kt` | Entry point Hilt, edge-to-edge y tema | Activo | Sí | Limpieza defensiva de `cacheDir/ocr_tmp` al inicio | No tocar flujo de orientación/IME |
| `Screen` / `NavGraph` / `MainScreen` | `ui/navigation/`, `ui/screens/MainScreen.kt` | Rutas, drawer y scaffold principal | Activo | Sí | Ninguna para Gráfico 2D (seguirá contextual dentro de Solver) | Conservar `GraphScreen` fuera del drawer |
| `CalculatorScreen` / `CalculatorViewModel` / `CalculateExpressionUseCase` | `ui/screens/`, `viewmodel/`, `domain/usecase/` | Calculadora científica principal e historial | Activo (estable) | Sí | **No modificar** | Módulo crítico protegido |
| `MathExpressionEvaluator` / `MathError` | `domain/usecase/` | Evaluador matemático local `BigDecimal` + soporte `x` | Activo | Sí (con resguardo en serializador AST) | Documentar/revisar precedencia `u-` vs `^` en el futuro sin romper tests existentes | Precedencia actual `u-` (4) > `^` (3) probada en JVM |
| `AiAssistantScreen` (Solucionador) | `features/ai/ui/screens/AiAssistantScreen.kt` | Solucionador matemático local (sin IA externa) | Activo | Parcial | Extraer ViewModel propio, unificar `useRadians`, añadir entrada OCR con confirmación y botón contextual "Ver gráfico 2D" | Actualmente `useRadians = false` fijo y lógica acoplada en pantalla |
| `GraphScreen` | `features/graph/ui/screens/GraphScreen.kt` | Graficador 2D nativo con `Canvas` | Intacto, fuera de navegación | Motor de renderizado sí | Corregir validación `x = 1.234`, sincronizar `DEG`/`RAD`, extraer componente embebible | Limpiar `drawer_sub_graphs` en `strings.xml` y docs |
| `SettingsDataStore` | `data/local/datastore/SettingsDataStore.kt` | Preferencias globales (tema, OLED, `useRadians`, precisión, háptica) | Activo | Sí | Fuente única de verdad para `useRadians` en Solver y Gráfico 2D | Ninguno |
| `CalculatorDatabase` / `HistoryDao` / `CalculatorRepositoryImpl` | `data/local/room/`, `data/repository/` | Persistencia local Room del historial y fachada de ajustes | Activo | Sí | Ninguna inmediata | Ninguno |
| `CurrencyRepositoryImpl` | `data/remote/repository/CurrencyRepositoryImpl.kt` | Tasas de cambio con caché local | Activo | Sí | Ninguna | Única dependencia de red legítima de la app |
| Módulos `finance`, `converter`, `engineering`, `programmer`, `settings` | `features/{finance,converter,engineering,programmer,settings}/` | Calculadoras especializadas y ajustes | Activos (Fase 3 cerrada) | Sí | **No modificar** | Fuera del alcance de Fase 4.1 |

---

## 8. Matriz de Pruebas (`PASS` / `FAIL` / `BLOCKED` / `NOT RUN`)

| Prueba / Artefacto | Estado | Resultado / Evidencia verificable |
|---|---|---|
| `gradle :app:testDebugUnitTest` (regresión producción CalcSuits) | **PASS** | `136/136` pruebas unitarias (14 clases), `0` fallos, `0` omitidas; `app/` sin modificaciones |
| `gradle -p poc/ocr/android assembleDebug testDebugUnitTest` (POC ARM64 aislada) | **PASS** | `10/10` pruebas JVM (`Sha256VerifierTest`, `ByteLevelBpeDecoderTest`, `TexTellerImagePreprocessorTest`); APK `21 MB` (`arm64-v8a`, 0 permiso `INTERNET` verificado con `aapt2`) |
| `node poc/ocr/test_offline_integrity.mjs` (SHA-256 pre-carga + offline real) | **PASS** | `6/6` pruebas: 4 motores verificados pre-carga (incluidos JSON auxiliares), rechazo de corruptos/shadow, e inferencia 100 % offline con red bloqueada (`0` llamadas) en TexTeller y Tesseract |
| `node poc/ocr/test_ocr_validation.mjs` (determinismo, resoluciones, casos adversos) | **PASS** | `4/4` pruebas: `67/67` PNG bit-idénticos (`PYTHONHASHSEED=1` vs `42`), `10` resoluciones, `0` manuscritas reales, 8 JSON consistentes, y 3 casos adversos (`blank_white`, `garbage_noise`, `non_math_text`) rechazados limpiamente |
| `node poc/ocr/test_math_normalizer.mjs` | **PASS** | `12/12` pruebas del normalizador base |
| `node poc/ocr/test_math_ir_ast.mjs` (AST + contraste JVM real) | **PASS** | `31/31` pruebas AST + `24/24` verificaciones JVM contra `MathExpressionEvaluator` compilado |
| Benchmarks contenedor x86_64 (`Tesseract`, `RapidLaTeXOCR`, `Texify q8`, `TexTeller q4f16`, 88 img c/u) | **PASS** | 8 archivos JSON íntegros en `poc/ocr/results/` consolidados en `poc/ocr/poc_results_summary.json` |
| Compilación de integridad de producción (`compile_applet`) | **PASS** | Compilación limpia del proyecto principal sin cambios en `app/` |
| Benchmark en dispositivo físico Android ARM64 (latencia, RAM nativa, térmico, XNNPACK/NNAPI) | **BLOCKED** | Sin dispositivo físico ARM64 ni ADB en el contenedor (harness listo en `poc/ocr/android/`) |
| Evaluación con fotos reales de escritura manuscrita humana en papel con cámara | **BLOCKED** | `0` muestras humanas reales en contenedor; requiere capturas reales en dispositivo físico |
| Re-exportación de `TexTeller-ONNX` con KV-cache (`past_key_values`) | **NOT RUN** | Requiere entorno PyTorch/Optimum para re-exportar pesos desde `OleehyO/TexTeller` antes de Fase 4.2 |
| Google ML Kit Text Recognition v2 en ejecución Android | **BLOCKED** | Requiere runtime Android/Play Services y está descartado por incompatibilidad legal con GPL-3.0 |
| Verificación de estado Git (`main`, commit `dbbacbe...`) | **BLOCKED** | No existe directorio `.git` en el workspace del contenedor |

---

## 9. Conclusión Técnica y Veredicto de Fase 4.1

1. **Motor OCR recomendado (con justificación multiplataforma y legal):**
   - **Descartados:**
     - **Tesseract 5 LSTM** (`44.8 %`, `0 %` exponentes/raíces/2D): incapaz de reconocer estructura matemática 2D.
     - **Google ML Kit Text Recognition v2**: incompatible con **GPL-3.0** (ToS propietarios) y OCR lineal sin 2D.
     - **RapidAI/RapidLaTeXOCR (`pix2tex` ONNX FP32)** (`29.9 %`, `170.66 MB`, `0 %` en `15% de 250`): extrema fragilidad fuera de LaTeX limpio.
     - **Xenova/texify (`q8`)** (`80.6 %`, `306.46 MB`, `1,404 MB` RSS): fricción legal (`CC-BY-SA-4.0` vs. `GPL-3.0`) y excesivo consumo de memoria.
   - **Candidato principal recomendado:** **`OleehyO/TexTeller` (`onnx-community/TexTeller-ONNX q4f16`)** sobre **`onnxruntime-android:1.20.0` (`arm64-v8a`)**:
     - Licencia **Apache-2.0** (modelo/código) + **MIT** (`onnxruntime-android`), 100 % compatible con **GPL-3.0**.
     - Mejor precisión empírica: **`88.1 %` (`59/67`)** extremo a extremo y **`95.5 %` (`64/67`)** en lectura matemática bruta (`100 %` en 2D, exponentes y manuscrito sintético; `98.5 %` símbolos).
     - Compatibilidad binaria ARM64 verificada (`MatMulNBits` + XNNPACK/NNAPI presentes en `arm64-v8a/libonnxruntime.so` de `16.76 MB`), decodificador BPE en Kotlin puro sin Node.js/Transformers.js, y APK ARM64 offline aislado compilado (`poc/ocr/android/`, `21 MB`, 0 permiso `INTERNET`).
2. **Veredicto final: `Fase 4.1 PENDIENTE` (no cerrada definitivamente para pasar a producción):**
   - Aunque toda la ingeniería verificable en contenedor (evaluación multi-motor, determinismo, seguridad SHA-256 + offline real, AST contrastado en JVM, auditoría real de `GraphScreen.kt` y POC Android ARM64 compilable) está completada con **100 % PASS**, persisten **3 bloqueantes físicos/previos a Fase 4.2**:
     1. Ejecutar `poc/ocr/android/app-debug.apk` en un **teléfono físico Android ARM64** en modo avión para medir latencia real, pico de RAM nativa y comportamiento térmico.
     2. Capturar y evaluar un lote de **fotografías reales de escritura manuscrita humana** (actualmente `0` muestras reales).
     3. Re-exportar `TexTeller-ONNX` con **KV-cache (`past_key_values`)** si la latencia sin KV-cache en ARM64 supera el objetivo interactivo (< 2–3 s) y definir entrega opcional bajo demanda de los ~193 MB de pesos (sin inflar el APK base).
3. **Veredicto sobre Gráfico 2D:** Reutilizable como vista contextual dentro del Solucionador en Fase 4.2 (sin volver al menú lateral), aplicando los 5 ajustes mínimos verificados en código (§5.2).
