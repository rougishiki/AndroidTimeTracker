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

**待办页**
- 两个清单：**今天**和**本周**，顶部一键切换
- 日待办每天早上都是空的（昨天的不自动延续，但可以左右翻回去看）
- 日待办页下方常驻显示**本周还没完成的事项**，可以直接在那里打勾——一件周任务今天做完了，勾掉即**整周完成**
- 未完成在前，已完成收在下面的「已完成」分组里，不占地方
- 打勾完成 / 再点取消，左滑删除，长按重命名
- 日期可左右翻，也可以点日历图标**直接跳到某一天**（月历以**周一为第一列**）
- 本周视图可点图标打开**周列表**，直接跳到某一周
- 日历与周列表上带红点的，表示那天 / 那周**还有未完成的待办**

**统计页**
- 默认显示今天，可左右切换到任意历史日期
- 当日总时长 + 任务项数
- **环形饼图**：各任务时长占比
- **条形图**：每项任务的时长、百分比，按耗时从多到少排列
- 正在计时的任务会**实时增长**，不需要手动刷新

**导出页**
- 范围：今天 / 最近 7 天 / 最近 30 天 / 全部
- **CSV**：每段记录一行（任务名、开始、结束、秒数、小时数、状态）。已带 UTF-8 BOM，Excel / WPS 双击直接打开，中文不乱码
- **JSON**：结构化完整备份，保留原始时间戳；**待办列表始终全量导出**（导出范围只作用于计时记录）
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
.\build.ps1                      # release 正式版（约 1.24 MB）
.\build.ps1 -Variant debug       # debug 版（约 9.4 MB，不混淆，排查问题用）
```

> 如果报"无法加载文件 build.ps1，未对文件进行数字签名"，是 PowerShell 执行策略的问题，
> 与项目无关。绕过方式：
> `powershell -ExecutionPolicy Bypass -File .\build.ps1`
> 或者按下面「手动构建」直接调 Gradle。

产物：

```
TimeTrack\app\build\outputs\apk\release\app-release.apk    1.24 MB
TimeTrack\app\build\outputs\apk\debug\app-debug.apk        9.42 MB
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

`app/src/test/` 下有 **47 个纯 JVM 单元测试**，覆盖最容易出错的日期、统计与导出规则：

```powershell
cd TimeTrack
.\gradlew.bat :app:testDebugUnitTest
```

> ⚠️ 旧版本这里写过"本机沙箱禁止命名管道，Gradle 的 test worker 起不来，只能手工拼
> classpath 跑 JUnit"。**那条说明已作废**：现在 `testDebugUnitTest` 能直接跑
> （实测 47 个用例全绿，报告在 `app/build/reports/tests/`）。

四组测试：

| 测试类 | 用例 | 覆盖内容 |
|---|---|---|
| `TodoPeriodTest` | 16 | 周期键、ISO 周年边界、周历网格 |
| `MigrationsSchemaTest` | 3 | 手写迁移与 Room 生成的 schema 是否一致 |
| `StatsCalculatorTest` | 9 | 跨天切分、进行中区间、时钟回拨、窗口裁剪、合并排序 |
| `ExporterTest` | 19 | CSV 的 BOM 与转义、小数点的语言无关性、JSON 的 null 与待办段 |

三个专门钉死的坑：

- **ISO 周年 ≠ 日历年。** `2027-01-01` 的周键是 `2026-W53`，不是 `2027-W53`——它属于
  2026-12-28 那一周。天真的 `年-W周数` 会把**同一个 ISO 周劈成两个键**，一条周待办于是
  同时出现在两个"周"里。测试逐日校验 2020–2030 每天都能从键回环到该周周一（0 失败）。
- **迁移必须与 schema 逐字符一致。** `MigrationsSchemaTest` 读 `app/schemas/` 里 Room
  导出的 JSON 比对迁移语句。改了实体却忘了改迁移，这个测试会红——否则用户升级后
  App 一打开就崩。
