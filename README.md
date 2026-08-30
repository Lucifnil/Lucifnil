# OnePlus 15T Native SukiSU Kernel

面向 OnePlus 15T（PLZ110）的可审计 Native 内核项目。源码直接来自一加官方
15T manifest，并固定到当前原厂 `boot.img` 对应的官方 Common 提交，使用官方
Kleaf 构建 Android 16 / Linux 6.12 / 4K GKI。

[项目源码](https://github.com/Lucifnil/OnePlus15T-SukiSU-Native) ·
[最新真机验证版 r30](https://github.com/Lucifnil/OnePlus15T-SukiSU-Native/releases/tag/official-native-sukisu-susfs-built-in-6.12.38-r30)

## 当前版本

项目提供三个 CI 变体：

- `baseline`：不包含 KernelSU、SukiSU、SUSFS、KPM 或性能补丁的纯净基线；
- `sukisu_builtin`：集成官方 SukiSU Ultra `v4.1.3`，使用
  `CONFIG_KSU=y` 的 Built-in 后端，不启用 SUSFS 或 KPM；
- `sukisu_susfs_builtin`：在 Built-in 版本上集成官方 SUSFS `v2.1.0`，
  启用隐藏功能、禁用内核日志且不启用 KPM。

目前的正式成果是 `sukisu_susfs_builtin` r30。它已在
OnePlus 15T `PLZ110_16.0.10.500_CN01` 上完成临时启动、永久刷入、冷启动和
完整真机回归。

## 真机验证

r30 已确认：

- 内核版本为 `6.12.38-android16-5-g844001fb8721-ab14552068-4k`；
- SukiSU Ultra 显示 `工作中 <Built-in>`，驱动和管理器版本均为 `40796`；
- SuSFS 显示 `v2.1.0 (Tracepoint Syscall Redirect)`；
- Wi-Fi、5G 移动数据、蓝牙、扬声器、振动器和主要传感器正常；
- 相机设备及 1216×2640 / 60–165 Hz 显示链路正常；
- SELinux 保持 `Enforcing`，未发现 panic、Oops、watchdog bite 或新增驱动回归。

## 为什么采用 Native 路线

早期通用 GKI/错误 Common 提交与当前 OTA 厂商模块不兼容，曾导致 Wi-Fi、移动
数据、蓝牙和扬声器失效。本项目最终回到一加官方 PLZ110 源码与原厂提交，并
处理公开 Kleaf 目标生成的保护模块名列表与量产系统实际模块策略之间的差异。

当前构建使用原厂 Clang build `14043575`、GENDWARFKSYMS 和 Zstd DWARF 设置；
抽查的 434 个公共模块 CRC 与原厂模块全部一致，确保内核与随 OTA 发布的厂商
模块保持兼容。

## 构建与刷入纪律

- 所有主要输入均记录在 `manifest.lock`；
- CI 校验官方 manifest、源码提交、Clang build、SukiSU/SUSFS 版本、最终配置、
  补丁哈希和产物 SHA-256；
- 明确拒绝 KPM、ADIOS、ReKernel、TCP Brutal 和预编译 `vmlinux` 覆盖；
- Release 附带最终 `.config`、原厂锚点、源码清单、内核版本和 SHA-256；
- 推荐使用 Release 中的 AnyKernel3 ZIP 刷入，它只更新 `boot`，不需要先刷 LKM，
  也不会修改 `init_boot`；
- 刷写前应保留与当前 OTA 匹配的原厂 `boot.img` 作为回退文件。

> 本项目只维护基于一加官方源码的 Native 路线；旧 Stable/通用 GKI 实验不再作为
> 当前发布方向。
