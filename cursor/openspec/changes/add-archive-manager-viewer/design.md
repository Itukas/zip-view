## 上下文

全新的 Android 应用工程（Kotlin），目标是"压缩包管理器 + 免解压图册阅读器"二合一。核心技术挑战来自一个内在张力：**用户既想要"全格式支持"，又想要"免解压秒开图"，而这两者在某些格式上是互相冲突的**。

- zip 不必整体解压：`ZipFile` 可只读中央目录拿到条目列表，再对单个 entry 开 `InputStream` 直接喂给图片解码器，全程不在磁盘落地。
- 但 7z（solid 压缩）、tar.gz（流式压缩）的底层结构决定了"看第 N 张图"可能需要先解出前面一大坨数据，无法真正随机抽取。

因此设计的重心是：**用一个统一抽象层把格式差异封装起来，让上层 UI 永远只面对"一个能列条目、能取流的压缩包"，并为不支持随机访问的格式提供降级方案。**

约束：

- 平台为 Android，需处理存储权限与 SAF（Storage Access Framework）。
- 移动端内存有限，大图解码必须采样防 OOM。
- UI 要求简易明了，主路径是"压缩包列表 → 包内浏览 → 图册看图"。

## 目标 / 非目标

**目标：**

- 提供统一的 `ArchiveProvider` 抽象，让新增格式 = 新增一个 Provider，UI 零改动。
- 首版保证 `zip`/`cbz` 的满级"免解压秒开 + 流式翻页"体验。
- 架构支持 `rar`/`7z`/`tar` 等格式（可分批落地），对不支持随机访问的格式提供"后台解到缓存"的降级路径，体验降级但功能不缺。
- 图册查看器支持翻页、缩放、预加载，并具备内存/OOM 防护。
- 打开条目时按文件类型路由到内置查看器，并支持转交外部 app（Intent）。

**非目标：**

- 首版不做压缩/打包/创建加密包。
- 不做云盘同步、账号体系。
- 不追求所有格式都"免解压"——明确接受按格式分级的体验差异。

## 决策

### 决策 1：统一抽象 `ArchiveProvider`，UI 与格式解耦

核心接口（概念性，非最终签名）：

```
interface ArchiveProvider {
    fun listEntries(): List<ArchiveEntry>          // 列条目（名/大小/是否目录/类型）
    fun openStream(entry: ArchiveEntry): InputStream  // 取单条目的流（看图就靠它）
    val supportsRandomAccess: Boolean              // 是否支持随机抽取
    fun close()
}
```

每种格式一个实现：`ZipProvider`（JDK 原生 `ZipFile`）、`RarProvider`（junrar）、`SevenZProvider`（sevenzipjbinding / commons-compress）、`TarProvider`（commons-compress）。

- **为什么**：把"格式差异"这个最大的复杂度源关进一个边界里。上层的列表、浏览、看图组件只依赖接口，永远不感知具体格式。新增格式不影响已有功能，降低回归风险。
- **替代方案**：在 UI 层直接 `when(format)` 分支处理 → 否决，会让格式逻辑散布到各处，难维护、难扩展。

### 决策 2：随机访问能力分级 + 降级缓存

`supportsRandomAccess` 把格式分两类：

```
能随机抽取 (zip/cbz/rar)  → openStream 直接对 entry 开流，秒开
不能 (7z-solid/tar.gz)    → 首次打开时后台解压到 app 私有缓存目录，
                            后续 openStream 从缓存读；UI 显示"正在准备…"
```

- **为什么**：在"完全不支持重型格式"和"全部一律先解压（丢掉免解压卖点）"之间取中间路线——能秒开的就秒开，不能的退化为"准备一次"。卖点（zip/cbz 免解压）和野心（全格式）兼得。
- **替代方案**：所有格式统一先解到缓存（实现最简单）→ 否决，会让 zip/cbz 也丧失免解压优势，违背核心价值。

### 决策 3：实现分批，首版只交付 `ZipProvider`

架构按全格式设计，但首版仅实现 zip/cbz Provider；rar/7z/tar 作为后续增量。

- **为什么**：最快拿到可用版本、验证主路径与抽象层是否合理；重型格式库（sevenzipjbinding 含 native .so，体积/兼容性成本高）延后引入，避免首版风险。
- **替代方案**：首版就全格式 → 否决，体积大、周期长、风险高，且抽象层尚未经实战验证就背上多实现负担。

### 决策 4：图片解码用 Glide/Coil + 自定义数据源

将 `ArchiveProvider.openStream(entry)` 包装成图片库的自定义数据源（Glide `ModelLoader` / Coil `Fetcher`），由图片库负责采样、缓存、复用 bitmap。

- **为什么**：复用成熟库的内存管理、降采样、磁盘/内存缓存与生命周期管理，避免自己造 OOM 防护轮子。
- **替代方案**：手写 `BitmapFactory` + 采样 → 否决，重复造轮子且易出内存问题。

### 决策 5：文件来源走 SAF + 分享 Intent，弱化全盘扫描

通过系统文件选择器（SAF / `ACTION_OPEN_DOCUMENT`）与"分享到本应用"的 Intent 接入压缩包，列表保存用户授权过的 Uri，而非首版就申请全盘存储权限做扫描。

- **为什么**：契合新版 Android 的分区存储策略，权限摩擦小、上架合规风险低。
- **替代方案**：`MANAGE_EXTERNAL_STORAGE` 全盘扫描 → 否决，权限敏感、审核严，首版不值得。

## 风险 / 权衡

- **重型格式免解压本质不可行** → 通过 `supportsRandomAccess=false` + 后台解缓存降级，UI 明确提示"正在准备"，不假装秒开。
- **7z/rar 第三方库体积与兼容性（含 native .so）** → 分批引入；可考虑按 ABI 拆分 APK 或动态特性模块，仅在用户需要时下载。
- **大图/超多图 OOM** → 交给 Glide/Coil 做降采样与缓存，预加载窗口限制为前后各 1~2 张。
- **缓存膨胀**（降级解压产生的临时文件） → 设缓存上限 + LRU 清理 + 退出/低空间时清理。
- **SAF 的 Uri 权限可能失效**（设备重启/授权撤销） → 持久化可持久 Uri 权限，失效时引导用户重新选择。
- **首版仅 zip 可能让"全格式"承诺落空感** → proposal/UI 明确标注格式为"分批支持"，避免预期错位。

## 待解决问题

- `minSdk` 定多少（影响 SAF 行为与库兼容）。
- 7z 用 sevenzipjbinding（功能全、含 native）还是 commons-compress（纯 Java、对 solid 随机访问支持有限）。
- 是否需要"最近打开""书签/续读进度"等阅读器增强（可放二期）。