- **小数点在任意语言下都是 `.`**，否则 CSV 列会错位。


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
├── app/schemas/                  Room 导出的每版 schema（★ 迁移的对照物，必须提交）
└── app/src/main/
    ├── AndroidManifest.xml
    ├── res/                      主题、图标、字符串
    └── java/com/timetrack/app/
        ├── TimeTrackApp.kt       Application，持有两个仓库单例
        ├── MainActivity.kt       唯一 Activity，运行时申请通知权限
        ├── data/
        │   ├── Task.kt           任务定义（软删除，统计不丢历史）
        │   ├── Session.kt        每段计时（endTime=null 表示进行中）
        │   ├── Models.kt         统计 / 导出 / 待办聚合用的数据模型
        │   ├── TimeTrackDao.kt   Room 查询
        │   ├── TimeTrackDatabase.kt     v2，注册迁移与类型转换器
        │   ├── Migrations.kt     ★ 手写 1→2 迁移，语句与 schema 逐字符对齐
        │   ├── StatsCalculator.kt       ★ 纯函数聚合，可单元测试
        │   ├── TimeTrackRepository.kt   ★ 计时域的全部业务规则
        │   ├── Todo.kt           待办表（日／周共用一张表）
        │   ├── TodoDao.kt        待办查询
        │   ├── TodoPeriod.kt     ★ 周期键与日期运算，纯函数，可单元测试
        │   ├── TodoRepository.kt ★ 待办域的全部业务规则
        │   └── Converters.kt     enum 按名字存库，避免序号漂移
        ├── service/TimerService.kt     前台服务，只负责通知
        ├── ui/
        │   ├── AppViewModel.kt      计时 / 统计 / 导出状态
        │   ├── TodoViewModel.kt     待办状态（两个页面共用，锚在一个 dayKey 上）
        │   ├── AppRoot.kt           底部导航框架（4 个 tab）
        │   ├── TimerScreen.kt       计时页
        │   ├── TodoScreen.kt        待办页（日 / 周、月历、周列表）
        │   ├── StatsScreen.kt       统计页
        │   ├── ExportScreen.kt      导出页
        │   ├── Charts.kt            自绘环形图
        │   └── theme/
        └── util/
            ├── Formatters.kt     时间格式化
            └── Exporter.kt       CSV / JSON 生成
```

**计时和待办是两个互不引用的域**：`TimeTrackRepository` 与 `TodoRepository` 各管各的，
`Task` / `Session` 与 `Todo` 之间没有任何外键。好处是加待办功能时**计时域的代码一行都没改**；
代价是两个清单只是粒度不同的两个桶，周待办不会自动拆解成日待办。

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

**7. 待办只存"完成时刻"，不存"已完成"标志**

`todos` 表里没有 `done` 字段，只有 `doneAt`。日／周两个清单共用这一张表，靠
`periodKey` 的格式区分（`2026-10-05` / `2026-W53`）。这带来一个意外的好处：

> 因为完成状态只有一个时间戳，**过去某一天的"本周还剩什么"可以纯推导重建**——
> `doneAt IS NULL OR doneAt >= 那一天开始`。翻回上周三，看到的就是上周三**当时**的样子，
> 不需要快照表，也不需要任何定时任务。

**8. 周期键必须用 ISO 周年，不能用日历年**

`TodoPeriod.weekKey` 取的是 `IsoFields.WEEK_BASED_YEAR`。这两者每逢跨年就不一致：
`2027-01-01` 属于 `2026-W53`（2026 有 53 个 ISO 周）。如果按 `年-W周数` 生成键，
**同一个 ISO 周会得到两个键**，一条周待办同时出现在两个"周"里，勾掉一个另一个还在。
`TodoPeriodTest` 逐日校验 2020–2030 的键能回环到该周周一。

**9. 有史以来的第一次数据库迁移是手写的**

v1 时期 `exportSchema` 是关的，所以没有 v1 schema 给 `@AutoMigration` 做对比，v2 只能手写
`MIGRATION_1_2`。为了让它可验证，`app/schemas/` 里的 `1.json` 是在改实体**之前**导出的，
`2.json` 是改完之后导出的，`MigrationsSchemaTest` 拿两者比对迁移语句。

Room 会在升级后第一次打开数据库时按 `2.json` 重新校验，**任何列类型、可空性或索引名的差异
都会抛异常**——用户看到的就是"更新后 App 一打开就崩"。

---

## 数据存在哪

应用私有目录下的 SQLite 数据库（**当前版本 v2**）：

```
/data/data/com.timetrack.app/databases/timetrack.db
```

表：`tasks`、`sessions`（计时域）、`todos`（待办域）。

卸载应用会一并删除，**所以重要数据请定期用导出页备份**。从 v1 升到 v2 会走
`MIGRATION_1_2`，只新建 `todos` 表及其索引，**既有计时数据原样保留**。

---

## 以后想扩展

- **周 / 月 / 年统计**：`TimeTrackRepository` 里已有按任意日期区间取数的方法（`exportRows` 的思路），加一个聚合维度即可
- **番茄钟**：在 `Session` 上加 `plannedMillis` 字段，`TimerService` 里用 `setChronometerCountDown(true)`
- **任务改名 / 归档界面**：`dao.updateTask` 已就绪，只缺 UI
- **自动备份**：把 `Exporter` 的输出定期写入 SAF 目录
- **待办的习惯型重复**：现在的模型是"一次性待办"。习惯型（每天／每周自动出现、不用重新输入）
  需要再加一张完成记录表，`Todo` 本身不用动
- **把周待办拉到某一天做**：`todos` 表已有 `periodKey`，加一条"本期激活"的记录即可，不用改表结构
