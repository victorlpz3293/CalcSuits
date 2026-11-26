package com.calcsuits.domain.usecase

import com.calcsuits.domain.model.converter.ConversionCategory
import com.calcsuits.domain.model.converter.ConversionUnit
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConvertUnitsUseCaseTest {

    private lateinit var useCase: ConvertUnitsUseCase

    @Before
    fun setUp() {
        useCase = ConvertUnitsUseCase()
    }

    @Test
    fun lengthKmToMilesAndRoundTrip() {
        val km = unitOf(ConversionCategory.Length, "km")
        val mi = unitOf(ConversionCategory.Length, "mi")

        val miles = useCase(10.0, km, mi, locale = Locale.US).toDouble()
        assertEquals(6.213712, miles, 1e-5)

        val backToKm = useCase(miles, mi, km, locale = Locale.US).toDouble()
        assertEquals(10.0, backToKm, 1e-4)
    }

    @Test
    fun massKgToPoundsAndRoundTrip() {
        val kg = unitOf(ConversionCategory.Mass, "kg")
        val lb = unitOf(ConversionCategory.Mass, "lb")

        val pounds = useCase(5.0, kg, lb, locale = Locale.US).toDouble()
        assertEquals(11.023113, pounds, 1e-5)

        val backToKg = useCase(pounds, lb, kg, locale = Locale.US).toDouble()
        assertEquals(5.0, backToKg, 1e-4)
    }

    @Test
    fun temperatureCelsiusFahrenheitKelvinAndRoundTrip() {
        val c = unitOf(ConversionCategory.Temperature, "°C")
        val f = unitOf(ConversionCategory.Temperature, "°F")
        val k = unitOf(ConversionCategory.Temperature, "K")

        assertEquals(32.0, useCase(0.0, c, f, locale = Locale.US).toDouble(), 1e-6)
        assertEquals(212.0, useCase(100.0, c, f, locale = Locale.US).toDouble(), 1e-6)
        assertEquals(-40.0, useCase(-40.0, c, f, locale = Locale.US).toDouble(), 1e-6)
        assertEquals(273.15, useCase(0.0, c, k, locale = Locale.US).toDouble(), 1e-6)

        val fVal = useCase(37.0, c, f, locale = Locale.US).toDouble()
        assertEquals(98.6, fVal, 1e-6)
        assertEquals(37.0, useCase(fVal, f, c, locale = Locale.US).toDouble(), 1e-5)

        val kVal = useCase(fVal, f, k, locale = Locale.US).toDouble()
        assertEquals(310.15, kVal, 1e-5)
        assertEquals(37.0, useCase(kVal, k, c, locale = Locale.US).toDouble(), 1e-5)
    }

    @Test
    fun areaHectareToSquareMetersAndRoundTrip() {
        val ha = unitOf(ConversionCategory.Area, "ha")
        val m2 = unitOf(ConversionCategory.Area, "m²")

        val sqMeters = useCase(2.5, ha, m2, locale = Locale.US).toDouble()
        assertEquals(25_000.0, sqMeters, 1e-6)

        val backToHa = useCase(sqMeters, m2, ha, locale = Locale.US).toDouble()
        assertEquals(2.5, backToHa, 1e-6)
    }

    @Test
    fun volumeUsGallonsToLitersAndRoundTrip() {
        val gal = unitOf(ConversionCategory.Volume, "gal")
        val liter = unitOf(ConversionCategory.Volume, "L")

        val liters = useCase(1.0, gal, liter, locale = Locale.US).toDouble()
        assertEquals(3.785412, liters, 1e-5)

        val backToGal = useCase(liters, liter, gal, locale = Locale.US).toDouble()
        assertEquals(1.0, backToGal, 1e-4)
    }

    @Test
    fun speedMetersPerSecondToKmPerHourAndRoundTrip() {
        val ms = unitOf(ConversionCategory.Speed, "m/s")
        val kmh = unitOf(ConversionCategory.Speed, "km/h")

        val kmPerHour = useCase(10.0, ms, kmh, locale = Locale.US).toDouble()
        assertEquals(36.0, kmPerHour, 1e-6)

        val backToMs = useCase(kmPerHour, kmh, ms, locale = Locale.US).toDouble()
        assertEquals(10.0, backToMs, 1e-5)
    }

    @Test
    fun dataGigabytesToMegabytesAndRoundTrip() {
        val gb = unitOf(ConversionCategory.Data, "GB")
        val mb = unitOf(ConversionCategory.Data, "MB")

        val megabytes = useCase(2.0, gb, mb, locale = Locale.US).toDouble()
        assertEquals(2048.0, megabytes, 1e-6)

        val backToGb = useCase(megabytes, mb, gb, locale = Locale.US).toDouble()
        assertEquals(2.0, backToGb, 1e-6)
    }

    @Test
    fun timeWeeksToDaysAndHoursToSecondsRoundTrip() {
        val wk = unitOf(ConversionCategory.Time, "wk")
        val day = unitOf(ConversionCategory.Time, "d")
        val hour = unitOf(ConversionCategory.Time, "h")
        val sec = unitOf(ConversionCategory.Time, "s")

        val days = useCase(3.0, wk, day, locale = Locale.US).toDouble()
        assertEquals(21.0, days, 1e-6)
        assertEquals(3.0, useCase(days, day, wk, locale = Locale.US).toDouble(), 1e-6)

        val seconds = useCase(2.0, hour, sec, locale = Locale.US).toDouble()
        assertEquals(7200.0, seconds, 1e-6)
        assertEquals(2.0, useCase(seconds, sec, hour, locale = Locale.US).toDouble(), 1e-6)
    }

    @Test
    fun currencyUsdToEurWithFixedDecimalsAndRoundTrip() {
        val usd = unitOf(ConversionCategory.Currency, "USD")
        val eur = unitOf(ConversionCategory.Currency, "EUR")
        val nio = unitOf(ConversionCategory.Currency, "NIO")

        val eurFormatted = useCase(100.0, usd, eur, fixedDecimals = 2, locale = Locale.US)
        assertEquals("92.00", eurFormatted)

        val backToUsd = useCase(eurFormatted.toDouble(), eur, usd, fixedDecimals = 2, locale = Locale.US)
        assertEquals("100.00", backToUsd)

        val largeNioFormatted = useCase(1000.0, usd, nio, fixedDecimals = 2, locale = Locale.US)
        assertEquals("36,620.00", largeNioFormatted)
    }

    @Test
    fun categoryOrderMatchesPhase3SpecificationAndPreservesAllUnits() {
        assertEquals(
            listOf(
                ConversionCategory.Length,
                ConversionCategory.Mass,
                ConversionCategory.Area,
                ConversionCategory.Volume,
                ConversionCategory.Temperature,
                ConversionCategory.Speed,
                ConversionCategory.Time,
                ConversionCategory.Data,
                ConversionCategory.Currency
            ),
            ConversionCategory.all
        )
        assertEquals(9, ConversionCategory.all.size)
        ConversionCategory.all.forEach { category ->
            assert(category.units.size >= 3) { "Expected at least 3 units in ${category.key}" }
        }
    }

    @Test
    fun temperatureAbsoluteZeroAndCrossScaleInverseConversionsAreAccurate() {
        val c = unitOf(ConversionCategory.Temperature, "°C")
        val f = unitOf(ConversionCategory.Temperature, "°F")
        val k = unitOf(ConversionCategory.Temperature, "K")

        assertEquals(-273.15, useCase(0.0, k, c, locale = Locale.US).toDouble(), 1e-6)
        assertEquals(-459.67, useCase(0.0, k, f, locale = Locale.US).toDouble(), 1e-2)
        assertEquals(0.0, useCase(-459.67, f, k, locale = Locale.US).toDouble(), 1e-2)
    }

    @Test
    fun identityZeroAndNonFiniteInputsBehaveSafely() {
        val m = unitOf(ConversionCategory.Length, "m")
        val km = unitOf(ConversionCategory.Length, "km")

        assertEquals("42.5", useCase(42.5, m, m, locale = Locale.US))
        assertEquals("0", useCase(0.0, m, km, locale = Locale.US))
        assertEquals("0.00", useCase(0.0, m, km, fixedDecimals = 2, locale = Locale.US))
        assertEquals("", useCase(Double.NaN, m, m, locale = Locale.US))
        assertEquals("", useCase(Double.POSITIVE_INFINITY, m, m, locale = Locale.US))
    }

    private fun unitOf(category: ConversionCategory, symbol: String): ConversionUnit =
        category.units.first { it.symbol == symbol }
}
