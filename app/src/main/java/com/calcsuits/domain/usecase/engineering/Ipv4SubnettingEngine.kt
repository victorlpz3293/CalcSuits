package com.calcsuits.domain.usecase.engineering

import com.calcsuits.domain.model.engineering.FlsmCriteria
import com.calcsuits.domain.model.engineering.FlsmDesignResult
import com.calcsuits.domain.model.engineering.FlsmOutcome
import com.calcsuits.domain.model.engineering.HistoricalClassfulClass
import com.calcsuits.domain.model.engineering.Ipv4AddressScope
import com.calcsuits.domain.model.engineering.Ipv4AnalysisOutcome
import com.calcsuits.domain.model.engineering.Ipv4AnalysisResult
import com.calcsuits.domain.model.engineering.Ipv4Error
import com.calcsuits.domain.model.engineering.SpecialPrefixKind
import com.calcsuits.domain.model.engineering.SubnetAllocation
import com.calcsuits.domain.model.engineering.VlsmDesignResult
import com.calcsuits.domain.model.engineering.VlsmOutcome
import com.calcsuits.domain.model.engineering.VlsmRequirement
import javax.inject.Inject

/**
 * Pure domain IPv4 addressing, CIDR analysis, FLSM and VLSM engine.
 * All 32-bit IPv4 arithmetic is performed in unsigned 64-bit [Long] space (0..0xFFFFFFFFL)
 * to eliminate signed 32-bit integer overflow across all prefixes (/0../32) and address limits.
 */
class Ipv4SubnettingEngine @Inject constructor() {

    companion object {
        private const val MASK_32 = 0xFFFFFFFFL
        private const val TOTAL_IPV4_SPACE = 1L shl 32
        const val MAX_GENERATED_FLSM_SUBNETS = 1024
    }

    /**
     * Analyzes an IPv4 address using either a CIDR prefix (e.g. "/24" or "24")
     * or a dotted-decimal subnet mask (e.g. "255.255.255.0").
     */
    fun analyze(ipInput: String, prefixOrMaskInput: String): Ipv4AnalysisOutcome {
        val trimmedIp = ipInput.trim()
        val trimmedMask = prefixOrMaskInput.trim()

        if (trimmedIp.isEmpty() || trimmedMask.isEmpty()) {
            return Ipv4AnalysisOutcome.Failure(Ipv4Error.MissingInput)
        }

        val ipLong = parseIpv4(trimmedIp)
            ?: return Ipv4AnalysisOutcome.Failure(Ipv4Error.InvalidIpAddress)

        val prefix = when (val parsed = parsePrefixOrMask(trimmedMask)) {
            is PrefixParseResult.Valid -> parsed.prefix
            is PrefixParseResult.Invalid -> return Ipv4AnalysisOutcome.Failure(parsed.error)
        }

        return Ipv4AnalysisOutcome.Success(buildAnalysis(ipLong, prefix))
    }

    /**
     * Analyzes an IPv4 address with an explicit dotted-decimal mask (validating contiguity).
     */
    fun analyzeWithDecimalMask(ipInput: String, decimalMaskInput: String): Ipv4AnalysisOutcome {
        val trimmedIp = ipInput.trim()
        val trimmedMask = decimalMaskInput.trim()
        if (trimmedIp.isEmpty() || trimmedMask.isEmpty()) {
            return Ipv4AnalysisOutcome.Failure(Ipv4Error.MissingInput)
        }

        val ipLong = parseIpv4(trimmedIp)
            ?: return Ipv4AnalysisOutcome.Failure(Ipv4Error.InvalidIpAddress)

        val prefix = when (val parsed = parseDecimalMask(trimmedMask)) {
            is PrefixParseResult.Valid -> parsed.prefix
            is PrefixParseResult.Invalid -> return Ipv4AnalysisOutcome.Failure(parsed.error)
        }

        return Ipv4AnalysisOutcome.Success(buildAnalysis(ipLong, prefix))
    }

