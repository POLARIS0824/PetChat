# Jetpack Compose Edge-to-Edge 软键盘自适应与 IME Padding 优化技术文档

本篇技术文档记录了 PetChat 移动端应用在 Edge-to-Edge 全屏模式下，软键盘弹起时出现**输入框与键盘重叠遮挡**、**键盘与输入框存在缝隙（双重 Padding）**等问题的根本原因，以及现代 Compose 的标准解决方案。

---

## 1. 根本原因分析（Root Cause）

在 Android 引入 Edge-to-Edge（全屏边缘对齐）之后，传统的窗口大小调整逻辑（如 `adjustResize`）不再直接缩放应用窗口，而是通过窗口 Insets（WindowInsets）分发至 View 树。在 Jetpack Compose 中，IME 适配问题通常由以下几个层面的因素叠加引起：

### 1.1 全局软键盘模式冲突 (`adjustNothing`)
* **问题描述**：在 `AndroidManifest.xml` 中，`<application>` 节点下配置了 `android:windowSoftInputMode="adjustNothing"`。
* **原因**：这会强制系统完全不尝试对键盘弹出做出任何窗口和布局层面的调整。即便在 `MainActivity` 中通过代码设置了 `SOFT_INPUT_ADJUST_RESIZE`，也会因为 Manifest 的全局机制或遗留系统行为而导致 IME Insets 无法正确通知到 Compose 的 Insets 监听器。

### 1.2 遗留的 Legacy 系统栏设置
* **问题描述**：`MainActivity` 中混合使用了老旧的 `ViewCompat`、`WindowCompat.setDecorFitsSystemWindows` 以及废弃的 `SOFT_INPUT_ADJUST_RESIZE` 标志位。
* **原因**：在 API 30+ 之后，遗留的 `SOFT_INPUT_ADJUST_RESIZE` 已经过时。不规范的手动 Insets 设置与 Compose 内部的 Insets 消费机制冲突，导致键盘高度计算不准确。

### 1.3 著名的“双重 Padding 缝隙”问题（Double Padding Bug）
这是导致**聊天输入框与键盘之间产生一截高度等于底部导航栏空白缝隙**的最核心原因：
1. **Scaffold 边距传递**：`PetChatApp` 中的根布局使用了 Material 3 的 `Scaffold`，其包裹的主内容 Box 使用了 `.padding(innerPadding)`，用来避开顶部的 `TopAppBar` 和底部的 `PetChatBottomBar`（底部导航栏）。
2. **缺乏 Insets 消费声明**：该 Box 仅仅应用了物理 Padding，但**并没有通知 Compose 运行时这些系统栏 Insets 已经被消费了**。
3. **IME 边距累加**：在 `ChatScreen` 的底部输入框 `ChatInput` 节点上，应用了 `.imePadding()`。因为之前的底部栏 Padding 没有被标记为消费，Compose 会认为这部分空间依然可用，所以在键盘弹出时，最终应用的 Padding 变成了：
   $$\text{Total Padding} = \text{键盘高度 (IME)} + \text{底部导航栏高度 (Scaffold Bottom Padding)}$$
   这导致输入框被推得过高，留出了一个完全等于底部导航栏高度的尴尬空白缝隙。

### 1.4 滚动容器缺失键盘响应
* **问题描述**：在 `SettingsScreen`（设置页面）中包含多个输入框，但当键盘弹起时，底部的输入框会被直接遮挡。
* **原因**：设置页面在全屏模式下，其外部的 Column 使用了 `.verticalScroll()`，但由于整个页面没有任何容器设置 `.imePadding()` 或者是 Insets 消费，导致整个页面高度在键盘弹起时没有发生变化，滚动容器也无法响应可滚动区域的缩水，从而无法滚上去。

---

## 2. 解决方案与实施步骤（Solutions）

为了彻底并优雅地解决上述适配问题，我们采用 Android 现代 Jetpack Compose 官方推荐的 Insets 最佳实践进行整体重构。

