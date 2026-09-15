# 简账·慧录

简账·慧录是一款支持本地识别与用户自定义 AI 的票据记账助手。用户可以拍摄购物小票，或主动导入发票、支付账单截图；系统提取关键信息并要求用户核对后再写入账本。

## 当前能力

- 中文 Material 3 界面和底部导航：账本、固支、记账、资产、统计。
- Room 本地数据库；金额统一使用 Long 保存分，避免 Float 精度问题。
- 账单新增、编辑、删除，以及账单与账户余额的原子联动。
- 一句话记账：使用正则、关键词和日期规则离线识别金额、收支类型、分类和日期。
- 简易记账：首页大入口支持拍摄小票或从相册导入图片，流程为“拍照/导入—核对—完成”。
- 本地票据 OCR：使用随 APK 提供的 ML Kit 中文文字识别模型，首次演示不等待临时模型下载。
- 自定义 AI 识票：独立服务管理页可保存多个千问、豆包、智谱、腾讯混元、DeepSeek 或其他 OpenAI 兼容配置，一键切换时无需重新填写；API 地址、模型和密钥均由用户提供，应用不内置开发者密钥或默认云端服务。
- 三种识别方式：视觉模型直接分析图片、本地 OCR 后由 AI 分析文字、完全本地识别；云端失败时自动回退到本地 OCR。
- 密钥保护：API Key 由 Android Keystore 加密后保存在本机，不进入 Room 账本、Git、日志或 CSV 导出。
- 票据解析：优先从“实付、付款金额、订单金额、应付、合计”等关键词附近提取金额，并推断日期、时间、商户和消费分类；商户和导入来源作为可选账单元数据保存在现有 Room 数据库。
- 人工确认：金额、日期、时间、商户、分类、账户和备注均可修改；低置信或缺失金额必须先核对或填写，不能直接入账。
- 隐私边界：不读取微信、支付宝账户。默认本地识别不上传票据；只有用户主动启用自定义 AI 后，所选图片或 OCR 文字才会发送到用户配置的服务商，界面会按当前模式明确提示。
- 固定支出到期确认、防重复记账和下一个周期日期推进。
- 月度总预算与分类预算，以及周、月、年、全部历史的收支概览、分类分布、单笔排行、收支趋势和资产趋势。
- 账户转账、余额校正、高级筛选、主题切换和 CSV 导出。
- 首次启动引导和可重复生成/清除的演示数据；清除演示数据会回滚演示交易的余额影响。

## 构建

项目包含 Gradle Wrapper。Android SDK 需要安装 API 35 和 Build Tools 35.0.0，JDK 需要 17。

Windows 中文路径需要开启 `android.overridePathCheck=true`；若 `aapt2` 仍无法处理中文路径，可像开发环境一样用 `subst` 将项目和 SDK 映射到 ASCII 盘符后执行：

```powershell
subst J: C:\简账
subst K: C:\简账\.tools
$env:JAVA_HOME = 'K:\jdk'
$env:ANDROID_HOME = 'K:\android-sdk'
J:\gradlew.bat :app:assembleDebug
```

Debug APK 位于：`app/build/outputs/apk/debug/app-debug.apk`。

完整验收建议分开执行（可避免 Android Lint 与 Kotlin 测试分析器并发时的偶发工具链异常）：

```powershell
J:\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
J:\gradlew.bat :app:lintDebug --no-daemon --console=plain
J:\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --no-daemon --console=plain
```

Release 产物位于 `app/build/outputs/apk/release/app-release-unsigned.apk`。项目没有内置参赛方签名密钥，正式发布前需要自行签名。

真机 Instrumentation 安装测试 APK 时，需要保留测试安装参数：

```powershell
adb -s 10.0.40.110:40461 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 10.0.40.110:40461 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

## 代码入口

- `app/src/main/java/com/jianzhang/app/data/local`：Room 实体、DAO 和数据库。
- `app/src/main/java/com/jianzhang/app/data/repository/FinanceRepository.kt`：账单与账户余额一致性事务。
- `app/src/main/java/com/jianzhang/app/domain/parser/NaturalLanguageParser.kt`：离线一句话记账解析器。
- `app/src/main/java/com/jianzhang/app/domain/ocr/LocalReceiptOcr.kt`：捆绑式中文 OCR 调用。
- `app/src/main/java/com/jianzhang/app/domain/ai`：AI 服务商预设、OpenAI 兼容请求和统一票据结构化协议。
- `app/src/main/java/com/jianzhang/app/data/preferences/AiConfigStore.kt`：多套 AI 服务配置持久化，每个 API Key 均由 Keystore 密钥加密保存。
- `app/src/main/java/com/jianzhang/app/ui/screens/AiSettingsScreen.kt`：AI 服务新增、编辑、删除、切换、连接诊断和识别模式管理。
- `app/src/main/java/com/jianzhang/app/domain/parser/ReceiptParser.kt`：票据字段解析与置信提示。
- `app/src/main/java/com/jianzhang/app/ui/screens/ReceiptEntryScreen.kt`：拍照/相册、识别和确认流程。
- `app/src/main/java/com/jianzhang/app/ui`：ViewModel、导航、主题和 Compose 页面。
