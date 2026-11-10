package com.rcapture.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rcapture.R
import com.rcapture.data.CaptureEntity
import com.rcapture.intelligence.EntityCategory
import com.rcapture.intelligence.ExtractedEntity
import com.rcapture.service.RcaptureAccessibilityService
import com.rcapture.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: RcaptureViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onImageSelected(uri)
    }

    HomeScreenContent(
        uiState = uiState,
        isAccessibilityServiceRunning = RcaptureAccessibilityService.isServiceRunning,
        onPickImage = {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onClearAll = { viewModel.clearAll() },
        onSaveCurrentText = { viewModel.saveCurrentText() },
        onDeleteCapture = { viewModel.deleteCapture(it) },
        onClearAllHistory = { viewModel.clearAllHistory() },
        onSetStatusMessage = { viewModel.setStatusMessage(it) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    uiState: RcaptureUiState,
    isAccessibilityServiceRunning: Boolean = false,
    onPickImage: () -> Unit = {},
    onClearAll: () -> Unit = {},
    onSaveCurrentText: () -> Unit = {},
    onDeleteCapture: (CaptureEntity) -> Unit = {},
    onClearAllHistory: () -> Unit = {},
    onSetStatusMessage: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showClearDialog by remember { mutableStateOf(false) }

    var resultViewMode by remember { mutableStateOf(0) } // 0 = Raw Text, 1 = Structured Entities
    var entitySearchQuery by remember { mutableStateOf("") }
    var selectedEntityCategory by remember { mutableStateOf<EntityCategory?>(null) }

    val filteredEntities = remember(uiState.detectedEntities, entitySearchQuery, selectedEntityCategory) {
        uiState.detectedEntities.filter { entity ->
            val matchesCategory = (selectedEntityCategory == null) || entity.category == selectedEntityCategory
            val matchesQuery = entitySearchQuery.isBlank() ||
                    entity.value.contains(entitySearchQuery, ignoreCase = true) ||
                    entity.category.displayName.contains(entitySearchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All History") },
            text = { Text("Are you sure you want to delete all saved captures? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAllHistory()
                    showClearDialog = false
                }) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        if (uiState.selectedImageUri != null || uiState.extractedText.isNotEmpty()) {
                            IconButton(
                                onClick = onClearAll,
                                modifier = Modifier.testTag("clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.clear_text)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Banner
                uiState.statusMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("status_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Floating Overlay Info Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("floating_overlay_info_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Floating Capture & Split-Screen Aggregation",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "When active, '📷 R-Cap' floats over any app to capture active window text and multi-window split-screens on demand.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Image Picker Card / Preview
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .testTag("image_picker_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.selectedImageUri != null) {
                            AsyncImage(
                                model = uiState.selectedImageUri,
                                contentDescription = stringResource(R.string.select_image),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f))
                            )
                            Button(
                                onClick = onPickImage,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.testTag("change_image_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Change Image")
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Select an image for on-device OCR",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = onPickImage,
                                    modifier = Modifier.testTag("select_image_button")
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.select_image))
                                }
                            }
                        }

                        if (uiState.isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.processing_ocr),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Error Display
                uiState.errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Extracted Text & Structured Intelligence Results Section
                if (uiState.extractedText.isNotEmpty() || uiState.isLoading) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("extracted_text_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Header with Save, Copy, Share
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "EXTRACTION RESULTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = onSaveCurrentText,
                                        modifier = Modifier.testTag("save_button")
                                    ) {
                                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Save text")
                                    }
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("R-Capture Text", uiState.extractedText)
                                            clipboard.setPrimaryClip(clip)
                                            onSetStatusMessage(context.getString(R.string.copied_to_clipboard))
                                        },
                                        modifier = Modifier.testTag("copy_button")
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_text))
                                    }
                                    IconButton(
                                        onClick = {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, uiState.extractedText)
                                                type = "text/plain"
                                            }
                                            val shareIntent = Intent.createChooser(sendIntent, null)
                                            context.startActivity(shareIntent)
                                        },
                                        modifier = Modifier.testTag("share_button")
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_text))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Segmented Tabs: Raw Text vs Structured Entities
                            TabRow(
                                selectedTabIndex = resultViewMode,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("extraction_tabs")
                            ) {
                                Tab(
                                    selected = resultViewMode == 0,
                                    onClick = { resultViewMode = 0 },
                                    text = { Text("Raw Text (${uiState.extractedText.length} chars)") }
                                )
                                Tab(
                                    selected = resultViewMode == 1,
                                    onClick = { resultViewMode = 1 },
                                    text = { Text("Structured Entities (${uiState.detectedEntities.size})") }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (resultViewMode == 0) {
                                // Raw Text View
                                OutlinedTextField(
                                    value = uiState.extractedText,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp, max = 250.dp)
                                        .testTag("extracted_text_field"),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            } else {
                                // Structured Entities View
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Search field inside entities
                                    OutlinedTextField(
                                        value = entitySearchQuery,
                                        onValueChange = { entitySearchQuery = it },
                                        placeholder = { Text("Filter detected entities...", style = MaterialTheme.typography.bodySmall) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("entity_search_field"),
                                        shape = RoundedCornerShape(10.dp),
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        trailingIcon = {
                                            if (entitySearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { entitySearchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    )

                                    // Category Filter Chips
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = selectedEntityCategory == null,
                                            onClick = { selectedEntityCategory = null },
                                            label = { Text("All (${uiState.detectedEntities.size})") }
                                        )
                                        EntityCategory.entries.forEach { category ->
                                            val count = uiState.detectedEntities.count { it.category == category }
                                            if (count > 0) {
                                                FilterChip(
                                                    selected = selectedEntityCategory == category,
                                                    onClick = {
                                                        selectedEntityCategory = if (selectedEntityCategory == category) null else category
                                                    },
                                                    label = { Text("${category.tag} ($count)") }
                                                )
                                            }
                                        }
                                    }

                                    // Copy All Entities Button
                                    if (filteredEntities.isNotEmpty()) {
                                        FilledTonalButton(
                                            onClick = {
                                                val exportEntities = filteredEntities.joinToString("\n") {
                                                    "[${it.category.tag}] ${if (it.isSensitive) it.maskedValue else it.value}"
                                                }
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("R-Capture Structured Entities", exportEntities)
                                                clipboard.setPrimaryClip(clip)
                                                onSetStatusMessage("Copied ${filteredEntities.size} entities to clipboard")
                                            },
                                            modifier = Modifier.fillMaxWidth().testTag("copy_all_entities_button")
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Copy All (${filteredEntities.size} Entities)")
                                        }
                                    }

                                    // Entity Item Cards
                                    if (filteredEntities.isEmpty()) {
                                        Text(
                                            text = if (uiState.detectedEntities.isEmpty()) "No structured entities (URLs, emails, phones, dates, financial) detected." else "No entities matching search filter.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 12.dp)
                                        )
                                    } else {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())
                                        ) {
                                            filteredEntities.forEach { entity ->
                                                ExtractedEntityCard(
                                                    entity = entity,
                                                    onCopy = { copyText ->
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        val clip = ClipData.newPlainText(entity.category.displayName, copyText)
                                                        clipboard.setPrimaryClip(clip)
                                                        onSetStatusMessage("Copied ${entity.category.displayName} to clipboard")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Persistent Local History Section (Room)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Persistent Local History (${uiState.savedCaptures.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (uiState.savedCaptures.isNotEmpty()) {
                                TextButton(
                                    onClick = { showClearDialog = true },
                                    modifier = Modifier.testTag("clear_all_history_button")
                                ) {
                                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (uiState.savedCaptures.isEmpty()) {
                            Text(
                                text = "No saved captures yet. Use the floating overlay or Save button to record text locally.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())
                            ) {
                                uiState.savedCaptures.forEach { capture ->
                                    HistoryItemCard(
                                        capture = capture,
                                        onDelete = { onDeleteCapture(capture) },
                                        onCopy = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("R-Capture History", capture.text)
                                            clipboard.setPrimaryClip(clip)
                                            onSetStatusMessage("Copied saved capture to clipboard")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Accessibility Service Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("accessibility_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Accessibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.accessibility_service_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isAccessibilityServiceRunning) stringResource(R.string.accessibility_service_enabled) else stringResource(R.string.accessibility_service_disabled),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAccessibilityServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.accessibility_service_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("enable_accessibility_button")
                        ) {
                            Text(stringResource(R.string.enable_accessibility))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExtractedEntityCard(
    entity: ExtractedEntity,
    onCopy: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint) = when (entity.category) {
                        EntityCategory.URL -> Icons.Default.Link to ElectricCyan
                        EntityCategory.EMAIL -> Icons.Default.Mail to CyberBlue
                        EntityCategory.PHONE -> Icons.Default.Phone to CyberEmerald
                        EntityCategory.DATETIME -> Icons.Default.CalendarToday to SecurityAmber
                        EntityCategory.ADDRESS -> Icons.Default.Place to MaterialTheme.colorScheme.secondary
                        EntityCategory.FINANCIAL -> Icons.Default.AccountBalance to (if (entity.isSensitive) CyberCrimson else SecurityAmber)
                    }
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = entity.category.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        fontWeight = FontWeight.Bold
                    )
                    if (entity.isSensitive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(MASKED)",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCrimson,
                            fontSize = 10.sp
                        )
                    }
                }

                Row {
                    if (entity.isSensitive) {
                        TextButton(
                            onClick = { onCopy(entity.maskedValue) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Copy Masked", fontSize = 11.sp)
                        }
                    }
                    IconButton(
                        onClick = { onCopy(if (entity.isSensitive) entity.maskedValue else entity.value) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (entity.isSensitive) entity.maskedValue else entity.value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (entity.category == EntityCategory.FINANCIAL || entity.category == EntityCategory.DATETIME) FontFamily.Monospace else FontFamily.Default,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun HistoryItemCard(
    capture: CaptureEntity,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val dateStr = dateFormat.format(Date(capture.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${capture.source} • $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row {
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = capture.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3
            )
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HomeScreenPreview() {
    MyApplicationTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = RcaptureUiState(
                extractedText = "Sample OCR text extracted from document:\n\nInvoice #10492\nAmount Due: $249.50\nDue Date: 2026-11-15\nContact: billing@rcapture.io\nVisit: https://rcapture.io/pay",
                detectedEntities = listOf(
                    ExtractedEntity(
                        category = EntityCategory.FINANCIAL,
                        value = "$249.50",
                        maskedValue = "$249.50",
                        startIndex = 0,
                        endIndex = 7
                    ),
                    ExtractedEntity(
                        category = EntityCategory.DATETIME,
                        value = "2026-11-15",
                        maskedValue = "2026-11-15",
                        startIndex = 8,
                        endIndex = 18
                    ),
                    ExtractedEntity(
                        category = EntityCategory.EMAIL,
                        value = "billing@rcapture.io",
                        maskedValue = "billing@rcapture.io",
                        startIndex = 19,
                        endIndex = 38
                    ),
                    ExtractedEntity(
                        category = EntityCategory.URL,
                        value = "https://rcapture.io/pay",
                        maskedValue = "https://rcapture.io/pay",
                        startIndex = 39,
                        endIndex = 61
                    )
                ),
                statusMessage = "Text extracted successfully.",
                savedCaptures = listOf(
                    CaptureEntity(
                        id = 1,
                        text = "Flight Booking Confirmation #AA8492\nTerminal 3, Gate 12",
                        timestamp = 1731170000000L,
                        source = "Floating Overlay"
                    ),
                    CaptureEntity(
                        id = 2,
                        text = "Meeting Notes: Finalize Q4 native Android security audit report.",
                        timestamp = 1731083600000L,
                        source = "Manual Save"
                    )
                )
            ),
            isAccessibilityServiceRunning = true
        )
    }
}
