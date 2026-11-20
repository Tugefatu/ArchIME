#!/usr/bin/env bash
set -euo pipefail
ROOT="${GITHUB_WORKSPACE:?}"
TEMP="${RUNNER_TEMP:-/tmp}"
MAIN="$ROOT/android-v1/app/src/main"
LEGACY_ZIP="$ROOT/legacy/ArchIME-0_7_2-dev-source.zip"
OLD="$TEMP/archime-legacy-source"
TRIME="$TEMP/archime-trime"
DOUBLE_PINYIN="$TEMP/archime-double-pinyin"
TRIME_COMMIT="8ef9ff825c7e993934682f1fc6da0bccfc620360"

test -s "$LEGACY_ZIP"
rm -rf "$OLD" "$TRIME" "$DOUBLE_PINYIN"
mkdir -p "$OLD"
unzip -q "$LEGACY_ZIP" -d "$OLD"

echo "== Stage tested Rime Kotlin/JNI integration and dictionaries =="
mkdir -p "$MAIN/java/org/archime/ime/engine" "$MAIN/assets/rime"
cp "$OLD/java/org/archime/ime/engine/"*.kt "$MAIN/java/org/archime/ime/engine/"
cp "$OLD/java/org/archime/ime/ArchInputMethodService.kt" "$MAIN/java/org/archime/ime/ArchInputMethodService.kt"
sed -i 's/R\.drawable\.ic_arch_logo/R.drawable.ic_arch_mark/g' "$MAIN/java/org/archime/ime/ArchInputMethodService.kt"
sed -i 's/InputType.TYPE_TEXT/InputType.TYPE_CLASS_TEXT/g; s/InputType.TYPE_NUMBER/InputType.TYPE_CLASS_NUMBER/g' "$MAIN/java/org/archime/ime/ArchInputMethodService.kt"
rm -rf "$MAIN/assets/rime"
cp -a "$OLD/assets/rime" "$MAIN/assets/rime"
cp "$OLD/cpp/archime_bridge.cpp" "$TEMP/archime_native_bridge.cpp"

echo "== Fetch pinned Trime/librime source tree =="
git clone --depth 1 --branch develop --recurse-submodules --shallow-submodules https://github.com/osfans/trime.git "$TRIME"
git -C "$TRIME" fetch --depth 1 origin "$TRIME_COMMIT"
git -C "$TRIME" checkout --detach "$TRIME_COMMIT"
git -C "$TRIME" submodule sync --recursive
git -C "$TRIME" submodule update --init --recursive --depth 1

rm -rf "$MAIN/cpp"
mkdir -p "$MAIN/cpp"
cp -a "$TRIME/app/src/main/jni/." "$MAIN/cpp/"
cp "$TEMP/archime_native_bridge.cpp" "$MAIN/cpp/archime_bridge.cpp"

python3 - "$MAIN/cpp/CMakeLists.txt" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
s = s.replace('add_subdirectory(librime_jni)', '# ArchIME JNI uses the direct librime API')
marker = '# ArchIME direct JNI bridge'
if marker not in s:
    s += r"""
# ArchIME direct JNI bridge
find_package(Opencc REQUIRED)
add_library(archime_rime SHARED archime_bridge.cpp)
target_compile_features(archime_rime PRIVATE cxx_std_17)
target_compile_definitions(archime_rime PRIVATE ARCHIME_RIME_DEPENDENCY_STAGED=1)
target_include_directories(archime_rime PRIVATE
  "${CMAKE_SOURCE_DIR}/librime/include"
  "${CMAKE_SOURCE_DIR}/librime/src"
  "${CMAKE_BINARY_DIR}/librime/src"
  "${Opencc_INCLUDE_PATH}"
)
find_library(log-lib log REQUIRED)
target_link_libraries(archime_rime PRIVATE rime-static ${Opencc_LIBRARY} ${log-lib})
"""
    p.write_text(s)
PY

echo "== Validate staged runtime data =="
for f in default.yaml punctuation.yaml key_bindings.yaml luna_pinyin.dict.yaml stroke.schema.yaml stroke.dict.yaml double_pinyin_mspy.schema.yaml; do
  test -s "$MAIN/assets/rime/shared/$f"
done
test -s "$MAIN/cpp/archime_bridge.cpp"
echo "Rime sources, Microsoft Shuangpin, dictionaries and JNI bridge staged."
