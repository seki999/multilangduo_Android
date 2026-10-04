# MultilangDuo Android

把提供的 `multilangduo.html` 移植成 Kotlin 原生 Android 应用。使用 Jetpack Compose / Material 3、系统 TextToSpeech、系统 SpeechRecognizer、Coroutines、ViewModel 和 SharedPreferences；没有 WebView，没有收费 API，也不需要 API Key。

原始 HTML 完整保存在 `reference/multilangduo.html`。与下载目录中的原文件 SHA-256 一致：`DC631E3AD8786ACAF8A766937A129916A24BCAC2A396994BFDF1BCA748658DAA`。

完整功能对应清单见 [FEATURE_CHECKLIST.md](FEATURE_CHECKLIST.md)，八项真实手机验收见 [docs/DEVICE_TESTS.md](docs/DEVICE_TESTS.md)。

## 环境与打开项目

- 本机 Android Studio：2026.1.4（build `AI-261.26222.65.2614.16204760`）。
- Android Gradle Plugin 8.7.3、Gradle Wrapper 8.9、Kotlin / Compose compiler plugin 2.0.20。
- JDK 17，Compose BOM 2024.09.00；minSdk 26，compileSdk / targetSdk 35。
- SDK Platform 35；AGP 默认使用 Build Tools 34.0.0，本机同时安装了 35.0.0。

