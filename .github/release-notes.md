Android 8.0+，仅 arm64-v8a。

- 手动检查更新时仅显示「下载」和「稍后」。「跳过此版」保留在自动检查的弹窗里，已跳过的版本仍可手动查到。
- 点击「下载」时，使用 Android 设置的默认浏览器打开 APK 直链。

APK 由 GitHub Actions 编译和签名，沿用既有签名证书。build-provenance.json 记录构建来源。

85 项本地单元测试通过。
