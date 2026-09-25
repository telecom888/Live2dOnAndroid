# Release 签名配置

项目优先从 Gradle 属性读取以下值；本地没有相应 Gradle 属性时，会读取仓库根目录的 `release-signing.properties`：

```properties
BANGDREAM_RELEASE_STORE_FILE=/absolute/path/to/release.jks
BANGDREAM_RELEASE_STORE_PASSWORD=...
BANGDREAM_RELEASE_KEY_ALIAS=...
BANGDREAM_RELEASE_KEY_PASSWORD=...
```

`release-signing.properties` 已加入 `.gitignore`，请勿提交签名文件或密码。`BANGDREAM_RELEASE_STORE_FILE` 可以是绝对路径，也可以是相对仓库根目录的路径。本机配置使用 E 盘已有的 JKS；其他开发设备需自行提供具有相同证书的密钥文件，才能构建可用于升级既有安装的签名包。

构建：`./gradlew :app:assembleRelease`。成功后检查 `app/build/outputs/apk/release/app-release.apk`，并使用 Android SDK 的 `apksigner verify` 验证签名。若未提供全部四项签名配置，Gradle 会生成未签名的 release APK。
