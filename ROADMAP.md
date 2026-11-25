# CalcSuits — Roadmap

## Visión
Una suite matemática y técnica modular para Android: rápida, determinista, respetuosa de la privacidad y usable sin conexión en el día a día.

---

## v1.0 — Lanzamiento inicial

### Funcionamiento offline y módulos centrales
- [x] Arquitectura modular (Clean Architecture + MVVM + Navigation Drawer).
- [x] **Calculadora básica y científica:** operaciones con `BigDecimal`, funciones científicas (`Double` → `BigDecimal`), memoria (`MC`/`MR`/`M+`/`M-`) e historial local en Room.
- [x] **Gráficas 2D:** renderizado nativo en `Canvas` con zoom, paneo, trazado de puntos y manejo de discontinuidades.
- [x] **Solucionador local determinista:** resolución paso a paso de expresiones, porcentajes y ecuaciones lineales/cuadráticas sin dependencia de servicios externos.
- [x] **Conversores de unidades y divisas:** 8 categorías offline + módulo de monedas conectado a **open.er-api.com** (sin clave API, con soporte nativo para `NIO`), caché local con fecha/hora en DataStore y atribución visible.
- [x] **Finanzas:** préstamos con tabla de amortización, interés simple vs. compuesto e IVA/impuestos.
- [x] **Ingeniería:** Ley de Ohm, potencia eléctrica, resistencias, divisor de voltaje y Subnetting IPv4.
- [x] **Programador:** conversión HEX/DEC/OCT/BIN (8–64 bits), operaciones bit a bit y matriz interactiva de bits.
- [x] **Ajustes y ergonomía:** temas Sistema / Claro / Oscuro + Negro puro (OLED), regla global de encabezados fijos (`CalcPinnedScaffold`) y soporte completo edge-to-edge.

### Pruebas y calidad
- [x] Suite de pruebas unitarias (JUnit + Coroutines Test): evaluador matemático, finanzas, ingeniería/IPv4, conversores, programador, migración de preferencias y repositorio de monedas con fake API.
- [x] Integración continua en GitHub Actions (`assembleDebug` + `testDebugUnitTest`).

### Idiomas (internacionalización)
- [x] Internacionalización completa en **Español** e **Inglés** (`values`, `values-es`, `values-en`), soporte de `plurals`, formateo numérico/monetario por `Locale` y selector de idioma dentro de la app (`AppCompatDelegate.setApplicationLocales` + `localeConfig`).

### Preparación para Google Play
- [x] `compileSdk` y `targetSdk` en API 36 (Android 16) con `minSdk` 24.
- [x] Reglas R8/ProGuard (`proguard-rules.pro`) para Room, Hilt, Retrofit/OkHttp, Gson y Coroutines.
- [x] Cumplimiento legal y transparencia: GPL-3.0, avisos de terceros (`THIRD_PARTY_NOTICES.md`), licencia SIL OFL 1.1 de fuentes, política de privacidad (`PRIVACY.md`) y sección *Acerca de* interactiva.
- [ ] Publicación oficial en Google Play Store (ficha, capturas finales y firma de distribución).

---

## v1.1+ — Evolución futura

- [ ] **Asistente IA opcional con proveedor configurable:** capa opcional para explicaciones o resolución en lenguaje natural permitiendo al usuario elegir y configurar su propio proveedor/clave, manteniendo el solucionador determinista offline por defecto.
- [ ] **OCR matemático con cámara:** captura de expresiones impresas o manuscritas directamente hacia la calculadora y el solucionador.
- [ ] **Respaldo y exportación:** exportación/importación local (JSON/CSV) del historial de cálculos, tablas de amortización y preferencias del usuario.
- [ ] **Gráficas avanzadas:** soporte para múltiples curvas simultáneas y detección de raíces/intersecciones.
- [ ] **Cálculo matricial y estadística descriptiva:** nuevo módulo técnico orientado a estudiantes e ingeniería.
