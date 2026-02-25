package kaist.iclab.wearabletracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.dialog.Dialog
import kaist.iclab.wearabletracker.R
import kaist.iclab.wearabletracker.theme.AppSizes
import kaist.iclab.wearabletracker.theme.AppSpacing
import kaist.iclab.wearabletracker.theme.AppTypography
import kaist.iclab.wearabletracker.theme.SensorNameText

@Composable
fun AutoSyncSettings(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    intervalMs: Long,
    onIntervalChange: (Long) -> Unit
) {
    var showIntervalDialog by remember { mutableStateOf(false) }

    val intervals = listOf(
        0L to stringResource(R.string.interval_none),
        300_000L to stringResource(R.string.interval_5m),
        1_800_000L to stringResource(R.string.interval_30m),
        3_600_000L to stringResource(R.string.interval_1h),
        7_200_000L to stringResource(R.string.interval_2h),
        21_600_000L to stringResource(R.string.interval_6h),
        43_200_000L to stringResource(R.string.interval_12h)
    )

    val currentIntervalLabel = intervals.find { it.first == intervalMs }?.second
        ?: stringResource(R.string.interval_none)

    // Animated icon tint based on enabled state
    val syncIconColor by animateColorAsState(
        targetValue = if (enabled)
            MaterialTheme.colors.primary
        else
            MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
        label = "syncIconColor"
    )

    Column {
        // ── Auto-sync toggle chip ──
        ToggleChip(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppSpacing.sensorChipHorizontal,
                    end = AppSpacing.sensorChipHorizontal,
                    bottom = AppSpacing.sensorChipBottom
                )
                .height(AppSizes.sensorChipHeight),
            checked = enabled,
            onCheckedChange = onEnabledChange,
            label = {
                SensorNameText(
                    text = stringResource(R.string.auto_sync_label),
                    maxLines = 1
                )
            },
            appIcon = {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = syncIconColor,
                    modifier = Modifier.size(16.dp)
                )
            },
            toggleControl = {
                val switchOnText = stringResource(R.string.switch_on)
                val switchOffText = stringResource(R.string.switch_off)
                Switch(
                    checked = enabled,
                    modifier = Modifier.semantics {
                        this.contentDescription =
                            if (enabled) switchOnText else switchOffText
                    }
                )
            },
            colors = ToggleChipDefaults.toggleChipColors(
                checkedStartBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.15f),
                checkedEndBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.05f),
            )
        )

        // ── Interval selector chip ──
        Chip(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppSpacing.sensorChipHorizontal,
                    end = AppSpacing.sensorChipHorizontal,
                    bottom = AppSpacing.sensorChipBottom
                )
                .height(AppSizes.sensorChipHeight),
            onClick = { showIntervalDialog = true },
            enabled = enabled,
            label = {
                SensorNameText(
                    text = "${stringResource(R.string.auto_sync_interval_label)}: $currentIntervalLabel",
                    maxLines = 1
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = if (enabled)
                        MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = ChipDefaults.secondaryChipColors()
        )

        // Spacer before sensor list
        Spacer(modifier = Modifier.height(2.dp))
    }

    // ── Interval selection dialog ──
    Dialog(
        showDialog = showIntervalDialog,
        onDismissRequest = { showIntervalDialog = false }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.auto_sync_interval_label),
                    style = AppTypography.dialogTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )
            }
            items(intervals) { (ms, label) ->
                val isSelected = ms == intervalMs
                Chip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    onClick = {
                        onIntervalChange(ms)
                        showIntervalDialog = false
                    },
                    label = { Text(label) },
                    icon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    colors = if (isSelected) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }
        }
    }
}
