package com.calcsuits.domain.model.engineering

enum class Ipv4AddressScope {
    PRIVATE_RFC1918,
    PUBLIC_GLOBAL,
    LOOPBACK,
    LINK_LOCAL,
    CGNAT,
    MULTICAST,
    LIMITED_BROADCAST,
    THIS_NETWORK,
    DOCUMENTATION,
    BENCHMARKING,
    IETF_PROTOCOL,
    RESERVED_FUTURE
}

enum class HistoricalClassfulClass {
    CLASS_A,
    CLASS_B,
    CLASS_C,
    CLASS_D,
    CLASS_E
}

enum class SpecialPrefixKind {
    NONE,
    DEFAULT_ROUTE_0,
    POINT_TO_POINT_31,
    HOST_ROUTE_32
}

data class Ipv4AnalysisResult(
    val inputIp: String,
    val prefixLength: Int,
    val networkAddress: String,
    val networkLong: Long,
    val broadcastAddress: String,
    val broadcastLong: Long,
    val subnetMask: String,
    val wildcardMask: String,
    val firstUsableHost: String,
    val lastUsableHost: String,
    val totalAddresses: Long,
    val usableHosts: Long,
    val ipBinary: String,
    val maskBinary: String,
    val scope: Ipv4AddressScope,
    val historicalClass: HistoricalClassfulClass,
    val specialPrefixKind: SpecialPrefixKind
) {
    val cidrNotation: String
        get() = "$networkAddress/$prefixLength"

    val isSpecialOrReservedRange: Boolean
        get() = scope != Ipv4AddressScope.PUBLIC_GLOBAL && scope != Ipv4AddressScope.PRIVATE_RFC1918
}

enum class SubnetDesignMode(val key: String) {
    FLSM("flsm"),
    VLSM("vlsm")
}

enum class FlsmCriteria(val key: String) {
    BY_SUBNET_COUNT("subnets"),
    BY_HOSTS_PER_SUBNET("hosts")
}

data class SubnetAllocation(
    val index: Int,
    val name: String,
    val requestedHosts: Long? = null,
    val networkLong: Long,
    val broadcastLong: Long,
    val networkAddress: String,
    val prefixLength: Int,
    val subnetMask: String,
    val broadcastAddress: String,
    val firstUsableHost: String,
    val lastUsableHost: String,
    val totalCapacity: Long,
    val usableHosts: Long
) {
    val cidrNotation: String
        get() = "$networkAddress/$prefixLength"
}

data class FlsmDesignResult(
    val baseNetwork: Ipv4AnalysisResult,
    val criteria: FlsmCriteria,
    val requestedValue: Long,
    val newPrefix: Int,
    val borrowedBits: Int,
    val totalSubnets: Long,
    val capacityPerSubnet: Long,
    val usableHostsPerSubnet: Long,
    val subnetMask: String,
    val subnets: List<SubnetAllocation>,
    val isTruncated: Boolean = false
)

data class VlsmRequirement(
    val id: Long,
    val name: String,
    val requiredHosts: Long
)

data class VlsmDesignResult(
    val baseNetwork: Ipv4AnalysisResult,
    val allocations: List<SubnetAllocation>,
    val totalBaseAddresses: Long,
    val allocatedAddresses: Long,
    val remainingAddresses: Long,
    val remainingCidrBlocks: List<String>,
    val utilizationPercent: Double
)

sealed class Ipv4Error {
    data object MissingInput : Ipv4Error()
    data object InvalidIpAddress : Ipv4Error()
    data object PrefixOutOfRange : Ipv4Error()
    data object NonContiguousMask : Ipv4Error()
    data object InvalidSubnetMask : Ipv4Error()
    data object InvalidSubnetCount : Ipv4Error()
    data object InvalidHostCount : Ipv4Error()
    data class InsufficientCapacity(
        val requiredAddresses: Long,
        val availableAddresses: Long
    ) : Ipv4Error()
    data object NoVlsmRequirements : Ipv4Error()
    data class InvalidVlsmRequirement(val requirementName: String) : Ipv4Error()
}

sealed class Ipv4AnalysisOutcome {
    data class Success(val result: Ipv4AnalysisResult) : Ipv4AnalysisOutcome()
    data class Failure(val error: Ipv4Error) : Ipv4AnalysisOutcome()
}

sealed class FlsmOutcome {
    data class Success(val result: FlsmDesignResult) : FlsmOutcome()
    data class Failure(val error: Ipv4Error) : FlsmOutcome()
}

sealed class VlsmOutcome {
    data class Success(val result: VlsmDesignResult) : VlsmOutcome()
    data class Failure(val error: Ipv4Error) : VlsmOutcome()
}
