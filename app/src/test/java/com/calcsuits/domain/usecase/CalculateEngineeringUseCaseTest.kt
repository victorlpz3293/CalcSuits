package com.calcsuits.domain.usecase

import androidx.annotation.StringRes
import com.calcsuits.R
import com.calcsuits.domain.model.UiText
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculateEngineeringUseCaseTest {

    private lateinit var useCase: CalculateEngineeringUseCase
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        useCase = CalculateEngineeringUseCase()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun ohmsLawSolvesVoltageCurrentAndResistanceInAllThreeDirections() {
        val solveV = useCase.calculateOhmsLaw(v = null, i = 2.0, r = 6.0)
        assertNull(solveV.error)
        assertEquals(12.0, extractNumeric(solveV.headline, "V = ", " V"), 1e-4)
        assertEquals(UiText.DynamicString("V = I × R"), detailValue(solveV, R.string.eng_detail_formula))
        assertEquals(24.0, extractNumeric(detailValue(solveV, R.string.eng_detail_power), "", " W"), 1e-4)

        val solveI = useCase.calculateOhmsLaw(v = 12.0, i = null, r = 4.0)
        assertNull(solveI.error)
        assertEquals(3.0, extractNumeric(solveI.headline, "I = ", " A"), 1e-4)
        assertEquals(UiText.DynamicString("I = V / R"), detailValue(solveI, R.string.eng_detail_formula))
        assertEquals(36.0, extractNumeric(detailValue(solveI, R.string.eng_detail_power), "", " W"), 1e-4)

        val solveR = useCase.calculateOhmsLaw(v = 24.0, i = 3.0, r = null)
        assertNull(solveR.error)
        assertEquals(8.0, extractNumeric(solveR.headline, "R = ", " Ω"), 1e-4)
        assertEquals(UiText.DynamicString("R = V / I"), detailValue(solveR, R.string.eng_detail_formula))
        assertEquals(72.0, extractNumeric(detailValue(solveR, R.string.eng_detail_power), "", " W"), 1e-4)
    }

    @Test
    fun ohmsLawReturnsTypedUiTextErrorsForInvalidCombinations() {
        val tooFew = useCase.calculateOhmsLaw(v = 12.0, i = null, r = null)
        assertNull(tooFew.headline)
        assertNull(tooFew.error)

        val allThree = useCase.calculateOhmsLaw(v = 12.0, i = 2.0, r = 6.0)
        assertEquals(UiText.StringResource(R.string.eng_error_leave_one_empty), allThree.error)

        val zeroResistance = useCase.calculateOhmsLaw(v = 12.0, i = null, r = 0.0)
        assertEquals(UiText.StringResource(R.string.eng_error_resistance_zero), zeroResistance.error)

        val zeroCurrent = useCase.calculateOhmsLaw(v = 12.0, i = 0.0, r = null)
        assertEquals(UiText.StringResource(R.string.eng_error_current_zero), zeroCurrent.error)
    }

    @Test
    fun electricalPowerSolvesPowerVoltageAndCurrentInAllThreeDirections() {
        val solveP = useCase.calculatePower(v = 12.0, i = 2.5, p = null)
        assertNull(solveP.error)
        assertEquals(30.0, extractNumeric(solveP.headline, "P = ", " W"), 1e-4)
        assertEquals(UiText.DynamicString("P = V × I"), detailValue(solveP, R.string.eng_detail_formula))
        assertEquals(4.8, extractNumeric(detailValue(solveP, R.string.eng_detail_equiv_resistance), "", " Ω"), 1e-4)

        val solveV = useCase.calculatePower(v = null, i = 2.0, p = 100.0)
        assertNull(solveV.error)
        assertEquals(50.0, extractNumeric(solveV.headline, "V = ", " V"), 1e-4)
        assertEquals(UiText.DynamicString("V = P / I"), detailValue(solveV, R.string.eng_detail_formula))

        val solveI = useCase.calculatePower(v = 20.0, i = null, p = 100.0)
        assertNull(solveI.error)
        assertEquals(5.0, extractNumeric(solveI.headline, "I = ", " A"), 1e-4)
        assertEquals(UiText.DynamicString("I = P / V"), detailValue(solveI, R.string.eng_detail_formula))
    }

    @Test
    fun electricalPowerReturnsTypedUiTextErrorsForDivisionByZeroAndAllThreeProvided() {
        assertEquals(
            UiText.StringResource(R.string.eng_error_leave_one_empty),
            useCase.calculatePower(v = 10.0, i = 2.0, p = 20.0).error
        )
        assertEquals(
            UiText.StringResource(R.string.eng_error_current_zero),
            useCase.calculatePower(v = null, i = 0.0, p = 20.0).error
        )
        assertEquals(
            UiText.StringResource(R.string.eng_error_voltage_zero),
            useCase.calculatePower(v = 0.0, i = null, p = 20.0).error
        )
    }

    @Test
    fun subnettingSlash24CalculatesExpectedNetworkBroadcastAndUsableHosts() {
        val out = useCase.calculateSubnetting("192.168.1.10", "/24")
        assertNull(out.error)
        assertEquals(UiText.DynamicString("192.168.1.0/24"), out.headline)
        assertEquals(UiText.DynamicString("192.168.1.0"), detailValue(out, R.string.eng_detail_network_addr))
        assertEquals(UiText.DynamicString("255.255.255.0"), detailValue(out, R.string.eng_detail_subnet_mask))
        assertEquals(UiText.DynamicString("0.0.0.255"), detailValue(out, R.string.eng_detail_wildcard))
        assertEquals(UiText.DynamicString("192.168.1.255"), detailValue(out, R.string.eng_detail_broadcast))
        assertEquals(UiText.DynamicString("192.168.1.1"), detailValue(out, R.string.eng_detail_first_host))
        assertEquals(UiText.DynamicString("192.168.1.254"), detailValue(out, R.string.eng_detail_last_host))
        assertEquals(UiText.DynamicString("256"), detailValue(out, R.string.eng_detail_total_addresses))
        assertEquals(UiText.DynamicString("254"), detailValue(out, R.string.eng_detail_usable_hosts))
    }

    @Test
    fun subnettingSlash30Slash31Slash32AndSlash8HandleSpecialRangesCorrectly() {
        val s30 = useCase.calculateSubnetting("192.168.1.10", "/30")
        assertEquals(UiText.DynamicString("192.168.1.8/30"), s30.headline)
        assertEquals(UiText.DynamicString("192.168.1.8"), detailValue(s30, R.string.eng_detail_network_addr))
        assertEquals(UiText.DynamicString("192.168.1.11"), detailValue(s30, R.string.eng_detail_broadcast))
        assertEquals(UiText.DynamicString("192.168.1.9"), detailValue(s30, R.string.eng_detail_first_host))
        assertEquals(UiText.DynamicString("192.168.1.10"), detailValue(s30, R.string.eng_detail_last_host))
        assertEquals(UiText.DynamicString("4"), detailValue(s30, R.string.eng_detail_total_addresses))
        assertEquals(UiText.DynamicString("2"), detailValue(s30, R.string.eng_detail_usable_hosts))

        val s31 = useCase.calculateSubnetting("192.168.1.10", "/31")
        assertEquals(UiText.DynamicString("192.168.1.10/31"), s31.headline)
        assertEquals(UiText.DynamicString("192.168.1.10"), detailValue(s31, R.string.eng_detail_network_addr))
        assertEquals(UiText.DynamicString("192.168.1.11"), detailValue(s31, R.string.eng_detail_broadcast))
        assertEquals(UiText.DynamicString("192.168.1.10"), detailValue(s31, R.string.eng_detail_first_host))
        assertEquals(UiText.DynamicString("192.168.1.11"), detailValue(s31, R.string.eng_detail_last_host))
        assertEquals(UiText.DynamicString("2"), detailValue(s31, R.string.eng_detail_total_addresses))
        assertEquals(UiText.DynamicString("2"), detailValue(s31, R.string.eng_detail_usable_hosts))

        val s32 = useCase.calculateSubnetting("192.168.1.10", "/32")
        assertEquals(UiText.DynamicString("192.168.1.10/32"), s32.headline)
        assertEquals(UiText.DynamicString("192.168.1.10"), detailValue(s32, R.string.eng_detail_network_addr))
        assertEquals(UiText.DynamicString("255.255.255.255"), detailValue(s32, R.string.eng_detail_subnet_mask))
        assertEquals(UiText.DynamicString("192.168.1.10"), detailValue(s32, R.string.eng_detail_broadcast))
        assertEquals(UiText.DynamicString("192.168.1.10"), detailValue(s32, R.string.eng_detail_first_host))
        assertEquals(UiText.DynamicString("192.168.1.10"), detailValue(s32, R.string.eng_detail_last_host))
        assertEquals(UiText.DynamicString("1"), detailValue(s32, R.string.eng_detail_total_addresses))
        assertEquals(UiText.DynamicString("1"), detailValue(s32, R.string.eng_detail_usable_hosts))

        val s8 = useCase.calculateSubnetting("10.20.30.40", "/8")
        assertEquals(UiText.DynamicString("10.0.0.0/8"), s8.headline)
        assertEquals(UiText.DynamicString("10.0.0.0"), detailValue(s8, R.string.eng_detail_network_addr))
        assertEquals(UiText.DynamicString("255.0.0.0"), detailValue(s8, R.string.eng_detail_subnet_mask))
        assertEquals(UiText.DynamicString("10.255.255.255"), detailValue(s8, R.string.eng_detail_broadcast))
        assertEquals(UiText.DynamicString("10.0.0.1"), detailValue(s8, R.string.eng_detail_first_host))
        assertEquals(UiText.DynamicString("10.255.255.254"), detailValue(s8, R.string.eng_detail_last_host))
        assertEquals(UiText.DynamicString("16,777,216"), detailValue(s8, R.string.eng_detail_total_addresses))
        assertEquals(UiText.DynamicString("16,777,214"), detailValue(s8, R.string.eng_detail_usable_hosts))
    }

    @Test
    fun subnettingAcceptsDottedDecimalMask() {
        val out = useCase.calculateSubnetting("192.168.1.130", "255.255.255.192")
        assertNull(out.error)
        assertEquals(UiText.DynamicString("192.168.1.128/26"), out.headline)
        assertEquals(UiText.DynamicString("192.168.1.128"), detailValue(out, R.string.eng_detail_network_addr))
        assertEquals(UiText.DynamicString("192.168.1.191"), detailValue(out, R.string.eng_detail_broadcast))
        assertEquals(UiText.DynamicString("62"), detailValue(out, R.string.eng_detail_usable_hosts))
    }

    @Test
    fun subnettingReturnsTypedErrorForInvalidIpOrMask() {
        val blank = useCase.calculateSubnetting("", "/24")
        assertNull(blank.headline)
        assertNull(blank.error)

        val expectedError = UiText.StringResource(R.string.eng_error_invalid_ipv4)
        assertEquals(expectedError, useCase.calculateSubnetting("999.1.1.1", "/24").error)
        assertEquals(expectedError, useCase.calculateSubnetting("192.168.1", "/24").error)
        assertEquals(expectedError, useCase.calculateSubnetting("192.168.1.10", "/33").error)
        assertEquals(expectedError, useCase.calculateSubnetting("192.168.1.10", "/-1").error)
        assertEquals(expectedError, useCase.calculateSubnetting("192.168.1.10", "255.0.255.0").error)
        assertEquals(expectedError, useCase.calculateSubnetting("abc.def.ghi.jkl", "255.255.255.0").error)
    }

    private fun detailValue(output: EngineeringOutput, @StringRes labelRes: Int): UiText {
        val target = UiText.StringResource(labelRes)
        return output.details.first { it.label == target }.value
    }

    private fun extractNumeric(uiText: UiText?, prefix: String, suffix: String): Double {
        assertTrue(uiText is UiText.DynamicString)
        val raw = (uiText as UiText.DynamicString).value
        assertTrue("Expected '$raw' to start with '$prefix'", raw.startsWith(prefix))
        assertTrue("Expected '$raw' to end with '$suffix'", raw.endsWith(suffix))
        return raw.removePrefix(prefix).removeSuffix(suffix).toDouble()
    }
}
