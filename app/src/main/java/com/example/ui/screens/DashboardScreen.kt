package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Badge
import com.example.ui.components.AudienceCard
import com.example.ui.components.AudienceDetailDialog
import com.example.ui.components.PrefixInputDialog
import com.example.ui.components.VcfPreviewDialog
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.ProcessingSpeed
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val audiences by viewModel.audiences.collectAsStateWithLifecycle()
    val filteredAudiences by viewModel.filteredAudiences.collectAsStateWithLifecycle()
    val recentAudience by viewModel.recentAudience.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedAudience by viewModel.selectedAudience.collectAsStateWithLifecycle()
    val fileName by viewModel.currentFileName.collectAsStateWithLifecycle()
    val speedMode by viewModel.speedMode.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val targetPrefix by viewModel.targetPrefix.collectAsStateWithLifecycle()
    val isPrefixDialogOpen by viewModel.isPrefixDialogOpen.collectAsStateWithLifecycle()

    var showVcfPreviewDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // File Picker Launcher for CSV (GetContent is universally supported on all Android versions)
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFromUri(context, it) }
    }

    val openCsvPicker = {
        try {
            csvPickerLauncher.launch("*/*")
        } catch (t: Throwable) {
            Toast.makeText(context, "No file manager found: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // Save VCF Document Launcher (SAF) - streams directly to avoid OOM even on gigabyte VCFs
    val saveVcfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-vcard")
    ) { uri: Uri? ->
        uri?.let { destUri ->
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { os ->
                        viewModel.copyVcfToOutputStream(context, os)
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "VCF file saved successfully!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Error saving VCF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Restore previous session checkpoint and VCF file if app was accidentally closed
    LaunchedEffect(Unit) {
        viewModel.restorePreviousSessionOnLaunch(context)
    }

    // Show toast message if any
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    // Auto scroll to top when new records are added if we are at top
    LaunchedEffect(recentAudience) {
        if (audiences.isNotEmpty() && !listState.isScrollInProgress) {
            // keep user oriented
        }
    }

    fun shareVcfFile() {
        if (audiences.isEmpty()) {
            Toast.makeText(context, "No $targetPrefix audiences to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        val file = viewModel.prepareVcfFile(context)
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "$targetPrefix National Code Audiences (${audiences.size} contacts)")
            putExtra(Intent.EXTRA_TEXT, "Exported ${audiences.size} contacts with Latin-transliterated names.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share VCF File"))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Slate950,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Cyan400.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterAlt,
                                contentDescription = "Logo",
                                tint = Cyan400,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "ParsVCF Extractor",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "استخراج مخاطبین با کد ملی و ذخیره در VCF",
                                style = MaterialTheme.typography.labelSmall,
                                color = Cyan400
                            )
                        }
                    }
                },
                actions = {
                    // Prefix Change Button
                    OutlinedButton(
                        onClick = { viewModel.openPrefixDialog() },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("top_bar_prefix_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Amber400
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = "National Code Prefix",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = targetPrefix,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Speed Mode Button
                    OutlinedButton(
                        onClick = { viewModel.toggleSpeedMode() },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("toggle_speed_mode_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (speedMode == ProcessingSpeed.TURBO_FAST) Amber400 else Cyan400
                        )
                    ) {
                        Icon(
                            imageVector = if (speedMode == ProcessingSpeed.TURBO_FAST) Icons.Default.Bolt else Icons.Default.Speed,
                            contentDescription = "Speed mode",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (speedMode == ProcessingSpeed.TURBO_FAST) "Turbo" else "Live",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Reset / Re-run
                    IconButton(
                        onClick = { viewModel.restartCurrentProcessing(context) },
                        modifier = Modifier.testTag("restart_processing_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-run extraction",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 1. Real-Time Progress Card
            item {
                RealTimeProgressCard(
                    stats = stats,
                    fileName = fileName,
                    targetPrefix = targetPrefix,
                    onOpenPrefix = { viewModel.openPrefixDialog() },
                    onPauseResume = {
                        if (stats.isProcessing) {
                            viewModel.stopProcessing()
                        } else {
                            viewModel.restartCurrentProcessing(context)
                        }
                    }
                )
            }

            // 2. Metrics 4-Grid Card
            item {
                MetricsGrid(stats = stats)
            }

            // 3. Action Toolbar (Select CSV, Export VCF, Share, Preview)
            item {
                ActionToolbar(
                    onSelectCsv = openCsvPicker,
                    onExportVcf = {
                        if (audiences.isEmpty()) {
                            Toast.makeText(context, "No matched audiences to export.", Toast.LENGTH_SHORT).show()
                        } else {
                            try {
                                saveVcfLauncher.launch("audiences_${targetPrefix}_${System.currentTimeMillis()}.vcf")
                            } catch (t: Throwable) {
                                Toast.makeText(context, "Failed to launch save dialog: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onShareVcf = { shareVcfFile() },
                    onViewVcf = { showVcfPreviewDialog = true },
                    hasData = audiences.isNotEmpty()
                )
            }

            // 4. Audiences Section Header & Search
            item {
                AudienceHeaderAndSearch(
                    totalSaved = audiences.size,
                    filteredCount = filteredAudiences.size,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) }
                )
            }

            // 5. Audience List Items
            if (filteredAudiences.isEmpty()) {
                item {
                    EmptyAudienceState(
                        isProcessing = stats.isProcessing,
                        hasQuery = searchQuery.isNotBlank(),
                        totalAudienceCount = audiences.size,
                        targetPrefix = targetPrefix,
                        onSelectCsv = openCsvPicker
                    )
                }
            } else {
                items(
                    items = filteredAudiences,
                    key = { it.id }
                ) { record ->
                    AudienceCard(
                        record = record,
                        isRecent = record.id == recentAudience?.id,
                        onClick = { viewModel.selectAudience(record) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Detail Dialog for single audience
    selectedAudience?.let { audience ->
        AudienceDetailDialog(
            audience = audience,
            onDismiss = { viewModel.selectAudience(null) },
            onSaveLatinName = { updatedName ->
                viewModel.updateAudienceLatinName(audience.id, updatedName)
            }
        )
    }

    // Full VCF Preview Dialog
    if (showVcfPreviewDialog) {
        VcfPreviewDialog(
            vcfContent = viewModel.getFullVcfString(),
            totalAudienceCount = audiences.size,
            onDismiss = { showVcfPreviewDialog = false },
            onShare = {
                showVcfPreviewDialog = false
                shareVcfFile()
            },
            onSaveToDevice = {
                showVcfPreviewDialog = false
                saveVcfLauncher.launch("contacts_${targetPrefix}_${System.currentTimeMillis()}.vcf")
            }
        )
    }

    // National Code Prefix Input Dialog (prompted before processing or on demand)
    if (isPrefixDialogOpen) {
        PrefixInputDialog(
            initialPrefix = targetPrefix,
            fileName = if (fileName != "No CSV file loaded") fileName else null,
            onDismiss = { viewModel.closePrefixDialog() },
            onConfirm = { chosenPrefix ->
                viewModel.confirmPrefixAndStart(context, chosenPrefix)
            }
        )
    }
}

@Composable
private fun RealTimeProgressCard(
    stats: com.example.model.DashboardStats,
    fileName: String,
    targetPrefix: String,
    onOpenPrefix: () -> Unit,
    onPauseResume: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = stats.progressFraction,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "progress_bar_anim"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("realtime_progress_card"),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate700)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top: File name chip & processing status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "CSV File",
                        tint = Cyan400,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    // Target Prefix Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(Amber400.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .clickable { onOpenPrefix() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("progress_card_prefix_chip")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = targetPrefix,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Amber400
                        )
                    }
                }

                // Processing status chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .background(
                            if (stats.isProcessing) Cyan400.copy(alpha = 0.15f) else Emerald400.copy(alpha = 0.15f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    if (stats.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 2.dp,
                            color = Cyan400
                        )
                        Text(
                            text = "EXTRACTING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Cyan400
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Emerald400, CircleShape)
                        )
                        Text(
                            text = "COMPLETED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald400
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Percentage & Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Cyan400
                    )
                    Text(
                        text = " processed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                }

                val formattedProcessed = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(stats.totalRowsProcessed)
                Text(
                    text = "$formattedProcessed rows scanned",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Animated Real-Time Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF070E1A))
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("dashboard_progress_bar"),
                    color = if (stats.progressFraction >= 1f) Emerald400 else Cyan400,
                    trackColor = Color.Transparent
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status message & control button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stats.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onPauseResume,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("pause_resume_button")
                ) {
                    Icon(
                        imageVector = if (stats.isProcessing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (stats.isProcessing) "Pause" else "Resume",
                        tint = if (stats.isProcessing) Amber400 else Emerald400
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricsGrid(stats: com.example.model.DashboardStats) {
    val prefix = stats.targetPrefix
    val nf = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "Total Scanned",
                value = nf.format(stats.totalRowsProcessed),
                icon = Icons.Default.Storage,
                tint = Cyan400,
                modifier = Modifier.weight(1f),
                tag = "metric_total_scanned"
            )

            MetricCard(
                title = "$prefix + Mobile",
                value = nf.format(stats.matchedPrefixAndMobile),
                icon = Icons.Default.CheckCircle,
                tint = Emerald400,
                modifier = Modifier.weight(1f),
                tag = "metric_matched_audiences"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "Skipped (Non-$prefix)",
                value = nf.format(stats.skippedNonPrefix),
                icon = Icons.Default.FilterAlt,
                tint = Amber400,
                modifier = Modifier.weight(1f),
                tag = "metric_skipped_non_prefix"
            )

            MetricCard(
                title = "Skipped (No Mobile)",
                value = nf.format(stats.skippedMissingMobile),
                icon = Icons.Default.FilterAlt,
                tint = Rose500,
                modifier = Modifier.weight(1f),
                tag = "metric_skipped_no_mobile"
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    tag: String
) {
    Card(
        modifier = modifier.testTag(tag),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(tint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionToolbar(
    onSelectCsv: () -> Unit,
    onExportVcf: () -> Unit,
    onShareVcf: () -> Unit,
    onViewVcf: () -> Unit,
    hasData: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Select CSV File
        Button(
            onClick = onSelectCsv,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("select_csv_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan500)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = "Pick CSV",
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select CSV File", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        // Row 2: VCF actions (Export, Share, View)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onExportVcf,
                enabled = hasData,
                modifier = Modifier
                    .weight(1f)
                    .testTag("export_vcf_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Save VCF",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save VCF", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onShareVcf,
                enabled = hasData,
                modifier = Modifier
                    .weight(1f)
                    .testTag("share_vcf_button"),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate700)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Cyan400,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share", color = Color.White)
            }

            OutlinedButton(
                onClick = onViewVcf,
                enabled = hasData,
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("view_vcf_button"),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate700)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "View VCF",
                    tint = Amber400,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Preview", color = Color.White)
            }
        }
    }
}

@Composable
private fun AudienceHeaderAndSearch(
    totalSaved: Int,
    filteredCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Emerald400, CircleShape)
                )
                Text(
                    text = "Audiences Saved to VCF",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .background(Emerald400.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "$totalSaved Contacts" else "$filteredCount / $totalSaved",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Emerald400
                )
            }
        }

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search by Latin/Persian name, 469 code, mobile...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Cyan400
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_audiences_input"),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun EmptyAudienceState(
    isProcessing: Boolean,
    hasQuery: Boolean,
    totalAudienceCount: Int,
    targetPrefix: String,
    onSelectCsv: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Cyan400
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Filtering CSV records...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "Checking NATIONAL_CODE starting with '$targetPrefix' and valid MOBILE numbers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (hasQuery) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "No match",
                    tint = Slate700,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No audiences match '$totalAudienceCount' search",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Default.FilterAlt,
                    contentDescription = "No records",
                    tint = Cyan400,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No CSV Database Loaded",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Provide a CSV database containing:\nNATIONAL_CODE, CARD_NO, FULL_NAME, BIRTH_DATE, and MOBILE.\nRecords starting with '$targetPrefix' with a mobile number will be extracted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onSelectCsv,
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                    modifier = Modifier.testTag("empty_state_select_csv_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Select CSV",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select CSV File", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
