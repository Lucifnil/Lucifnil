package com.kikyo.cloudlauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab {
    Home,
    Settings
}

data class LauncherUiState(
    val selectedTab: AppTab = AppTab.Home,
    val rootState: RootState = RootState.Checking,
    val rootDetail: String = "正在检查 Root 状态",
    val runPhase: RunPhase = RunPhase.Ready,
    val statusDetail: String = "正在检查环境",
    val cardKeySaved: Boolean = false,
    val soFiles: List<String> = emptyList(),
    val selectedSoFile: String? = null,
    val soFilesDetail: String = "进入设置后读取 /data/adb",
    val soFilesLoaded: Boolean = false,
    val isLoadingSoFiles: Boolean = false,
    val logs: List<CommandLog> = emptyList()
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SecureCardKeyStore(application)
    private val rootRunner = RootRunner()
    private val rememberedSo = store.loadSelectedSo().takeIf(::isSafeSoFileName)

    private val _uiState = MutableStateFlow(
        LauncherUiState(
            cardKeySaved = store.hasSavedKeyFor(rememberedSo),
            selectedSoFile = rememberedSo
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        refreshRoot()
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == AppTab.Settings) {
            refreshSoFiles()
        }
    }

    fun refreshRoot() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    rootState = RootState.Checking,
                    rootDetail = "正在检查 Root 状态"
                )
            }
            val check = rootRunner.checkRoot()
            _uiState.update {
                val phase = if (check.granted && it.runPhase == RunPhase.Ready) {
                    RunPhase.Ready
                } else {
                    it.runPhase
                }
                it.copy(
                    rootState = if (check.granted) RootState.Granted else RootState.Denied,
                    rootDetail = check.detail,
                    runPhase = phase,
                    statusDetail = when {
                        check.granted && it.runPhase == RunPhase.Ready -> {
                            "Root 已就绪 · 点击启动程序"
                        }
                        !check.granted -> "尚未获得 Root 授权"
                        else -> it.statusDetail
                    }
                )
            }
        }
    }

    fun refreshSoFiles() {
        if (_uiState.value.isLoadingSoFiles) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingSoFiles = true,
                    soFilesDetail = "正在读取 ${BuildConfig.LAUNCHER_DIRECTORY}"
                )
            }

            val root = rootRunner.checkRoot()
            if (!root.granted) {
                _uiState.update {
                    it.copy(
                        rootState = RootState.Denied,
                        rootDetail = root.detail,
                        soFilesLoaded = false,
                        isLoadingSoFiles = false,
                        soFilesDetail = "需要 Root 权限才能读取 ${BuildConfig.LAUNCHER_DIRECTORY}"
                    )
                }
                return@launch
            }

            val scan = rootRunner.listSoFiles(BuildConfig.LAUNCHER_DIRECTORY)
            val selected = reconcileSelectedSo(scan.files)
            _uiState.update {
                it.copy(
                    rootState = RootState.Granted,
                    rootDetail = root.detail,
                    soFiles = scan.files,
                    selectedSoFile = selected,
                    cardKeySaved = store.hasSavedKeyFor(selected),
                    soFilesLoaded = true,
                    isLoadingSoFiles = false,
                    soFilesDetail = scan.detail
                )
            }
        }
    }

    fun selectSoFile(fileName: String) {
        if (!isSafeSoFileName(fileName) || fileName !in _uiState.value.soFiles) return
        store.saveSelectedSo(fileName)
        _uiState.update {
            it.copy(
                selectedSoFile = fileName,
                cardKeySaved = store.hasSavedKeyFor(fileName),
                statusDetail = "已选择启动文件：$fileName"
            )
        }
    }

    fun saveCardKey(cardKey: String): Boolean {
        val selectedSo = _uiState.value.selectedSoFile ?: return false
        if (cardKey.isBlank()) return false
        store.saveCardKeyFor(selectedSo, cardKey)
        _uiState.update {
            it.copy(
                cardKeySaved = true,
                statusDetail = if (it.runPhase == RunPhase.RequiresCardKey) {
                    "已保存 $selectedSo 的卡密 · 可以启动程序"
                } else {
                    it.statusDetail
                }
            )
        }
        return true
    }

    fun clearCardKey() {
        val selectedSo = _uiState.value.selectedSoFile ?: return
        store.clearCardKeyFor(selectedSo)
        _uiState.update {
            it.copy(
                cardKeySaved = false,
                runPhase = RunPhase.RequiresCardKey,
                statusDetail = "还没有保存卡密"
            )
        }
    }

    fun launchProgram() {
        if (_uiState.value.runPhase == RunPhase.Starting) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    rootState = RootState.Checking,
                    rootDetail = "正在请求 Root 授权"
                )
            }
            val root = rootRunner.checkRoot()
            if (!root.granted) {
                _uiState.update {
                    it.copy(
                        rootState = RootState.Denied,
                        rootDetail = root.detail,
                        runPhase = RunPhase.Failed,
                        statusDetail = "未获得 Root 授权"
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    rootState = RootState.Granted,
                    rootDetail = root.detail,
                    isLoadingSoFiles = true,
                    soFilesDetail = "正在确认启动 SO"
                )
            }
            val scan = rootRunner.listSoFiles(BuildConfig.LAUNCHER_DIRECTORY)
            val selectedSo = reconcileSelectedSo(scan.files)
            _uiState.update {
                it.copy(
                    soFiles = scan.files,
                    selectedSoFile = selectedSo,
                    cardKeySaved = store.hasSavedKeyFor(selectedSo),
                    soFilesLoaded = true,
                    isLoadingSoFiles = false,
                    soFilesDetail = scan.detail
                )
            }

            if (scan.files.isEmpty()) {
                _uiState.update {
                    it.copy(
                        selectedTab = AppTab.Settings,
                        runPhase = RunPhase.Failed,
                        statusDetail = "读取不到 .so 文件 · ${scan.detail}"
                    )
                }
                return@launch
            }

            if (selectedSo == null) {
                _uiState.update {
                    it.copy(
                        selectedTab = AppTab.Settings,
                        runPhase = RunPhase.Failed,
                        statusDetail = "检测到多个 .so，请先在设置中选择"
                    )
                }
                return@launch
            }

            val cardKey = store.loadCardKeyFor(selectedSo)
            if (cardKey.isBlank()) {
                _uiState.update {
                    it.copy(
                        selectedTab = AppTab.Settings,
                        runPhase = RunPhase.RequiresCardKey,
                        cardKeySaved = false,
                        statusDetail = "请先为 $selectedSo 保存卡密"
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    rootState = RootState.Granted,
                    rootDetail = root.detail,
                    runPhase = RunPhase.Starting,
                    statusDetail = "正在启动，等待程序输出…",
                    logs = emptyList()
                )
            }

            val result = rootRunner.runLauncher(
                scriptPath = BuildConfig.LAUNCHER_SCRIPT_PATH,
                cardKey = cardKey,
                soFileName = selectedSo,
                onLog = { log ->
                    _uiState.update { state ->
                        state.copy(logs = (state.logs + log).takeLast(MAX_LOG_LINES))
                    }
                }
            )

            _uiState.update {
                when {
                    result.timedOut -> it.copy(
                        runPhase = RunPhase.Failed,
                        statusDetail = "启动超时 · 点击查看输出"
                    )
                    result.exitCode == 0 -> it.copy(
                        runPhase = RunPhase.Succeeded,
                        statusDetail = "启动成功 · 点击查看输出"
                    )
                    else -> it.copy(
                        runPhase = RunPhase.Failed,
                        statusDetail = "启动失败 · " + result.detail
                    )
                }
            }
        }
    }

    /**
     * Retains an existing valid selection. If there is exactly one candidate,
     * select it automatically so the original one-file setup still works.
     * A failed scan does not erase the remembered filename; it may be a
     * temporary mount or Root issue rather than a removed file.
     */
    private fun reconcileSelectedSo(files: List<String>): String? {
        val saved = store.loadSelectedSo().takeIf(::isSafeSoFileName)
        val selected = when {
            saved != null && saved in files -> saved
            files.size == 1 -> files.single()
            else -> null
        }

        when {
            selected != null && selected != saved -> store.saveSelectedSo(selected)
            selected == null && files.isNotEmpty() && saved != null -> store.clearSelectedSo()
        }
        return selected
    }

    private fun isSafeSoFileName(name: String): Boolean {
        return name.endsWith(".so") &&
            name.isNotBlank() &&
            '/' !in name &&
            '\\' !in name &&
            '\n' !in name &&
            '\r' !in name &&
            '\u0000' !in name &&
            name != ".so"
    }

    private companion object {
        const val MAX_LOG_LINES = 200
    }
}
