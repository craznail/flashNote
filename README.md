# flashNote（闪记）

Android 悬浮球截屏笔记应用（侧载）。点击悬浮球 → MediaProjection 截屏 → 保存 PNG 到应用私有目录 → 写入 Room 笔记 → 悬浮层反馈「已保存到笔记」。

**不使用无障碍（Accessibility）抓取。** `isPremium=false` 占位。

## 环境要求

- JDK 17+
- Android SDK（`compileSdk` / `targetSdk` 34，`minSdk` 26）
- 本仓库构建时设置：`ANDROID_HOME=/workspace/android-sdk`（或本机 SDK 路径写入 `local.properties` 的 `sdk.dir`）

## 构建

```bash
export ANDROID_HOME=/workspace/android-sdk   # 按本机路径调整
./gradlew :app:assembleDebug
```

成功后 Debug APK：

```
app/build/outputs/apk/debug/app-debug.apk
```

安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 使用流程

1. 打开应用 → 笔记收件箱。
2. 点击「开启悬浮球」。若未授权「显示在其他应用上层」，会跳转系统设置。
3. 点击悬浮球：展开弧形玻璃菜单（再点球或点空白处收起）。
4. 菜单项：「只存图」「存图+摘要」「设置」「退出」。首次截屏项会弹出 **MediaProjection** 授权；同意后截屏保存。
5. 成功：球心绿色对勾约 420ms（无中心 Toast）；可选缩略图角标。截屏前会暂时隐藏悬浮球，避免球入镜。
6. 拒绝授权：球体红色闪约 350ms。
7. 系统 tip（授权成功 / 共享中断 / 再点退出）：球旁短 pill（≤8 字）。
8. 「退出」需连点两次确认（侧边 pill「再点退出」）；「设置」打开应用设置页。

### 设置

- **本地摘要**：默认关闭，开关会持久化；当前版本不真正生成摘要。

## 权限说明

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮球 |
| `FOREGROUND_SERVICE` / `SPECIAL_USE` | 悬浮球前台服务 |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | 截屏前台服务 |
| `POST_NOTIFICATIONS`（API 33+） | 前台服务通知 |
| MediaProjection（运行时弹窗） | 截取屏幕像素 |

## Overlay → Capture → Save → Feedback 代码路径

1. `OverlayService` + `OverlayBallView`：40dp 玻璃球、贴边露出约 29dp、点击展开弧形菜单（按下缩放 0.92）。
2. 无 MediaProjection 令牌时 → `ProjectionPermissionActivity`（透明、用完即关）。
3. `CaptureService`（`mediaProjection` FGS）→ `VirtualDisplay` + `ImageReader` → PNG 写入 `files/notes/`。
4. `NoteRepository` / Room 插入行 → `OverlayService.notifySaved` → 球心绿勾 420ms（无中心弹层）。

## 国产 ROM 注意事项（MIUI / 华为 / HarmonyOS 等）

- **显示悬浮窗**：设置 → 应用设置 → 闪记 → 允许「显示悬浮窗 / 在其他应用上层显示」。
- **后台弹窗 / 后台启动界面**：MediaProjection 授权页可能被拦截，请允许「后台弹出界面」。
- **省电 / 自启动**：将闪记设为「无限制 / 允许自启动」，避免悬浮球服务被杀。
- **通知**：需允许通知，否则前台服务可能被系统限制。
- 部分机型首次截屏后令牌会失效，再次点击球会重新走系统授权（属预期）。

## 技术栈

- Kotlin、AGP 8.2.x、Compose（收件箱/设置）、Views（悬浮球）
- Room、Coil、Foreground Service（specialUse + mediaProjection）

## 许可证

私有仓库；按项目约定使用。


## Slice ② — OCR & local summary

- After capture (unless 「只存图」), ML Kit Chinese text recognition runs on-device and fills `ocrText`.
- Settings 「本地摘要」 or arc menu 「存图+摘要」 builds a **local** heuristic summary (first meaningful lines) — no cloud.
- Notes list shows summary snippet, else OCR snippet.