    /**
     * Converts a valid CIDR prefix (0..32) into its dotted-decimal mask string.
     */
    fun prefixToMaskString(prefix: Int): String? {
        if (prefix !in 0..32) return null
        return longToIp(prefixToMaskLong(prefix))
    }

    /**
     * Parses a CIDR prefix string ("/24" or "24") or dotted-decimal mask ("255.255.255.0")
     * and returns the prefix length (0..32), or null if invalid.
     */
    fun resolvePrefixOrNull(prefixOrMask: String): Int? =
        when (val res = parsePrefixOrMask(prefixOrMask.trim())) {
            is PrefixParseResult.Valid -> res.prefix
            is PrefixParseResult.Invalid -> null
        }

    /**
     * Designs Fixed-Length Subnet Mask (FLSM) blocks for [baseIp] / [basePrefixOrMask].
     *
     * - [FlsmCriteria.BY_SUBNET_COUNT]: borrows the minimum bits b where 2^b >= targetValue.
     * - [FlsmCriteria.BY_HOSTS_PER_SUBNET]: allocates the smallest host-bit block h >= 2
     *   such that (2^h - 2) >= targetValue (standard IPv4 subnet with network + broadcast).
     */
    fun designFlsm(
        baseIp: String,
        basePrefixOrMask: String,
        criteria: FlsmCriteria,
        targetValue: Long
    ): FlsmOutcome {
        val baseAnalysis = when (val outcome = analyze(baseIp, basePrefixOrMask)) {
            is Ipv4AnalysisOutcome.Success -> outcome.result
            is Ipv4AnalysisOutcome.Failure -> return FlsmOutcome.Failure(outcome.error)
        }

        val basePrefix = baseAnalysis.prefixLength
        val availableHostBits = 32 - basePrefix
        val baseTotal = baseAnalysis.totalAddresses

        val newPrefix: Int
        val borrowedBits: Int

        when (criteria) {
            FlsmCriteria.BY_SUBNET_COUNT -> {
                if (targetValue <= 0L) {
                    return FlsmOutcome.Failure(Ipv4Error.InvalidSubnetCount)
                }
                if (targetValue > baseTotal) {
                    return FlsmOutcome.Failure(
                        Ipv4Error.InsufficientCapacity(
                            requiredAddresses = targetValue,
                            availableAddresses = baseTotal
                        )
                    )
                }
                borrowedBits = ceilLog2(targetValue)
                if (borrowedBits > availableHostBits) {
                    return FlsmOutcome.Failure(
                        Ipv4Error.InsufficientCapacity(
                            requiredAddresses = 1L shl borrowedBits.coerceAtMost(32),
                            availableAddresses = baseTotal
                        )
                    )
                }
                newPrefix = basePrefix + borrowedBits
            }

            FlsmCriteria.BY_HOSTS_PER_SUBNET -> {
                if (targetValue <= 0L) {
                    return FlsmOutcome.Failure(Ipv4Error.InvalidHostCount)
                }
                val hostBits = requiredHostBitsForStandardSubnet(targetValue)
                    ?: return FlsmOutcome.Failure(
                        Ipv4Error.InsufficientCapacity(
                            requiredAddresses = targetValue + 2L,
                            availableAddresses = baseTotal
                        )
                    )
                if (hostBits > availableHostBits) {
                    return FlsmOutcome.Failure(
                        Ipv4Error.InsufficientCapacity(
                            requiredAddresses = 1L shl hostBits,
                            availableAddresses = baseTotal
                        )
                    )
                }
                newPrefix = 32 - hostBits
                borrowedBits = newPrefix - basePrefix
            }
        }

        val totalSubnets = 1L shl borrowedBits
        val blockSize = 1L shl (32 - newPrefix)
        val usablePerSubnet = usableHostsForPrefix(newPrefix)
        val maskStr = longToIp(prefixToMaskLong(newPrefix))
        val generateCount = minOf(totalSubnets, MAX_GENERATED_FLSM_SUBNETS.toLong()).toInt()
        val isTruncated = totalSubnets > MAX_GENERATED_FLSM_SUBNETS.toLong()

        val subnets = ArrayList<SubnetAllocation>(generateCount)
        val baseStart = baseAnalysis.networkLong
        for (i in 0 until generateCount) {
            val net = baseStart + i.toLong() * blockSize
            val bcast = net + blockSize - 1L
            subnets.add(
                buildAllocation(
                    index = i + 1,
                    name = "#${i + 1}",
                    requestedHosts = if (criteria == FlsmCriteria.BY_HOSTS_PER_SUBNET) targetValue else null,
                    networkLong = net,
                    broadcastLong = bcast,
                    prefixLength = newPrefix,
                    subnetMask = maskStr,
                    totalCapacity = blockSize,
                    usableHosts = usablePerSubnet
                )
            )
        }

        return FlsmOutcome.Success(
            FlsmDesignResult(
                baseNetwork = baseAnalysis,
                criteria = criteria,
                requestedValue = targetValue,
                newPrefix = newPrefix,
                borrowedBits = borrowedBits,
                totalSubnets = totalSubnets,
                capacityPerSubnet = blockSize,
                usableHostsPerSubnet = usablePerSubnet,
                subnetMask = maskStr,
                subnets = subnets,
                isTruncated = isTruncated
            )
        )
    }

