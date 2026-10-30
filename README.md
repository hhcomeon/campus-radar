# 校招雷达（CampusRadar）

一个 Android 应用，用来**每天自动收集秋招 / 春招 / 实习的招聘信息**，并跟踪自己的投递进度。

- 包名：`com.example.campusradar`
- minSdk 24（Android 7.0） / targetSdk 37
- 语言：Kotlin，构建：Gradle 9.5 + AGP 9.3
- **零三方网络/数据库依赖**：网络用 `HttpURLConnection`，存储用原生 `SQLiteOpenHelper`，定时任务用系统 `AlarmManager`。改起来不绕。

---

## 一、用 Android Studio 打开

1. File → Open → 选择 `D:\开发项目`
2. 首次打开会同步 Gradle，等右下角进度走完
3. 直接 Run ▶ 即可（连接真机或模拟器）

> 注意：项目路径含中文，已在 `gradle.properties` 里加了 `android.overridePathCheck=true`，否则 AGP 会报 "non-ASCII characters" 错误。

---

## 二、功能一览

| 功能 | 位置 | 说明 |
| --- | --- | --- |
| 每日自动采集 | 设置页开关 | 每天固定时间后台抓一次，抓到新的发通知 |
| 手动立即采集 | 首页右上角 ⟳ | 立刻跑一次采集 |
| 招聘季筛选 | 首页第一排 Chip | 全部 / 秋招 / 春招 / 实习 |
| 状态筛选 | 首页第二排 Chip | 全部 / 今日新增 / 收藏 / 未投递 / 已投递 / 面试中 / 已Offer |
| 搜索 | 首页顶部输入框 | 按公司、岗位、城市模糊匹配 |
| **网申投递** | 卡片右下角按钮 | 点开网申页面（App 内置浏览器），同时把状态推到「已投递」 |
| 状态流转 | 点卡片上的状态标签 | 未投递 → 已投递 → 面试中 → 已Offer → 已结束 |
| 收藏 | 点卡片右上角 ☆ | 收藏的会排在列表最前面 |
| 手动录入 | 右下角 ➕ | 自己填公司/岗位/截止日期/链接/状态 |
| 编辑删除 | 长按卡片 | 打开网申页 / 编辑信息 / 复制链接 / 删除 |

---

## 三、目录结构

```
app/src/main/java/com/example/campusradar/
├── MainActivity.kt              首页：列表 + 筛选 + 搜索
├── AddEditActivity.kt           新增 / 编辑单条信息
├── SettingsActivity.kt          数据源、自动采集时间、通知、投递打开方式
├── WebActivity.kt               内置浏览器（打开网申页）
├── model/
│   └── RecruitItem.kt           数据模型（字段 + 状态常量 + 剩余天数计算）
├── data/
│   ├── DbHelper.kt              SQLite 建表 / 增删改查 / 去重
│   └── Prefs.kt                 SharedPreferences 配置读写
├── collect/
│   ├── Collector.kt             采集引擎：demo / JSON / RSS 三种数据源解析
│   ├── CollectRunner.kt         采集 → 入库 → 通知 的流程封装
│   ├── AlarmScheduler.kt        每日定时任务注册 / 取消
│   ├── DailyReceiver.kt         定时到点的广播接收器
│   └── BootReceiver.kt          开机后恢复定时任务
└── ui/
    ├── ItemAdapter.kt           列表 RecyclerView 适配器
    ├── Links.kt                 网申链接的打开逻辑（含兜底搜索）
    ├── Edge.kt                  边到边 / 状态栏适配
    └── Notify.kt                通知渠道 + 发通知
```

资源文件：
```
app/src/main/res/
├── layout/   activity_main.xml / item_recruit.xml / activity_add_edit.xml / activity_settings.xml / activity_web.xml
├── menu/     menu_main.xml（首页右上角）/ menu_web.xml（内置浏览器右上角）
├── drawable/ bg_status_*.xml（状态标签配色）/ bg_search / bg_edit / ic_stat_radar
└── values/   strings.xml（应用名）/ colors.xml（配色）/ themes.xml
```

---

## 四、接入你自己的数据源

设置页里「数据来源」有三个选项：

1. **内置演示数据**（默认）—— 完全离线，每天自动换一批示例公司/岗位，用来看效果
2. **JSON 接口** —— 填一个返回 JSON 的 URL
3. **RSS / Atom 订阅源** —— 填 RSS 地址

### JSON 格式要求

接口用 GET 请求，返回下面两种之一即可：

```json
{
  "items": [
    {
      "company": "腾讯",
      "title": "2027届校园招聘 · 后端开发工程师",
      "season": "秋招",
      "city": "深圳",
      "deadline": "2026-11-20",
      "url": "https://join.qq.com/xxx",
      "tags": "后端"
    }
  ]
}
```

或者直接返回数组：

```json
[ { "company": "...", "title": "..." }, { ... } ]
```

字段说明（只有 `title` 必填，其余可省略）：

| 字段 | 含义 | 缺省时的处理 |
| --- | --- | --- |
| `company` | 公司名 | 从 title 里猜 |
| `title` | 岗位名 | **必填**，为空则跳过该条 |
| `season` | 秋招 / 春招 / 实习 | 从 title 关键词猜，猜不到算秋招 |
| `city` | 城市 | 从 title 里匹配常见城市名 |
| `deadline` | 截止日期 | 支持 `2026-11-20` / `2026/11/20` / `11月20日` 等写法 |
| `url` | 网申投递链接 | 空的话点投递会跳到搜索引擎找入口（见第九节） |

