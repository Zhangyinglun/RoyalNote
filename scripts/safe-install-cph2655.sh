#!/usr/bin/env bash

set -euo pipefail

readonly PACKAGE="com.example.royalnote"
readonly ACTIVITY="com.example.royalnote/.MainActivity"
readonly EXPECTED_MODEL="CPH2655"
readonly EXPECTED_HARDWARE_SERIAL="d0caddca"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SERIAL=""
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
BACKUP_ROOT="$ROOT_DIR/.device-backups"
BUILD_APK=1
LAUNCH_APP=1

usage() {
    cat <<'EOF'
安全地把 RoyalNote 原位升级到已授权的 OPPO CPH2655。

用法：
  bash scripts/safe-install-cph2655.sh --serial <adb-serial> [选项]

必需参数：
  --serial <serial>       明确的 ADB 序列号，例如 d0caddca 或 192.168.68.51:36459

可选参数：
  --apk <path>            使用已有 APK，并跳过 Gradle 构建
  --backup-root <path>    备份根目录；默认 .device-backups
  --no-launch             安装并核对后不启动应用
  -h, --help              显示帮助

安全约束：
  - 只接受型号 CPH2655、硬件序列号 d0caddca，拒绝 emulator-*。
  - 只使用 adb install -r；不卸载、不清数据、不降级、不运行设备测试。
  - 安装前必须完成离机备份、SQLite 完整性检查、记录统计和签名比对。
  - 安装后必须再次核对数据库内容、记录数、设置和反思记忆。
  - 真正安装前必须手动输入 INSTALL 确认。
EOF
}

fail() {
    printf '错误：%s\n' "$*" >&2
    exit 1
}

info() {
    printf '\n==> %s\n' "$*"
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "缺少命令：$1"
}

sha256_file() {
    shasum -a 256 "$1" | awk '{print $1}'
}

directory_manifest() {
    local directory="$1"
    if [[ ! -d "$directory" ]]; then
        printf 'ABSENT\n'
        return
    fi

    local found=0
    while IFS= read -r file; do
        found=1
        printf '%s  %s\n' "$(sha256_file "$file")" "${file#"$directory"/}"
    done < <(find "$directory" -type f | LC_ALL=C sort)

    if [[ "$found" -eq 0 ]]; then
        printf 'EMPTY\n'
    fi
}

database_fingerprint() {
    local database="$1"
    sqlite3 "$database" '.dump' | shasum -a 256 | awk '{print $1}'
}

records_fingerprint() {
    local database="$1"
    sqlite3 "$database" "
        SELECT
            id || '|' ||
            hex(eventText) || '|' ||
            COALESCE(hex(moodTag), 'NULL') || '|' ||
            COALESCE(hex(moodNote), 'NULL') || '|' ||
            startedAt || '|' || endedAt || '|' ||
            hex(eventDate) || '|' || hex(zoneId) || '|' || hex(source) || '|' ||
            COALESCE(hex(importBatchId), 'NULL') || '|' ||
            COALESCE(importOrdinal, 'NULL') || '|' ||
            createdAt || '|' || updatedAt
        FROM note_records
        ORDER BY id;
    " | shasum -a 256 | awk '{print $1}'
}

read_database_state() {
    local database="$1"
    local output
    output="$(sqlite3 "$database" 'PRAGMA integrity_check; SELECT count(*) FROM note_records; PRAGMA user_version;')"
    DB_INTEGRITY="$(printf '%s\n' "$output" | sed -n '1p')"
    DB_RECORD_COUNT="$(printf '%s\n' "$output" | sed -n '2p')"
    DB_SCHEMA_VERSION="$(printf '%s\n' "$output" | sed -n '3p')"
    DB_FINGERPRINT="$(database_fingerprint "$database")"
    DB_RECORDS_FINGERPRINT="$(records_fingerprint "$database")"
}

