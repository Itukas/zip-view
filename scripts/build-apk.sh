#!/usr/bin/env bash
# 自包含构建脚本：把 JDK17 / Android SDK / Gradle 全部装进项目内 .toolchain/，
# 仅在本脚本进程内设置环境变量，绝不修改全局配置或默认 Java 版本。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="$ROOT/.toolchain"
mkdir -p "$TC"

JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jdk/hotspot/normal/eclipse?project=jdk"
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-mac-14742923_latest.zip"
GRADLE_VER="8.9"
GRADLE_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VER}-bin.zip"

dl() { echo ">>> 下载 $2"; curl -fL --retry 3 --retry-delay 2 "$1" -o "$2"; }

# ---------- 1. JDK 17 ----------
echo ">>> STAGE 1/5: 准备 JDK 17"
if [ ! -x "$TC/jdk17/Contents/Home/bin/java" ]; then
  dl "$JDK_URL" "$TC/jdk17.tar.gz"
  rm -rf "$TC/jdk17"; mkdir -p "$TC/jdk17"
  tar -xzf "$TC/jdk17.tar.gz" -C "$TC/jdk17" --strip-components=1
  rm -f "$TC/jdk17.tar.gz"
fi
export JAVA_HOME="$TC/jdk17/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
echo ">>> JAVA_HOME=$JAVA_HOME"; java -version

# ---------- 2. Android SDK 命令行工具 ----------
echo ">>> STAGE 2/5: 准备 Android SDK 命令行工具"
SDK="$TC/android-sdk"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  dl "$CMDLINE_URL" "$TC/cmdline-tools.zip"
  rm -rf "$SDK/cmdline-tools"; mkdir -p "$SDK/cmdline-tools"
  unzip -q "$TC/cmdline-tools.zip" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -f "$TC/cmdline-tools.zip"
fi
SDKMGR="$SDK/cmdline-tools/latest/bin/sdkmanager"

# ---------- 3. SDK 组件 + 许可 ----------
echo ">>> STAGE 3/5: 安装 platform-tools / platforms;android-35 / build-tools;35.0.0"
yes | "$SDKMGR" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
"$SDKMGR" --sdk_root="$SDK" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# ---------- 4. local.properties + Gradle ----------
echo ">>> STAGE 4/5: 准备 Gradle ${GRADLE_VER}"
echo "sdk.dir=$SDK" > "$ROOT/local.properties"
if [ ! -x "$TC/gradle-${GRADLE_VER}/bin/gradle" ]; then
  dl "$GRADLE_URL" "$TC/gradle.zip"
  unzip -q "$TC/gradle.zip" -d "$TC"
  rm -f "$TC/gradle.zip"
fi
GRADLE="$TC/gradle-${GRADLE_VER}/bin/gradle"

# ---------- 5. 构建 ----------
echo ">>> STAGE 5/5: 构建 Debug APK"
cd "$ROOT"
"$GRADLE" :app:assembleDebug --no-daemon --console=plain "$@"

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo ">>> APK_READY: $APK"
  ls -lh "$APK"
else
  echo ">>> APK 未生成，请检查上方构建日志"
  exit 1
fi
