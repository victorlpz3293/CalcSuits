package com.calcsuits.features.engineering.ui.viewmodel

import com.calcsuits.domain.model.UiText
import com.calcsuits.domain.model.engineering.EngineeringFormula
import com.calcsuits.domain.model.engineering.EngineeringSpecialty
import com.calcsuits.domain.model.engineering.FlsmCriteria
import com.calcsuits.domain.model.engineering.FlsmOutcome
import com.calcsuits.domain.model.engineering.Ipv4AnalysisOutcome
import com.calcsuits.domain.model.engineering.Ipv4Error
import com.calcsuits.domain.model.engineering.NetworkingTool
import com.calcsuits.domain.model.engineering.SubnetDesignMode
import com.calcsuits.domain.model.engineering.VlsmOutcome
import com.calcsuits.domain.usecase.CalculateEngineeringUseCase
import com.calcsuits.domain.usecase.engineering.Ipv4SubnettingEngine
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
        viewModel = EngineeringViewModel(CalculateEngineeringUseCase(), Ipv4SubnettingEngine())
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
    fun ipv4AnalysisSynchronizesCidrAndDecimalMaskAndValidatesErrors() {
        viewModel.onSpecialtySelected(EngineeringSpecialty.NETWORKING)

        // Editing CIDR updates decimal mask
        viewModel.onIpv4CidrChange("/26")
        assertEquals("255.255.255.192", viewModel.state.value.ipv4MaskInput)
        val success = viewModel.state.value.ipv4Analysis as Ipv4AnalysisOutcome.Success
        assertEquals(26, success.result.prefixLength)
        assertEquals(62L, success.result.usableHosts)

        // Editing decimal mask updates CIDR
        viewModel.onIpv4DecimalMaskChange("255.255.240.0")
        assertEquals("/20", viewModel.state.value.ipv4CidrInput)
        val success20 = viewModel.state.value.ipv4Analysis as Ipv4AnalysisOutcome.Success
        assertEquals(20, success20.result.prefixLength)

        // Non-contiguous decimal mask yields NonContiguousMask error
        viewModel.onIpv4DecimalMaskChange("255.0.255.0")
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.NonContiguousMask), viewModel.state.value.ipv4Analysis)
    }

    @Test
    fun subnetDesignSupportsFlsmAndVlsmAndPreservesElectricalState() {
        viewModel.onSpecialtySelected(EngineeringSpecialty.ELECTRICAL)
        viewModel.onFormulaSelected(EngineeringFormula.OhmsLaw)
        viewModel.onInputChange("v", "24")
        viewModel.onInputChange("r", "8")
        assertTrue((viewModel.state.value.output.headline as UiText.DynamicString).value.startsWith("I = 3"))

        viewModel.onSpecialtySelected(EngineeringSpecialty.NETWORKING)
        viewModel.onNetworkingToolSelected(NetworkingTool.SUBNET_DESIGN)

        // FLSM by subnets (default 192.168.1.0/24 -> 4 subnets -> /26)
        viewModel.onSubnetDesignModeSelected(SubnetDesignMode.FLSM)
        val flsm4 = viewModel.state.value.flsmOutcome as FlsmOutcome.Success
        assertEquals(26, flsm4.result.newPrefix)
        assertEquals(4, flsm4.result.subnets.size)

        // FLSM by hosts per subnet (30 hosts -> /27 -> 8 subnets)
        viewModel.onFlsmCriteriaSelected(FlsmCriteria.BY_HOSTS_PER_SUBNET)
        viewModel.onFlsmTargetChange("30")
        val flsmHosts = viewModel.state.value.flsmOutcome as FlsmOutcome.Success
        assertEquals(27, flsmHosts.result.newPrefix)
        assertEquals(8, flsmHosts.result.subnets.size)

        // VLSM: add, update, and remove requirements
        viewModel.onSubnetDesignModeSelected(SubnetDesignMode.VLSM)
        val initialCount = viewModel.state.value.vlsmRequirements.size
        viewModel.onAddVlsmRequirement("DMZ")
        assertEquals(initialCount + 1, viewModel.state.value.vlsmRequirements.size)
        val addedId = viewModel.state.value.vlsmRequirements.last().id
        viewModel.onUpdateVlsmRequirementHosts(addedId, "12")
        val vlsmRes = viewModel.state.value.vlsmOutcome as VlsmOutcome.Success
        assertEquals(initialCount + 1, vlsmRes.result.allocations.size)

        viewModel.onRemoveVlsmRequirement(addedId)
        assertEquals(initialCount, viewModel.state.value.vlsmRequirements.size)

        // Returning to Electrical restores previous electrical inputs and result
        viewModel.onSpecialtySelected(EngineeringSpecialty.ELECTRICAL)
        assertEquals("24", viewModel.state.value.inputs["v"])
        assertEquals("8", viewModel.state.value.inputs["r"])
        assertTrue((viewModel.state.value.output.headline as UiText.DynamicString).value.startsWith("I = 3"))
    }

    @Test
    fun decimalMaskFieldDoesNotFalselySyncBareCidrInteger() {
        viewModel.onSpecialtySelected(EngineeringSpecialty.NETWORKING)
        viewModel.onIpv4CidrChange("/26")
        assertEquals("/26", viewModel.state.value.ipv4CidrInput)

        // Typing bare "24" into the decimal mask field must fail as InvalidSubnetMask and NOT overwrite CIDR
        viewModel.onIpv4DecimalMaskChange("24")
        assertEquals("/26", viewModel.state.value.ipv4CidrInput)
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.InvalidSubnetMask), viewModel.state.value.ipv4Analysis)
    }

    @Test
    fun switchingBetweenFlsmAndVlsmKeepsStatesIsolatedAndPreservesFlsmCriteriaInputs() {
        viewModel.onSpecialtySelected(EngineeringSpecialty.NETWORKING)
        viewModel.onNetworkingToolSelected(NetworkingTool.SUBNET_DESIGN)

        // Configure FLSM on 10.0.0.0/16 with 8 subnets
        viewModel.onSubnetDesignModeSelected(SubnetDesignMode.FLSM)
        viewModel.onDesignBaseIpChange("10.0.0.0")
        viewModel.onDesignBaseMaskChange("/16")
        viewModel.onFlsmTargetChange("8")
        val flsmBefore = viewModel.state.value.flsmOutcome as FlsmOutcome.Success
        assertEquals("10.0.0.0/16", flsmBefore.result.baseNetwork.cidrNotation)
        assertEquals(19, flsmBefore.result.newPrefix)

        // Switching criteria to hosts & back preserves user-entered values
        viewModel.onFlsmCriteriaSelected(FlsmCriteria.BY_HOSTS_PER_SUBNET)
        viewModel.onFlsmTargetChange("500")
        assertEquals(23, (viewModel.state.value.flsmOutcome as FlsmOutcome.Success).result.newPrefix)

        viewModel.onFlsmCriteriaSelected(FlsmCriteria.BY_SUBNET_COUNT)
        assertEquals("8", viewModel.state.value.flsmTargetInput)
        assertEquals(19, (viewModel.state.value.flsmOutcome as FlsmOutcome.Success).result.newPrefix)

        // Tapping already-active criteria does not wipe user input
        viewModel.onFlsmCriteriaSelected(FlsmCriteria.BY_SUBNET_COUNT)
        assertEquals("8", viewModel.state.value.flsmTargetInput)

        // Switch to VLSM and set a DIFFERENT base network + invalid blank name -> must NOT affect FLSM
        viewModel.onSubnetDesignModeSelected(SubnetDesignMode.VLSM)
        assertEquals("192.168.1.0", viewModel.state.value.designBaseIp)
        assertEquals("/24", viewModel.state.value.designBaseMask)

        viewModel.onDesignBaseIpChange("172.16.0.0")
        viewModel.onDesignBaseMaskChange("/20")
        val firstReqId = viewModel.state.value.vlsmRequirements.first().id
        viewModel.onUpdateVlsmRequirementName(firstReqId, "   ")
        assertEquals(
            VlsmOutcome.Failure(Ipv4Error.EmptyVlsmRequirementName(1)),
            viewModel.state.value.vlsmOutcome
        )
        viewModel.onUpdateVlsmRequirementName(firstReqId, "Campus")
        assertTrue(viewModel.state.value.vlsmOutcome is VlsmOutcome.Success)

        // Clearing VLSM clears ONLY VLSM; switching back to FLSM restores 10.0.0.0/16 & 8 subnets intact
        viewModel.clearInputs()
        assertEquals(VlsmOutcome.Failure(Ipv4Error.NoVlsmRequirements), viewModel.state.value.vlsmOutcome)

        viewModel.onSubnetDesignModeSelected(SubnetDesignMode.FLSM)
        assertEquals("10.0.0.0", viewModel.state.value.designBaseIp)
        assertEquals("/16", viewModel.state.value.designBaseMask)
        assertEquals("8", viewModel.state.value.flsmTargetInput)
        val flsmAfter = viewModel.state.value.flsmOutcome as FlsmOutcome.Success
        assertEquals("10.0.0.0/16", flsmAfter.result.baseNetwork.cidrNotation)
        assertEquals(19, flsmAfter.result.newPrefix)
    }
}
