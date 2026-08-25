package com.kikyo.cloudlauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kikyo.cloudlauncher.liquid.LiquidBottomBarItem
import com.kikyo.cloudlauncher.liquid.LiquidGlassSurface
import com.kikyo.cloudlauncher.liquid.SukiLiquidBottomBar
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SkyBlue = Color(0xFF5BCEFA)
private val SoftBlue = Color(0xFFBCEBFF)
private val SoftPink = Color(0xFFF5A9B8)
private val InkBlue = Color(0xFF1D64BD)
private val MutedBlue = Color(0xFF5A85B9)
private val MintGreen = Color(0xFF19A968)
private val ErrorPink = Color(0xFFD65779)

@Composable
fun CloudLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = SkyBlue,
            secondary = SoftPink,
            background = Color.Transparent,
            surface = Color.Transparent,
            surfaceVariant = Color.Transparent,
            onSurface = InkBlue
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudLauncherApp(
    state: LauncherUiState,
    onSelectTab: (AppTab) -> Unit,
    onLaunch: () -> Unit,
    onRefreshRoot: () -> Unit,
    onRefreshSoFiles: () -> Unit,
    onSelectSoFile: (String) -> Unit,
    onSaveCardKey: (String) -> Boolean,
    onClearCardKey: () -> Unit
) {
    var showLogs by rememberSaveable { mutableStateOf(false) }
    val pageBackdrop = rememberLayerBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        // A LayerBackdrop must capture *only* the scene behind glass.  The
        // previous structure captured this Scaffold too, while its cards were
        // simultaneously sampling pageBackdrop.  That creates a RenderNode
        // cycle and causes a native RenderThread stack overflow / SIGSEGV.
        CloudBackground(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop),
        )

        // Glass surfaces are siblings of their captured scene, never children
        // of it.  They still sample the live gradient through pageBackdrop.
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Spacer(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .height(88.dp)
                )
            }
        ) { contentPadding ->
            when (state.selectedTab) {
                AppTab.Home -> HomeScreen(
                    state = state,
                    contentPadding = contentPadding,
                    backdrop = pageBackdrop,
                    onLaunch = onLaunch,
                    onShowLogs = { showLogs = true }
                )

                AppTab.Settings -> SettingsScreen(
                    state = state,
                    contentPadding = contentPadding,
                    backdrop = pageBackdrop,
                    onSaveCardKey = onSaveCardKey,
                    onClearCardKey = onClearCardKey,
                    onRefreshRoot = onRefreshRoot,
                    onRefreshSoFiles = onRefreshSoFiles,
                    onSelectSoFile = onSelectSoFile
                )
            }
        }

        CloudBottomBar(
            selectedTab = state.selectedTab,
            onSelectTab = onSelectTab,
            backdrop = pageBackdrop,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showLogs) {
        LogBottomSheet(
            logs = state.logs,
            onDismiss = { showLogs = false }
        )
    }
}

@Composable
private fun CloudBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF67C8FA),
                        Color(0xFFAEDFFF),
                        Color(0xFFF0B8CF),
                        Color(0xFF9DDAFF)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .offset(x = (-80).dp, y = 170.dp)
                .size(260.dp)
                .clip(CircleShape)
                .background(SoftBlue.copy(alpha = 0.42f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 36.dp)
                .size(310.dp)
                .clip(CircleShape)
                .background(SoftPink.copy(alpha = 0.23f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 96.dp, y = (-170).dp)
                .size(260.dp)
                .clip(CircleShape)
                .background(SoftPink.copy(alpha = 0.18f))
        )
    }
}

