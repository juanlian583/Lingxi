#!/usr/bin/env bash
# 灵汐 (Lingxi) 手动构建脚本
# 用法: ./build-tools/build.sh
# 可用环境变量覆盖: AAPT2 / ANDROID_JAR / D8 / APKSIGNER / KEYSTORE / KEYSTORE_PASS
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/root/android-sdk}}"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="${ANDROID_JAR:-$SDK/platforms/android-34/android.jar}"
D8="${D8:-$BT/d8}"
APKSIGNER="${APKSIGNER:-$BT/apksigner}"

if [ -z "${AAPT2:-}" ]; then
    for cand in /root/termux-pkgs/aapt2/data/data/com.termux/files/usr/bin/aapt2 "$BT/aapt2"; do
        if [ -x "$cand" ]; then AAPT2="$cand"; break; fi
    done
fi
: "${AAPT2:?aapt2 未找到，请通过 AAPT2 环境变量指定}"

# termux 版 aapt2 依赖其动态库 —— 只对 aapt2 注入，避免污染 javac 等工具
AAPT2_LD=""
if [[ "$AAPT2" == *termux* ]]; then
    AAPT2_LD="/root/termux-libs/x/data/data/com.termux/files/usr/lib:/tmp/fmtdir/data/data/com.termux/files/usr/lib:/data/data/com.termux/files/usr/lib"
fi
run_aapt2() {
    if [ -n "$AAPT2_LD" ]; then
        LD_LIBRARY_PATH="$AAPT2_LD${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" "$AAPT2" "$@"
    else
        "$AAPT2" "$@"
    fi
}

echo "==> aapt2: $AAPT2"
run_aapt2 version

echo "[1/8] 准备构建目录"
rm -rf build && mkdir -p build/gen build/classes build/dex
cp app/src/main/AndroidManifest.xml build/AndroidManifest.xml
# aapt2 需要 manifest 上的 package 属性（Gradle 8 用 namespace，因此单独注入）
sed -i '0,/<manifest /s//<manifest package="com.lingxi.pet" /' build/AndroidManifest.xml

echo "[2/8] aapt2 compile 资源"
run_aapt2 compile --dir app/src/main/res -o build/res.zip

echo "[3/8] aapt2 link"
run_aapt2 link -o build/unsigned.apk \
    --manifest build/AndroidManifest.xml \
    -I "$ANDROID_JAR" \
    --java build/gen \
    -A app/src/main/assets \
    --min-sdk-version 26 \
    --target-sdk-version 34 \
    build/res.zip

echo "[4/8] javac 编译 Java 源码"
find app/src/main/java -name '*.java' > build/sources.txt
javac --release 8 -encoding UTF-8 -nowarn \
    -classpath "$ANDROID_JAR" \
    -d build/classes \
    build/gen/com/lingxi/pet/R.java \
    @build/sources.txt

echo "[5/8] d8 打包 dex"
find build/classes -name '*.class' > build/classes.txt
"$D8" --release --min-api 26 --lib "$ANDROID_JAR" --output build/dex @build/classes.txt

echo "[6/8] 保真重打包 + 注入 classes.dex（保留对齐）"
python3 build-tools/repackage.py build/unsigned.apk build/dex/classes.dex build/withdex.apk

echo "[7/8] 签名（v1 + v2 + v3 全启用，兼容所有 Android 版本）"
KEYSTORE="${KEYSTORE:-keystore/lingxi.jks}"
KEYSTORE_PASS="${KEYSTORE_PASS:-lingxi123}"
mkdir -p keystore dist
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -alias lingxi -keyalg RSA -keysize 2048 \
        -validity 10000 -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
        -dname "CN=Lingxi Pet, OU=Desktop Pet, O=Lingxi, L=China, C=CN" 2>/dev/null
fi
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias lingxi \
    --ks-pass "pass:$KEYSTORE_PASS" --key-pass "pass:$KEYSTORE_PASS" \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out dist/lingxi-v1.6.4.apk build/withdex.apk

echo "[8/8] 验证"
"$APKSIGNER" verify --verbose dist/lingxi-v1.6.4.apk
echo "---- badging ----"
run_aapt2 dump badging dist/lingxi-v1.6.4.apk | grep -E "^(package|application-label|sdkVersion|targetSdkVersion|uses-permission)" | head -12
echo "---- APK 内容 ----"
python3 - <<'EOF'
import zipfile
z = zipfile.ZipFile("dist/lingxi-v1.6.4.apk")
for n in z.namelist():
    print("  ", n)
print("APK size:", __import__('os').path.getsize("dist/lingxi-v1.6.4.apk"), "bytes")
EOF
echo "✅ 构建完成: dist/lingxi-v1.6.4.apk"
