# 依赖管理与升级验证

核查与验证日期：2026-10-12。适用于 PetChat 当前工作区。升级分七批完成，每批结束后验证构建及模拟器安装启动。

## 最终版本

| 组件 | 升级前基线 | 最终版本 |
| --- | --- | --- |
| Gradle Wrapper | 8.14.4 | 9.8.1，官方 SHA-256 固定 |
| AGP | 8.9.1 | 9.4.1 |
| Kotlin / Compose compiler / serialization plugin | 2.0.21 | 2.4.21 |
| KSP | 2.0.21-1.0.27 | 2.3.12（KSP2） |
| Dagger / Hilt | 2.53.1 | 2.60.1 |
| Foojay settings plugin | 0.10.0 | 1.0.0 |
| Compose BOM | 2024.12.01 | 2026.09.00 |
| Compose UI / runtime / foundation | 实际 1.9.5 | 实际 1.12.1 |
| Material 3 | 1.3.1 | 1.4.0，由 Compose BOM 管理 |
| Core | 实际 1.16.0 | 1.19.1 |
| Activity Compose | 实际 1.12.0 | 1.13.0 |
| Lifecycle | 实际 2.10.0 | 2.11.0 |
| Navigation 3 | 1.0.0 | 1.2.0 |
| AndroidX Hilt | 1.2.0 | 1.4.0 |
| Room runtime / compiler | 2.6.1 | 2.8.5 |
| WorkManager | 2.10.0 | 2.12.0 |
| DataStore | 实际 1.2.1 | 1.2.1，核查时已是最新稳定版 |
| Coroutines BOM | 实际 1.9.0 | 1.11.0 |
| Serialization BOM | 1.7.3 | 1.11.0 |
| OkHttp BOM | 4.9.3 | 5.5.0 |
| Retrofit BOM / converter | 2.11.0 | 3.0.0 |
| Mockito BOM | 5.11.0 | 5.24.0 |
| Mockito Kotlin | 5.2.1 | 6.4.0 |
| AndroidX JUnit / Espresso | 1.2.1 / 3.6.1 | 1.3.0 / 3.7.0 |
| JUnit 4 | 4.13.2 | 4.13.2，继续使用 JUnit 4 |

版本来自核查时的官方 Google Maven、Maven Central 和发布页面，是时间点快照。应用配置为 compileSdk 37.0、targetSdk 36、minSdk 31、JVM target 17；Gradle daemon 使用 JDK 21。

## 版本目录与 BOM 规则

`gradle/libs.versions.toml` 集中管理项目插件、库坐标和版本引用。库别名统一使用 `module` 坐标；同一发布族共用 `version.ref`。Kotlin 编译插件、KGP classpath 与 Kotlin BOM 使用同一版本；Hilt 插件、runtime、compiler 也共享版本。

已使用七组官方 BOM：Compose、Kotlin、Coroutines、Serialization、OkHttp、Retrofit、Mockito。BOM 内的库别名省略单库版本，在对应构建配置中通过 `platform(...)` 引入。Mockito BOM 仅用于单元测试配置，不能管理 `org.mockito.kotlin:mockito-kotlin`，该库保留独立版本。

Lifecycle、Activity、Core、Navigation 3、DataStore、Room、WorkManager 和 AndroidX Hilt 使用各自发布族的共享版本引用。Compose compiler 随 Kotlin 版本管理，不属于 Compose BOM。Kotlin BOM 也不管理 Kotlinx 库。BOM 与版本目录提供约束和声明，不是依赖锁文件；需要核对实际解析版本，不能把目录数字直接当作运行版本。