    /**
     * Designs Variable-Length Subnet Mask (VLSM) allocations within [baseIp] / [basePrefixOrMask].
     *
     * Requirements are sorted descending by required host capacity so that power-of-2 CIDR blocks
     * pack contiguously with natural boundary alignment, zero overlap, and maximum remaining space.
     */
    fun designVlsm(
        baseIp: String,
        basePrefixOrMask: String,
        requirements: List<VlsmRequirement>
    ): VlsmOutcome {
        val baseAnalysis = when (val outcome = analyze(baseIp, basePrefixOrMask)) {
            is Ipv4AnalysisOutcome.Success -> outcome.result
            is Ipv4AnalysisOutcome.Failure -> return VlsmOutcome.Failure(outcome.error)
        }

        if (requirements.isEmpty()) {
            return VlsmOutcome.Failure(Ipv4Error.NoVlsmRequirements)
        }

        val sizedReqs = mutableListOf<SizedVlsmReq>()
        var totalRequiredAddresses = 0L

        for ((idx, req) in requirements.withIndex()) {
            if (req.requiredHosts <= 0L) {
                return VlsmOutcome.Failure(Ipv4Error.InvalidVlsmRequirement(req.name))
            }
            val hostBits = requiredHostBitsForStandardSubnet(req.requiredHosts)
                ?: return VlsmOutcome.Failure(
                    Ipv4Error.InsufficientCapacity(
                        requiredAddresses = req.requiredHosts + 2L,
                        availableAddresses = baseAnalysis.totalAddresses
                    )
                )
            val prefix = 32 - hostBits
            val blockSize = 1L shl hostBits
            if (totalRequiredAddresses <= TOTAL_IPV4_SPACE) {
                totalRequiredAddresses += blockSize
            }
            sizedReqs.add(
                SizedVlsmReq(
                    originalIndex = idx,
                    requirement = req,
                    prefixLength = prefix,
                    blockSize = blockSize
                )
            )
        }

        if (totalRequiredAddresses > baseAnalysis.totalAddresses) {
            return VlsmOutcome.Failure(
                Ipv4Error.InsufficientCapacity(
                    requiredAddresses = totalRequiredAddresses,
                    availableAddresses = baseAnalysis.totalAddresses
                )
            )
        }

        // Sort largest required block first; stable on original index for equal sizes.
        val sorted = sizedReqs.sortedWith(
            compareByDescending<SizedVlsmReq> { it.blockSize }
                .thenByDescending { it.requirement.requiredHosts }
                .thenBy { it.originalIndex }
        )

        val baseStart = baseAnalysis.networkLong
        val baseEnd = baseAnalysis.broadcastLong
        var cursor = baseStart
        val allocations = ArrayList<SubnetAllocation>(sorted.size)

        for ((idx, item) in sorted.withIndex()) {
            val blockSize = item.blockSize
            // Enforce strict power-of-2 CIDR boundary alignment
            if (cursor % blockSize != 0L) {
                cursor = ((cursor / blockSize) + 1L) * blockSize
            }
            val endAddr = cursor + blockSize - 1L
            if (endAddr > baseEnd) {
                return VlsmOutcome.Failure(
                    Ipv4Error.InsufficientCapacity(
                        requiredAddresses = totalRequiredAddresses,
                        availableAddresses = baseAnalysis.totalAddresses
                    )
                )
            }

            val maskStr = longToIp(prefixToMaskLong(item.prefixLength))
            val usable = usableHostsForPrefix(item.prefixLength)
            val label = item.requirement.name.trim().ifEmpty { "#${idx + 1}" }

            allocations.add(
                buildAllocation(
                    index = idx + 1,
                    name = label,
                    requestedHosts = item.requirement.requiredHosts,
                    networkLong = cursor,
                    broadcastLong = endAddr,
                    prefixLength = item.prefixLength,
                    subnetMask = maskStr,
                    totalCapacity = blockSize,
                    usableHosts = usable
                )
            )
            cursor = endAddr + 1L
        }

        val allocatedAddresses = allocations.sumOf { it.totalCapacity }
        val remainingAddresses = baseAnalysis.totalAddresses - allocatedAddresses
        val remainingBlocks = summarizeCidrRange(cursor, baseEnd)
        val utilization = (allocatedAddresses.toDouble() / baseAnalysis.totalAddresses.toDouble()) * 100.0

        return VlsmOutcome.Success(
            VlsmDesignResult(
                baseNetwork = baseAnalysis,
                allocations = allocations,
                totalBaseAddresses = baseAnalysis.totalAddresses,
                allocatedAddresses = allocatedAddresses,
                remainingAddresses = remainingAddresses,
                remainingCidrBlocks = remainingBlocks,
                utilizationPercent = utilization
            )
        )
    }

