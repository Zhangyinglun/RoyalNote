# 实体手机安全部署

使用以下脚本把当前 debug APK 安全地原位升级到已授权的 OPPO CPH2655：

```text
bash scripts/safe-install-cph2655.sh --serial 192.168.68.51:36459
```

USB 连接时可使用：

```text
bash scripts/safe-install-cph2655.sh --serial d0caddca
```

脚本会依次完成设备身份校验、APK 构建、应用数据离机备份、SQLite 完整性与记录数检查、新旧 APK 签名比对、`adb install -r`、安装后数据核对、首次启动以及启动后的迁移与历史记录复核。真正安装前需要输入 `INSTALL`。

备份默认保存在 `.device-backups/<时间>-d0caddca/`，该目录已加入 Git 忽略。备份含数据库、WAL/SHM、设置及反思记忆，可能包含私人内容和 API 密钥，请勿上传或分享。

若无线调试地址变化，先在手机上完成 ADB 配对，再把“无线调试”主页当前显示的 IP 和端口传给 `--serial`。脚本不会运行仪器测试，也不会卸载、清数据、降级或自动恢复数据。