## Slice ③ — Inbox & detail

- Notes list: newest first, thumbnail, badge (本地摘要 / 含 OCR / 仅图), snippet.
- Tap row → detail: full image + summary + OCR text.


## 0.1.12 — Remote loading + inbox micro-polish

- Sticky side pill for「摘要生成中…」(clears on green-check success / fail tip / capture fail); no center toast.
- Inbox: Material card press ripple; swipe-delete reveal fades in with progress; slightly tighter list spacing.

## Slice ④ — Remote AI (paid path)

- Settings: 「模拟付费」unlocks 「远端 AI 概要」 (real IAP later).
- Arc menu 「存图+摘要」 with premium + remote on → real OpenAI-compatible Chat Completions; sticky side pill「摘要生成中…」while remote runs; success → green check only; fail → local summary + side-pill tip; screenshot always saved.
- Missing Base URL / API Key → tip「未配置远端 API…」(no silent fake success; QA `【远端】` stand-in removed).
- Request sends OCR text only (in `messages`); no images / device ids.
- Image-only / summary-off paths never call remote.

### 配置远端 API（Key 勿提交 git）

1. 打开应用 → 设置 → 打开「模拟付费」→ 打开「远端 AI 概要」。
2. 填写 **Base URL**（如 `https://api.openai.com/v1`，或完整 `.../chat/completions`）、**API Key**、**模型**（默认 `gpt-4o-mini`）→「保存 API 配置」。
3. （可选）构建默认值可写在本机 `local.properties`（已 gitignore）：

```properties
REMOTE_AI_ENDPOINT=https://api.openai.com/v1
REMOTE_AI_API_KEY=sk-...
REMOTE_AI_MODEL=gpt-4o-mini
```

仅当 SharedPreferences 对应项为空时才会用到上述 BuildConfig 默认值。


## P0 — One MediaProjection consent per process

- After first grant, `CaptureService` stays as MEDIA_PROJECTION FGS and reuses the token.
- Subsequent ball taps do **not** open the system dialog until the process dies or the system revokes projection.
- Android 14+: `MediaProjectionConfig.createConfigForDefaultDisplay()` prefers whole-screen capture.


## Emulator self-test（box，勿打扰真机）

```bash
export ANDROID_HOME=/workspace/android-sdk
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

# 1) 保证只有一个健康 emulator
adb devices -l
# 若 offline / 重复 qemu：杀掉后冷启动
# 本 box 若 nested KVM 崩溃（dmesg kvm_spurious_fault），用 -accel off（TCG，慢）
# emulator -avd memtrain34 -no-window -no-audio -gpu swiftshader_indirect -no-boot-anim -accel off

adb wait-for-device
until [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == "1" ]]; do sleep 2; done

# 2) 安装
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3) 悬浮窗权限 + 启动
adb shell appops set com.craznail.flashnote SYSTEM_ALERT_WINDOW allow
adb shell am start -n com.craznail.flashnote/.MainActivity

# 4) 权限 + 开悬浮球（服务 exported=false，用 Activity intent）
adb shell pm grant com.craznail.flashnote android.permission.POST_NOTIFICATIONS
adb shell am start -n com.craznail.flashnote/.MainActivity \
  -a com.craznail.flashnote.START_OVERLAY --ez startOverlay true

# 5) 冒烟：版本号；OverlayService isForeground；logcat 无 FATAL / FGS timeout
adb shell dumpsys package com.craznail.flashnote | grep versionName
adb shell dumpsys activity services com.craznail.flashnote | grep -A3 OverlayService
adb logcat -d -s AndroidRuntime:E *:F | tail -50
```

Overlay 锁定规格：球 46dp / 贴边可见 34dp；成功仅绿勾 420ms；失败红闪 ~350ms；系统 tip 仅授权成功/共享中断（侧 pill ≤8 字、1.2s）。