Android Studio 中选择 Open，打开 **`C:\Users\seki9\Documents\multilangduo_Android`**，不要打开 `.git` 子目录。等待 Gradle Sync；选择 Android 8.0 或以上的设备，点击 Run。构建版本组合符合 [AGP 8.7 官方兼容要求](https://developer.android.com/build/releases/agp-8-7-0-release-notes)。

`local.properties` 为本机生成且被 Git 忽略，当前 SDK 为 `C:/Users/seki9/AppData/Local/Android/Sdk`。在其他电脑上由 Android Studio 自动生成自己的 SDK 路径。

## 构建、测试和安装

在项目根目录运行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lintDebug
```

macOS / Linux 可执行 `chmod +x gradlew` 后使用 `./gradlew` 执行相同任务。

Debug APK：

```text
C:\Users\seki9\Documents\multilangduo_Android\app\build\outputs\apk\debug\app-debug.apk
```

将 APK 复制到手机后打开，按系统提示允许该文件管理器安装应用；或者开启 USB 调试、连接手机并运行：

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Debug APK 自动使用本机 Android debug key 签名，可直接测试安装；正式发布需另行配置自己的 release 签名。

## 功能与输入格式

两个 Speaker 各自选择 English / 中文 / 日本語和系统 Voice，速度 0.50–2.00、步进 0.01；默认语言英语／中文，默认速度 1.00／1.25。设置和最后输入文本在关闭应用后保留。

```text
Speaker 1: Hello [你好]
{pause:3}
Speaker 2: 很好。
{confirm}
Speaker 1: Thank you.
```

- `Speaker 1:`、`Speaker 2:` 是精确前缀，执行时不读；无前缀默认 Speaker 1。
- 方括号片段不读。空行跳过，预览实时显示有效行。
- 朗读预览隐藏行首的 `Speaker 1:`／`Speaker 2:`，只显示后面的内容；输入框原文、Speaker 分配、控制指令、方括号显示和 WAV 导出逻辑保持原样。
- `{pause:3}` 或 `{静音:3}`：打开英文跟读识别约 3 秒，可提前点击「完成并继续」。按 HTML 的正则规则，行内出现 pause 也会使整行成为控制行。
- `{confirm}` 或 `{确认}`：必须整行匹配，等待「完成并继续」。可以「停止录音」并保留窗口，或「退出任务」。
- 每行先等待原生 TTS 文件合成完成，再等待原生播放器播放完成；高亮／自动滚动预览，任务结束显示按顺序记录的跟读汇总。
- 「暂停播放」立即暂停当前行音频，「继续播放」从原播放位置继续，不重读整句。当前行准备合成时点击暂停，则合成完成后保持等待，不自动发声。
- 跟读／confirm 窗口也提供暂停／继续：暂停录音并保留识别文本，pause 的剩余倒计时冻结，继续后恢复。
- 「取消朗读」停止当前任务，后面的行不会继续。切换其他 App、按 Home／返回键和旋转不再取消任务，由前台服务继续运行；通知可暂停／继续／取消，confirm 通知还可「完成并继续」。
- 应用在前台时保持屏幕常亮，不会因系统空闲超时自动黑屏；切到后台后系统恢复正常休眠。用户主动按电源键仍可锁屏。
- 任务开始后自动向上滚动，直到「取消朗读」按钮位于内容区域最上端，朗读预览紧接其下；当前行继续在预览内自动滚动。
- 预览框高度随屏幕可用高度自适应，填满取消按钮、状态和标题下方的剩余空间，不再固定为 360dp。
- 播放期间可以修改文本和 Speaker 设置。当前执行列表是开始时的快照；文本修改实时更新预览，Voice／速度修改用于之后的行。

「播放对话历史记录」按时间从新到旧显示每次开始朗读时的完整文本快照，支持查看、载入对话后再次朗读和单条删除。记录保存在应用内部存储，关闭后仍保留；载入使用当前 Speaker 设置。取消或失败的已启动朗读也保留记录，仅生成 WAV 不添加历史；不保存麦克风音频。删除历史不清空当前输入，也不停止播放。首次安装没有历史记录。

首次启动文本框为空，不再预填测试对话。覆盖安装后，若保存的文本仍是旧版完整默认测试对话，会自动清空；其他用户文本和 Speaker 设置保留。

## 系统 Voice 与识别

应用初始化系统默认 TTS 引擎后读取全部 `tts.voices`，严格按 `Voice.locale.language` 过滤 en／zh／ja，显示 Voice.name 和 locale。网页里的 Microsoft Emma / Xiaoxiao 不保证存在于 Android；未保存 Voice 时选择对应语言的首个可用 Voice。没有 Voice 时不代用其他语言，会给出安装语音数据提示。

可以打开「系统 TTS 设置」安装语言数据；返回应用会刷新列表。更换默认 TTS 引擎后请结束当前任务并重新启动应用进程，使 TTS 实例绑定新引擎；仅切换界面不会替换后台任务使用的引擎。

SpeechRecognizer 使用 en-US 和 partial results。每个最终话段／可恢复的静音超时后，识别会话延迟 300ms 重启；停止录音、取消、退出后不重启。pause 从打开阶段开始计时，到期调用 stopListening，并最多等待 350ms 接收最终结果；引擎未返回 final 时保留最近的 partial 内容。系统识别服务可能不支持持续识别，话段之间会有短暂重启间隔。

没有可用识别服务、权限被拒绝、网络或麦克风错误会显示中文提示。不可恢复的识别错误停止该轮录音；confirm 仍允许手动继续，pause 到期继续。应用没有自行调用网络服务；所选系统 TTS Voice 或识别服务可能需要网络。API 使用方式参考 [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech) 与 [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer) 官方文档。

## WAV 生成和保存

点击「生成语音并下载 (WAV)」后逐行调用 `TextToSpeech.synthesizeToFile()`，等待真实完成回调，保留不同 Voice 与速度。优先使用引擎合成回调提供的 PCM；没有 PCM 回调时检查引擎生成文件：标准 WAV 读取数据块，其他编码使用 Android MediaExtractor / MediaCodec 解码。

每段音频统一为 22050Hz、单声道、16-bit PCM，线性重采样／声道平均混音后流式写入 RIFF WAV，最终填写正确文件头；不会仅修改扩展名。支持引擎回调的 8-bit / 16-bit / float PCM。引擎若不提供合法 PCM、标准 WAV 或系统可解码音频，显示生成错误，避免输出伪 WAV。

导出时 pause 写入指定秒数的真实零值 PCM（四舍五入至最近样本），confirm 跳过，不打开麦克风、不等待用户确认。输出先生成至应用缓存，再通过 Storage Access Framework 的 `ACTION_CREATE_DOCUMENT` 让用户选择保存位置和文件名，默认 `tts-audio.wav`。取消生成清理临时音频；保存／取消文件选择后清理本次缓存。RIFF 文件最大约 4GB，超出会报错。保存失败时 provider 可能留下未完整写入的目标文件，应删除后重试。

## 后台播放、权限和生命周期

含 pause／confirm 的正常朗读开始前请求 `RECORD_AUDIO`，普通文本朗读和 WAV 生成不需要麦克风。Android 13 以上启动任务时请求 `POST_NOTIFICATIONS`，允许后可在通知栏控制任务；拒绝通知权限仍可运行前台服务，但系统可能仅在活动应用／任务管理入口显示服务，应用内按钮仍可操作。

新增 `FOREGROUND_SERVICE` 及 mediaPlayback／microphone／dataSync 类型权限，以及 `WAKE_LOCK`，用于实际后台音频、跟读和文件生成／保存。没有旧存储权限，不申请电池优化白名单，不开机自动录音。任务由用户在前台启动；包含识别的任务从一开始就建立 microphone 类型前台服务，使切到后台后仍能进入识别阶段，符合 [Android 前台服务类型及权限要求](https://developer.android.com/develop/background-work/services/fgs/service-types)。

Application 级 `PlaybackController` 持有任务状态和协程，`TtsViewModel` 仅作为 UI 适配器，Activity 销毁／重建不会取消服务任务。`PlaybackService` 显示常驻任务通知并维持 CPU 唤醒；暂停释放 CPU 唤醒，继续恢复，任务结束／取消移除通知并停止服务。SpeechRecognizer 在每轮结束／取消时 destroy；每行播放器在完成／取消时 release；TTS 保留初始化实例供当前应用使用，进程终止时由系统回收资源，Application 测试生命周期结束时显式 shutdown。

为实现精确暂停位置，普通朗读使用同一系统 TTS 引擎按当前 Speaker 的 Voice／速度生成当前行 WAV，再用 [Android MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer) 播放。推进下一行同时依赖合成 `onDone` 和播放器 `onCompletion`。不一次合成整个脚本，长行仍按 TTS 输入上限分块合并；当前行合成可能有短暂准备延迟。取消同时终止协程、播放器、TTS 和识别；临时音频清理。WAV 导出格式和控制行处理保持原样，导出期间不提供播放暂停。

其他 App 仅获得界面焦点时本应用继续播放。如果其他音视频应用／来电夺取系统音频焦点，本应用暂停以避免两路语音互相干扰，可手动点击继续。系统强行停止应用、回收进程、用户结束前台服务或厂商限制后台服务时，任务会停止；不会从头自动重启或擅自重新录音。Android 15 的后台文件处理时间限制触发时停止任务并提示。正在后台等待 confirm 时不会强行弹出界面，可使用通知继续或点击通知返回 App；后台 WAV 完成后返回 App 选择保存位置。

## 验证与限制

已执行 Gradle `assembleDebug test lintDebug`，结果 `BUILD SUCCESSFUL`。Parser 8 项、WAV 9 项、顺序执行与取消 5 项、暂停与冻结倒计时 6 项，播放历史持久保存与删除 9 项，共 **37 项测试**；Debug 与 Release 两种配置均通过。Lint 报告在 `app/build/reports/lint-results-debug.html`，测试报告在 `app/build/reports/tests/`。最新历史记录构建见 [docs/history-build.log](docs/history-build.log)，完整记录见 [docs/BUILD_REPORT.md](docs/BUILD_REPORT.md)。

本机无已连接 Android 手机，尚未执行真实 TTS 发声、麦克风跟读和系统文件选择器的端到端验收。请按照 [手机验收清单](docs/DEVICE_TESTS.md) 验证八项请求用例以及旋转／后台／拒绝权限。不同手机引擎的 Voice 名称、语速听感、网络需求、partial 支持、识别准确率和 synthesis 支持可能不同。

## 文件结构

```text
app/src/main/java/com/seki/multilangduo/
  MainActivity.kt             权限和 Activity 生命周期
  TtsViewModel.kt             UI 状态和操作适配
  MultilangDuoApplication.kt  跨 Activity 的共享任务实例
  PlaybackController.kt      状态机、业务协调和保存
  service/PlaybackService.kt  前台服务、通知操作和 CPU 唤醒
  model/Models.kt             Speaker／解析行／UI 状态模型
  parser/TextParser.kt        与 HTML 对应的集中解析规则
  playback/SequencePlayer.kt  顺序等待和取消边界
  playback/PauseGate.kt       暂停屏障和可冻结倒计时
  playback/PausableAudioPlayer.kt  原生播放位置保留和音频焦点
  tts/TtsManager.kt           TTS 初始化、Voice、发声、文件合成
  speech/SpeechRecognitionManager.kt  识别会话、重启和停止
  audio/AudioExporter.kt      按原始行顺序导出
  audio/WavWriter.kt          PCM 转换、静音、重采样、WAV 文件头
  audio/EngineAudioDecoder.kt 非 WAV 输出的系统解码备用路径
  data/SettingsRepository.kt  设置和文本持久保存
  ui/AppScreen.kt             原生 Compose 控件和弹窗
app/src/main/res/             应用主题、图标和备份规则
app/src/test/                 37 项单元测试
gradle/wrapper/               Gradle 8.9 Wrapper
reference/multilangduo.html   原始产品规格
FEATURE_CHECKLIST.md          完整移植清单和平台差异
docs/DEVICE_TESTS.md          实机验收步骤
```
