# TimeTrack · 时间管理

一个轻量的安卓时间记录应用：**随时新建任务、点一下就开始计时、停止即结束**，按天用饼图和条形图看时间去向，数据可导出成 CSV / JSON。

参考了番茄 Todo 和 ATracker 的交互思路，但刻意只做"正计时记录"这一件事——不含番茄钟。

---

## 功能

**计时页**
- 输入文字 → 点"开始"或按回车，**立刻开始计时**，没有多余步骤
- 大号计时器显示当前任务的已用时长，一键停止
- "最近任务"列表点一下即可切换过去
- 开始新任务时会**自动结束上一个**，不会出现两段计时重叠

**统计页**
- 默认显示今天，可左右切换到任意历史日期
- 当日总时长 + 任务项数
- **环形饼图**：各任务时长占比
- **条形图**：每项任务的时长、百分比，按耗时从多到少排列
- 正在计时的任务会**实时增长**，不需要手动刷新

**导出页**
- 范围：今天 / 最近 7 天 / 最近 30 天 / 全部
- **CSV**：每段记录一行（任务名、开始、结束、秒数、小时数、状态）。已带 UTF-8 BOM，Excel / WPS 双击直接打开，中文不乱码
- **JSON**：结构化完整备份，保留原始时间戳
- 保存位置由系统文件选择器决定，**应用不申请任何存储权限**

**其它**
- 通知栏常驻显示正在计时的任务和已用时长
- 支持深色模式
- 适配 Android 8.0 ~ 15（minSdk 26 / targetSdk 34）

---

## 构建

环境已经在这台机器上准备好了（`.toolchain` 目录里），直接运行：

```powershell
cd TimeTrack
.\build.ps1                      # release 正式版（约 1.2 MB）
.\build.ps1 -Variant debug       # debug 版（约 9 MB，不混淆，排查问题用）
```

产物：

```
TimeTrack\app\build\outputs\apk\release\app-release.apk    1.18 MB
TimeTrack\app\build\outputs\apk\debug\app-debug.apk        9.28 MB
```

> **两个版本的包名不同**（release 是 `com.timetrack.app`，debug 是 `com.timetrack.app.debug`），
> 所以可以同时装在一台手机上，数据互相独立。日常用 release，出问题用 debug 对照。

### 工程自带 wrapper

`gradlew.bat` 已经生成好，`distributionUrl` 指向华为云镜像（官方 `services.gradle.org`
在该网络下**无法连接**，实测超时）。换机器时直接 `.\gradlew.bat assembleRelease` 即可。

### 手动构建（等价于脚本）

```powershell
$env:JAVA_HOME       = 'D:\Dev\JDK\jdk-21'
$env:ANDROID_HOME    = '..\.toolchain\android-sdk'
$env:GRADLE_USER_HOME= '..\.toolchain\gradle-home'

..\.toolchain\gradle-8.9\bin\gradle.bat assembleRelease
```

> `local.properties` 里的 `sdk.dir` 用 **Unicode 转义** 写中文路径，因为 `java.util.Properties` 按 ISO-8859-1 解码。移动 SDK 后需要按同样方式重写。

---

## 测试

`app/src/test/` 下有 23 个纯 JVM 单元测试，覆盖最容易出错的统计与导出规则：

```powershell
# 注意：本机沙箱禁止命名管道，Gradle 的 test worker 起不来，需用下面的方式跑
java -cp "<mainClasses>;<testClasses>;<junit>;<hamcrest>;<kotlin-stdlib>" `
     org.junit.runner.JUnitCore `
     com.timetrack.app.data.StatsCalculatorTest `
     com.timetrack.app.util.ExporterTest
```

覆盖的关键规则：跨天区间两天各计一半、进行中区间按 `now` 计算、系统时钟回拨不产生负数、
区间跨越窗口边界被裁剪、同任务多段合并并按耗时降序、CSV 的 UTF-8 BOM、
任务名含逗号/引号时的转义、小数点在任意语言下都是 `.`、JSON 的 null 与转义。


---

## 装到手机

**方式一：直接拷贝（最简单）**
1. 把 `app-debug.apk` 通过微信 / QQ / 数据线传到手机
2. 手机上点开安装，若提示"未知来源"就允许一次

**方式二：数据线直连（调试方便）**
1. 手机「设置 → 关于手机」连点**版本号** 7 次，开启开发者模式
2. 「设置 → 开发者选项」里打开 **USB 调试**
3. 数据线连电脑，手机上弹窗点"允许"
4. 然后：

```powershell
..\.toolchain\android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