> 解析代码在 `Collector.parseJson()`，想加字段改这一个函数就行。
> 去重规则：公司名 + 岗位名相同视为同一条，重复采集不会重复入库。

---

## 五、命令行构建 & 安装

```bash
# 编译 Debug 包
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 通过 adb 装到真机（先无线调试连接）
adb connect 10.152.235.190:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.campusradar/.MainActivity
```

环境变量（已在本机配置好，Android Studio 也能读到）：
- `JAVA_HOME` = `D:\JDK`
- `ANDROID_HOME` = `D:\A\env\android-sdk`（实际 SDK 目录 `D:\Tools`）

---

## 六、后续想改功能，从哪下手

| 想做的事 | 改哪里 |
| --- | --- |
| 加字段（比如薪资、学历要求） | `RecruitItem.kt` 加属性 → `DbHelper.kt` 加列并把 `DB_VERSION` +1 → 布局加输入框 |
| 换配色 | `res/values/colors.xml` + `themes.xml` |
| 换定时策略（比如改成 WorkManager） | `AlarmScheduler.kt` / `DailyReceiver.kt` |
| 加新数据源类型（比如爬网页） | `Collector.kt` 里加一个解析函数，`Prefs.MODE_*` 加常量，设置页 Spinner 加一项 |
| 改通知内容 | `Notify.kt` |
| 加「导出 CSV / 分享」 | 在 `MainActivity` 菜单里加 item，读 `DbHelper.query()` 的结果写文件 |

改数据库结构时注意：`onUpgrade()` 目前是**直接删表重建**，要保留数据的话改成 `ALTER TABLE ADD COLUMN`。

---

## 七、踩过的坑（改代码时别再踩）

**1. 主题必须是 NoActionBar**
代码里用 `setSupportActionBar(toolbar)` 自己挂了 Toolbar。如果主题是 `Theme.MaterialComponents.DayNight.DarkActionBar`，
会直接崩：`IllegalStateException: This Activity already has an action bar supplied by the window decor`。
所以 `themes.xml` 和 `values-night/themes.xml` 都用的 `NoActionBar`，并显式写了 `windowActionBar=false` / `windowNoTitle=true`。

**2. targetSdk 35+ 强制边到边，要自己处理状态栏**
页面会画到状态栏底下，标题会被时间/信号图标压住。用的是 `ui/Edge.kt`：
给 Toolbar 加状态栏高度的顶部内边距（Toolbar 背景色正好填满状态栏区域），给根布局加底部内边距避开导航栏和输入法。
新加 Activity 记得也调一次 `Edge.setup(findViewById(R.id.root), toolbar)`，并且布局根节点要有 `android:id="@+id/root"`。

**3. 中文路径**
`gradle.properties` 里的 `android.overridePathCheck=true` 不要删，删了 AGP 会直接报 non-ASCII 路径错误。

**4. XML 属性里的引号**
写 `android:text="{\"a\":1}"` 会解析失败（XML 不认反斜杠转义），必须写 `&quot;`。

**5. 首页搜索框别自动获取焦点**
根布局加了 `focusableInTouchMode=true`，Manifest 里 MainActivity 也配了 `stateAlwaysHidden`，否则一进 App 就弹键盘把列表挤没。

---

## 八、在平板上验证过的设备

- 三星 Galaxy Tab A9+（SM-X210），Android 16，arm64-v8a，横屏 1920×1200
- 安装命令：`adb install -r 校招雷达-debug.apk`

---

## 九、点「网申投递」之后发生了什么

代码在 `ui/Links.kt`，一共三种情况：

| 情况 | 行为 |
| --- | --- |
| 这条信息**有** `url` | 用内置浏览器（`WebActivity`）打开网申页 |
| 这条信息**没有** `url` | 用搜索引擎搜「公司 + 岗位 + 校园招聘 网申」，跳到搜索结果 |
| 页面打不开 | 右上角菜单 / 底部按钮「浏览器打开」，改用系统浏览器重试 |

点投递按钮时，如果状态还是「未投递」，会自动改成「已投递」并提示一次。
点卡片空白处只打开网页，不改状态；状态标签点一下才会轮转。

**内置浏览器**（`WebActivity.kt`）不是外面套一层 WebView 那么简单，做了几件事：
- 开了 JavaScript、DOM Storage、第三方 Cookie —— 网申页要登录、要存表单草稿
- 底部有后退 / 前进 / 刷新，返回键优先网页内后退
- 微信、支付宝、电话等非 http 协议的链接自动交给系统处理
- 右上角可以复制链接、用系统浏览器打开

**设置页可以改两件事**：
- 「用系统浏览器打开网申页」：打开后不走内置浏览器。有些企业官网会检测 WebView，加载不出来时用它
- 「没有链接时用它搜投递入口」：百度 / 必应 / 搜狗 / 牛客，模板在 `Prefs.SEARCH_ENGINES` 里

**演示数据的链接**在 `collect/Collector.kt` 的 `COMPANIES` 里，是一张「公司 → 官方校招官网」的表，
都已经实测过能解析、能打开。你要加公司就往这张表里加一行。

**去重时的链接补全**：`DbHelper.insertOrIgnore()` 遇到已存在的记录会跳过，但如果老记录 `url` 是空的、
这次采集拿到了链接，会把链接补进去（只补空链接，不覆盖你自己改过的状态和收藏）。