    private fun buildAnalysis(ipLong: Long, prefix: Int): Ipv4AnalysisResult {
        val maskLong = prefixToMaskLong(prefix)
        val wildcardLong = maskLong.inv() and MASK_32
        val networkLong = ipLong and maskLong
        val broadcastLong = (networkLong or wildcardLong) and MASK_32
        val totalAddresses = 1L shl (32 - prefix)
        val usableHosts = usableHostsForPrefix(prefix)
        val firstHostLong = if (prefix <= 30) networkLong + 1L else networkLong
        val lastHostLong = if (prefix <= 30) broadcastLong - 1L else broadcastLong

        val specialPrefixKind = when (prefix) {
            0 -> SpecialPrefixKind.DEFAULT_ROUTE_0
            31 -> SpecialPrefixKind.POINT_TO_POINT_31
            32 -> SpecialPrefixKind.HOST_ROUTE_32
            else -> SpecialPrefixKind.NONE
        }

        return Ipv4AnalysisResult(
            inputIp = longToIp(ipLong),
            prefixLength = prefix,
            networkAddress = longToIp(networkLong),
            networkLong = networkLong,
            broadcastAddress = longToIp(broadcastLong),
            broadcastLong = broadcastLong,
            subnetMask = longToIp(maskLong),
            wildcardMask = longToIp(wildcardLong),
            firstUsableHost = longToIp(firstHostLong),
            lastUsableHost = longToIp(lastHostLong),
            totalAddresses = totalAddresses,
            usableHosts = usableHosts,
            ipBinary = longToBinary(ipLong),
            maskBinary = longToBinary(maskLong),
            scope = classifyScope(ipLong),
            historicalClass = classifyHistoricalClass(ipLong),
            specialPrefixKind = specialPrefixKind
        )
    }

