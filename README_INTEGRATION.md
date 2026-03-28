# 🚀 Runtime Mock System (OkHttp & Ktor)

Un sistema de mocks dinámicos para Android y Desktop (Kotlin Multiplatform) que permite simular estados complejos, como **polling con transiciones de tiempo**.

## 📦 Instalación (Librería)

He configurado el proyecto para que se publique como una librería en tu repositorio local de Maven (`.m2`). Para usarla en otra aplicación, sigue estos pasos:

### 1. Añade `mavenLocal()` a tu proyecto
En tu `settings.gradle.kts` o `build.gradle.kts` raíz:
```kotlin
repositories {
    mavenCentral()
    mavenLocal() // <--- Añade esto para ver la librería en tu PC
}
```

### 2. Añade las dependencias
En el `build.gradle.kts` de tu aplicación:
```kotlin
dependencies {
    // Core y soporte OkHttp (Android/JVM)
    implementation("com.fusion.mock:shared:1.0.0")
    
    // Soporte para Ktor (opcional)
    implementation("com.fusion.mock:ktor:1.0.0")
}
```

## ✨ Características
- **⏳ Transiciones por Tiempo**: Los mocks pueden durar un tiempo determinado (`durationMs`) antes de cambiar al siguiente.
- **🔄 Estrategias de Secuencia**: Soporte para `FIFO` (secuencial), `LOOP` (bucle) y `RANDOM`.
- **📱 Multi-Plataforma**: Interceptores nativos para OkHttp (Android/JVM) y Plugins para Ktor (Cloud/Desktop).
- **📟 Dashboard de Depuración**: Interfaz de terminal de alta densidad para monitorear peticiones en tiempo real.

---

## 🤖 Integración en Android (OkHttp)

### 1. Copia los archivos base
Copia `MockCore.kt` y `StatefulMockInterceptor.kt` a tu módulo Android.

### 2. Configura tu OkHttpClient
```kotlin
val mockProvider = StatefulMockProvider()

val client = OkHttpClient.Builder()
    .addInterceptor(StatefulMockInterceptor(mockProvider)) // Registra el interceptor
    .build()
```

### 3. Registra tus escenarios de Mock
```kotlin
mockProvider.addMock(
    path = "/api/v1/status",
    method = "GET",
    responses = listOf(
        // Este mock durará 10 segundos antes de cambiar
        TimedMockResponse(
            body = """{"state": "LOADING"}""", 
            durationMs = 10000 
        ),
        TimedMockResponse(
            body = """{"state": "SUCCESS"}""", 
            durationMs = 10000 
        )
    ),
    strategy = MockStrategy.LOOP
)
```

---

## 💻 Integración en Desktop (Ktor)

### 1. Copia los archivos base
Copia `MockCore.kt` y `StatefulMockPlugin.kt` a tu proyecto.

### 2. Configura tu HttpClient
```kotlin
val mockProvider = StatefulMockProvider()

val client = HttpClient(CIO) {
    install(StatefulMockPlugin(mockProvider)) // Instala el plugin
}
```

---

## 📊 Conceptos Clave

### TimedMockResponse
Representa una respuesta individual del servidor.
- `body`: El JSON o texto de respuesta.
- `code`: Código HTTP (ej. 200, 404).
- `delayMs`: Latencia artificial para simular red lenta.
- `durationMs`: Cuánto tiempo (ms) este mock se mantiene "vivo" antes de pasar al siguiente en la cola.

### MockStrategy
- `FIFO`: Al agotarse los mocks, la ruta deja de responder (devuelve null/red real).
- `LOOP`: Al terminar la lista, vuelve a empezar el ciclo.
- `RANDOM`: Elige una respuesta al azar de la lista.

---

## 🧪 Ejemplo de Polling (2s / 10s)
Si quieres que tu app haga polling cada **2 segundos** y que el estado cambie cada **10 segundos**:
1. Tu app ejecuta un `while(true) { fetch(); delay(2000); }`.
2. En el `mockProvider`, registras los mocks con `durationMs = 10000`.
3. El sistema devolverá el mismo Mock durante 5 peticiones (5 x 2s = 10s) y luego saltará automáticamente al siguiente.

---

¡Disfruta de tus pruebas de integración sin depender del backend! 🚀✨