```
+--------------------------------------------+
|             PetChatTopBar (Top)            |
+--------------------------------------------+
|                                            |
|  [Box] padding(innerPadding)                |
|        consumeWindowInsets(innerPadding)   |
|  +--------------------------------------+  |
|  |  NavDisplay                          |  |
|  |                                      |  |
|  |  [ChatScreen]                        |  |
|  |  +--------------------------------+  |  |
|  |  | LazyColumn (weight 1f)         |  |  |
|  |  +--------------------------------+  |  |
|  |  | ChatInput                      |  |  |
|  |  | -> Modifier.imePadding()       |  |  |
|  |  +--------------------------------+  |  |
|  +--------------------------------------+  |
+--------------------------------------------+
|        PetChatBottomBar (Bottom)           |
+--------------------------------------------+
```

### 2.1 修正 Manifest 的键盘配置
在 `AndroidManifest.xml` 中，针对 `MainActivity` 单独显式声明 `adjustResize`：
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:label="@string/app_name"
    android:theme="@style/Theme.Chat"
    android:windowSoftInputMode="adjustResize">
```
* **效果**：这保证了系统会在键盘弹起时，向 Compose 正确分发最新的 `WindowInsets.ime` Insets 数据。

### 2.2 现代化 Edge-to-Edge 迁移
在 `MainActivity.kt` 中，清理所有遗留的 SystemBar 设置代码，直接使用标准 Activity KTX 提供的 `enableEdgeToEdge()` API：
```kotlin
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 核心一键全屏适配：自动支持浅色/深色主题的系统图标对比度适配
        enableEdgeToEdge()

        setContent {
            PetChatApp()
        }
    }
}
```

### 2.3 使用 `consumeWindowInsets` 消除重复 Padding
在 `PetChatApp.kt` 中，在包裹导航的容器 Box 上，在应用 `padding(innerPadding)` 后，紧接着调用 `.consumeWindowInsets(innerPadding)`：
```kotlin
import androidx.compose.foundation.layout.consumeWindowInsets

Box(
    modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)             // 1. 物理排挤导航栏高度
        .consumeWindowInsets(innerPadding)  // 2. 标记这部分 Insets 已经被安全消费！
        .clickable(...)
) {
    NavDisplay(...)
}
```
* **原理解析**：一旦父容器调用了 `consumeWindowInsets(innerPadding)`，任何嵌套在底层的子组件（如 `ChatInput`）在计算 `.imePadding()` 时，Compose 就会智能地进行**差值计算**：
  $$\text{实际应用 Padding} = \max(0, \text{键盘高度} - \text{已消费的底部栏高度})$$
  这样，在键盘拉起时，输入框便会正好紧贴软键盘的顶部，没有任何多余的缝隙。

### 2.4 在滚动区域增加 IME 响应
针对像 `SettingsScreen` 这样带输入框且可滚动的页面，在其滚动 Column 容器上合适的位置（`.verticalScroll()` 之前）追加 `.imePadding()`：
```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .imePadding() // 1. 在键盘弹起时，物理缩减此 Column 的可见高度
        .verticalScroll(rememberScrollState()) // 2. 此时滚动状态能够感知到可用高度减少，从而允许输入框滑上来
        .padding(horizontal = 24.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp)
) {
    // TextFields...
}
```
* **原理解析**：当键盘弹出时，`imePadding()` 占据了底部空间，滚动容器的高度相应缩小。Android 焦点的自动滚动（Auto-scroll-to-focused-field）或者用户的物理拖拽即可完美将处于底部的 `TextField` 展现在键盘上方。

---

## 3. Jetpack Compose 键盘适配避坑指南（Best Practices）

1. **避免双重 Padding**：只要你在 `Scaffold` 的 `content` 块中为非 LazyColumn/ListView 节点直接应用了 `innerPadding` 物理 padding，请务必紧跟 `.consumeWindowInsets(innerPadding)`，否则子 View 的 `.imePadding()` 或其他 safeDrawing 边距必定会发生累加冲突。
2. **修饰符顺序至关重要**：对于滚动容器，`.imePadding()` 必须放置在 `.verticalScroll()` **之前**。如果顺序颠倒（例如先 verticalScroll 再 imePadding），会导致整个滚动内容区域的边界计算错误，进而引发闪烁、部分内容无法滑出的 Bug。
3. **慎用全局 windowSoftInputMode 限制**：尽量只在需要的 Activity 级应用 `adjustResize`，不要在 Application 级粗暴地使用 `adjustNothing`，这会破坏所有的 Compose Insets 联动体系。
