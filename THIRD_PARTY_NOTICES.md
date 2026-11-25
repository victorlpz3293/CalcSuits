# Avisos de terceros y licencias (Third-Party Notices)

**CalcSuits** se distribuye bajo la licencia **GNU General Public License v3.0 (GPL-3.0)** (ver [LICENSE](LICENSE)).

A continuación se listan todas las bibliotecas de terceros, fuentes tipográficas y servicios externos utilizados por la aplicación, junto con sus licencias y la verificación de compatibilidad con GPL-3.0.

---

## 1. Dependencias de software empaquetadas en la app

| Componente | Artefactos principales | Licencia | Compatibilidad con GPL-3.0 |
| --- | --- | --- | --- |
| **Jetpack Compose** | `androidx.compose:compose-bom`, `ui`, `ui-graphics`, `activity-compose`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | Apache License 2.0 | Compatible (FSF) |
| **Material Design 3 para Compose** | `androidx.compose.material3:material3`, `material-icons-extended` | Apache License 2.0 | Compatible (FSF) |
| **AndroidX Core, AppCompat & Lifecycle** | `androidx.core:core-ktx`, `androidx.appcompat:appcompat`, `androidx.lifecycle:lifecycle-runtime-ktx` | Apache License 2.0 | Compatible (FSF) |
| **Jetpack Navigation Compose** | `androidx.navigation:navigation-compose`, `androidx.hilt:hilt-navigation-compose` | Apache License 2.0 | Compatible (FSF) |
| **Dagger Hilt** | `com.google.dagger:hilt-android`, `hilt-android-compiler` | Apache License 2.0 | Compatible (FSF) |
| **Jetpack Room** | `androidx.room:room-runtime`, `room-ktx`, `room-compiler` | Apache License 2.0 | Compatible (FSF) |
| **Jetpack DataStore** | `androidx.datastore:datastore-preferences` | Apache License 2.0 | Compatible (FSF) |
| **Retrofit** | `com.squareup.retrofit2:retrofit`, `converter-gson` | Apache License 2.0 | Compatible (FSF) |
| **OkHttp & Okio** _(transitiva de Retrofit)_ | `com.squareup.okhttp3:okhttp`, `com.squareup.okio:okio` | Apache License 2.0 | Compatible (FSF) |
| **Gson** | `com.google.code.gson:gson` | Apache License 2.0 | Compatible (FSF) |
| **Kotlin Standard Library & Coroutines** | `org.jetbrains.kotlin`, `org.jetbrains.kotlinx:kotlinx-coroutines-*` | Apache License 2.0 | Compatible (FSF) |

> **Nota sobre Apache License 2.0 y GPL-3.0:** De acuerdo con la Free Software Foundation (FSF), la licencia Apache 2.0 es una licencia de software libre compatible con la GNU GPL versión 3.0. El código combinado se distribuye bajo GPL-3.0 respetando los avisos de copyright originales de cada componente Apache 2.0.

---

## 2. Fuentes tipográficas empaquetadas (`app/src/main/res/font/`)

| Fuente | Archivo | Titular de copyright | Licencia | Compatibilidad con distribución GPL-3.0 |
| --- | --- | --- | --- | --- |
| **JetBrains Mono** | `jetbrains_mono.ttf` | The JetBrains Mono Project Authors (JetBrains s.r.o.) | SIL Open Font License 1.1 | Compatible para empaquetado y redistribución |
| **Space Grotesk** | `space_grotesk.ttf` | The Space Grotesk Project Authors (Florian Karsten) | SIL Open Font License 1.1 | Compatible para empaquetado y redistribución |

- El texto íntegro de la licencia **SIL Open Font License 1.1** se encuentra en [licenses/OFL-1.1.txt](licenses/OFL-1.1.txt).
- Conforme a las condiciones de la SIL OFL 1.1, las fuentes se incluyen sin venderse por separado, conservan su licencia OFL-1.1 original y van acompañadas de su aviso de copyright y del texto de licencia.

---

## 3. Dependencias exclusivas de pruebas (no distribuidas en el APK)

| Componente | Artefacto | Licencia | Alcance |
| --- | --- | --- | --- |
| **JUnit 4** | `junit:junit` | Eclipse Public License 1.0 (EPL-1.0) | `testImplementation` únicamente (no se empaqueta en el binario distribuido) |
| **Kotlinx Coroutines Test** | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | Apache License 2.0 | `testImplementation` únicamente |
| **AndroidX Test / Espresso** | `androidx.test.ext:junit`, `androidx.test.espresso:espresso-core`, `androidx.compose.ui:ui-test-junit4` | Apache License 2.0 | `androidTestImplementation` únicamente |

---

## 4. Servicios externos de datos

- **ExchangeRate-API (Open Access — `open.er-api.com`):** Utilizado únicamente para obtener tasas de cambio públicas sin clave API. Se muestra atribución visible con enlace a <https://www.exchangerate-api.com> en la sección Moneda de la aplicación, conforme a sus términos de uso.

---

## Resumen de verificación de compatibilidad con GPL-3.0

- **Todas las bibliotecas empaquetadas en el APK** están licenciadas bajo **Apache License 2.0**, plenamente compatible con **GPL-3.0**.
- **Ambas tipografías empaquetadas** están licenciadas bajo **SIL OFL 1.1**, cuya redistribución conjunta con aplicaciones GPL-3.0 está permitida siempre que las fuentes permanezcan bajo OFL-1.1 y se incluya su aviso y texto íntegro ([licenses/OFL-1.1.txt](licenses/OFL-1.1.txt)).
- **JUnit 4 (EPL-1.0)** solo se usa en el entorno de pruebas unitarias y no forma parte del APK distribuido.
