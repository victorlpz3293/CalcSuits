package com.calcsuits.domain.model.converter

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

sealed class ConversionCategory(
    val title: String,
    val icon: ImageVector,
    val units: List<ConversionUnit>,
    val note: String? = null
) {
    object Length : ConversionCategory(
        "Longitud",
        Icons.Default.Straighten,
        listOf(
            ConversionUnit("Metros", "m", 1.0),
            ConversionUnit("Kilómetros", "km", 1000.0),
            ConversionUnit("Centímetros", "cm", 0.01),
            ConversionUnit("Milímetros", "mm", 0.001),
            ConversionUnit("Millas", "mi", 1609.344),
            ConversionUnit("Yardas", "yd", 0.9144),
            ConversionUnit("Pies", "ft", 0.3048),
            ConversionUnit("Pulgadas", "in", 0.0254)
        )
    )

    object Mass : ConversionCategory(
        "Masa",
        Icons.Default.Scale,
        listOf(
            ConversionUnit("Kilogramos", "kg", 1.0),
            ConversionUnit("Gramos", "g", 0.001),
            ConversionUnit("Miligramos", "mg", 0.000001),
            ConversionUnit("Libras", "lb", 0.45359237),
            ConversionUnit("Onzas", "oz", 0.028349523),
            ConversionUnit("Toneladas", "t", 1000.0)
        )
    )

    object Temperature : ConversionCategory(
        "Temperatura",
        Icons.Default.Thermostat,
        listOf(
            ConversionUnit("Celsius", "°C", 1.0, isTemperature = true),
            ConversionUnit("Fahrenheit", "°F", 1.0, isTemperature = true),
            ConversionUnit("Kelvin", "K", 1.0, isTemperature = true)
        )
    )

    object Area : ConversionCategory(
        "Área",
        Icons.Default.CropSquare,
        listOf(
            ConversionUnit("Metro cuadrado", "m²", 1.0),
            ConversionUnit("Kilómetro cuadrado", "km²", 1_000_000.0),
            ConversionUnit("Centímetro cuadrado", "cm²", 0.0001),
            ConversionUnit("Hectárea", "ha", 10_000.0),
            ConversionUnit("Pie cuadrado", "ft²", 0.09290304),
            ConversionUnit("Acre", "ac", 4046.8564224)
        )
    )

    object Volume : ConversionCategory(
        "Volumen",
        Icons.Default.WaterDrop,
        listOf(
            ConversionUnit("Litros", "L", 1.0),
            ConversionUnit("Mililitros", "mL", 0.001),
            ConversionUnit("Metro cúbico", "m³", 1000.0),
            ConversionUnit("Galón (US)", "gal", 3.785411784),
            ConversionUnit("Onza líquida", "fl oz", 0.02957353)
        )
    )

    object Speed : ConversionCategory(
        "Velocidad",
        Icons.Default.Speed,
        listOf(
            ConversionUnit("Metros / seg", "m/s", 1.0),
            ConversionUnit("Kilómetros / hora", "km/h", 1.0 / 3.6),
            ConversionUnit("Millas / hora", "mph", 0.44704),
            ConversionUnit("Nudos", "kn", 0.514444)
        )
    )

    object Data : ConversionCategory(
        "Datos",
        Icons.Default.Storage,
        listOf(
            ConversionUnit("Bytes", "B", 1.0),
            ConversionUnit("Kilobytes", "KB", 1024.0),
            ConversionUnit("Megabytes", "MB", 1024.0 * 1024.0),
            ConversionUnit("Gigabytes", "GB", 1024.0 * 1024.0 * 1024.0),
            ConversionUnit("Terabytes", "TB", 1024.0 * 1024.0 * 1024.0 * 1024.0)
        )
    )

    object Time : ConversionCategory(
        "Tiempo",
        Icons.Default.Schedule,
        listOf(
            ConversionUnit("Segundos", "s", 1.0),
            ConversionUnit("Milisegundos", "ms", 0.001),
            ConversionUnit("Minutos", "min", 60.0),
            ConversionUnit("Horas", "h", 3600.0),
            ConversionUnit("Días", "d", 86400.0),
            ConversionUnit("Semanas", "sem", 604800.0)
        )
    )

    object Currency : ConversionCategory(
        "Moneda",
        Icons.Default.AttachMoney,
        listOf(
            ConversionUnit("Dólar estadounidense", "USD", 1.0),
            ConversionUnit("Euro", "EUR", 1.0 / 0.92),
            ConversionUnit("Córdoba (NI)", "NIO", 1.0 / 36.62),
            ConversionUnit("Peso mexicano", "MXN", 1.0 / 17.50),
            ConversionUnit("Colón (CR)", "CRC", 1.0 / 512.0),
            ConversionUnit("Libra esterlina", "GBP", 1.0 / 0.79)
        ),
        note = "Tasas de referencia locales aproximadas (base USD)"
    )

    companion object {
        val all: List<ConversionCategory> by lazy {
            listOf(Length, Mass, Temperature, Area, Volume, Speed, Data, Time, Currency)
        }
    }
}

data class ConversionUnit(
    val name: String,
    val symbol: String,
    val factorToBase: Double,
    val isTemperature: Boolean = false
)
