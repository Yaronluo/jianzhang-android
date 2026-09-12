# 简账

简账是一款面向大学生和年轻用户的本地优先个人财务管理 Android App。

## 当前能力

- 中文 Material 3 界面和底部导航：账本、固支、记账、资产、统计。
- Room 本地数据库；金额统一使用 Long 保存分，避免 Float 精度问题。
- 账单新增、编辑、删除，以及账单与账户余额的原子联动。
- 一句话记账：使用正则、关键词和日期规则离线识别金额、收支类型、分类和日期。
- 固定支出到期确认、防重复记账和下一个周期日期推进。
- 月度总预算与分类预算、分类消费排行、趋势柱状图、消费预测和模板化财务洞察。
- 消费日历、异常消费提醒、账户转账、余额校正、高级筛选、主题切换和 CSV 导出。
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
- `app/src/main/java/com/jianzhang/app/ui`：ViewModel、导航、主题和 Compose 页面。
