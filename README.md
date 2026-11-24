# CalcSuits

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://developer.android.com/)
[![Min SDK](https://img.shields.io/badge/minSdk-24-informational.svg)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF.svg)](https://kotlinlang.org/)

**CalcSuits** es una suite matemática y técnica modular para Android construida con **Kotlin**, **Jetpack Compose** y **Material 3**, respaldada por un motor determinista basado en `BigDecimal`.

---

## Módulos incluidos

- **Calculadora básica y científica:** Operaciones aritméticas encadenadas, trigonometría, logaritmos, potencias, raíces, factoriales, constantes, memoria (`MC`, `MR`, `M+`, `M-`) e historial persistente en Room.
- **Gráficas 2D:** Renderizado nativo en `Canvas` para funciones matemáticas (`sin`, `cos`, `tan`, `log`, `ln`, `sqrt`, `abs`, polinomios), con cuadrícula, ejes y manejo de discontinuidades.
- **Solucionador local:** Resolución determinista en dispositivo de expresiones y ecuaciones paso a paso sin depender de servicios externos.
- **Conversores:** Motor modular de conversión para longitud, masa, temperatura, volumen, área, velocidad, tiempo, almacenamiento de datos y divisas (con soporte HTTP vía Retrofit).
- **Finanzas:** Simulador de préstamos con tasa anual, plazo, cuota mensual, intereses totales, pago total y tabla de amortización.
- **Ingeniería:** Herramientas técnicas con Ley de Ohm, potencia eléctrica, **Subnetting IPv4** (red, broadcast, máscara, rango de hosts) y catálogo de fórmulas.
- **Programador:** Conversión simultánea entre bases **DEC**, **BIN**, **HEX** y **OCT**, además de operaciones bit a bit (*bitwise*).
- **Ajustes:** Personalización persistida en Jetpack DataStore con modos de tema, precisión decimal, unidad angular (grados/radianes), respuesta háptica y gestión del historial.

---

## Especificaciones técnicas

- **Paquete / Application ID:** `com.calcsuits`
- **Min SDK:** `24` (Android 7.0)
- **Compile / Target SDK:** `35`
- **JVM Target:** Java `17`
- **Arquitectura:** Clean Architecture + MVVM + StateFlow
- **Stack principal:** Jetpack Compose, Material 3, Navigation Compose, Hilt (KSP), Room, Jetpack DataStore, Retrofit + Gson, Kotlin Coroutines & Flow

---

## Estructura del proyecto

```text
CalcSuits/
├── app/
│   └── src/
│       ├── main/java/com/calcsuits/
│       │   ├── CalcSuitsApp.kt
│       │   ├── MainActivity.kt
│       │   ├── data/                # Room, DataStore, Retrofit y repositorios
│       │   ├── di/                  # Módulos Hilt
│       │   ├── domain/              # Modelos, casos de uso y MathExpressionEvaluator
│       │   ├── features/
│       │   │   ├── ai/              # Solucionador local
│       │   │   ├── converter/       # Conversores de unidades y divisas
│       │   │   ├── engineering/     # Ingeniería y Subnetting IPv4
│       │   │   ├── finance/         # Finanzas y amortización
│       │   │   ├── graph/           # Gráficas 2D
│       │   │   ├── programmer/      # Programador y bitwise
│       │   │   └── settings/        # Ajustes y preferencias
│       │   ├── model/               # Estado y acciones de la calculadora
│       │   ├── ui/                  # Navegación, tema, componentes y pantallas base
│       │   └── viewmodel/           # CalculatorViewModel
│       ├── test/java/com/calcsuits/ # Pruebas unitarias (JUnit)
│       └── androidTest/java/com/calcsuits/
├── ROADMAP.md
├── CONTRIBUTING.md
└── LICENSE
```

---

## Compilación y pruebas

```bash
# Compilar APK de depuración
./gradlew assembleDebug

# Ejecutar pruebas unitarias
./gradlew testDebugUnitTest
```

---

## Screenshots

> _Próximamente: capturas de pantalla de la calculadora científica, gráficas 2D, ingeniería (Subnetting IPv4), finanzas, programador y ajustes._

| Calculadora | Gráficas 2D | Ingeniería & Redes | Programador |
| :---: | :---: | :---: | :---: |
| _Placeholder_ | _Placeholder_ | _Placeholder_ | _Placeholder_ |

---

## Roadmap

Consulta las fases completadas y los próximos hitos del proyecto en [ROADMAP.md](ROADMAP.md).

---

## Contribuir

Las contribuciones son bienvenidas. Consulta la guía en [CONTRIBUTING.md](CONTRIBUTING.md) para conocer el flujo de issues, Pull Requests y validaciones obligatorias.

---

## Licencia

Este proyecto está licenciado bajo los términos de la **GNU General Public License v3.0 (GPL-3.0)**. Consulta el archivo [LICENSE](LICENSE) para más detalles.

---

## Autor

**Victor R. Lopez**  
Web: [https://victorlpz3293.me](https://victorlpz3293.me)