package com.example.crayfarm

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NavigationBlue = Color(0xFF2639A6)


@Composable
fun BottomNavigation(
    selectedPage: AppPage,
    userRole: String,
    onPageSelected: (AppPage) -> Unit
) {
    val thirdLabel = if (userRole == "admin") "Employees" else "Profile"
    val thirdIcon = if (userRole == "admin") {
        Icons.Rounded.Groups
    } else {
        Icons.Rounded.Person
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = NavigationBlue.copy(alpha = 0.12f)
        ),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 5.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavigationItem(
                icon = Icons.Rounded.Home,
                label = "Dashboard",
                selected = selectedPage == AppPage.DASHBOARD,
                onClick = { onPageSelected(AppPage.DASHBOARD) }
            )
            NavigationItem(
                icon = Icons.Rounded.Insights,
                label = "Analytics",
                selected = selectedPage == AppPage.ANALYTICS,
                onClick = { onPageSelected(AppPage.ANALYTICS) }
            )
            NavigationItem(
                icon = Icons.Rounded.Description,
                label = "Reports",
                selected = selectedPage == AppPage.REPORTS,
                onClick = { onPageSelected(AppPage.REPORTS) }
            )
            NavigationItem(
                icon = thirdIcon,
                label = thirdLabel,
                selected = selectedPage == AppPage.PROFILE_OR_EMPLOYEES,
                onClick = { onPageSelected(AppPage.PROFILE_OR_EMPLOYEES) }
            )
            NavigationItem(
                icon = Icons.Rounded.Settings,
                label = "Settings",
                selected = selectedPage == AppPage.SETTINGS,
                onClick = { onPageSelected(AppPage.SETTINGS) }
            )
        }
    }
}

@Composable
private fun NavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .width(43.dp)
                .height(30.dp),
            color = if (selected) {
                NavigationBlue.copy(alpha = 0.12f)
            } else {
                Color.Transparent
            },
            shape = RoundedCornerShape(15.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) {
                        NavigationBlue
                    } else {
                        Color(0xFF68708A)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) NavigationBlue else Color(0xFF68708A),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun PageHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF17266F), NavigationBlue)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .width(4.dp)
                .height(28.dp),
            color = Color(0xFF8FA0FF),
            shape = RoundedCornerShape(2.dp)
        ) {}

        Spacer(modifier = Modifier.width(11.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "CRAYFARM",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
        }
    }
}