@Composable
private fun HomeScreen(
    state: LauncherUiState,
    contentPadding: PaddingValues,
    backdrop: Backdrop,
    onLaunch: () -> Unit,
    onShowLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        ProfileHeader(rootState = state.rootState, backdrop = backdrop)
        Spacer(modifier = Modifier.height(16.dp))

        // The identity and Root state stay visible while the content scrolls.
        // That keeps the page from feeling like a long poster on small screens.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroPanel(backdrop = backdrop)
            LaunchTicket(
                phase = state.runPhase,
                hasCardKey = state.cardKeySaved,
                rootState = state.rootState,
                selectedSoFile = state.selectedSoFile,
                backdrop = backdrop,
                onLaunch = onLaunch
            )
            if (state.runPhase != RunPhase.Ready && state.runPhase != RunPhase.RequiresCardKey) {
                RunStatusCard(
                    phase = state.runPhase,
                    detail = state.statusDetail,
                    hasLogs = state.logs.isNotEmpty(),
                    backdrop = backdrop,
                    onShowLogs = onShowLogs
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(rootState: RootState, backdrop: Backdrop) {
    val shape = RoundedCornerShape(26.dp)
    LiquidGlassSurface(
        backdrop = backdrop,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ImageAppIcon(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(SkyBlue, SoftBlue, SoftPink)))
                    .padding(3.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "云朵启动器",
                    color = InkBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "CINNAMOROLL  ·  V1.3.3",
                    color = MutedBlue,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.4.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            RootPill(rootState = rootState)
        }
    }
}

@Composable
private fun RootPill(rootState: RootState) {
    val (label, color, icon) = when (rootState) {
        RootState.Granted -> Triple("ROOT", MintGreen, Icons.Outlined.Shield)
        RootState.Denied -> Triple("未授权", ErrorPink, Icons.Outlined.ErrorOutline)
        RootState.Checking -> Triple("检查中", InkBlue, Icons.Outlined.Sync)
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.62f), shape)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HeroPanel(backdrop: Backdrop) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 26.dp, y = (-16).dp)
                .size(185.dp)
                .clip(CircleShape)
                .background(SoftBlue.copy(alpha = 0.30f))
        )
        Text(
            text = "✦  CLOUD FLIGHT",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 7.dp, top = 13.dp),
            color = InkBlue.copy(alpha = 0.74f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
        ImageHero(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(176.dp)
                .offset(x = 12.dp, y = (-8).dp)
        )
        val titleShape = RoundedCornerShape(27.dp)
        LiquidGlassSurface(
            backdrop = backdrop,
            shape = titleShape,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(87.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 19.dp, vertical = 13.dp)
            ) {
                Text(
                    text = "云朵启动器",
                    color = InkBlue,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 31.sp
                )
                Text(
                    text = "和玉桂狗一起，轻轻飞向云端",
                    color = MutedBlue,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LaunchTicket(
    phase: RunPhase,
    hasCardKey: Boolean,
    rootState: RootState,
    selectedSoFile: String?,
    backdrop: Backdrop,
    onLaunch: () -> Unit
) {
    val starting = phase == RunPhase.Starting
    val ticketTitle = if (starting) "正在启动…" else "一键启动"
    val ticketSubtitle = when {
        starting -> "正在接收程序输出"
        !hasCardKey -> "未保存卡密时会跳转到设置"
        rootState == RootState.Denied -> "请先在 Root 管理器中授权"
        selectedSoFile != null -> "SO · $selectedSoFile"
        else -> "ROOT LAUNCH READY"
    }
    val ticketShape = RoundedCornerShape(30.dp)

    LiquidGlassSurface(
        backdrop = backdrop,
        shape = ticketShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp)
            .clickable(
                enabled = !starting,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onLaunch
            )
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 23.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (starting) InkBlue else MintGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ticketSubtitle,
                    color = InkBlue.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
            }
            Spacer(modifier = Modifier.height(13.dp))
            Text(
                text = ticketTitle,
                color = InkBlue,
                fontSize = 31.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = if (starting) "请不要关闭页面" else "轻触登机牌开始运行",
                color = MutedBlue,
                fontSize = 13.sp
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 19.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(SoftBlue.copy(alpha = 0.42f))
                .border(1.dp, SoftPink.copy(alpha = 0.78f), CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (starting) Icons.Outlined.Sync else Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = InkBlue,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Text(
            text = "☁",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 91.dp, bottom = 6.dp),
            color = SoftPink.copy(alpha = 0.7f),
            fontSize = 26.sp
        )
    }
}

@Composable
private fun RunStatusCard(
    phase: RunPhase,
    detail: String,
    hasLogs: Boolean,
    backdrop: Backdrop,
    onShowLogs: () -> Unit
) {
    val (color, icon) = when (phase) {
        RunPhase.Starting -> InkBlue to Icons.Outlined.Sync
        RunPhase.Succeeded -> MintGreen to Icons.Outlined.CheckCircle
        RunPhase.Failed -> ErrorPink to Icons.Outlined.ErrorOutline
        RunPhase.RequiresCardKey -> SoftPink to Icons.Outlined.Key
        RunPhase.Ready -> MintGreen to Icons.Outlined.CheckCircle
    }
    val clickable = hasLogs || phase == RunPhase.Failed || phase == RunPhase.Succeeded
    val statusShape = RoundedCornerShape(22.dp)

    LiquidGlassSurface(
        backdrop = backdrop,
        shape = statusShape,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = clickable,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onShowLogs
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f))
                    .border(1.dp, color.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (phase == RunPhase.Starting) "本次正在运行" else "本次运行结果",
                    color = InkBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = detail,
                    color = MutedBlue,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (clickable) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "查看输出",
                    tint = InkBlue.copy(alpha = 0.65f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: LauncherUiState,
    contentPadding: PaddingValues,
    backdrop: Backdrop,
    onSaveCardKey: (String) -> Boolean,
    onClearCardKey: () -> Unit,
    onRefreshRoot: () -> Unit,
    onRefreshSoFiles: () -> Unit,
    onSelectSoFile: (String) -> Unit
) {
    var input by rememberSaveable(state.selectedSoFile) { mutableStateOf("") }
    var showInput by rememberSaveable(state.selectedSoFile) { mutableStateOf(false) }
    var hint by rememberSaveable(state.selectedSoFile) { mutableStateOf<String?>(null) }
    var showSoPicker by rememberSaveable { mutableStateOf(false) }
    val keyCardShape = RoundedCornerShape(30.dp)
    val soCardShape = RoundedCornerShape(28.dp)
    val rootCardShape = RoundedCornerShape(28.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "设置",
                    fontSize = 33.sp,
                    fontWeight = FontWeight.Black,
                    color = InkBlue
                )
            }
            ImageHero(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(SoftBlue.copy(alpha = 0.68f), SoftPink.copy(alpha = 0.50f))
                        )
                    )
                    .padding(5.dp)
            )
        }

        LiquidGlassSurface(
            backdrop = backdrop,
            shape = keyCardShape,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SoftPink.copy(alpha = 0.24f))
                            .border(1.dp, SoftPink.copy(alpha = 0.62f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = SoftPink,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "卡密管理",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkBlue
                        )
                        Text(
                            text = when {
                                state.selectedSoFile == null -> "请先选择启动 SO"
                                state.cardKeySaved -> "当前 SO 已安全保存"
                                else -> "当前 SO 尚未保存卡密"
                            },
                            fontSize = 13.sp,
                            color = if (state.cardKeySaved) MintGreen else MutedBlue
                        )
                    }
                }

                Text(
                    text = "请先在下方选择启动 SO。每个 SO 都会单独保存自己的卡密，互不覆盖。",
                    color = MutedBlue,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        hint = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("当前 SO 的卡密") },
                    placeholder = { Text(state.selectedSoFile ?: "先选择启动 SO") },
                    visualTransformation = if (showInput) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showInput = !showInput }) {
                            Icon(
                                imageVector = if (showInput) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (showInput) "隐藏卡密" else "显示卡密"
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SkyBlue,
                        unfocusedBorderColor = SoftBlue,
                        focusedLabelColor = InkBlue,
                        cursorColor = InkBlue,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(20.dp)
                )

                hint?.let {
                    Text(
                        text = it,
                        color = if (it.startsWith("已")) MintGreen else ErrorPink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = {
                        if (state.selectedSoFile == null) {
                            hint = "请先在下方选择启动 SO"
                        } else if (onSaveCardKey(input)) {
                            input = ""
                            showInput = false
                            hint = "已安全保存当前 SO 的卡密"
                        } else {
                            hint = "请输入卡密后再保存"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SkyBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存当前 SO 的卡密", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                if (state.cardKeySaved) {
                    Button(
                        onClick = {
                            onClearCardKey()
                            hint = "已清除当前 SO 的卡密"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = ErrorPink
                        ),
                        elevation = null
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text("清除当前 SO 的卡密")
                    }
                }
            }
        }

        LiquidGlassSurface(
            backdrop = backdrop,
            shape = soCardShape,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SkyBlue.copy(alpha = 0.22f))
                            .border(1.dp, SkyBlue.copy(alpha = 0.60f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = InkBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "启动 SO",
                            color = InkBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "/data/adb · 只读取该目录",
                            color = MutedBlue,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onRefreshSoFiles,
                        enabled = !state.isLoadingSoFiles
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "重新读取 SO 文件",
                            tint = InkBlue
                        )
                    }
                }

                Text(
                    text = state.soFilesDetail,
                    color = if (state.soFilesLoaded && state.soFiles.isEmpty()) ErrorPink else MutedBlue,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Text(
                    text = state.selectedSoFile?.let { "已选择：$it" } ?: "尚未选择启动文件",
                    color = if (state.selectedSoFile != null) MintGreen else MutedBlue,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Button(
                    onClick = { showSoPicker = true },
                    enabled = !state.isLoadingSoFiles && state.soFiles.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SkyBlue,
                        contentColor = Color.White,
                        disabledContainerColor = SoftBlue.copy(alpha = 0.46f),
                        disabledContentColor = Color.White.copy(alpha = 0.78f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择启动 SO", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LiquidGlassSurface(
            backdrop = backdrop,
            shape = rootCardShape,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = if (state.rootState == RootState.Granted) MintGreen else InkBlue,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Root 状态",
                        color = InkBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = state.rootDetail,
                        color = MutedBlue,
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = onRefreshRoot) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "重新检查 Root",
                        tint = InkBlue
                    )
                }
            }
        }
    }

    if (showSoPicker) {
        SoFilePickerBottomSheet(
            files = state.soFiles,
            selectedSoFile = state.selectedSoFile,
            onSelect = { fileName ->
                onSelectSoFile(fileName)
                showSoPicker = false
            },
            onDismiss = { showSoPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoFilePickerBottomSheet(
    files: List<String>,
    selectedSoFile: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF0F8FF),
        contentColor = InkBlue
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
        ) {
            Text(
                text = "选择要启动的 SO",
                color = InkBlue,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "来自 /data/adb 的直接 .so 文件",
                color = MutedBlue,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it }) { fileName ->
                    val selected = fileName == selectedSoFile
                    val itemShape = RoundedCornerShape(18.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(itemShape)
                            .background(
                                if (selected) SkyBlue.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.58f)
                            )
                            .border(
                                1.dp,
                                if (selected) SkyBlue.copy(alpha = 0.72f) else SoftBlue.copy(alpha = 0.70f),
                                itemShape
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { onSelect(fileName) }
                            )
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selected) {
                                Icons.Outlined.RadioButtonChecked
                            } else {
                                Icons.Outlined.RadioButtonUnchecked
                            },
                            contentDescription = if (selected) "已选择" else "选择 $fileName",
                            tint = if (selected) InkBlue else MutedBlue,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = fileName,
                            color = InkBlue,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudBottomBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    SukiLiquidBottomBar(
        selectedIndex = { if (selectedTab == AppTab.Home) 0 else 1 },
        onSelected = { index -> onSelectTab(if (index == 0) AppTab.Home else AppTab.Settings) },
        backdrop = backdrop,
        tabsCount = 2,
        accentColor = InkBlue,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        LiquidBottomBarItem(
            onClick = { onSelectTab(AppTab.Home) },
            modifier = Modifier.defaultMinSize(minWidth = 150.dp)
        ) {
            CloudTabContent(
                title = "首页",
                icon = Icons.Outlined.Home,
                selected = selectedTab == AppTab.Home
            )
        }
        LiquidBottomBarItem(
            onClick = { onSelectTab(AppTab.Settings) },
            modifier = Modifier.defaultMinSize(minWidth = 150.dp)
        ) {
            CloudTabContent(
                title = "设置",
                icon = Icons.Outlined.Settings,
                selected = selectedTab == AppTab.Settings
            )
        }
    }
}

@Composable
private fun CloudTabContent(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean
) {
    Icon(
        imageVector = icon,
        contentDescription = title,
        tint = if (selected) InkBlue else MutedBlue,
        modifier = Modifier.size(22.dp)
    )
    Text(
        text = title,
        color = if (selected) InkBlue else MutedBlue,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 13.sp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogBottomSheet(
    logs: List<CommandLog>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFBCEBFF)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 560.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "运行输出",
                color = InkBlue,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "卡密会自动脱敏，不会显示在这里。",
                color = MutedBlue,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (logs.isEmpty()) {
                Text(
                    text = "暂时没有输出。",
                    color = MutedBlue,
                    modifier = Modifier.padding(vertical = 28.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(logs) { log ->
                        val error = log.stream == LogStream.StandardError
                        val logShape = RoundedCornerShape(15.dp)
                        val logColor = if (error) SoftPink else SoftBlue
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(logShape)
                                .background(logColor.copy(alpha = 0.34f))
                                .border(1.dp, logColor.copy(alpha = 0.72f), logShape)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = formatTimestamp(log.timestampMillis) +
                                        if (error) "  STDERR" else "  STDOUT",
                                    color = if (error) ErrorPink else InkBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = log.text,
                                    color = Color(0xFF314D70),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageAppIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.cinnamoroll_app_icon),
        contentDescription = "云朵启动器图标",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun ImageHero(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.cinnamoroll_hero),
        contentDescription = "玉桂狗",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