    private fun buildAllocation(
        index: Int,
        name: String,
        requestedHosts: Long?,
        networkLong: Long,
        broadcastLong: Long,
        prefixLength: Int,
        subnetMask: String,
        totalCapacity: Long,
        usableHosts: Long
    ): SubnetAllocation {
        val firstHostLong = if (prefixLength <= 30) networkLong + 1L else networkLong
        val lastHostLong = if (prefixLength <= 30) broadcastLong - 1L else broadcastLong
        return SubnetAllocation(
            index = index,
            name = name,
            requestedHosts = requestedHosts,
            networkLong = networkLong,
            broadcastLong = broadcastLong,
            networkAddress = longToIp(networkLong),
            prefixLength = prefixLength,
            subnetMask = subnetMask,
            broadcastAddress = longToIp(broadcastLong),
            firstUsableHost = longToIp(firstHostLong),
            lastUsableHost = longToIp(lastHostLong),
            totalCapacity = totalCapacity,
            usableHosts = usableHosts
        )
    }

    private fun summarizeCidrRange(start: Long, end: Long): List<String> {
        if (start > end) return emptyList()
        val blocks = mutableListOf<String>()
        var cursor = start
        while (cursor <= end) {
            val remaining = end - cursor + 1L
            val maxAlign = if (cursor == 0L) TOTAL_IPV4_SPACE else java.lang.Long.lowestOneBit(cursor)
            var block = 1L
            while (block < maxAlign && (block shl 1) <= remaining) {
                block = block shl 1
            }
            val prefix = 32 - java.lang.Long.numberOfTrailingZeros(block)
            blocks.add("${longToIp(cursor)}/$prefix")
            cursor += block
        }
        return blocks
    }

    private fun requiredHostBitsForStandardSubnet(requiredHosts: Long): Int? {
        if (requiredHosts <= 0L) return null
        // Minimum h = 2 (/30: 4 addresses, 2 usable hosts) for standard LAN/WAN subnet with network & broadcast
        for (h in 2..32) {
            val usable = (1L shl h) - 2L
            if (usable >= requiredHosts) return h
        }
        return null
    }

    private fun ceilLog2(value: Long): Int {
        if (value <= 1L) return 0
        return 64 - java.lang.Long.numberOfLeadingZeros(value - 1L)
    }

    private fun usableHostsForPrefix(prefix: Int): Long = when (prefix) {
        32 -> 1L
        31 -> 2L
        else -> ((1L shl (32 - prefix)) - 2L).coerceAtLeast(0L)
    }

    private fun parseIpv4(raw: String): Long? {
        val parts = raw.split(".")
        if (parts.size != 4) return null
        var acc = 0L
        for (part in parts) {
            if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            acc = (acc shl 8) or octet.toLong()
        }
        return acc and MASK_32
    }

    private fun parsePrefixOrMask(raw: String): PrefixParseResult {
        if (raw.contains(".")) {
            return parseDecimalMask(raw)
        }
        val stripped = if (raw.startsWith("/")) raw.drop(1) else raw
        if (stripped.isEmpty()) return PrefixParseResult.Invalid(Ipv4Error.PrefixOutOfRange)
        val parsed = stripped.toIntOrNull() ?: return PrefixParseResult.Invalid(Ipv4Error.PrefixOutOfRange)
        if (parsed !in 0..32) return PrefixParseResult.Invalid(Ipv4Error.PrefixOutOfRange)
        return PrefixParseResult.Valid(parsed)
    }

    private fun parseDecimalMask(raw: String): PrefixParseResult {
        val maskLong = parseIpv4(raw) ?: return PrefixParseResult.Invalid(Ipv4Error.InvalidSubnetMask)
        val inv = maskLong.inv() and MASK_32
        if ((inv and (inv + 1L)) != 0L) {
            return PrefixParseResult.Invalid(Ipv4Error.NonContiguousMask)
        }
        return PrefixParseResult.Valid(java.lang.Long.bitCount(maskLong))
    }

