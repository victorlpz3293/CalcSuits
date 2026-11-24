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

## 4. Licencia de las contribuciones

Al enviar una contribución a **CalcSuits**, aceptas que tu código se distribuya bajo la misma licencia del proyecto: **GNU General Public License v3.0 (GPL-3.0)** (ver [LICENSE](LICENSE)).
