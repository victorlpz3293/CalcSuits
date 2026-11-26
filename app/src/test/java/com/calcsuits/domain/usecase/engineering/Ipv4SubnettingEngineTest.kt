package com.calcsuits.domain.usecase.engineering

import com.calcsuits.domain.model.engineering.FlsmCriteria
import com.calcsuits.domain.model.engineering.FlsmOutcome
import com.calcsuits.domain.model.engineering.HistoricalClassfulClass
import com.calcsuits.domain.model.engineering.Ipv4AddressScope
import com.calcsuits.domain.model.engineering.Ipv4AnalysisOutcome
import com.calcsuits.domain.model.engineering.Ipv4Error
import com.calcsuits.domain.model.engineering.SpecialPrefixKind
import com.calcsuits.domain.model.engineering.VlsmOutcome
import com.calcsuits.domain.model.engineering.VlsmRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Ipv4SubnettingEngineTest {

    private lateinit var engine: Ipv4SubnettingEngine

    @Before
    fun setUp() {
        engine = Ipv4SubnettingEngine()
    }

    // --- IPv4 Analysis: /8, /16, /24, variable prefixes, binary, decimal mask ---

    @Test
    fun analyzeSlash24ComputesAllFieldsAndBinaryRepresentationAccurately() {
        val outcome = engine.analyze("192.168.1.10", "/24")
        assertTrue(outcome is Ipv4AnalysisOutcome.Success)
        val res = (outcome as Ipv4AnalysisOutcome.Success).result

        assertEquals("192.168.1.0", res.networkAddress)
        assertEquals("192.168.1.255", res.broadcastAddress)
        assertEquals("255.255.255.0", res.subnetMask)
        assertEquals("0.0.0.255", res.wildcardMask)
        assertEquals("192.168.1.1", res.firstUsableHost)
        assertEquals("192.168.1.254", res.lastUsableHost)
        assertEquals(256L, res.totalAddresses)
        assertEquals(254L, res.usableHosts)
        assertEquals(24, res.prefixLength)
        assertEquals("11000000.10101000.00000001.00001010", res.ipBinary)
        assertEquals("11111111.11111111.11111111.00000000", res.maskBinary)
        assertEquals(Ipv4AddressScope.PRIVATE_RFC1918, res.scope)
        assertEquals(HistoricalClassfulClass.CLASS_C, res.historicalClass)
        assertEquals(SpecialPrefixKind.NONE, res.specialPrefixKind)
        assertFalse(res.isSpecialOrReservedRange)
    }

    @Test
    fun analyzeSlash8Slash16AndVariablePrefixesMatchIndependentCalculations() {
        // /8 network
        val s8 = (engine.analyze("10.20.30.40", "/8") as Ipv4AnalysisOutcome.Success).result
        assertEquals("10.0.0.0", s8.networkAddress)
        assertEquals("10.255.255.255", s8.broadcastAddress)
        assertEquals("255.0.0.0", s8.subnetMask)
        assertEquals("0.255.255.255", s8.wildcardMask)
        assertEquals("10.0.0.1", s8.firstUsableHost)
        assertEquals("10.255.255.254", s8.lastUsableHost)
        assertEquals(16_777_216L, s8.totalAddresses)
        assertEquals(16_777_214L, s8.usableHosts)
        assertEquals(Ipv4AddressScope.PRIVATE_RFC1918, s8.scope)
        assertEquals(HistoricalClassfulClass.CLASS_A, s8.historicalClass)

        // /16 network with decimal mask input
        val s16 = (engine.analyzeWithDecimalMask("172.16.45.12", "255.255.0.0") as Ipv4AnalysisOutcome.Success).result
        assertEquals("172.16.0.0", s16.networkAddress)
        assertEquals("172.16.255.255", s16.broadcastAddress)
        assertEquals(16, s16.prefixLength)
        assertEquals(65_536L, s16.totalAddresses)
        assertEquals(65_534L, s16.usableHosts)
        assertEquals(Ipv4AddressScope.PRIVATE_RFC1918, s16.scope)
        assertEquals(HistoricalClassfulClass.CLASS_B, s16.historicalClass)

        // Variable CIDR /22 (CIDR supernet of Class C range — verifies CIDR != classful)
        val s22 = (engine.analyze("192.168.5.77", "/22") as Ipv4AnalysisOutcome.Success).result
        assertEquals("192.168.4.0", s22.networkAddress)
        assertEquals("192.168.7.255", s22.broadcastAddress)
        assertEquals("255.255.252.0", s22.subnetMask)
        assertEquals("0.0.3.255", s22.wildcardMask)
        assertEquals("192.168.4.1", s22.firstUsableHost)
        assertEquals("192.168.7.254", s22.lastUsableHost)
        assertEquals(1_024L, s22.totalAddresses)
        assertEquals(1_022L, s22.usableHosts)
        assertEquals(HistoricalClassfulClass.CLASS_C, s22.historicalClass)
        assertEquals(22, s22.prefixLength)
    }

    @Test
    fun analyzeHandlesSlash0Slash31Slash32AndUpperBoundaryWithoutOverflow() {
        // /0 default route: full 2^32 IPv4 space (4,294,967,296) without Int overflow
        val s0 = (engine.analyze("0.0.0.0", "/0") as Ipv4AnalysisOutcome.Success).result
        assertEquals("0.0.0.0", s0.networkAddress)
        assertEquals("255.255.255.255", s0.broadcastAddress)
        assertEquals("0.0.0.0", s0.subnetMask)
        assertEquals("255.255.255.255", s0.wildcardMask)
        assertEquals("0.0.0.1", s0.firstUsableHost)
        assertEquals("255.255.255.254", s0.lastUsableHost)
        assertEquals(4_294_967_296L, s0.totalAddresses)
        assertEquals(4_294_967_294L, s0.usableHosts)
        assertEquals(SpecialPrefixKind.DEFAULT_ROUTE_0, s0.specialPrefixKind)

        // /31 RFC 3021 Point-to-Point
        val s31 = (engine.analyze("192.168.10.5", "/31") as Ipv4AnalysisOutcome.Success).result
        assertEquals("192.168.10.4", s31.networkAddress)
        assertEquals("192.168.10.5", s31.broadcastAddress)
        assertEquals("192.168.10.4", s31.firstUsableHost)
        assertEquals("192.168.10.5", s31.lastUsableHost)
        assertEquals(2L, s31.totalAddresses)
        assertEquals(2L, s31.usableHosts)
        assertEquals(SpecialPrefixKind.POINT_TO_POINT_31, s31.specialPrefixKind)

        // /32 Host route at upper IPv4 address limit 255.255.255.255
        val s32 = (engine.analyze("255.255.255.255", "/32") as Ipv4AnalysisOutcome.Success).result
        assertEquals("255.255.255.255", s32.networkAddress)
        assertEquals("255.255.255.255", s32.broadcastAddress)
        assertEquals("255.255.255.255", s32.firstUsableHost)
        assertEquals("255.255.255.255", s32.lastUsableHost)
        assertEquals(1L, s32.totalAddresses)
        assertEquals(1L, s32.usableHosts)
        assertEquals(SpecialPrefixKind.HOST_ROUTE_32, s32.specialPrefixKind)
        assertEquals(Ipv4AddressScope.LIMITED_BROADCAST, s32.scope)
    }

    @Test
    fun analyzeClassifiesPrivatePublicAndSpecialReservedRangesAccurately() {
        fun scopeOf(ip: String): Ipv4AddressScope =
            (engine.analyze(ip, "/24") as Ipv4AnalysisOutcome.Success).result.scope

        // Private RFC 1918
        assertEquals(Ipv4AddressScope.PRIVATE_RFC1918, scopeOf("10.0.0.1"))
        assertEquals(Ipv4AddressScope.PRIVATE_RFC1918, scopeOf("172.16.0.1"))
        assertEquals(Ipv4AddressScope.PRIVATE_RFC1918, scopeOf("172.31.255.254"))
        assertEquals(Ipv4AddressScope.PRIVATE_RFC1918, scopeOf("192.168.0.1"))

        // Public global unicast (adjacent to private/special boundaries)
        assertEquals(Ipv4AddressScope.PUBLIC_GLOBAL, scopeOf("8.8.8.8"))
        assertEquals(Ipv4AddressScope.PUBLIC_GLOBAL, scopeOf("1.1.1.1"))
        assertEquals(Ipv4AddressScope.PUBLIC_GLOBAL, scopeOf("172.15.255.1"))
        assertEquals(Ipv4AddressScope.PUBLIC_GLOBAL, scopeOf("172.32.0.1"))

        // Special & reserved ranges
        assertEquals(Ipv4AddressScope.LOOPBACK, scopeOf("127.0.0.1"))
        assertEquals(Ipv4AddressScope.LINK_LOCAL, scopeOf("169.254.10.20"))
        assertEquals(Ipv4AddressScope.CGNAT, scopeOf("100.64.1.1"))
        assertEquals(Ipv4AddressScope.MULTICAST, scopeOf("224.0.0.5"))
        assertEquals(Ipv4AddressScope.DOCUMENTATION, scopeOf("192.0.2.1"))
        assertEquals(Ipv4AddressScope.DOCUMENTATION, scopeOf("198.51.100.10"))
        assertEquals(Ipv4AddressScope.DOCUMENTATION, scopeOf("203.0.113.50"))
        assertEquals(Ipv4AddressScope.BENCHMARKING, scopeOf("198.18.0.1"))
        assertEquals(Ipv4AddressScope.IETF_PROTOCOL, scopeOf("192.0.0.1"))
        assertEquals(Ipv4AddressScope.RESERVED_FUTURE, scopeOf("240.0.0.1"))
        assertEquals(Ipv4AddressScope.THIS_NETWORK, scopeOf("0.10.20.30"))
    }

    @Test
    fun analyzeRejectsInvalidAddressesOutOfRangePrefixesAndNonContiguousMasks() {
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.InvalidIpAddress), engine.analyze("999.1.1.1", "/24"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.InvalidIpAddress), engine.analyze("192.168.1", "/24"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.InvalidIpAddress), engine.analyze("192.168.1.1.1", "/24"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.PrefixOutOfRange), engine.analyze("192.168.1.1", "/33"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.PrefixOutOfRange), engine.analyze("192.168.1.1", "/-1"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.NonContiguousMask), engine.analyze("192.168.1.1", "255.0.255.0"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.NonContiguousMask), engine.analyzeWithDecimalMask("192.168.1.1", "0.255.255.255"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.InvalidSubnetMask), engine.analyzeWithDecimalMask("192.168.1.1", "255.255.300.0"))
    }

    // --- FLSM Tests ---

    @Test
    fun flsmBySubnetCountDividesSlash24Into4EqualSlash26Subnets() {
        val outcome = engine.designFlsm(
            baseIp = "192.168.1.50", // host IP inside 192.168.1.0/24 -> normalized to network boundary
            basePrefixOrMask = "/24",
            criteria = FlsmCriteria.BY_SUBNET_COUNT,
            targetValue = 4L
        )
        assertTrue(outcome is FlsmOutcome.Success)
        val res = (outcome as FlsmOutcome.Success).result

        assertEquals("192.168.1.0/24", res.baseNetwork.cidrNotation)
        assertEquals(26, res.newPrefix)
        assertEquals(2, res.borrowedBits)
        assertEquals(4L, res.totalSubnets)
        assertEquals(64L, res.capacityPerSubnet)
        assertEquals(62L, res.usableHostsPerSubnet)
        assertEquals("255.255.255.192", res.subnetMask)
        assertEquals(4, res.subnets.size)
        assertFalse(res.isTruncated)

        // Independent verification of all 4 subnets
        assertEquals("192.168.1.0", res.subnets[0].networkAddress)
        assertEquals("192.168.1.1", res.subnets[0].firstUsableHost)
        assertEquals("192.168.1.62", res.subnets[0].lastUsableHost)
        assertEquals("192.168.1.63", res.subnets[0].broadcastAddress)

        assertEquals("192.168.1.64", res.subnets[1].networkAddress)
        assertEquals("192.168.1.127", res.subnets[1].broadcastAddress)

        assertEquals("192.168.1.128", res.subnets[2].networkAddress)
        assertEquals("192.168.1.191", res.subnets[2].broadcastAddress)

        assertEquals("192.168.1.192", res.subnets[3].networkAddress)
        assertEquals("192.168.1.193", res.subnets[3].firstUsableHost)
        assertEquals("192.168.1.254", res.subnets[3].lastUsableHost)
        assertEquals("192.168.1.255", res.subnets[3].broadcastAddress)
    }

    @Test
    fun flsmBySubnetCountRoundsUpToNextPowerOfTwoAndHandlesSlash31AndSlash32() {
        // Requesting 5 subnets from /24 borrows 3 bits -> 8 x /27
        val r5 = (engine.designFlsm("192.168.10.0", "/24", FlsmCriteria.BY_SUBNET_COUNT, 5L) as FlsmOutcome.Success).result
        assertEquals(27, r5.newPrefix)
        assertEquals(8L, r5.totalSubnets)
        assertEquals(30L, r5.usableHostsPerSubnet)

        // Requesting 2 subnets from /30 -> 2 x /31 (RFC 3021)
        val r31 = (engine.designFlsm("10.0.0.0", "/30", FlsmCriteria.BY_SUBNET_COUNT, 2L) as FlsmOutcome.Success).result
        assertEquals(31, r31.newPrefix)
        assertEquals(2L, r31.usableHostsPerSubnet)
        assertEquals("10.0.0.0", r31.subnets[0].firstUsableHost)
        assertEquals("10.0.0.1", r31.subnets[0].lastUsableHost)
        assertEquals("10.0.0.2", r31.subnets[1].firstUsableHost)
        assertEquals("10.0.0.3", r31.subnets[1].lastUsableHost)

        // Requesting 4 subnets from /30 -> 4 x /32 host routes
        val r32 = (engine.designFlsm("10.0.0.0", "/30", FlsmCriteria.BY_SUBNET_COUNT, 4L) as FlsmOutcome.Success).result
        assertEquals(32, r32.newPrefix)
        assertEquals(1L, r32.usableHostsPerSubnet)
        assertEquals("10.0.0.3", r32.subnets[3].networkAddress)
        assertEquals("10.0.0.3", r32.subnets[3].broadcastAddress)
    }

    @Test
    fun flsmByHostsPerSubnetSizesBlocksCorrectlyOnSlash16AndSlash8() {
        // 172.16.0.0/16 with 1,000 hosts per subnet -> needs h=10 (1022 usable) -> /22 -> 64 subnets
        val r16 = (engine.designFlsm("172.16.0.0", "/16", FlsmCriteria.BY_HOSTS_PER_SUBNET, 1_000L) as FlsmOutcome.Success).result
        assertEquals(22, r16.newPrefix)
        assertEquals(6, r16.borrowedBits)
        assertEquals(64L, r16.totalSubnets)
        assertEquals(1_024L, r16.capacityPerSubnet)
        assertEquals(1_022L, r16.usableHostsPerSubnet)
        assertEquals("172.16.0.0", r16.subnets.first().networkAddress)
        assertEquals("172.16.252.0", r16.subnets.last().networkAddress)
        assertEquals("172.16.255.255", r16.subnets.last().broadcastAddress)

        // 10.0.0.0/8 with 65,000 hosts per subnet -> h=16 (/16) -> 256 subnets
        val r8 = (engine.designFlsm("10.0.0.0", "/8", FlsmCriteria.BY_HOSTS_PER_SUBNET, 65_000L) as FlsmOutcome.Success).result
        assertEquals(16, r8.newPrefix)
        assertEquals(256L, r8.totalSubnets)
        assertEquals(65_534L, r8.usableHostsPerSubnet)
        assertEquals("10.255.0.0", r8.subnets.last().networkAddress)
        assertEquals("10.255.255.255", r8.subnets.last().broadcastAddress)
    }

    @Test
    fun flsmDetectsInsufficientCapacityAndUpperBoundaryWithoutOverflow() {
        // Asking for 300 subnets inside /24 (256 total addresses) -> InsufficientCapacity
        val tooManySubnets = engine.designFlsm("192.168.1.0", "/24", FlsmCriteria.BY_SUBNET_COUNT, 300L)
        assertTrue(tooManySubnets is FlsmOutcome.Failure)
        assertTrue((tooManySubnets as FlsmOutcome.Failure).error is Ipv4Error.InsufficientCapacity)

        // Asking for 300 hosts per subnet inside /24 -> InsufficientCapacity
        val tooManyHosts = engine.designFlsm("192.168.1.0", "/24", FlsmCriteria.BY_HOSTS_PER_SUBNET, 300L)
        assertTrue(tooManyHosts is FlsmOutcome.Failure)
        assertTrue((tooManyHosts as FlsmOutcome.Failure).error is Ipv4Error.InsufficientCapacity)

        // Upper boundary 255.255.255.0/24 divided into 4 subnets without signed overflow
        val upper = (engine.designFlsm("255.255.255.0", "/24", FlsmCriteria.BY_SUBNET_COUNT, 4L) as FlsmOutcome.Success).result
        assertEquals("255.255.255.192", upper.subnets.last().networkAddress)
        assertEquals("255.255.255.255", upper.subnets.last().broadcastAddress)
    }

    // --- VLSM Tests ---

    @Test
    fun vlsmSortsAlignsPreventsOverlapsAndComputesExactRemainingSpace() {
        // Unsorted requirements: WAN(2), HR(20), Sales(100), Eng(50) on 192.168.1.0/24
        val reqs = listOf(
            VlsmRequirement(1L, "WAN", 2L),
            VlsmRequirement(2L, "HR", 20L),
            VlsmRequirement(3L, "Sales", 100L),
            VlsmRequirement(4L, "Eng", 50L)
        )
        val outcome = engine.designVlsm("192.168.1.0", "/24", reqs)
        assertTrue(outcome is VlsmOutcome.Success)
        val res = (outcome as VlsmOutcome.Success).result

        assertEquals(4, res.allocations.size)

        // Sorted descending: Sales (/25), Eng (/26), HR (/27), WAN (/30)
        val sales = res.allocations[0]
        assertEquals("Sales", sales.name)
        assertEquals("192.168.1.0/25", sales.cidrNotation)
        assertEquals("255.255.255.128", sales.subnetMask)
        assertEquals("192.168.1.1", sales.firstUsableHost)
        assertEquals("192.168.1.126", sales.lastUsableHost)
        assertEquals("192.168.1.127", sales.broadcastAddress)
        assertEquals(128L, sales.totalCapacity)
        assertEquals(126L, sales.usableHosts)

        val eng = res.allocations[1]
        assertEquals("Eng", eng.name)
        assertEquals("192.168.1.128/26", eng.cidrNotation)
        assertEquals("192.168.1.129", eng.firstUsableHost)
        assertEquals("192.168.1.190", eng.lastUsableHost)
        assertEquals("192.168.1.191", eng.broadcastAddress)

        val hr = res.allocations[2]
        assertEquals("HR", hr.name)
        assertEquals("192.168.1.192/27", hr.cidrNotation)
        assertEquals("192.168.1.193", hr.firstUsableHost)
        assertEquals("192.168.1.222", hr.lastUsableHost)
        assertEquals("192.168.1.223", hr.broadcastAddress)

        val wan = res.allocations[3]
        assertEquals("WAN", wan.name)
        assertEquals("192.168.1.224/30", wan.cidrNotation)
        assertEquals("192.168.1.225", wan.firstUsableHost)
        assertEquals("192.168.1.226", wan.lastUsableHost)
        assertEquals("192.168.1.227", wan.broadcastAddress)

        // Totals: 128 + 64 + 32 + 4 = 228 allocated, 28 remaining
        assertEquals(256L, res.totalBaseAddresses)
        assertEquals(228L, res.allocatedAddresses)
        assertEquals(28L, res.remainingAddresses)
        assertEquals(
            listOf("192.168.1.228/30", "192.168.1.232/29", "192.168.1.240/28"),
            res.remainingCidrBlocks
        )

        // Verify strict alignment, zero overlaps, and base network containment for every allocation
        val baseStart = res.baseNetwork.networkLong
        val baseEnd = res.baseNetwork.broadcastLong
        for (i in res.allocations.indices) {
            val a = res.allocations[i]
            assertEquals("Block ${a.cidrNotation} must be power-of-2 aligned", 0L, a.networkLong % a.totalCapacity)
            assertTrue(a.networkLong >= baseStart)
            assertTrue(a.broadcastLong <= baseEnd)
            for (j in i + 1 until res.allocations.size) {
                val b = res.allocations[j]
                val overlaps = a.networkLong <= b.broadcastLong && b.networkLong <= a.broadcastLong
                assertFalse("Subnets ${a.cidrNotation} and ${b.cidrNotation} must never overlap", overlaps)
            }
        }
    }

    @Test
    fun vlsmDetectsAddressExhaustionAndInvalidRequirements() {
        // 192.168.1.0/24 (256 total): 100 (/25=128) + 100 (/25=128) + 2 (/30=4) = 260 > 256
        val exhausted = engine.designVlsm(
            baseIp = "192.168.1.0",
            basePrefixOrMask = "/24",
            requirements = listOf(
                VlsmRequirement(1L, "A", 100L),
                VlsmRequirement(2L, "B", 100L),
                VlsmRequirement(3L, "C", 2L)
            )
        )
        assertTrue(exhausted is VlsmOutcome.Failure)
        val err = (exhausted as VlsmOutcome.Failure).error
        assertTrue(err is Ipv4Error.InsufficientCapacity)
        assertEquals(260L, (err as Ipv4Error.InsufficientCapacity).requiredAddresses)
        assertEquals(256L, err.availableAddresses)

        // Exact 100% fill: 120 (/25=128) + 60 (/26=64) + 60 (/26=64) = 256 -> 0 remaining
        val exactFill = (engine.designVlsm(
            baseIp = "192.168.1.0",
            basePrefixOrMask = "/24",
            requirements = listOf(
                VlsmRequirement(1L, "A", 120L),
                VlsmRequirement(2L, "B", 60L),
                VlsmRequirement(3L, "C", 60L)
            )
        ) as VlsmOutcome.Success).result
        assertEquals(256L, exactFill.allocatedAddresses)
        assertEquals(0L, exactFill.remainingAddresses)
        assertTrue(exactFill.remainingCidrBlocks.isEmpty())

        // Empty list, blank name & zero host requirement
        assertEquals(
            VlsmOutcome.Failure(Ipv4Error.NoVlsmRequirements),
            engine.designVlsm("192.168.1.0", "/24", emptyList())
        )
        assertEquals(
            VlsmOutcome.Failure(Ipv4Error.EmptyVlsmRequirementName(1)),
            engine.designVlsm("192.168.1.0", "/24", listOf(VlsmRequirement(1L, "   ", 10L)))
        )
        assertEquals(
            VlsmOutcome.Failure(Ipv4Error.InvalidVlsmRequirement("Bad")),
            engine.designVlsm("192.168.1.0", "/24", listOf(VlsmRequirement(1L, "Bad", 0L)))
        )
    }

    @Test
    fun analyzeSlash1Slash30Rfc3021BroadcastSemanticsAndStrictLeadingZeroValidation() {
        // /1 upper half (128.0.0.0/1) verified independently
        val s1 = (engine.analyze("192.168.1.1", "/1") as Ipv4AnalysisOutcome.Success).result
        assertEquals("128.0.0.0", s1.networkAddress)
        assertEquals("255.255.255.255", s1.broadcastAddress)
        assertEquals("128.0.0.0", s1.subnetMask)
        assertEquals("127.255.255.255", s1.wildcardMask)
        assertEquals("128.0.0.1", s1.firstUsableHost)
        assertEquals("255.255.255.254", s1.lastUsableHost)
        assertEquals(2_147_483_648L, s1.totalAddresses)
        assertEquals(2_147_483_646L, s1.usableHosts)
        assertTrue(s1.hasConventionalBroadcast)

        // /30 classic point-to-point subnet
        val s30 = (engine.analyze("172.16.0.6", "/30") as Ipv4AnalysisOutcome.Success).result
        assertEquals("172.16.0.4", s30.networkAddress)
        assertEquals("172.16.0.7", s30.broadcastAddress)
        assertEquals("172.16.0.5", s30.firstUsableHost)
        assertEquals("172.16.0.6", s30.lastUsableHost)
        assertEquals(4L, s30.totalAddresses)
        assertEquals(2L, s30.usableHosts)
        assertTrue(s30.hasConventionalBroadcast)
        assertEquals("172.16.0.7", s30.effectiveBroadcastAddress)

        // /31 (RFC 3021) and /32 MUST NOT expose conventional broadcast
        val s31 = (engine.analyze("10.0.0.1", "/31") as Ipv4AnalysisOutcome.Success).result
        assertFalse(s31.hasConventionalBroadcast)
        assertEquals(null, s31.effectiveBroadcastAddress)
        assertEquals("10.0.0.0", s31.firstUsableHost)
        assertEquals("10.0.0.1", s31.lastUsableHost)

        val s32 = (engine.analyze("0.0.0.0", "/32") as Ipv4AnalysisOutcome.Success).result
        assertFalse(s32.hasConventionalBroadcast)
        assertEquals(null, s32.effectiveBroadcastAddress)
        assertEquals("0.0.0.0", s32.firstUsableHost)

        // Strict validation rejects leading zeros, sign prefixes, and malformed masks
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.InvalidIpAddress), engine.analyze("010.0.0.1", "/8"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.InvalidIpAddress), engine.analyze("192.168.01.10", "/24"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.InvalidIpAddress), engine.analyze("192.168.1.00", "/24"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.PrefixOutOfRange), engine.analyze("192.168.1.1", "/+24"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.PrefixOutOfRange), engine.analyze("192.168.1.1", "/08"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.PrefixOutOfRange), engine.analyze("192.168.1.1", "/-0"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.NonContiguousMask), engine.analyzeWithDecimalMask("10.0.0.1", "255.255.255.253"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.NonContiguousMask), engine.analyzeWithDecimalMask("10.0.0.1", "127.0.0.0"))
        assertEquals(Ipv4AnalysisOutcome.Failure(Ipv4Error.InvalidSubnetMask), engine.analyzeWithDecimalMask("10.0.0.1", "255.255.00.0"))

        // Decimal-mask-only resolver accepts valid dotted masks (/0, /1, /31, /32) and rejects bare integers
        assertEquals(0, engine.resolveDecimalMaskPrefixOrNull("0.0.0.0"))
        assertEquals(1, engine.resolveDecimalMaskPrefixOrNull("128.0.0.0"))
        assertEquals(31, engine.resolveDecimalMaskPrefixOrNull("255.255.255.254"))
        assertEquals(32, engine.resolveDecimalMaskPrefixOrNull("255.255.255.255"))
        assertEquals(null, engine.resolveDecimalMaskPrefixOrNull("24"))
        assertEquals(null, engine.resolveDecimalMaskPrefixOrNull("/24"))
        assertEquals(null, engine.resolveDecimalMaskPrefixOrNull("255.0.255.0"))
    }

    @Test
    fun flsmEnforcesExact1024MaterializationLimitAndTruncatesLargeDivisionsAccurately() {
        // Exactly 1,024 subnets: 10.0.0.0/16 -> 1024 x /26 -> NOT truncated
        val exact1024 = (engine.designFlsm(
            baseIp = "10.0.0.0",
            basePrefixOrMask = "/16",
            criteria = FlsmCriteria.BY_SUBNET_COUNT,
            targetValue = 1_024L
        ) as FlsmOutcome.Success).result
        assertEquals(26, exact1024.newPrefix)
        assertEquals(10, exact1024.borrowedBits)
        assertEquals(1_024L, exact1024.totalSubnets)
        assertEquals(1_024, exact1024.subnets.size)
        assertFalse(exact1024.isTruncated)
        assertEquals("10.0.255.192", exact1024.subnets.last().networkAddress)
        assertEquals("10.0.255.255", exact1024.subnets.last().broadcastAddress)

        // 1,025 requested -> rounds to 2,048 x /27 -> IS truncated at 1,024 materialized subnets
        val truncated2048 = (engine.designFlsm(
            baseIp = "10.0.0.0",
            basePrefixOrMask = "/16",
            criteria = FlsmCriteria.BY_SUBNET_COUNT,
            targetValue = 1_025L
        ) as FlsmOutcome.Success).result
        assertEquals(27, truncated2048.newPrefix)
        assertEquals(11, truncated2048.borrowedBits)
        assertEquals(2_048L, truncated2048.totalSubnets)
        assertEquals(Ipv4SubnettingEngine.MAX_GENERATED_FLSM_SUBNETS, truncated2048.subnets.size)
        assertTrue(truncated2048.isTruncated)
        // First 1,024 subnets of 10.0.0.0/16 divided into /27 (size 32) span 0..(1024*32 - 1) = 0..32767 = 10.0.127.255
        assertEquals("10.0.0.0", truncated2048.subnets.first().networkAddress)
        assertEquals("10.0.127.224", truncated2048.subnets.last().networkAddress)
        assertEquals("10.0.127.255", truncated2048.subnets.last().broadcastAddress)

        // Large /8 network by hosts (2 hosts -> /30 -> 4,194,304 subnets) without OOM or overflow
        val largeByHosts = (engine.designFlsm(
            baseIp = "10.0.0.0",
            basePrefixOrMask = "/8",
            criteria = FlsmCriteria.BY_HOSTS_PER_SUBNET,
            targetValue = 2L
        ) as FlsmOutcome.Success).result
        assertEquals(30, largeByHosts.newPrefix)
        assertEquals(4_194_304L, largeByHosts.totalSubnets)
        assertEquals(1_024, largeByHosts.subnets.size)
        assertTrue(largeByHosts.isTruncated)

        // Long.MAX_VALUE hosts must fail cleanly with InsufficientCapacity without Long overflow
        val hugeHosts = engine.designFlsm("10.0.0.0", "/8", FlsmCriteria.BY_HOSTS_PER_SUBNET, Long.MAX_VALUE)
        assertTrue(hugeHosts is FlsmOutcome.Failure)
        val err = (hugeHosts as FlsmOutcome.Failure).error as Ipv4Error.InsufficientCapacity
        assertTrue(err.requiredAddresses > 0L)
    }

    @Test
    fun vlsmPreservesOrderStabilityForEqualBlocksAndMathematicallyPartitionsEntireBaseSpace() {
        // Equal-sized blocks (10 hosts -> /28 each) mixed with larger & smaller blocks on 172.16.0.0/22 (1024 addrs)
        val reqs = listOf(
            VlsmRequirement(1L, "Branch-1", 10L),
            VlsmRequirement(2L, "Core", 250L),      // /24 (256)
            VlsmRequirement(3L, "Branch-2", 10L),
            VlsmRequirement(4L, "Link-A", 2L),      // /30 (4)
            VlsmRequirement(5L, "Branch-3", 10L),
            VlsmRequirement(6L, "WiFi", 120L)       // /25 (128)
        )
        val res = (engine.designVlsm("172.16.0.0", "/22", reqs) as VlsmOutcome.Success).result

        // Verify descending block order and strict relative stability for equal requirements Branch-1, Branch-2, Branch-3
        assertEquals(
            listOf("Core", "WiFi", "Branch-1", "Branch-2", "Branch-3", "Link-A"),
            res.allocations.map { it.name }
        )
        assertEquals(listOf(24, 25, 28, 28, 28, 30), res.allocations.map { it.prefixLength })

        // Independent mathematical partition proof:
        // Every address in [baseStart, baseEnd] must belong to EXACTLY ONE block in (allocations U remainingCidrBlocks)
        val baseStart = res.baseNetwork.networkLong
        val baseEnd = res.baseNetwork.broadcastLong
        val allocatedIntervals = res.allocations.map { it.networkLong to it.broadcastLong }
        val remainingIntervals = res.remainingCidrBlocks.map { parseCidrIntervalIndependently(it) }

        val remainingSumFromBlocks = remainingIntervals.sumOf { (s, e) -> e - s + 1L }
        assertEquals(
            "Sum of remainingCidrBlocks must exactly equal remainingAddresses",
            res.remainingAddresses,
            remainingSumFromBlocks
        )

        val allIntervals = (allocatedIntervals + remainingIntervals).sortedBy { it.first }
        assertEquals("Partition must start at base network", baseStart, allIntervals.first().first)
        assertEquals("Partition must end at base broadcast", baseEnd, allIntervals.last().second)
        for (i in 1 until allIntervals.size) {
            val prevEnd = allIntervals[i - 1].second
            val currStart = allIntervals[i].first
            assertEquals(
                "Intervals must tile contiguously with zero gaps and zero overlaps",
                prevEnd + 1L,
                currStart
            )
        }
        assertEquals(
            res.totalBaseAddresses,
            allIntervals.sumOf { (s, e) -> e - s + 1L }
        )
    }

    private fun parseCidrIntervalIndependently(cidr: String): Pair<Long, Long> {
        val (ipStr, prefixStr) = cidr.split("/")
        val prefix = prefixStr.toInt()
        val octets = ipStr.split(".").map { it.toLong() }
        val start = (octets[0] shl 24) or (octets[1] shl 16) or (octets[2] shl 8) or octets[3]
        val size = 1L shl (32 - prefix)
        assertEquals("Remaining CIDR block $cidr must be power-of-2 aligned", 0L, start % size)
        return start to (start + size - 1L)
    }
}