certificate_digest() {
    JAVA_HOME="$JAVA_HOME" "$APKSIGNER" verify --print-certs "$1" \
        | awk -F': ' '/SHA-256 digest/ { print $2; exit }'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial)
            [[ $# -ge 2 ]] || fail "--serial 缺少值"
            SERIAL="$2"
            shift 2
            ;;
        --apk)
            [[ $# -ge 2 ]] || fail "--apk 缺少值"
            APK_PATH="$2"
            BUILD_APK=0
            shift 2
            ;;
        --backup-root)
            [[ $# -ge 2 ]] || fail "--backup-root 缺少值"
            BACKUP_ROOT="$2"
            shift 2
            ;;
        --no-launch)
            LAUNCH_APP=0
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "未知参数：$1"
            ;;
    esac
done

[[ -n "$SERIAL" ]] || { usage; fail "必须显式提供 --serial"; }
[[ "$SERIAL" != emulator-* ]] || fail "拒绝在模拟器上执行实体手机部署脚本"

if [[ "$APK_PATH" != /* ]]; then
    APK_PATH="$ROOT_DIR/$APK_PATH"
fi
if [[ "$BACKUP_ROOT" != /* ]]; then
    BACKUP_ROOT="$ROOT_DIR/$BACKUP_ROOT"
fi

umask 077

ANDROID_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/Users/yinglun/Library/Android/sdk}}"
ADB="$ANDROID_SDK/platform-tools/adb"
[[ -x "$ADB" ]] || fail "找不到 ADB：$ADB"

if [[ -z "${JAVA_HOME:-}" ]]; then
    DEFAULT_JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    [[ -d "$DEFAULT_JAVA_HOME" ]] || fail "未设置 JAVA_HOME，且找不到 Android Studio JBR"
    export JAVA_HOME="$DEFAULT_JAVA_HOME"
fi

require_command sqlite3
require_command shasum
require_command tar
require_command find

if ! "$ADB" -s "$SERIAL" get-state >/dev/null 2>&1; then
    if [[ "$SERIAL" == *:* ]]; then
        info "连接无线 ADB：$SERIAL"
        "$ADB" connect "$SERIAL" >/dev/null
    fi
fi
[[ "$("$ADB" -s "$SERIAL" get-state 2>/dev/null || true)" == "device" ]] \
    || fail "设备未连接或未授权：$SERIAL"

MODEL="$("$ADB" -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
HARDWARE_SERIAL="$("$ADB" -s "$SERIAL" shell getprop ro.serialno | tr -d '\r')"
[[ "$MODEL" == "$EXPECTED_MODEL" ]] \
    || fail "设备型号不符：期望 $EXPECTED_MODEL，实际 $MODEL"
[[ "$HARDWARE_SERIAL" == "$EXPECTED_HARDWARE_SERIAL" ]] \
    || fail "硬件序列号不符：期望 $EXPECTED_HARDWARE_SERIAL，实际 $HARDWARE_SERIAL"

info "已确认设备：$MODEL / $HARDWARE_SERIAL / ADB $SERIAL"

INSTALLED_APK_LINE="$("$ADB" -s "$SERIAL" shell pm path "$PACKAGE" | tr -d '\r')"
[[ "$INSTALLED_APK_LINE" == package:* ]] || fail "手机上未安装 $PACKAGE，拒绝首次覆盖式安装"
INSTALLED_APK_PATH="${INSTALLED_APK_LINE#package:}"
"$ADB" -s "$SERIAL" shell run-as "$PACKAGE" id >/dev/null \
    || fail "run-as 不可用，无法创建可恢复备份；停止安装"

if [[ "$BUILD_APK" -eq 1 ]]; then
    info "构建 debug APK"
    (
        cd "$ROOT_DIR"
        ANDROID_HOME="$ANDROID_SDK" ANDROID_SDK_ROOT="$ANDROID_SDK" bash ./gradlew assembleDebug
    )
fi

[[ -s "$APK_PATH" ]] || fail "APK 不存在或为空：$APK_PATH"
APK_PATH="$(cd "$(dirname "$APK_PATH")" && pwd)/$(basename "$APK_PATH")"

APKSIGNER="$(find "$ANDROID_SDK/build-tools" -type f -name apksigner | LC_ALL=C sort | tail -1)"
[[ -n "$APKSIGNER" && -x "$APKSIGNER" ]] || fail "找不到 apksigner"
AAPT="$(find "$ANDROID_SDK/build-tools" -type f -name aapt | LC_ALL=C sort | tail -1)"
[[ -n "$AAPT" && -x "$AAPT" ]] || fail "找不到 aapt"
NEW_PACKAGE="$("$AAPT" dump badging "$APK_PATH" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
[[ "$NEW_PACKAGE" == "$PACKAGE" ]] \
    || fail "APK applicationId 不符：期望 $PACKAGE，实际 ${NEW_PACKAGE:-无法读取}"

TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
BACKUP_DIR="$BACKUP_ROOT/$TIMESTAMP-$HARDWARE_SERIAL"
PRE_DIR="$BACKUP_DIR/pre-install"
POST_DIR="$BACKUP_DIR/post-install"
FINAL_DIR="$BACKUP_DIR/post-launch"
mkdir -p "$PRE_DIR" "$POST_DIR" "$FINAL_DIR"

on_error() {
    local exit_code=$?
    printf '\n部署未完成。不会自动恢复或重试。\n备份目录：%s\n' "$BACKUP_DIR" >&2
    exit "$exit_code"
}
trap on_error ERR

info "停止应用并创建安装前离机备份"
"$ADB" -s "$SERIAL" shell am force-stop "$PACKAGE"

tar_paths=(databases)
"$ADB" -s "$SERIAL" shell run-as "$PACKAGE" test -f databases/royal_note.db \
    || fail "数据库 databases/royal_note.db 不存在"
for optional_path in shared_prefs files; do
    if "$ADB" -s "$SERIAL" shell run-as "$PACKAGE" test -d "$optional_path"; then
        tar_paths+=("$optional_path")
    fi
done

"$ADB" -s "$SERIAL" exec-out run-as "$PACKAGE" tar -cf - "${tar_paths[@]}" \
    > "$BACKUP_DIR/pre-install-data.tar"
[[ -s "$BACKUP_DIR/pre-install-data.tar" ]] || fail "安装前备份为空"
tar -xf "$BACKUP_DIR/pre-install-data.tar" -C "$PRE_DIR"
[[ -s "$PRE_DIR/databases/royal_note.db" ]] || fail "备份中的主数据库缺失或为空"

read_database_state "$PRE_DIR/databases/royal_note.db"
PRE_INTEGRITY="$DB_INTEGRITY"
PRE_RECORD_COUNT="$DB_RECORD_COUNT"
PRE_SCHEMA_VERSION="$DB_SCHEMA_VERSION"
PRE_DB_FINGERPRINT="$DB_FINGERPRINT"
PRE_RECORDS_FINGERPRINT="$DB_RECORDS_FINGERPRINT"
PRE_PREFS_MANIFEST="$(directory_manifest "$PRE_DIR/shared_prefs")"
PRE_MEMORY_MANIFEST="$(directory_manifest "$PRE_DIR/files/reflection")"
PRE_TAR_SHA="$(sha256_file "$BACKUP_DIR/pre-install-data.tar")"

[[ "$PRE_INTEGRITY" == "ok" ]] || fail "安装前数据库完整性检查失败：$PRE_INTEGRITY"
[[ "$PRE_RECORD_COUNT" =~ ^[0-9]+$ ]] || fail "无法读取安装前记录数"
[[ "$PRE_SCHEMA_VERSION" =~ ^[0-9]+$ ]] || fail "无法读取数据库 schema 版本"

info "提取现有 APK 并验证签名"
"$ADB" -s "$SERIAL" pull "$INSTALLED_APK_PATH" "$BACKUP_DIR/installed-base.apk" >/dev/null
[[ -s "$BACKUP_DIR/installed-base.apk" ]] || fail "无法保存手机现有 APK"
INSTALLED_CERT="$(certificate_digest "$BACKUP_DIR/installed-base.apk")"
NEW_CERT="$(certificate_digest "$APK_PATH")"
[[ -n "$INSTALLED_CERT" && -n "$NEW_CERT" ]] || fail "无法读取 APK 签名证书"
[[ "$INSTALLED_CERT" == "$NEW_CERT" ]] \
    || fail "签名不兼容；拒绝安装。现有：$INSTALLED_CERT，新 APK：$NEW_CERT"

cat > "$BACKUP_DIR/metadata.txt" <<EOF
timestamp=$TIMESTAMP
adb_serial=$SERIAL
hardware_serial=$HARDWARE_SERIAL
model=$MODEL
package=$PACKAGE
apk=$APK_PATH
certificate_sha256=$NEW_CERT
pre_database_integrity=$PRE_INTEGRITY
pre_record_count=$PRE_RECORD_COUNT
pre_schema_version=$PRE_SCHEMA_VERSION
pre_database_fingerprint=$PRE_DB_FINGERPRINT
pre_records_fingerprint=$PRE_RECORDS_FINGERPRINT
pre_data_tar_sha256=$PRE_TAR_SHA
memory_state=$(if [[ "$PRE_MEMORY_MANIFEST" == "ABSENT" ]]; then printf 'absent'; else printf 'present'; fi)
EOF

printf '\n安全检查通过：\n'
printf '  设备：%s (%s)\n' "$MODEL" "$HARDWARE_SERIAL"
printf '  安装前记录数：%s\n' "$PRE_RECORD_COUNT"
printf '  数据库 schema：%s\n' "$PRE_SCHEMA_VERSION"
printf '  数据库完整性：%s\n' "$PRE_INTEGRITY"
printf '  备份：%s\n' "$BACKUP_DIR"
printf '  签名 SHA-256：%s\n' "$NEW_CERT"
printf '\n将只执行 adb install -r，不卸载、不清数据。请输入 INSTALL 继续：'
read -r confirmation
[[ "$confirmation" == "INSTALL" ]] || {
    printf '已取消安装；备份仍保留在 %s\n' "$BACKUP_DIR"
    trap - ERR
    exit 0
}

info "执行保留数据的原位升级"
INSTALL_OUTPUT="$("$ADB" -s "$SERIAL" install -r "$APK_PATH")"
printf '%s\n' "$INSTALL_OUTPUT"
printf '%s\n' "$INSTALL_OUTPUT" | grep -q '^Success$' \
    || fail "adb install -r 未返回 Success"

"$ADB" -s "$SERIAL" shell pm path "$PACKAGE" >/dev/null \
    || fail "安装后应用包不存在"
"$ADB" -s "$SERIAL" shell run-as "$PACKAGE" id >/dev/null \
    || fail "安装后 run-as 不可用，无法核对数据"

info "创建安装后快照并核对历史数据"
"$ADB" -s "$SERIAL" exec-out run-as "$PACKAGE" tar -cf - "${tar_paths[@]}" \
    > "$BACKUP_DIR/post-install-data.tar"
[[ -s "$BACKUP_DIR/post-install-data.tar" ]] || fail "安装后快照为空"
tar -xf "$BACKUP_DIR/post-install-data.tar" -C "$POST_DIR"
[[ -s "$POST_DIR/databases/royal_note.db" ]] || fail "安装后主数据库缺失或为空"

read_database_state "$POST_DIR/databases/royal_note.db"
POST_INTEGRITY="$DB_INTEGRITY"
POST_RECORD_COUNT="$DB_RECORD_COUNT"
POST_SCHEMA_VERSION="$DB_SCHEMA_VERSION"
POST_DB_FINGERPRINT="$DB_FINGERPRINT"
POST_RECORDS_FINGERPRINT="$DB_RECORDS_FINGERPRINT"
POST_PREFS_MANIFEST="$(directory_manifest "$POST_DIR/shared_prefs")"
POST_MEMORY_MANIFEST="$(directory_manifest "$POST_DIR/files/reflection")"
POST_TAR_SHA="$(sha256_file "$BACKUP_DIR/post-install-data.tar")"

[[ "$POST_INTEGRITY" == "ok" ]] || fail "安装后数据库完整性检查失败：$POST_INTEGRITY"
[[ "$POST_RECORD_COUNT" == "$PRE_RECORD_COUNT" ]] \
    || fail "记录数发生变化：安装前 $PRE_RECORD_COUNT，安装后 $POST_RECORD_COUNT"
[[ "$POST_SCHEMA_VERSION" == "$PRE_SCHEMA_VERSION" ]] \
    || fail "数据库 schema 意外变化：安装前 $PRE_SCHEMA_VERSION，安装后 $POST_SCHEMA_VERSION"
[[ "$POST_DB_FINGERPRINT" == "$PRE_DB_FINGERPRINT" ]] \
    || fail "数据库内容发生变化；停止启动并保留备份供恢复"
[[ "$POST_RECORDS_FINGERPRINT" == "$PRE_RECORDS_FINGERPRINT" ]] \
    || fail "历史记录内容发生变化；停止启动并保留备份供恢复"
[[ "$POST_PREFS_MANIFEST" == "$PRE_PREFS_MANIFEST" ]] \
    || fail "设置文件发生变化；停止启动并保留备份供恢复"
[[ "$POST_MEMORY_MANIFEST" == "$PRE_MEMORY_MANIFEST" ]] \
    || fail "反思记忆发生变化；停止启动并保留备份供恢复"

cat >> "$BACKUP_DIR/metadata.txt" <<EOF
post_database_integrity=$POST_INTEGRITY
post_record_count=$POST_RECORD_COUNT
post_schema_version=$POST_SCHEMA_VERSION
post_database_fingerprint=$POST_DB_FINGERPRINT
post_records_fingerprint=$POST_RECORDS_FINGERPRINT
post_data_tar_sha256=$POST_TAR_SHA
EOF

if [[ "$LAUNCH_APP" -eq 1 ]]; then
    info "核对通过，启动应用"
    "$ADB" -s "$SERIAL" shell am start -W -S -n "$ACTIVITY"

    info "停止应用并核对首次启动后的数据库与迁移结果"
    "$ADB" -s "$SERIAL" shell am force-stop "$PACKAGE"
    "$ADB" -s "$SERIAL" exec-out run-as "$PACKAGE" tar -cf - "${tar_paths[@]}" \
        > "$BACKUP_DIR/post-launch-data.tar"
    [[ -s "$BACKUP_DIR/post-launch-data.tar" ]] || fail "首次启动后的快照为空"
    tar -xf "$BACKUP_DIR/post-launch-data.tar" -C "$FINAL_DIR"
    [[ -s "$FINAL_DIR/databases/royal_note.db" ]] || fail "首次启动后主数据库缺失或为空"

    read_database_state "$FINAL_DIR/databases/royal_note.db"
    FINAL_INTEGRITY="$DB_INTEGRITY"
    FINAL_RECORD_COUNT="$DB_RECORD_COUNT"
    FINAL_SCHEMA_VERSION="$DB_SCHEMA_VERSION"
    FINAL_RECORDS_FINGERPRINT="$DB_RECORDS_FINGERPRINT"
    FINAL_PREFS_MANIFEST="$(directory_manifest "$FINAL_DIR/shared_prefs")"
    FINAL_MEMORY_MANIFEST="$(directory_manifest "$FINAL_DIR/files/reflection")"
    FINAL_TAR_SHA="$(sha256_file "$BACKUP_DIR/post-launch-data.tar")"

    [[ "$FINAL_INTEGRITY" == "ok" ]] \
        || fail "首次启动后数据库完整性检查失败：$FINAL_INTEGRITY"
    [[ "$FINAL_RECORD_COUNT" == "$PRE_RECORD_COUNT" ]] \
        || fail "首次启动后记录数变化：安装前 $PRE_RECORD_COUNT，启动后 $FINAL_RECORD_COUNT"
    [[ "$FINAL_RECORDS_FINGERPRINT" == "$PRE_RECORDS_FINGERPRINT" ]] \
        || fail "首次启动后历史记录内容变化；应用已停止，备份已保留"
    [[ "$FINAL_PREFS_MANIFEST" == "$PRE_PREFS_MANIFEST" ]] \
        || fail "首次启动后设置发生变化；应用已停止，备份已保留"
    [[ "$FINAL_MEMORY_MANIFEST" == "$PRE_MEMORY_MANIFEST" ]] \
        || fail "首次启动后反思记忆发生变化；应用已停止，备份已保留"

    cat >> "$BACKUP_DIR/metadata.txt" <<EOF
post_launch_database_integrity=$FINAL_INTEGRITY
post_launch_record_count=$FINAL_RECORD_COUNT
post_launch_schema_version=$FINAL_SCHEMA_VERSION
post_launch_records_fingerprint=$FINAL_RECORDS_FINGERPRINT
post_launch_data_tar_sha256=$FINAL_TAR_SHA
EOF

    info "首次启动后的数据核对通过，重新打开应用"
    "$ADB" -s "$SERIAL" shell am start -W -n "$ACTIVITY"
fi

trap - ERR
printf '\n部署完成。记录数保持为 %s，备份目录：%s\n' "$POST_RECORD_COUNT" "$BACKUP_DIR"