    private fun prefixToMaskLong(prefix: Int): Long =
        if (prefix == 0) 0L else (MASK_32 shl (32 - prefix)) and MASK_32

    private fun longToIp(value: Long): String =
        "${(value ushr 24) and 255L}.${(value ushr 16) and 255L}.${(value ushr 8) and 255L}.${value and 255L}"

    private fun longToBinary(value: Long): String = buildString(35) {
        append(((value ushr 24) and 255L).toString(2).padStart(8, '0'))
        append('.')
        append(((value ushr 16) and 255L).toString(2).padStart(8, '0'))
        append('.')
        append(((value ushr 8) and 255L).toString(2).padStart(8, '0'))
        append('.')
        append((value and 255L).toString(2).padStart(8, '0'))
    }

    private fun classifyScope(ipLong: Long): Ipv4AddressScope = when {
        ipLong == MASK_32 -> Ipv4AddressScope.LIMITED_BROADCAST
        (ipLong and 0xFF000000L) == 0x00000000L -> Ipv4AddressScope.THIS_NETWORK
        (ipLong and 0xFF000000L) == 0x0A000000L -> Ipv4AddressScope.PRIVATE_RFC1918
        (ipLong and 0xFFC00000L) == 0x64400000L -> Ipv4AddressScope.CGNAT
        (ipLong and 0xFF000000L) == 0x7F000000L -> Ipv4AddressScope.LOOPBACK
        (ipLong and 0xFFFF0000L) == 0xA9FE0000L -> Ipv4AddressScope.LINK_LOCAL
        (ipLong and 0xFFF00000L) == 0xAC100000L -> Ipv4AddressScope.PRIVATE_RFC1918
        (ipLong and 0xFFFFFF00L) == 0xC0000000L -> Ipv4AddressScope.IETF_PROTOCOL
        (ipLong and 0xFFFFFF00L) == 0xC0000200L -> Ipv4AddressScope.DOCUMENTATION
        (ipLong and 0xFFFF0000L) == 0xC0A80000L -> Ipv4AddressScope.PRIVATE_RFC1918
        (ipLong and 0xFFFE0000L) == 0xC6120000L -> Ipv4AddressScope.BENCHMARKING
        (ipLong and 0xFFFFFF00L) == 0xC6336400L -> Ipv4AddressScope.DOCUMENTATION
        (ipLong and 0xFFFFFF00L) == 0xCB007100L -> Ipv4AddressScope.DOCUMENTATION
        (ipLong and 0xF0000000L) == 0xE0000000L -> Ipv4AddressScope.MULTICAST
        (ipLong and 0xF0000000L) == 0xF0000000L -> Ipv4AddressScope.RESERVED_FUTURE
        else -> Ipv4AddressScope.PUBLIC_GLOBAL
    }

    private fun classifyHistoricalClass(ipLong: Long): HistoricalClassfulClass {
        val firstOctet = ((ipLong ushr 24) and 255L).toInt()
        return when (firstOctet) {
            in 0..127 -> HistoricalClassfulClass.CLASS_A
            in 128..191 -> HistoricalClassfulClass.CLASS_B
            in 192..223 -> HistoricalClassfulClass.CLASS_C
            in 224..239 -> HistoricalClassfulClass.CLASS_D
            else -> HistoricalClassfulClass.CLASS_E
        }
    }

    private data class SizedVlsmReq(
        val originalIndex: Int,
        val requirement: VlsmRequirement,
        val prefixLength: Int,
        val blockSize: Long
    )

    private sealed class PrefixParseResult {
        data class Valid(val prefix: Int) : PrefixParseResult()
        data class Invalid(val error: Ipv4Error) : PrefixParseResult()
    }
}
