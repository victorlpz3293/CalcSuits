package com.calcsuits.features.engineering.ui.viewmodel

import com.calcsuits.domain.model.UiText
import com.calcsuits.domain.model.engineering.EngineeringFormula
import com.calcsuits.domain.model.engineering.EngineeringSpecialty
import com.calcsuits.domain.model.engineering.NetworkingTool
import com.calcsuits.domain.usecase.CalculateEngineeringUseCase
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EngineeringViewModelTest {

    private lateinit var viewModel: EngineeringViewModel
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        viewModel = EngineeringViewModel(CalculateEngineeringUseCase())
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun startsAtSpecialtiesHubAndOpensAllThreeSpecialties() {
        assertNull(viewModel.state.value.selectedSpecialty)
        assertEquals(3, EngineeringSpecialty.entries.size)

        viewModel.onSpecialtySelected(EngineeringSpecialty.ELECTRICAL)
        assertEquals(EngineeringSpecialty.ELECTRICAL, viewModel.state.value.selectedSpecialty)
        assertEquals(EngineeringFormula.OhmsLaw, viewModel.state.value.selectedFormula)

        viewModel.onBackToSpecialties()
        assertNull(viewModel.state.value.selectedSpecialty)

        viewModel.onSpecialtySelected(EngineeringSpecialty.NETWORKING)
        assertEquals(EngineeringSpecialty.NETWORKING, viewModel.state.value.selectedSpecialty)
        assertEquals(NetworkingTool.IPV4_ANALYSIS, viewModel.state.value.selectedNetworkingTool)
        assertEquals(EngineeringFormula.Subnetting, viewModel.state.value.selectedFormula)
        assertEquals(UiText.DynamicString("192.168.1.0/24"), viewModel.state.value.output.headline)

        viewModel.onSpecialtySelected(EngineeringSpecialty.DIGITAL_ELECTRONICS)
        assertEquals(EngineeringSpecialty.DIGITAL_ELECTRONICS, viewModel.state.value.selectedSpecialty)
    }

    @Test
    fun electricalCalculatorSupportsAllFourOperations() {
        assertEquals(
            listOf(
                EngineeringFormula.OhmsLaw,
                EngineeringFormula.Power,
                EngineeringFormula.Resistors,
                EngineeringFormula.VoltageDivider
            ),
            EngineeringFormula.electricalFormulas
        )

        viewModel.onSpecialtySelected(EngineeringSpecialty.ELECTRICAL)

        // 1. Ohm's Law: I = 2 A, R = 6 Ω -> V = 12 V
        viewModel.onFormulaSelected(EngineeringFormula.OhmsLaw)
        viewModel.onInputChange("i", "2")
        viewModel.onInputChange("r", "6")
        assertNull(viewModel.state.value.output.error)
        assertTrue((viewModel.state.value.output.headline as UiText.DynamicString).value.startsWith("V = 12"))

        // 2. Electrical Power: V = 12 V, I = 2.5 A -> P = 30 W
        viewModel.onFormulaSelected(EngineeringFormula.Power)
        viewModel.onInputChange("v", "12")
        viewModel.onInputChange("i", "2.5")
        assertNull(viewModel.state.value.output.error)
        assertTrue((viewModel.state.value.output.headline as UiText.DynamicString).value.startsWith("P = 30"))

        // 3. Resistors: R1 = 100 Ω, R2 = 100 Ω -> Rs = 200 Ω, Rp = 50 Ω
        viewModel.onFormulaSelected(EngineeringFormula.Resistors)
        viewModel.onInputChange("r1", "100")
        viewModel.onInputChange("r2", "100")
        assertNull(viewModel.state.value.output.error)
        assertNotNull(viewModel.state.value.output.headline)

        // 4. Voltage Divider: Vin = 10 V, R1 = 100 Ω, R2 = 100 Ω -> Vout = 5 V
        viewModel.onFormulaSelected(EngineeringFormula.VoltageDivider)
        viewModel.onInputChange("vin", "10")
        viewModel.onInputChange("r1", "100")
        viewModel.onInputChange("r2", "100")
        assertNull(viewModel.state.value.output.error)
        assertTrue((viewModel.state.value.output.headline as UiText.DynamicString).value.startsWith("Vout = 5"))
    }

    @Test
    fun networkingSwitchesBetweenIpv4AnalysisAndSubnetDesignAndPreservesElectricalState() {
        viewModel.onSpecialtySelected(EngineeringSpecialty.ELECTRICAL)
        viewModel.onFormulaSelected(EngineeringFormula.OhmsLaw)
        viewModel.onInputChange("v", "24")
        viewModel.onInputChange("r", "8")
        assertTrue((viewModel.state.value.output.headline as UiText.DynamicString).value.startsWith("I = 3"))

        viewModel.onSpecialtySelected(EngineeringSpecialty.NETWORKING)
        viewModel.onInputChange("ip", "10.0.0.5")
        viewModel.onInputChange("mask", "/8")
        assertEquals(UiText.DynamicString("10.0.0.0/8"), viewModel.state.value.output.headline)

        viewModel.onNetworkingToolSelected(NetworkingTool.SUBNET_DESIGN)
        assertEquals(NetworkingTool.SUBNET_DESIGN, viewModel.state.value.selectedNetworkingTool)

        viewModel.onNetworkingToolSelected(NetworkingTool.IPV4_ANALYSIS)
        assertEquals(UiText.DynamicString("10.0.0.0/8"), viewModel.state.value.output.headline)

        // Returning to Electrical restores previous electrical inputs and result
        viewModel.onSpecialtySelected(EngineeringSpecialty.ELECTRICAL)
        assertEquals("24", viewModel.state.value.inputs["v"])
        assertEquals("8", viewModel.state.value.inputs["r"])
        assertTrue((viewModel.state.value.output.headline as UiText.DynamicString).value.startsWith("I = 3"))
    }
}