Foojay 是 settings 插件，Gradle 不支持在 settings 插件块使用版本目录插件别名，因此该版本保留在 `settings.gradle.kts` 并注明原因。Gradle 自身版本由 Wrapper 管理，`distributionSha256Sum` 使用 [9.8.1 官方校验值](https://services.gradle.org/distributions/gradle-9.8.1-bin.zip.sha256)。Wrapper 脚本和 JAR 已用对应版本重新生成。

相关规则见 [Gradle 版本目录](https://docs.gradle.org/current/userguide/version_catalogs.html)、[Gradle BOM 平台](https://docs.gradle.org/current/userguide/platforms.html)、[Android 版本目录指南](https://developer.android.com/build/migrate-to-catalogs) 和 [Compose BOM 指南](https://developer.android.com/develop/ui/compose/bom)。

## 必要的兼容迁移

- 采用 AGP 9 的内置 Kotlin，移除 `org.jetbrains.kotlin.android` 插件和旧 `android.kotlinOptions` DSL，通过目录中的 KGP classpath 选择 Kotlin 2.4.21。[迁移说明](https://developer.android.com/build/migrate-to-built-in-kotlin)
- 新版 AndroidX 需要 compileSdk 37；使用新的 `compileSdk { version = release(37) { minorApiLevel = 0 } }` DSL。[CompileSdkSpec](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/dsl/CompileSdkSpec)
- 将 Room 升级并入 KSP2 迁移，解决旧 Room 编译器处理 suspend / Unit 返回值时的 `unexpected jvm signature V`。数据库 schema 仍为版本 8，安装采用 `adb install -r` 保留模拟器上的已有应用数据。[上游问题与修复](https://github.com/google/ksp/issues/2177)
- `hiltViewModel()` 迁移至 `hilt-lifecycle-viewmodel-compose` 及 `androidx.hilt.lifecycle.viewmodel.compose`，最终应用图中不再有旧 Navigation 2 模块。[AndroidX Hilt 发布说明](https://developer.android.com/jetpack/androidx/releases/hilt)
- 移除已经合并为空的 `room-ktx`、`lifecycle-runtime-ktx`、`lifecycle-viewmodel-ktx` 直接声明，使用实际提供 API 的 runtime / viewmodel 模块。[Room 发布说明](https://developer.android.com/jetpack/androidx/releases/room)、[Lifecycle 发布说明](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- 删除 Compose 重复声明和未使用的 Accompanist；`ui-tooling` 仅用于 Debug，源码预览注解需要的 `ui-tooling-preview` 保留。

## 分批验证

运行环境为已有 Pixel_10 模拟器，Android API 37。各批记录在忽略目录 `.gradle/dependency-upgrades/<stage>/`，包含构建日志、安装日志、启动日志、目标进程 crash buffer、APK SHA-256、目录版本快照及画面。

| 批次 | 升级范围 | Debug 构建 | Release R8 | 安装与运行 |
| --- | --- | --- | --- | --- |
| 01_gradle_8_14_6 | Gradle 安全补丁、AndroidX Test / Espresso 兼容性 | 通过 | 通过 | 通过 |
| 02_toolchain | Gradle 9.8.1、AGP、Kotlin、KSP、Hilt、Foojay、Room、compileSdk | 通过 | 通过 | 通过 |
| 03_kotlinx | Coroutines、Serialization | 通过 | 通过 | 通过 |
| 04_compose_androidx | Compose BOM、Core、Activity、Lifecycle、Navigation 3、AndroidX Hilt | 通过 | 通过 | 通过 |
| 05_work_storage | WorkManager、清理已合并的 Room / Lifecycle KTX 模块 | 通过 | 通过 | 冷启动及主界面显示通过 |
| 06_network | OkHttp、Retrofit 和 converter | 通过 | 通过 | 冷启动及主界面显示通过 |
| 07_test_catalog | Mockito BOM、Mockito Kotlin 版本声明 | 通过 | 通过 | 冷启动及主界面显示通过 |

用户在第一批结束后指示暂停测试执行。之后的批次没有运行 JVM 或仪器测试；现有测试文件保留，测试库版本声明已更新。

复用验证入口：

```powershell
.\tools\verify-dependency-upgrade.ps1 -Stage my_upgrade -DeviceId emulator-5554
```

它执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:minifyReleaseWithR8 --continue --console=plain
```

随后停止旧进程、覆盖安装 Debug APK，通过 `am start -W -S` 冷启动，确认 MainActivity 位于前台、进程存活、目标 crash buffer 无 fatal 异常，并等待主界面出现在界面树中再截图。安装前停止应用用于避免 Android 的前台应用更新过渡 Activity 干扰启动判断。

当前机器用户级 Gradle 代理在 Google Maven 请求上出现间歇性 TLS 握手失败。后续下载使用仅限本次命令的代理覆盖参数，仓库配置保持官方 Google Maven / Maven Central，未关闭 TLS 证书校验：

```powershell
.\tools\verify-dependency-upgrade.ps1 -Stage my_upgrade -DeviceId emulator-5554 -GradleArguments @('-Dhttps.proxyHost=', '-Dhttp.proxyHost=')
```

已有完整缓存时也可以传入 `-GradleArguments '--offline'`。验证入口不修改用户级代理配置。

## 最终依赖图与验证边界

已核对 Debug / Release runtime 和两组 KSP processor classpath。主要解析版本与目录一致，Compose UI / runtime / foundation 为 1.12.1，Material 3 为 1.4.0。Release 图不含 `ui-tooling`；KSP 图中的 Room、AndroidX Hilt、Dagger Hilt compiler 分别为 2.8.5、1.4.0、2.60.1。

本地构建和模拟器运行证明的是当前组合的工程可用性。Kotlin 官方兼容表目前列出的 2.4.21 覆盖上界为 Gradle 9.7.0 / AGP 9.3.1，本项目采用更高的稳定版本并完成了上述本地验证，不能将其称为官方认证的全部兼容组合。[Kotlin 兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)

构建仍提示 Room 旧迁移配置 API 的弃用、原生库无法剥离及 Gradle 10 的弃用事项，没有阻止本次编译。没有执行 Android Studio Sync、真机验收、真实聊天 API 请求、完整功能回归或签名 Release APK 运行；Release 验证范围为 R8 编译，实际安装启动的是 Debug APK。
