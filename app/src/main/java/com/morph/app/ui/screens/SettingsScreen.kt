package com.morph.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.morph.app.BuildConfig
import com.morph.app.MorphPrefs
import com.morph.app.core.AppStrings
import com.morph.app.core.EnglishStrings
import com.morph.app.core.IndonesianStrings
import com.morph.app.core.OutputManager
import com.morph.app.ui.components.MorphCard
import com.morph.app.ui.components.pressScale
import com.morph.app.ui.theme.LocalMorphColors
import com.morph.app.ui.theme.MorphMotion
import com.morph.app.ui.theme.MorphSpace
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentTheme: Int,
    onThemeChange: (Int) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentLanguage: String? = null,
    onLanguageChange: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = remember { MorphPrefs(context) }
    val strings = AppStrings.current
    val scheme = MaterialTheme.colorScheme

    var selectedLanguage by remember(currentLanguage) {
        mutableStateOf(currentLanguage ?: prefs.appLanguage)
    }
    var keepName by remember { mutableStateOf(prefs.keepOriginalName) }
    var closeShare by remember { mutableStateOf(prefs.closeAfterShare) }
    var qualityPreset by remember { mutableIntStateOf(prefs.qualityPreset) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var isBatteryOk by remember {
        mutableStateOf(com.morph.app.checkBatteryOptimizationDisabled(context))
    }
    var showBatteryDialogInSettings by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isBatteryOk = com.morph.app.checkBatteryOptimizationDisabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Update check
    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var autoCheckUpdates by remember { mutableStateOf(prefs.autoCheckUpdates) }

    // PBI 5.1: custom output folder (validated on open so a revoked folder shows as default)
    var customFolderLabel by remember {
        mutableStateOf(OutputManager.validCustomTreeUri(context, prefs)?.let { OutputManager.describeTreeUri(it) })
    }
    var autoSubfolder by remember { mutableStateOf(prefs.autoSubfolderPerFile) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.customOutputTreeUri = treeUri.toString()
            customFolderLabel = OutputManager.describeTreeUri(treeUri)
        } catch (e: SecurityException) {
            Toast.makeText(context, strings.outputFolderPermissionFailed, Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MorphSpace.screen,
            end = MorphSpace.screen,
            top = MorphSpace.xs,
            bottom = MorphSpace.xl
        ),
        verticalArrangement = Arrangement.spacedBy(MorphSpace.m)
    ) {
        item(key = "intro") {
            Text(
                text = strings.settingsSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = MorphSpace.xs)
            )
        }

        // --- 1. THEME ---
        item(key = "theme") {
            SettingsSectionCard(title = strings.themeSectionTitle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(MorphSpace.s)
                ) {
                    listOf(
                        Triple(strings.themeLight, 1, Icons.Rounded.LightMode),
                        Triple(strings.themeDark, 2, Icons.Rounded.DarkMode),
                        Triple(strings.themeSystem, 0, Icons.Rounded.PhoneAndroid)
                    ).forEach { (title, idx, icon) ->
                        SettingsOptionTile(
                            label = title,
                            selected = currentTheme == idx,
                            onClick = { onThemeChange(idx) },
                            modifier = Modifier.weight(1f)
                        ) { tint ->
                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }

        // --- 2. LANGUAGE ---
        item(key = "language") {
            SettingsSectionCard(title = strings.languageSectionTitle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(MorphSpace.s)
                ) {
                    listOf(
                        Triple(strings.langIndonesian, "id", "ID"),
                        Triple(strings.langEnglish, "en", "EN")
                    ).forEach { (label, code, monogram) ->
                        SettingsOptionTile(
                            label = label,
                            selected = selectedLanguage == code,
                            onClick = {
                                selectedLanguage = code
                                prefs.appLanguage = code
                                AppStrings.setLanguage(code)
                                onLanguageChange?.invoke(code)
                                val msg = if (code == "en") {
                                    EnglishStrings.languageSetToast
                                } else {
                                    IndonesianStrings.languageSetToast
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) { tint ->
                            Text(
                                text = monogram,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = tint
                            )
                        }
                    }
                }
            }
        }

        // --- 3. DEFAULT CONVERSION QUALITY ---
        item(key = "quality") {
            SettingsSectionCard(title = strings.qualitySectionTitle) {
                Text(
                    text = strings.qualitySectionDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = MorphSpace.m)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple(strings.qualityLow, 2, Icons.Rounded.Compress),
                        Triple(strings.qualityMedium, 1, Icons.Rounded.Tune),
                        Triple(strings.qualityHigh, 0, Icons.Rounded.HighQuality),
                        Triple(strings.qualityInstant, 3, Icons.Rounded.Bolt)
                    ).forEach { (title, idx, icon) ->
                        SettingsOptionTile(
                            label = title,
                            selected = qualityPreset == idx,
                            onClick = {
                                qualityPreset = idx
                                prefs.qualityPreset = idx
                            },
                            modifier = Modifier.weight(1f)
                        ) { tint ->
                            Icon(
                                icon,
                                contentDescription = null,
                                // The Instant bolt keeps its yellow everywhere (spec 2.4)
                                tint = if (idx == 3) LocalMorphColors.current.instantBolt else tint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                val desc = when (qualityPreset) {
                    0 -> strings.qualityDescHigh
                    1 -> strings.qualityDescMedium
                    2 -> strings.qualityDescLow
                    3 -> strings.qualityDescInstant
                    else -> ""
                }
                AnimatedContent(
                    targetState = desc,
                    transitionSpec = { fadeIn(MorphMotion.medium()) togetherWith fadeOut(MorphMotion.fast()) },
                    label = "qualityDesc"
                ) { text ->
                    if (text.isNotEmpty()) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.primary,
                            modifier = Modifier.padding(top = MorphSpace.m)
                        )
                    }
                }
            }
        }

        // --- PBI 5.1: OUTPUT FOLDER ---
        item(key = "output") {
            SettingsSectionCard(title = strings.outputFolderSectionTitle, contentPadding = false) {
                Row(
                    modifier = Modifier.padding(horizontal = MorphSpace.l),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsIcon(Icons.Rounded.Folder)
                    Spacer(Modifier.width(MorphSpace.m))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = customFolderLabel ?: strings.outputFolderDefault,
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (customFolderLabel != null) strings.outputFolderCustomDesc else strings.outputFolderDefaultDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(start = MorphSpace.l, end = MorphSpace.l, top = MorphSpace.m),
                    horizontalArrangement = Arrangement.spacedBy(MorphSpace.s)
                ) {
                    OutlinedButton(
                        onClick = { folderPicker.launch(null) },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(strings.outputFolderChoose)
                    }
                    if (customFolderLabel != null) {
                        TextButton(onClick = {
                            prefs.customOutputTreeUri = null
                            customFolderLabel = null
                        }) {
                            Text(strings.outputFolderReset)
                        }
                    }
                }
                SettingsDivider(Modifier.padding(top = MorphSpace.m))
                SettingsSwitchRow(
                    icon = Icons.Rounded.CreateNewFolder,
                    title = strings.autoSubfolderTitle,
                    description = strings.autoSubfolderDesc,
                    checked = autoSubfolder,
                    onCheckedChange = {
                        autoSubfolder = it
                        prefs.autoSubfolderPerFile = it
                    }
                )
            }
        }

        // --- 4. BEHAVIOUR ---
        item(key = "behavior") {
            SettingsSectionCard(title = strings.behaviorSectionTitle, contentPadding = false) {
                SettingsSwitchRow(
                    icon = Icons.Rounded.Edit,
                    title = strings.keepNameTitle,
                    description = strings.keepNameDesc,
                    checked = keepName,
                    onCheckedChange = {
                        keepName = it
                        prefs.keepOriginalName = it
                    }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Rounded.Share,
                    title = strings.closeShareTitle,
                    description = strings.closeShareDesc,
                    checked = closeShare,
                    onCheckedChange = {
                        closeShare = it
                        prefs.closeAfterShare = it
                    }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = strings.batteryOptTitle,
                    description = if (isBatteryOk) strings.batteryOptStatusOk else strings.batteryOptStatusRestricted,
                    descriptionColor = if (isBatteryOk) LocalMorphColors.current.success else scheme.error,
                    onClick = { showBatteryDialogInSettings = true }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Rounded.Update,
                    title = strings.autoCheckUpdatesTitle,
                    description = strings.autoCheckUpdatesDesc,
                    checked = autoCheckUpdates,
                    onCheckedChange = {
                        autoCheckUpdates = it
                        prefs.autoCheckUpdates = it
                    }
                )
            }
        }

        // --- 5. ABOUT & OPEN SOURCE LICENSES ---
        item(key = "about") {
            SettingsSectionCard(title = strings.aboutSectionTitle, contentPadding = false) {
                Column(
                    modifier = Modifier.padding(start = MorphSpace.l, end = MorphSpace.l, bottom = MorphSpace.m),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = strings.aboutVersion(BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface
                    )
                    Text(
                        text = strings.aboutAuthor,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Description,
                    title = strings.licensesSectionTitle,
                    description = strings.licensesSectionSubtitle,
                    onClick = { showLicensesDialog = true }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.SystemUpdate,
                    title = strings.checkUpdatesTitle,
                    description = if (isCheckingUpdate) strings.checkingUpdatesSubtitle else strings.checkUpdatesSubtitle,
                    enabled = !isCheckingUpdate,
                    busy = isCheckingUpdate,
                    onClick = {
                        isCheckingUpdate = true
                        coroutineScope.launch {
                            val res = com.morph.app.core.UpdateChecker.checkForUpdates(context)
                            isCheckingUpdate = false
                            when (res) {
                                is com.morph.app.core.UpdateCheckResult.UpdateAvailable -> {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        res.release.htmlUrl.toUri()
                                    )
                                    context.startActivity(intent)
                                }
                                is com.morph.app.core.UpdateCheckResult.UpToDate -> {
                                    Toast.makeText(context, strings.upToDateToast, Toast.LENGTH_SHORT).show()
                                }
                                is com.morph.app.core.UpdateCheckResult.Error -> {
                                    Toast.makeText(context, strings.checkUpdatesFailedToast, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    if (showLicensesDialog) {
        OpenSourceLicensesDialog(onDismiss = { showLicensesDialog = false })
    }

    if (showBatteryDialogInSettings) {
        BatteryOptimizationGuideDialog(
            onDismiss = { showBatteryDialogInSettings = false },
            onOpenSettings = {
                showBatteryDialogInSettings = false
                com.morph.app.openAppBatterySettings(context)
            }
        )
    }
}

// ============================================================
// BUILDING BLOCKS
// ============================================================

/** A group of related settings: one flat card, its title inside (spec 7.7). */
@Composable
private fun SettingsSectionCard(
    title: String,
    contentPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    MorphCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                top = MorphSpace.l,
                bottom = if (contentPadding) MorphSpace.l else MorphSpace.xs
            )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = MorphSpace.l)
            )
            Spacer(Modifier.height(MorphSpace.m))
            if (contentPadding) {
                Column(modifier = Modifier.padding(horizontal = MorphSpace.l), content = content)
            } else {
                content()
            }
        }
    }
}

/** Icon-over-label choice: theme, language, default quality. Fill and border both mark the selection. */
@Composable
private fun SettingsOptionTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (tint: Color) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
        MorphMotion.fast(),
        label = "optionContainer"
    )
    val border by animateColorAsState(
        if (selected) scheme.primary else scheme.outlineVariant,
        MorphMotion.fast(),
        label = "optionBorder"
    )
    val content by animateColorAsState(
        if (selected) scheme.primary else scheme.onSurfaceVariant,
        MorphMotion.fast(),
        label = "optionContent"
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .heightIn(min = 76.dp)
            .pressScale(interaction, 0.97f)
            .clip(shape)
            .background(container)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = ripple()
            )
            .padding(horizontal = 4.dp, vertical = MorphSpace.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            leading(content)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** The whole row toggles, so the target is the full width and TalkBack reads one switch. */
@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = MorphSpace.l, vertical = MorphSpace.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(MorphSpace.m))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(MorphSpace.m))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    busy: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = MorphSpace.l, vertical = MorphSpace.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(MorphSpace.m))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = descriptionColor)
        }
        Spacer(Modifier.width(MorphSpace.s))
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 64.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

// ============================================================
// OPEN SOURCE LICENSES DIALOG
// ============================================================

@Composable
private fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    val strings = AppStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = LocalMorphColors.current.card,
        title = {
            Text(
                text = strings.licensesSectionTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MorphSpace.m)
            ) {
                Text(
                    text = strings.licensesDialogIntro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LicenseItemCard(
                    name = "FFmpegKit Full (v8.1.7)",
                    license = "GNU General Public License v3.0 (GPL-3.0) / LGPL-3.0",
                    copyright = "Copyright (c) Arlen Ceylan & FFmpeg Developers"
                )

                LicenseItemCard(
                    name = "x264 (build 165, native H.264 encoder)",
                    license = "GNU General Public License v2.0 or later (GPL-2.0-or-later)",
                    copyright = "Copyright (C) 2003-2025 x264 project (VideoLAN)"
                )

                LicenseItemCard(
                    name = "AndroidSVG (v1.4)",
                    license = "Apache License, Version 2.0",
                    copyright = "Copyright 2013–2019 Paul LeBeau, Cave Rock Software Ltd."
                )

                LicenseItemCard(
                    name = "Kotlin, Coroutines & Android Jetpack (Compose)",
                    license = "Apache License, Version 2.0",
                    copyright = "Copyright JetBrains s.r.o. & The Android Open Source Project"
                )

                LicenseItemCard(
                    name = "Android Native SDK (PdfRenderer, ImageDecoder, java.util.zip)",
                    license = "Apache License, Version 2.0",
                    copyright = "Copyright The Android Open Source Project"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = strings.btnClose,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

@Composable
private fun LicenseItemCard(
    name: String,
    license: String,
    copyright: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(MorphSpace.m),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = license,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = copyright,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
