# ZipView

管理压缩包、并能**免解压直接查看包内图片**的 Android 应用。把一个 zip/cbz 当成一本可以直接翻的图册，省去先解压一堆碎图的麻烦。

## 核心功能

- **管理压缩包**：通过系统文件选择器（SAF）或"分享/用其他应用打开"接入压缩包，列表展示，支持重命名、移除、查看信息。
- **免解压浏览**：打开压缩包只读目录表，秒速列出包内文件/文件夹，支持进入子目录与嵌套压缩包。
- **免解压图册查看器**：对包内图片直接开流解码，支持翻页、缩放、相邻预加载。
- **选择打开方式**：按文件类型路由到内置查看器（图片→图册、文本→预览），也可"用其他应用打开"转交外部 app。

## 格式支持（分批落地）

| 格式 | 状态 | 说明 |
|------|------|------|
| zip / cbz | ✅ 满级 | 文件描述符随机访问，免解压秒开 |
| tar | ✅ 降级 | 无中央目录，首次打开后台解压到缓存（"正在准备…"） |
| rar / 7z | ⏳ 预留 | 架构已支持，待后续 Provider 接入 |

## 架构

```
UI (Compose)  →  ArchiveProvider 抽象  →  Zip/Tar/... Provider
                       ↑
              文件来源 (SAF / 分享 Intent)
```

- `archive/ArchiveProvider`：统一抽象，UI 不感知具体格式；新增格式 = 新增一个 Provider。
- `archive/ZipProvider`：commons-compress `ZipFile` + 文件描述符 `SeekableByteChannel`，随机抽取单个条目。
- `archive/CachingArchiveProvider`：不支持随机访问格式的降级基类（`TarProvider` 为落地范例）。
- `coil/ArchiveImageFetcher`：Coil 自定义数据源，把条目流交给 Coil 解码，复用其降采样与缓存做 OOM 防护。

## 构建前置条件

本仓库当前环境**无法直接构建**，需要：

1. **JDK 17**（Android Gradle Plugin 8.5 要求；当前机器为 JDK 8）。
2. **Android SDK**（`compileSdk=35`），并配置 `ANDROID_HOME` 或 `local.properties` 中的 `sdk.dir`。
3. **Gradle Wrapper**：用 Android Studio 打开会自动补全 `gradlew`，或执行 `gradle wrapper --gradle-version 8.9` 生成。

构建：

```bash
./gradlew :app:assembleDebug
```

推荐用 **Android Studio** 直接打开项目根目录构建运行。

## 已知限制

- `rar`/`7z` 暂未接入（工厂会给出明确提示）。
- 重命名为"显示名覆盖"，不改动源文件。
- 降级缓存清理策略（LRU/上限）尚为基础实现，后续完善。

## 规范与设计

需求与设计文档见 `cursor/openspec/changes/add-archive-manager-viewer/`（proposal / design / specs / tasks）。
