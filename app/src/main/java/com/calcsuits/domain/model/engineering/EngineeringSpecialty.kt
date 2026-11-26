package com.calcsuits.domain.model.engineering

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Memory
import androidx.compose.ui.graphics.vector.ImageVector
import com.calcsuits.R

enum class EngineeringSpecialty(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val toolSummaryRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector
) {
    ELECTRICAL(
        key = "electrical",
        titleRes = R.string.eng_specialty_electrical,
        toolSummaryRes = R.string.eng_tool_electrical_calculator,
        descriptionRes = R.string.eng_specialty_electrical_desc,
        icon = Icons.Default.ElectricBolt
    ),
    NETWORKING(
        key = "networking",
        titleRes = R.string.eng_specialty_networking,
        toolSummaryRes = R.string.eng_specialty_networking_tools,
        descriptionRes = R.string.eng_specialty_networking_desc,
        icon = Icons.Default.Lan
    ),
    DIGITAL_ELECTRONICS(
        key = "digital_electronics",
        titleRes = R.string.eng_specialty_digital_electronics,
        toolSummaryRes = R.string.eng_tool_digital_logic,
        descriptionRes = R.string.eng_specialty_digital_electronics_desc,
        icon = Icons.Default.Memory
    )
}

enum class NetworkingTool(
    val key: String,
    @StringRes val titleRes: Int
) {
    IPV4_ANALYSIS(
        key = "ipv4_analysis",
        titleRes = R.string.eng_tool_ipv4_analysis
    ),
    SUBNET_DESIGN(
        key = "subnet_design",
        titleRes = R.string.eng_tool_subnet_design
    )
}
