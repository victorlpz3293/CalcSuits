package com.calcsuits.domain.model.converter

import androidx.annotation.StringRes
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
import com.calcsuits.R

sealed class ConversionCategory(
    val key: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val units: List<ConversionUnit>,
    @StringRes val noteRes: Int? = null
) {
    object Length : ConversionCategory(
        key = "length",
        titleRes = R.string.cat_length,
        icon = Icons.Default.Straighten,
        units = listOf(
            ConversionUnit(R.string.unit_meters, "m", 1.0),
            ConversionUnit(R.string.unit_kilometers, "km", 1000.0),
            ConversionUnit(R.string.unit_centimeters, "cm", 0.01),
            ConversionUnit(R.string.unit_millimeters, "mm", 0.001),
            ConversionUnit(R.string.unit_miles, "mi", 1609.344),
            ConversionUnit(R.string.unit_yards, "yd", 0.9144),
            ConversionUnit(R.string.unit_feet, "ft", 0.3048),
            ConversionUnit(R.string.unit_inches, "in", 0.0254)
        )
    )

    object Mass : ConversionCategory(
        key = "mass",
        titleRes = R.string.cat_mass,
        icon = Icons.Default.Scale,
        units = listOf(
            ConversionUnit(R.string.unit_kilograms, "kg", 1.0),
            ConversionUnit(R.string.unit_grams, "g", 0.001),
            ConversionUnit(R.string.unit_milligrams, "mg", 0.000001),
            ConversionUnit(R.string.unit_pounds, "lb", 0.45359237),
            ConversionUnit(R.string.unit_ounces, "oz", 0.028349523),
            ConversionUnit(R.string.unit_tonnes, "t", 1000.0)
        )
    )

    object Temperature : ConversionCategory(
        key = "temperature",
        titleRes = R.string.cat_temperature,
        icon = Icons.Default.Thermostat,
        units = listOf(
            ConversionUnit(R.string.unit_celsius, "°C", 1.0, isTemperature = true),
            ConversionUnit(R.string.unit_fahrenheit, "°F", 1.0, isTemperature = true),
            ConversionUnit(R.string.unit_kelvin, "K", 1.0, isTemperature = true)
        )
    )

    object Area : ConversionCategory(
        key = "area",
        titleRes = R.string.cat_area,
        icon = Icons.Default.CropSquare,
        units = listOf(
            ConversionUnit(R.string.unit_sq_meter, "m²", 1.0),
            ConversionUnit(R.string.unit_sq_kilometer, "km²", 1_000_000.0),
            ConversionUnit(R.string.unit_sq_centimeter, "cm²", 0.0001),
            ConversionUnit(R.string.unit_hectare, "ha", 10_000.0),
            ConversionUnit(R.string.unit_sq_foot, "ft²", 0.09290304),
            ConversionUnit(R.string.unit_acre, "ac", 4046.8564224)
        )
    )

    object Volume : ConversionCategory(
        key = "volume",
        titleRes = R.string.cat_volume,
        icon = Icons.Default.WaterDrop,
        units = listOf(
            ConversionUnit(R.string.unit_liters, "L", 1.0),
            ConversionUnit(R.string.unit_milliliters, "mL", 0.001),
            ConversionUnit(R.string.unit_cubic_meter, "m³", 1000.0),
            ConversionUnit(R.string.unit_gallon_us, "gal", 3.785411784),
            ConversionUnit(R.string.unit_fluid_ounce, "fl oz", 0.02957353)
        )
    )

    object Speed : ConversionCategory(
        key = "speed",
        titleRes = R.string.cat_speed,
        icon = Icons.Default.Speed,
        units = listOf(
            ConversionUnit(R.string.unit_meters_per_sec, "m/s", 1.0),
            ConversionUnit(R.string.unit_km_per_hour, "km/h", 1.0 / 3.6),
            ConversionUnit(R.string.unit_miles_per_hour, "mph", 0.44704),
            ConversionUnit(R.string.unit_knots, "kn", 0.514444)
        )
    )

    object Data : ConversionCategory(
        key = "data",
        titleRes = R.string.cat_data,
        icon = Icons.Default.Storage,
        units = listOf(
            ConversionUnit(R.string.unit_bytes, "B", 1.0),
            ConversionUnit(R.string.unit_kilobytes, "KB", 1024.0),
            ConversionUnit(R.string.unit_megabytes, "MB", 1024.0 * 1024.0),
            ConversionUnit(R.string.unit_gigabytes, "GB", 1024.0 * 1024.0 * 1024.0),
            ConversionUnit(R.string.unit_terabytes, "TB", 1024.0 * 1024.0 * 1024.0 * 1024.0)
        )
    )

    object Time : ConversionCategory(
        key = "time",
        titleRes = R.string.cat_time,
        icon = Icons.Default.Schedule,
        units = listOf(
            ConversionUnit(R.string.unit_seconds, "s", 1.0),
            ConversionUnit(R.string.unit_milliseconds, "ms", 0.001),
            ConversionUnit(R.string.unit_minutes, "min", 60.0),
            ConversionUnit(R.string.unit_hours, "h", 3600.0),
            ConversionUnit(R.string.unit_days, "d", 86400.0),
            ConversionUnit(R.string.unit_weeks, "wk", 604800.0)
        )
    )

    object Currency : ConversionCategory(
        key = "currency",
        titleRes = R.string.cat_currency,
        icon = Icons.Default.AttachMoney,
        units = listOf(
            ConversionUnit(R.string.unit_usd, "USD", 1.0),
            ConversionUnit(R.string.unit_eur, "EUR", 1.0 / 0.92),
            ConversionUnit(R.string.unit_nio, "NIO", 1.0 / 36.62),
            ConversionUnit(R.string.unit_mxn, "MXN", 1.0 / 17.50),
            ConversionUnit(R.string.unit_crc, "CRC", 1.0 / 512.0),
            ConversionUnit(R.string.unit_gbp, "GBP", 1.0 / 0.79)
        ),
        noteRes = R.string.cat_currency_note
    )

    companion object {
        val all: List<ConversionCategory> by lazy {
            listOf(Length, Mass, Area, Volume, Temperature, Speed, Time, Data, Currency)
        }
    }
}

data class ConversionUnit(
    @StringRes val nameRes: Int,
    val symbol: String,
    val factorToBase: Double,
    val isTemperature: Boolean = false
)
