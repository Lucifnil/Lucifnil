# OnePlus 15T Native SukiSU Kernel

面向 OnePlus 15T（PLZ110）的可审计 Native 内核项目。源码直接来自一加官方
15T manifest，并固定到当前原厂 `boot.img` 对应的官方 Common 提交，使用官方
Kleaf 构建 Android 16 / Linux 6.12 / 4K GKI。

[项目源码](https://github.com/Lucifnil/OnePlus15T-SukiSU-Native) ·
[最新真机验证版 r33](https://github.com/Lucifnil/OnePlus15T-SukiSU-Native/releases/tag/official-native-sukisu-susfs-built-in-6.12.38-r33)

## 当前版本

项目提供三个 CI 变体：

- `baseline`：不包含 KernelSU、SukiSU、SUSFS、KPM 或性能补丁的纯净基线；
- `sukisu_builtin`：集成官方 SukiSU Ultra `v4.1.3`，使用
  `CONFIG_KSU=y` 的 Built-in 后端，不启用 SUSFS 或 KPM；
- `sukisu_susfs_builtin`：在 Built-in 版本上集成官方 SUSFS `v2.1.0`，
  启用隐藏功能、禁用内核日志且不启用 KPM。

当前正式版本为 `sukisu_susfs_builtin` r33。它已在 OnePlus 15T
`PLZ110_16.0.10.500_CN01` 上完成临时启动、永久刷入、冷启动、厂商模块兼容性
检查和真实游戏对局回归。

## r33：全局 Hmbird 兼容修复

> r33 修复的是 PLZ110 系统级的厂商 split-BTF / Hmbird 兼容链，不是
> 《三角洲行动》专用优化。

r30 中 Built-in SukiSU/SUSFS 改变了自动生成的基础 BTF 类型 ID 空间，使一加
`oplus_bsp_sched_ext.ko` 的 split BTF 与 kfunc 注册失败，随后全局
`oplusHmbirdBpfManager` 以 `-EINVAL` 停止。任何依赖同一 Hmbird/`sched_ext`
链路的游戏都可能受到影响；《三角洲行动》只是最先暴露故障并用于验证修复的
高负载样本，项目没有加入 DFM 包名判断或专用频率、温控、调度参数。

r33 在最终链接前注入由同一官方提交、同一配置和锁定工具链生成的纯净源码基线
BTF，随后仍正常执行 `resolve_btfids`。CI 锁定 BTF 哈希、164887 个基础类型、
类型区和字符串区长度，并验证一加厂商模块的 split-BTF 合并，恢复一加原本设计
的全局风驰/Hmbird 调度。

## 真机验证

r33 已确认：

- 内核版本为 `6.12.38-android16-5-g844001fb8721-ab14552068-4k`；
- SukiSU Ultra 显示 `工作中 <Built-in>`，驱动和管理器版本均为 `40796`；
- SuSFS 显示 `v2.1.0 (Tracepoint Syscall Redirect)`；
- `oplus_bsp_sched_ext.ko` 的 BTF/kfunc 注册成功，Hmbird 管理器持续运行；
- 游戏场景中 `hmbird_II` 与 `sched_ext enabled` 正常挂载，退出或熄屏后正常卸载；
- 普通零号大坝约 5 分钟有效负载段内 CPU 持续约 48–55 °C，场景重挂瞬时峰值
  69.0 °C，机身峰值约 39.0 °C、电池峰值约 36.7 °C；未再复现 r30 的持续
  90 °C、持续约 6 W 或调度崩溃；
- Wi-Fi、5G、蓝牙、USB、音频、振动、传感器、四摄和 1216×2640 / 60–165 Hz
  显示链路正常；
- SELinux 保持 `Enforcing`，未发现 panic、Oops、watchdog bite 或新增驱动回归。

## 为什么采用 Native 路线

早期通用 GKI/错误 Common 提交与当前 OTA 厂商模块不兼容，曾导致 Wi-Fi、移动
数据、蓝牙和扬声器失效。本项目最终回到一加官方 PLZ110 源码与原厂提交，并
处理公开 Kleaf 目标生成的保护模块名列表、BTF 类型空间与量产系统实际厂商模块
策略之间的差异。

当前构建使用原厂 Clang build `14043575`、GENDWARFKSYMS 和 Zstd DWARF 设置；
抽查的 434 个公共模块 CRC 与原厂模块全部一致，确保内核与随 OTA 发布的厂商
模块保持兼容。

## 构建与刷入纪律

- 所有主要输入均记录在 `manifest.lock`；
- CI 校验官方 manifest、源码提交、Clang build、SukiSU/SUSFS 版本、BTF、最终
  配置、补丁哈希和产物 SHA-256；
- 明确拒绝 KPM、ADIOS、ReKernel、TCP Brutal 和预编译 `vmlinux` 覆盖；
- Release 附带校验文件、原厂锚点、源码清单和内核版本；
- 推荐使用 Release 中的 AnyKernel3 ZIP 刷入，它只更新 `boot`，不需要先刷 LKM，
  也不会修改 `init_boot`；
- 刷写前应保留与当前 OTA 匹配的原厂 `boot.img` 作为回退文件。

> 本项目只维护基于一加官方源码的 Native 路线；旧 Stable/通用 GKI 实验不再作为
> 当前发布方向。
