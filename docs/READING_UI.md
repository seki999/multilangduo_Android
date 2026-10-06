# 阅读预览 UI 优化（2026-10-06）

项目继续使用 Jetpack Compose / Material 3。TTS、解析器、播放顺序和控制器没有修改。

## 文件与调整入口

- 修改 `app/src/main/java/com/seki/multilangduo/ui/AppScreen.kt`：主题/字号持久化选择、预览列表、播放按钮和滚动。
- 新增 `app/src/main/java/com/seki/multilangduo/ui/ReadingStyle.kt`：三套 `readingPalettes`、四档 `readingScales`、集中字号/间距 `ReadingDimensions`、显示分类 `readingRole` 和高亮组件 `ReadingLine`。
- 新增 `app/src/test/java/com/seki/multilangduo/ui/ReadingRoleTest.kt`：显示分类及长文本回归检查。

调整朗读背景：修改对应 ReadingPalette 的 `highlight`，当前文字修改 `currentText`。
调整字号：修改 ReadingDimensions 或 readingScales。默认“大”=1.15；单词标题应用内最大38sp，仍遵循系统字体缩放。
主题与字号保存于独立 SharedPreferences `reading_ui`，不修改原有业务设置。

## 行为

保留 LazyColumn，索引与 ScriptLine 一一对应。原文、重复词和标点均不改动。
常见单词标题与音标在显示层识别，词性开头释义和分号英文搭配有独立层级。无法从现有自由文本可靠识别的内容按中文/英文正文显示；不为外观改动业务数据结构。
单词标题前留28dp形成学习块；普通行透明，仅当前行使用14dp圆角淡色高亮和4dp竖条，无阴影。
当前可见行以300ms动画移动至预览约40%位置；跨屏行使用Compose平滑滚动。列表边界会限制最终位置。
用户拖动后5秒内新行切换不触发自动跟踪；随后下一次行切换恢复跟踪。
按钮使用最小54dp高度，可随系统字体增长；横屏/大字体时外层可滚动。

## 验证与限制

使用本机 Microsoft JDK 17 构建（Android Studio 自带 JDK 25 无法运行当前 Gradle 配置）。
执行 assembleDebug、testDebugUnitTest、lintDebug。
当前ADB未连接设备；已有AVD配置，但SDK中没有emulator.exe。
因此长列表帧率、真实TTS、暂停恢复取消的设备表现、自动高亮滚动、系统大字体遮挡、长句显示、横屏和深色截图尚未通过设备验收。单元测试不能替代这些验收。
APK路径：app/build/outputs/apk/debug/app-debug.apk。

最终结果：assembleDebug、testDebugUnitTest、lintDebug 全部成功；39项单元测试，0失败。Lint为0错误、5项现有依赖更新提示。Debug APK已生成。git diff --check通过。
