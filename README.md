# ARuler for Android

基于 iOS 开源项目 [ARuler](https://github.com/duzexu/ARuler) 复刻的 Android 版本。使用 **ARCore** 或 **华为 AR Engine** 做空间追踪（启动时自动选择），用 **OpenGL ES 3.0** 自绘相机画面、测量线、特征点和调试平面，界面与交互尽量对照原 iOS 版本实现。

原项目通过 ARKit 的平面命中与特征点云实现 AR 测距；本移植版保留了同样的测量思路，并在 ARCore / AR Engine 上重新实现了特征点锥形筛选、3σ 离群点过滤、最小二乘平面拟合和末端点平滑。

> 运行需要支持 ARCore 的 Android 设备（Android 7.0 / API 24 及以上），或支持华为 AR Engine 的华为/荣耀设备。模拟器通常无法进行真实 AR 测量，建议在真机上验证。

## 功能对照

| 功能 | 状态 |
| --- | --- |
| AR 相机实时预览 | ✅ ARCore / 华为 AR Engine 相机 + OpenGL 自定义渲染 |
| 十字指示器，可测量时变绿、不可用时变红 | ✅ |
| 点击按钮设置起点 / 终点，实时显示长度 | ✅ |
| 同时保留多条已完成测量 | ✅ |
| 删除当前测量或上一条测量 | ✅ |
| 清空全部测量并重新初始化 AR 会话 | ✅ |
| 特征点云显示 | ✅ 始终显示（与 iOS 一致） |
| 平面网格、世界坐标轴、目标框调试视图 | ✅ 仅 Debug 构建显示调试按钮 |
| 长度单位：厘米 / 米 / 英尺 / 英寸 / 尺 | ✅ 设置页即时保存 |
| 首次启动新手引导（12 步教练层） | ✅ 支持高亮和点击继续 |
| 新手教程 / 图标说明页 | ✅ |
| 常见问题页 | ✅ |
| 关于 / 支持 / 打赏 / 开源协议页 | ✅ |
| 相机权限、ARCore / AR Engine 安装与不支持设备提示 | ✅ |
| 追踪状态 HUD 提示 | ✅ |
| 分享、应用商店评价、GitHub 链接 | ✅ |

## 构建与运行

环境要求：

- JDK 17
- Android SDK Platform 36、Build Tools 35+
- Gradle 8.14.3（仓库已包含 Gradle Wrapper）
- 一台支持 ARCore 或华为 AR Engine 的 Android 真机（模拟器无法进行真实测量）
- 华为 AR Engine SDK 从 `https://developer.huawei.com/repo/` 拉取，已在 `settings.gradle.kts` 中配置

如果命令行构建提示找不到 Android SDK，请在项目根目录的 `local.properties` 中写入：

```properties
sdk.dir=<你的 Android SDK 路径>
```

构建 Debug APK：

```bash
./gradlew :app:assembleDebug
```

产物位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 应用启动时会自动选择跟踪后端：普通设备使用“Google Play Services for AR (ARCore)”；没有 GMS 的华为/荣耀设备使用系统自带的华为 AR Engine。运行时缺失或过旧时，会在对应应用商店中引导安装/更新。

## 测量流程

每帧从屏幕中心发出相机射线，按以下优先级寻找世界坐标点：

1. **命中已有水平平面**：调用后端的 `hitTest`（ARCore `Frame.hitTest` / AR Engine `ARFrame.hitTest`），只接受处于追踪状态的水平平面。
2. **特征点平面拟合**：取以射线为轴、半角 2.5°、距离 0.1–50 m 的锥体内的特征点；用 3σ 规则过滤离群点；对剩余点做最小二乘平面拟合；将射线与该平面求交。交点距离超过 5 m 时退回特征点均值。
3. **水平面兜底**：仅在射线向下时，与高度为 0（AR 会话初始高度）的水平面求交。
4. 无有效结果时指示器变红，禁止开始新的测量。

确定起终点后，末端点使用最近 8 帧结果的平均值做平滑，以减轻抖动。测量线用面向相机的屏幕空间四边形绘制，端点绘制为圆形点精灵。

对应的关键代码：

```text
app/src/main/java/com/aruler/android/ar/TrackingBackend.kt       后端抽象与启动时选择
app/src/main/java/com/aruler/android/ar/ArCoreBackend.kt         ARCore 实现
app/src/main/java/com/aruler/android/ar/ArEngineBackend.kt       华为 AR Engine 实现
app/src/main/java/com/aruler/android/ar/HitTester.kt             命中优先级
app/src/main/java/com/aruler/android/ar/MeasurementMath.kt       3σ 过滤、Jacobi 特征分解、平面拟合、射线求交
app/src/main/java/com/aruler/android/ar/MeasurementController.kt 测量状态、平滑
app/src/main/java/com/aruler/android/ar/ArRenderer.kt            AR 帧循环与 OpenGL 绘制
app/src/main/assets/shaders/                                     相机背景、点精灵、粗线着色器
```

## 与 iOS 原项目的对应关系

| iOS | Android |
| --- | --- |
| `ViewController.swift` | `MainActivity.kt`、`ArRenderer.kt` |
| `LineNode.swift` | `MeasurementController.kt`、`ArRenderer.kt` |
| `FocusSquare.swift` | `ArRenderer.drawDebugVisuals()` 中的调试方框 |
| `Plane.swift`、`PlaneDebugVisualization.swift` | `ArRenderer.drawDebugVisuals()` 中的平面网格 |
| `Utilities.swift` | `MeasurementMath.kt`、`HitTester.kt` |
| `PlaneDetector.mm`（OpenCV SVD） | `MeasurementMath.fitPlane()`（纯 Kotlin Jacobi 特征分解） |
| `SettingViewController` | `SettingsActivity` |
| `GuideViewController` | `GuideActivity` |
| `QuestionViewController` | `FaqActivity` |
| `AboutViewController` | `AboutActivity` |
| `SupportViewController` | `SupportActivity` |
| `DonateViewController` | `DonateActivity` |
| `LicenseViewController` | `LicenseActivity` |
| `WebViewController`（SFSafari） | `WebViewActivity` |
| `Main.storyboard` | `res/layout/activity_*.xml` |
| `AwesomeIntroGuideView` | `CoachMarkOverlayView` |
| PKHUD | `HudView` |
| SnapKit / R.swift / LeanCloudFeedback | 原生 View 布局；反馈改为 GitHub Issues |

## 平台适配说明

- **AR 渲染**：iOS 使用 SceneKit；Android 使用 ARCore 或华为 AR Engine + 自定义 GLES 3.0 渲染器，不依赖 Sceneform。华为/荣耀设备启动时自动切换到 AR Engine，共用同一套渲染与测量代码。
- **平面检测**：与原版保持一致，只开启水平平面检测。垂直表面上的测量通过特征点拟合实现。
- **文字标注**：iOS 的 3D `SCNText` 改为跟随屏幕投影的 2D 标签（`MeasurementLabelView`），显示更清晰。
- **新手引导**：原版首次启动时 AR 会话尚未运行；本版在相机启动后显示教练层，背景为实时画面。
- **设置返回**：本版从设置/帮助页返回时保留已完成的测量；原版会重新初始化 AR 会话。
- **反馈入口**：“吐槽 ARuler”改为打开原项目的 GitHub Issues 页面（原版使用 LeanCloud 反馈组件）。
- **打赏**：保留原项目作者的微信 / 支付宝收款码，按钮与长按均可将二维码保存到相册；二维码仅用于向原作者打赏。
- **默认单位**：厘米。原版设置控件默认选中厘米，但历史代码存在索引错位；本版按界面语义修正为厘米。
- **调试按钮**：与 iOS 的 `#if DEBUG` 一致，只在 Debug 构建显示。

## 测试

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

- 18 个纯 JVM 单元测试覆盖平面拟合、射线求交、3σ 过滤、锥形特征点筛选、投影、四元数旋转、特征分解、单位换算和测量状态机。
- 5 个 Robolectric 测试验证主界面和所有二级页面的真实布局加载与关键控件。
- 1 个 Robolectric 原生渲染测试会把主要页面导出为 PNG（`app/build/reports/preview/`），用于检查布局与配色；这些预览不包含真实相机画面。
- Lint 无错误。

已在荣耀 30S（Android 10，华为 AR Engine 3.11）上验证应用启动、后端选择、相机预览和渲染循环；平面识别与测量交互仍需要在真实场景中移动设备验证。ARCore 后端需要在 ARCore 认证真机上验证。

## 目录结构

```text
app/src/main/
├── assets/shaders/        相机背景、点、粗线着色器
├── java/com/aruler/android/
│   ├── ar/                ARCore 会话、命中测试、测量数学、OpenGL 渲染
│   ├── ui/                HUD、教练层、标签层、单位分段控件
│   ├── MainActivity.kt    AR 主界面与会话生命周期
│   └── *Activity.kt       设置、教程、FAQ、关于、支持、打赏、协议、网页
└── res/
    ├── layout/            主界面与全部二级页面
    ├── drawable-nodpi/    从 iOS 原项目复用的图标、Logo、二维码
    └── values/            主题、颜色、尺寸、中文字符串
```

## 致谢与许可证

- 原 iOS 项目：[duzexu/ARuler](https://github.com/duzexu/ARuler)，作者 Zexu。本项目复用了其图标、Logo、二维码等资源，并参考其测量算法与页面结构。
- [Google ARCore SDK](https://github.com/google-ar/arcore-android-sdk)（Apache 2.0）
- [Huawei AR Engine SDK](https://developer.huawei.com/consumer/cn/hms/huawei-arengine/)（Apache 2.0）
- AndroidX / Material Components（Apache 2.0）
- OpenGL ES（Khronos Group）

本项目作为原项目的衍生移植，遵循原项目仓库中的 **GNU General Public License v2.0** 发布，完整协议见 [LICENSE](./LICENSE)。本项目与 Apple、Google 无关联；ARuler 名称与视觉标识归原作者所有。
