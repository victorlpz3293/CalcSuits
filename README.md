# CalcSuits

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://developer.android.com/)
[![Min SDK](https://img.shields.io/badge/minSdk-24-informational.svg)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF.svg)](https://kotlinlang.org/)

**CalcSuits** es una suite matemática y técnica modular para Android construida con **Kotlin**, **Jetpack Compose** y **Material 3**.

---

## Módulos incluidos

- **Calculadora básica y científica:** Operaciones aritméticas encadenadas, memoria (`MC`, `MR`, `M+`, `M-`), historial persistente en Room y panel científico (`sin`, `cos`, `tan`, inversas, hiperbólicas, `log`, `ln`, `exp`, potencias, raíces, factoriales y constantes `π` y `e`).
  - **Precisión del motor:** Las cuatro operaciones aritméticas básicas (`+`, `-`, `*`, `/`), el módulo (`%`) y el control de precisión decimal se calculan con `BigDecimal`. Las funciones trigonométricas, logaritmos, exponenciales, raíces, factoriales y potencias se evalúan con `Double` (IEEE 754, ~15 dígitos significativos) y luego se convierten a `BigDecimal`.
- **Gráficas 2D:** Renderizado nativo en `Canvas` para funciones reales `f(x)` con cuadrícula adaptable, trazado interactivo al tocar, zoom, paneo y manejo de discontinuidades.
- **Solucionador local (determinista, sin IA):** Evaluador paso a paso 100 % en dispositivo para expresiones matemáticas, porcentajes y ecuaciones lineales (`ax + b = c`) y cuadráticas (`ax² + bx + c = 0`). No utiliza inteligencia artificial ni envía datos a servicios externos.
- **Conversores de unidades y monedas:**
  - **Unidades offline:** Longitud, masa, temperatura, área, volumen, velocidad, datos y tiempo.
  - **Monedas (USD, EUR, NIO, MXN, CRC, GBP):** Consulta tasas de cambio públicas sin clave API mediante **open.er-api.com** (ExchangeRate-API, con soporte nativo para córdoba nicaragüense `NIO` y atribución visible). Las tasas y su fecha/hora se almacenan en caché local con Jetpack DataStore: la app muestra primero la caché, intenta refrescar en segundo plano (con timeouts de red y botón manual de actualización) y conserva la caché sin interrumpir la interfaz si no hay conexión. Los montos se muestran con 2 decimales fijos y separador de miles.
- **Finanzas:** Simulador de préstamos (cuota mensual, intereses totales, pago total y tabla de amortización), comparador de interés simple vs. compuesto y cálculo de IVA/impuestos (añadir o desglosar).
- **Ingeniería:** Ley de Ohm, potencia eléctrica, resistencias en serie/paralelo, divisor de voltaje y **Subnetting IPv4** (red, broadcast, máscara, wildcard y rango de hosts).
- **Programador:** Conversión simultánea entre **HEX**, **DEC**, **OCT** y **BIN** (8/16/32/64 bits), operaciones bit a bit (*bitwise*) y matriz interactiva de bits.
- **Ajustes:** Temas Sistema, Claro y Oscuro con opción de Negro puro (OLED), selector de idioma en la app (Sistema / Español / English), unidad angular (`DEG`/`RAD`), precisión decimal (2–12 dígitos), respuesta háptica, gestión del historial y sección *Acerca de* con licencias y código fuente.

---

## Especificaciones técnicas

- **Paquete / Application ID:** `com.calcsuits`
- **Min SDK:** `24` (Android 7.0)
- **Compile / Target SDK:** `36` (Android 16)
- **JVM Target:** Java `17`
- **Arquitectura:** Clean Architecture + MVVM + `StateFlow`
- **Stack principal:** Jetpack Compose, Material 3, Navigation Compose, Hilt (KSP), Room, Jetpack DataStore, Retrofit + OkHttp + Gson, Kotlin Coroutines & Flow

---

## Estructura del proyecto

```text
CalcSuits/
├── .github/
│   ├── ISSUE_TEMPLATE/          # Plantillas de reporte de bugs y mejoras
│   └── workflows/               # CI con assembleDebug y testDebugUnitTest
├── app/
│   └── src/
│       ├── main/java/com/calcsuits/
│       │   ├── CalcSuitsApp.kt
│       │   ├── MainActivity.kt
│       │   ├── data/            # Room, DataStore, API open.er-api.com y caché de monedas
│       │   ├── di/              # Módulos Hilt y configuración de OkHttp/Retrofit
│       │   ├── domain/          # Modelos, casos de uso y MathExpressionEvaluator
│       │   ├── features/
│       │   │   ├── ai/          # Solucionador local determinista
│       │   │   ├── converter/   # Conversores de unidades y divisas
│       │   │   ├── engineering/ # Ingeniería y Subnetting IPv4
│       │   │   ├── finance/     # Finanzas y amortización
│       │   │   ├── graph/       # Gráficas 2D
│       │   │   ├── programmer/  # Programador y matriz de bits
│       │   │   └── settings/    # Ajustes, preferencias y Acerca de
│       │   ├── model/           # Estado y acciones de la calculadora
│       │   ├── ui/              # Navegación, tema, componentes y pantallas base
│       │   └── viewmodel/       # CalculatorViewModel
│       └── test/java/com/calcsuits/ # Pruebas unitarias (JUnit + Coroutines Test)
├── licenses/
│   └── OFL-1.1.txt              # Texto completo de SIL OFL 1.1 (fuentes tipográficas)
├── CHANGELOG.md
├── CONTRIBUTING.md
├── LICENSE
├── PRIVACY.md
├── ROADMAP.md
└── THIRD_PARTY_NOTICES.md
```

---

## Compilación y pruebas

```bash
# Compilar APK de depuración
./gradlew assembleDebug

# Ejecutar pruebas unitarias
./gradlew testDebugUnitTest

# Compilar APK de lanzamiento (con R8/ProGuard)
./gradlew assembleRelease
```

---

## Documentación y cumplimiento

- **Hoja de ruta:** [ROADMAP.md](ROADMAP.md)
- **Historial de cambios:** [CHANGELOG.md](CHANGELOG.md)
- **Política de privacidad:** [PRIVACY.md](PRIVACY.md)
- **Avisos de terceros y fuentes:** [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- **Guía de contribución:** [CONTRIBUTING.md](CONTRIBUTING.md)

---

## Licencia

Este proyecto está licenciado bajo los términos de la **GNU General Public License v3.0 (GPL-3.0)**. Consulta el archivo [LICENSE](LICENSE) para más detalles.

Copyright (C) 2026 Victor R. Lopez · GPL-3.0

---

## Autor

**Victor R. Lopez**  
Web: [https://victorlpz3293.me](https://victorlpz3293.me)