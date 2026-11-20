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
SCHEMA_SOURCE="$(find "$OLD" -type f -path '*/assets/rime/shared/luna_pinyin.dict.yaml' -print -quit)"
test -n "$SCHEMA_SOURCE"
SRC="${SCHEMA_SOURCE%/assets/rime/shared/luna_pinyin.dict.yaml}"
BRIDGE="$SRC/cpp/archime_bridge.cpp"
if [ ! -s "$BRIDGE" ]; then BRIDGE="$SRC/cpp/native_bridge.cpp"; fi
test -s "$BRIDGE"

echo "== Stage Rime runtime data and native bridge =="
mkdir -p "$MAIN/assets"
rm -rf "$MAIN/assets/rime"
cp -a "$SRC/assets/rime" "$MAIN/assets/rime"
cp "$BRIDGE" "$TEMP/archime_native_bridge.cpp"

echo "== Fetch pinned Trime/librime source tree =="
git clone --depth 1 --branch develop --recurse-submodules --shallow-submodules https://github.com/osfans/trime.git "$TRIME"
git -C "$TRIME" fetch --depth 1 origin "$TRIME_COMMIT"
git -C "$TRIME" checkout --detach "$TRIME_COMMIT"
git -C "$TRIME" submodule sync --recursive
git -C "$TRIME" submodule update --init --recursive --depth 1

echo "== Refresh Rime dictionaries and Microsoft Shuangpin schema =="
SHARED="$MAIN/assets/rime/shared"
for component in prelude luna-pinyin stroke essay; do
  source_dir="$TRIME/app/data/rime/$component"
  if [ ! -d "$source_dir" ]; then
    echo "Missing Rime data submodule: $source_dir" >&2
    exit 1
  fi
  find -L "$source_dir" -type f \( -name '*.yaml' -o -name '*.txt' \) -exec cp -f {} "$SHARED/" \;
done
git clone --depth 1 https://github.com/rime/rime-double-pinyin.git "$DOUBLE_PINYIN"
test -s "$DOUBLE_PINYIN/double_pinyin_mspy.schema.yaml"
cp "$DOUBLE_PINYIN/double_pinyin_mspy.schema.yaml" "$SHARED/"

python3 - "$MAIN/assets/rime/shared/default.yaml" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
entry = "  - schema: double_pinyin_mspy"
if entry not in s:
    marker = "schema_list:\n"
    if marker not in s:
        raise SystemExit("default.yaml has no schema_list")
    s = s.replace(marker, marker + entry + "\n", 1)
    p.write_text(s)
PY

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
grep -q 'schema: double_pinyin_mspy' "$MAIN/assets/rime/shared/default.yaml"
test -s "$MAIN/cpp/archime_bridge.cpp"
echo "Rime sources, Microsoft Shuangpin, dictionaries and JNI bridge staged."
