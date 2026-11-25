# Changelog

Todos los cambios notables en **CalcSuits** se documentan en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y este proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

---

## [1.0.0] - 2026-11-25

### Añadido
- **Calculadora básica y científica:** Operaciones aritméticas con `BigDecimal`, funciones científicas (`sin`, `cos`, `tan`, inversas, hiperbólicas, `log`, `ln`, `exp`, raíz, factorial y potencias evaluadas en `Double` y convertidas a `BigDecimal`), memoria (`MC`, `MR`, `M+`, `M-`) e historial persistente en Room.
- **Gráficas 2D:** Renderizado nativo en `Canvas` con cuadrícula adaptable, trazado interactivo al tocar, zoom, paneo y detección de discontinuidades.
- **Solucionador local determinista:** Resolución paso a paso en dispositivo de expresiones aritméticas, porcentajes y ecuaciones lineales y cuadráticas (sin IA ni envío de datos externos).
- **Conversores de unidades y monedas:**
  - 8 categorías offline (longitud, masa, temperatura, área, volumen, velocidad, datos y tiempo).
  - Módulo de monedas (USD, EUR, NIO, MXN, CRC, GBP) conectado a **open.er-api.com** sin clave API, con caché local + fecha/hora en Jetpack DataStore, refresco en segundo plano con timeouts de OkHttp, respaldo offline sin interrumpir la UI, atribución visible a ExchangeRate-API y formato fijo de 2 decimales con separador de miles.
- **Finanzas:** Simulador de préstamos con tabla de amortización, comparador de interés simple vs. compuesto y cálculo de IVA/impuestos.
- **Ingeniería:** Ley de Ohm, potencia eléctrica, resistencias en serie/paralelo, divisor de voltaje y Subnetting IPv4.
- **Programador:** Conversión simultánea entre HEX, DEC, OCT y BIN (8/16/32/64 bits), operaciones bit a bit y matriz interactiva de bits.
- **Ajustes y personalización:**
  - Selector de tema (Sistema, Claro, Oscuro) e interruptor independiente de Negro puro (OLED).
  - Internacionalización completa en Español e Inglés con selector de idioma dentro de la app (`AppCompatDelegate.setApplicationLocales` + `localeConfig`).
  - Precisión decimal configurable (2–12 dígitos), unidad angular (`DEG`/`RAD`), respuesta háptica y borrado de historial.
  - Sección *Acerca de* con aviso de ausencia de garantía GPL-3.0, visor de licencia GPL-3.0, licencias de terceros y enlace al código fuente.
- **Ergonomía de scroll:** Patrón compartido `CalcPinnedScaffold` que mantiene fijos los encabezados de contexto y desplaza únicamente el contenido secundario.
- **Compilación, pruebas y cumplimiento:** `compileSdk`/`targetSdk` 36, reglas R8/ProGuard (`proguard-rules.pro`), 71 pruebas unitarias (JUnit + Coroutines Test), flujo CI en GitHub Actions, `PRIVACY.md`, `THIRD_PARTY_NOTICES.md` y texto completo de SIL OFL 1.1 (`licenses/OFL-1.1.txt`).
- **Configuración de release firmado (AAB):** Firma `release` condicional en `app/build.gradle.kts` activada solo mediante variables de entorno (`SIGNING_KEYSTORE_*`), `versionName` unificado en `"1.0.0"` (`versionCode = 1`), workflow `.github/workflows/release.yml` (disparado manualmente o por tags `v*`) con verificación `jarsigner`, huella SHA-256, subida de `.aab` y `mapping.txt` como artefactos (30 días) y limpieza garantizada del keystore temporal.
