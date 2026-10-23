# GitHub Actions 编译、签名和 Release

工作流：`.github/workflows/build-android.yml`。

## 手动发布

在 GitHub Actions 打开 **Build Android APK → Run workflow**，选择 `main`，勾选 `publish_release` 后运行。工作流读取 Gradle 的 `versionName`，将同次构建产出的 APK 签名并发布为 `v{versionName}`。

不勾选时只运行测试、Lint、编译并上传 Debug / 未签名 Release artifacts。推送 `v*` 标签也会编译、签名和发布，标签必须与 APK 版本一致。

发布新版本前，更新 `versionCode`、`versionName` 和 `.github/release-notes.md`。不会移动既有标签，也不会覆盖已公开的 Release；失败后未发布的同版本草稿可由相同提交重试。

## 签名 Secrets

仓库 Settings → Secrets and variables → Actions 中配置：

- `ANDROID_SIGNING_KEYSTORE_BASE64`：已有签名 keystore 的 Base64 编码。
- `BANGDREAM_RELEASE_STORE_PASSWORD`：keystore 密码。
- `BANGDREAM_RELEASE_KEY_ALIAS`：签名别名。
- `BANGDREAM_RELEASE_KEY_PASSWORD`：私钥密码。

凭据不写入 Git，密码通过环境变量传给 apksigner。keystore 仅解密到签名任务的临时目录，任务结束前清理。签名任务与源码编译任务分开，源码编译不接收签名 Secrets。

发布证书 SHA256 固定为现有证书的 `a133a52626d649efaf1c49f830279ab1f07ab1caa2a972ed858cc7a0a1ec06a3`，签名后不匹配则拒绝发布。

## 附件与校验

Release 包含签名 APK、`SHA256SUMS.txt` 和 `build-provenance.json`。签名后校验包名、版本、原生库、zipalign、证书和 APK 原始内容。附件先上传到草稿，GitHub 返回的大小和 SHA256 与本地文件一致后才公开发布。

编译/签名 artifacts 均可从对应 Actions 运行下载；Debug 和未签名包不作为正式 Release 附件。
