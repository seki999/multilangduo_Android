# 构建验证记录

验证日期：2026-10-04（Asia/Tokyo）。

项目目录：`C:\Users\seki9\Documents\multilangduo_Android`。

## 实际执行结果

```powershell
.\gradlew.bat clean assembleDebug test lintDebug --no-daemon --console=plain
```

首版全量构建：`BUILD SUCCESSFUL in 1m 19s`，75 个任务执行成功。原始输出：[build-output.log](build-output.log)。后续移除资源迁移留下的空目录后单独复跑 Lint，输出：[lint-output.log](lint-output.log)。下表为最新历史记录版本的测试结果。

| 自动化测试 | Debug | Release |
| --- | --- | --- |
| TextParserTest | 8 通过，0 失败 | 8 通过，0 失败 |
| WavWriterTest | 9 通过，0 失败 | 9 通过，0 失败 |
| SequencePlayerTest | 5 通过，0 失败 | 5 通过，0 失败 |
| PauseGateTest | 6 通过，0 失败 | 6 通过，0 失败 |
| PlaybackHistoryRepositoryTest | 9 通过，0 失败 | 9 通过，0 失败 |

37 个不同测试在两个配置中均通过。Lint 无错误，5 项依赖版本升级提示保留；依赖固定为本项目实测通过的版本。

APK 签名验证：`apksigner verify --verbose` → `Verifies`，APK Signature Scheme v2 验证通过，1 个 debug signer。

APK 路径：`app/build/outputs/apk/debug/app-debug.apk`。

最新 APK SHA-256：`9BADCBC6652EE3A6898DD49C32C0B60A618DAE1C697F8025473F576472B026E0`。

后续屏幕常亮与预览位置更新：执行 `assembleDebug lintDebug --no-daemon`，`BUILD SUCCESSFUL in 50s`。APK 签名验证再次通过，Lint 0 错误、5 项依赖更新提示。输出：[screen-update-build.log](screen-update-build.log)。本次 UI 修改没有新增或重跑单元测试。

后续预览隐藏 Speaker 前缀更新：仅在 Compose 显示字符串中移除两个行首前缀，原始文本、Parser、播放和音频导出模块未修改。执行 `assembleDebug testDebugUnitTest --no-daemon`，`BUILD SUCCESSFUL in 19s`，22 项 Debug 单元测试通过，APK 签名验证通过。输出：[preview-update-build.log](preview-update-build.log)。本次未重跑 Lint 或 Release 测试。

后续预览高度更新：取消固定 360dp，高度根据可见区域和上方控件自适应，填满剩余空间。执行 `assembleDebug lintDebug --no-daemon`，`BUILD SUCCESSFUL in 38s`；Lint 0 错误、5 项依赖更新提示，APK 签名验证通过。输出：[preview-height-build.log](preview-height-build.log)。本次仅修改布局，未新增或重跑单元测试；实际手机布局待覆盖安装后确认。

最新暂停与后台更新：增加前台服务、共享任务控制器、MediaPlayer 原位置暂停／继续、冻结跟读倒计时和通知控制。新增 6 项暂停测试；执行 `assembleDebug test lintDebug --no-daemon`，`BUILD SUCCESSFUL in 44s`，Debug／Release 各 28 项测试通过，Lint 0 错误、5 项依赖更新提示。新版 APK 签名验证通过。输出：[background-playback-build.log](background-playback-build.log)。Activity 切换／重建不再取消任务；系统音频焦点丢失会暂停。后台真实播放、录音和通知按钮尚未执行手机端验收。

核对 minSdk=26、targetSdk=35、applicationId=`com.seki.multilangduo`；应用声明麦克风、通知、前台服务对应类型和 CPU 唤醒权限，AndroidX 同时合并仅供应用内部接收器使用的 signature 级权限。

原始 HTML 参考副本与用户文件 SHA-256 相同。业务代码未使用 WebView，未留下 TODO / FIXME。

## 未执行的设备检查

删除默认对话更新：新安装初始文本为空；覆盖安装只清理与旧默认对话完全相同的保存内容，其他用户输入保留。执行 `assembleDebug testDebugUnitTest --no-daemon`，`BUILD SUCCESSFUL in 20s`，28 项 Debug 测试通过，APK 签名验证通过。输出：[empty-default-build.log](empty-default-build.log)。此次未重跑 Release 测试或 Lint。

`adb devices` 返回空列表，本轮没有安装或操作真实 Android 手机。系统 Voice 音质、SpeechRecognizer 的实时识别、Dialog 实际布局、SAF 文件保存及旋转／后台行为必须根据 [DEVICE_TESTS.md](DEVICE_TESTS.md) 实机验证。自动化测试通过不能代替这些检查。

历史记录更新：开始朗读时保存原始文本与时间，支持列表、全文查看、载入和单条确认删除。采用原子文件保存，长文本不受 writeUTF 长度上限限制，损坏记录仍可删除。新增 9 项测试验证重启持久化、顺序、删除、长文本、损坏文件、临时文件和路径边界。执行 assembleDebug test lintDebug --no-daemon，BUILD SUCCESSFUL in 54s；Debug／Release 各 37 项通过，Lint 0 错误、5 项依赖提示，APK 签名验证通过。输出：[history-build.log](history-build.log)。历史窗口实际手机布局待验收。
