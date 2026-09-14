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
3. 首次点击悬浮球：系统弹出 **屏幕录制 / MediaProjection** 授权框；同意后自动截一屏并保存。
4. 之后点击悬浮球：直接截屏保存（MediaProjection 令牌仍有效时）。
5. 成功：球心绿色对勾约 420ms + 底部 Toast「已保存到笔记」；可选右下角 24dp 缩略图角标。
6. 拒绝授权：红点闪两次 + Toast「未授权截屏」；再点球可重新进入系统授权。
7. 长按悬浮球约 400ms：弹出「只存图」「存图+摘要」（摘要生成为占位 no-op）。

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

1. `OverlayService` + `OverlayBallView`：拖拽、贴边（露出 40dp）、点击/长按。
2. 无 MediaProjection 令牌时 → `ProjectionPermissionActivity`（透明、用完即关）。
3. `CaptureService`（`mediaProjection` FGS）→ `VirtualDisplay` + `ImageReader` → PNG 写入 `files/notes/`。
4. `NoteRepository` / Room 插入行 → `OverlayService.notifySaved` → 绿勾 / Toast。

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
- Settings 「本地摘要」 or long-press 「存图+摘要」 builds a **local** heuristic summary (first meaningful lines) — no cloud.
- Notes list shows summary snippet, else OCR snippet.


## Slice ③ — Inbox & detail

- Notes list: newest first, thumbnail, badge (本地摘要 / 含 OCR / 仅图), snippet.
- Tap row → detail: full image + summary + OCR text.


## Slice ④ — Remote AI (paid path)

- Settings: 「模拟付费」unlocks 「远端 AI 概要」 (real IAP later).
- Long-press 「存图+摘要」 with remote on → cloud/stand-in; fail → local summary toast, never blocks browsing.
- Request body only `{ "text": "<ocr>" }` when `REMOTE_AI_ENDPOINT` is set in `build.gradle.kts`; empty endpoint uses QA stand-in prefixed `【远端】`.
- Turning local summary off + not long-pressing summary → image only.