> 首次打开会请求"通知"权限，同意后通知栏才会显示正在计时的任务。**拒绝也不影响计时功能**。

---

## 代码结构

```
TimeTrack/
├── build.ps1                     一键构建脚本
├── local.properties              SDK 路径（含中文，用 Unicode 转义）
├── gradle/libs.versions.toml     依赖版本集中管理
└── app/src/main/
    ├── AndroidManifest.xml
    ├── res/                      主题、图标、字符串
    └── java/com/timetrack/app/
        ├── TimeTrackApp.kt       Application，持有仓库单例
        ├── MainActivity.kt       唯一 Activity，运行时申请通知权限
        ├── data/
        │   ├── Task.kt           任务定义（软删除，统计不丢历史）
        │   ├── Session.kt        每段计时（endTime=null 表示进行中）
        │   ├── Models.kt         统计 / 导出用的数据模型
        │   ├── TimeTrackDao.kt   Room 查询
        │   ├── TimeTrackDatabase.kt
        │   ├── StatsCalculator.kt       ★ 纯函数聚合，可单元测试
        │   └── TimeTrackRepository.kt   ★ 全部业务规则在这里
        ├── service/TimerService.kt     前台服务，只负责通知
        ├── ui/
        │   ├── AppViewModel.kt   状态与操作
        │   ├── AppRoot.kt        底部导航框架
        │   ├── TimerScreen.kt    计时页
        │   ├── StatsScreen.kt    统计页
        │   ├── ExportScreen.kt   导出页
        │   ├── Charts.kt         自绘环形图
        │   └── theme/
        └── util/
            ├── Formatters.kt     时间格式化
            └── Exporter.kt       CSV / JSON 生成
```

---

## 几个关键设计（改动前建议先读）

**1. 时长永远由时间戳推导，不在内存里累加**

数据库只存 `startTime` 和 `endTime`。显示时长用 `now - startTime` 实时算。这样进程被杀、手机重启、切到后台，计时都不会错乱。

**2. 全局只有一个"进行中"的计时段**

`endTime IS NULL` 的行最多一条。开始任何任务都在**同一个事务里**先关掉旧的再开新的，中途崩溃也不会留下两段重叠记录。

**3. 跨天的计时段按重叠切分**

`23:30 → 00:30` 这半小时会**分别计入两天各 30 分钟**，而不是粗暴地全算给开始那天。查询用的是"时间区间有重叠"而不是"开始时间落在当天"。

**4. 任务不做物理删除**

统计通过 `taskId` 关联任务。删任务只置 `archived = true`，历史统计因此不会出现"未知任务"。

**5. 通知栏不用定时刷新**

用系统的 chronometer（`setWhen` + `setUsesChronometer`）让系统自己走秒，所以运行时**零唤醒、零耗电**，服务也不需要常驻刷新循环。

**6. 图表不依赖第三方库**

环形饼图是 `Canvas.drawArc` 手写的（约 20 行），条形图用 Compose 布局实现。省掉图表库让 APK 更小、构建更可控。

---

## 数据存在哪

应用私有目录下的 SQLite 数据库：

```
/data/data/com.timetrack.app/databases/timetrack.db
```

卸载应用会一并删除，**所以重要数据请定期用导出页备份**。

---

## 以后想扩展

- **周 / 月 / 年统计**：`TimeTrackRepository` 里已有按任意日期区间取数的方法（`exportRows` 的思路），加一个聚合维度即可
- **番茄钟**：在 `Session` 上加 `plannedMillis` 字段，`TimerService` 里用 `setChronometerCountDown(true)`
- **任务改名 / 归档界面**：`dao.updateTask` 已就绪，只缺 UI
- **自动备份**：把 `Exporter` 的输出定期写入 SAF 目录
