package com.bluemob.app.guide

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cabin
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class GuideCategory(val label: String, val color: Color, val icon: ImageVector) {
    FIRST_AID("First aid", Color(0xFFD94F55), Icons.Outlined.MedicalServices),
    WATER("Water", Color(0xFF2F7BC0), Icons.Outlined.WaterDrop),
    FIRE("Fire", Color(0xFFE07F2E), Icons.Outlined.LocalFireDepartment),
    SHELTER("Shelter", Color(0xFF11694E), Icons.Outlined.Cabin),
    NAVIGATION("Navigation", Color(0xFF7C6BD6), Icons.Outlined.Explore),
    SIGNALS("Signals", Color(0xFFC2621A), Icons.Outlined.Flag),
    WEATHER("Weather", Color(0xFF3D8FD9), Icons.Outlined.Bolt),
    DISASTERS("Disasters", Color(0xFF8A5A44), Icons.Outlined.WarningAmber),
    BASICS("Basics", Color(0xFF3A4A44), Icons.Outlined.Spa),
}
