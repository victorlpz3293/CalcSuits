# CalcSuits: Roadmap & Vision

## Visión
Una suite matemática modular de clase mundial, rápida, profesional y adaptable a diferentes perfiles (Ingeniería, Finanzas, Programación).

## Fases de Desarrollo

### Fase 1: Estabilización y Arquitectura (Completada)
- [x] Refactorización a arquitectura modular (Navigation Drawer & Suite Structure).
- [x] Mejora del motor de cálculo (`Core Engine`): Parser de expresiones matemáticas.
- [x] Auditoría de UI/UX: Consistencia visual con `CalcSuitsHeader` y Material 3.
- [x] Implementación de `Local Data Persistence` robusta (Room & DataStore).

### Fase 2: Expansión de Módulos (Completada)
- [x] **Módulo de Conversores:**
    - [x] Lógica base de conversión (Unit Engine).
    - [x] UI de selección de unidades.
    - [x] Conversor de Longitud y Masa.
    - [x] Conversor de Monedas (Estructura base lista).
- [x] **Módulo Financiero:** Préstamos, interés, IVA, amortizaciones.
- [x] **Módulo de Ingeniería:** Ley de Ohm, redes, fórmulas técnicas.
- [x] **Módulo de Programador:** Bitwise, Hex/Bin/Dec, máscaras de subred.
- [x] **Módulo de Gráficas:** Visualización 2D (Seno funcional).

### Fase 3: Inteligencia e Impacto Global (Iniciada)
- [ ] **Integración IA:** Resolución de problemas mediante lenguaje natural y OCR.
- [x] **Internacionalización:** Soporte multi-idioma (ES/EN) configurado.
- [ ] **Cloud Sync:** Sincronización entre dispositivos.
- [ ] **Publicación en Google Play.**

## Pilares Técnicos
- **Clean Architecture:** Separación clara de responsabilidades.
- **Jetpack Compose:** UI moderna y declarativa.
- **Hilt:** Inyección de dependencias para escalabilidad.
- **Kotlin Flow:** Gestión de estados reactivos.

---
*Este documento es una guía viva y evolucionará con el proyecto.*
