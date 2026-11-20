#!/usr/bin/env bash
set -euo pipefail

ROOT="${GITHUB_WORKSPACE:?GITHUB_WORKSPACE is required}"
TEMP="${RUNNER_TEMP:-/tmp}"
APP_MAIN="$ROOT/ArchIME-0.7.2-dev/app/src/main"
CPP="$APP_MAIN/cpp"
TRIME="$TEMP/archime-trime"
DOUBLE_PINYIN="$TEMP/archime-double-pinyin"
TRIME_COMMIT="8ef9ff825c7e993934682f1fc6da0bccfc620360"

echo "== Fetch pinned Trime native build sources =="
git clone --depth 1 --branch develop --recurse-submodules --shallow-submodules \
  https://github.com/osfans/trime.git "$TRIME"
git -C "$TRIME" fetch --depth 1 origin "$TRIME_COMMIT"
git -C "$TRIME" checkout --detach "$TRIME_COMMIT"
git -C "$TRIME" submodule sync --recursive
git -C "$TRIME" submodule update --init --recursive --depth 1

echo "== Replace the placeholder CMake tree with Trime's librime build tree =="
test -s "$CPP/native_bridge.cpp"
cp "$CPP/native_bridge.cpp" "$TEMP/archime_native_bridge.cpp"
rm -rf "$CPP"
mkdir -p "$CPP"
cp -a "$TRIME/app/src/main/jni/." "$CPP/"
cp "$TEMP/archime_native_bridge.cpp" "$CPP/archime_bridge.cpp"

python3 - "$CPP/CMakeLists.txt" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
# The original Trime frontend wrapper is not used; ArchIME calls librime directly.
text = text.replace('add_subdirectory(librime_jni)', '# ArchIME JNI replaces Trime JNI wrapper')
marker = "# ArchIME JNI bridge linked against the upstream Rime static engine."
if marker not in text:
    text += r"""

# ArchIME JNI bridge linked against the upstream Rime static engine.
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
    path.write_text(text)
print("Added archime_rime target to Trime's native CMake project.")
PY

echo "== Fetch official Microsoft Shuangpin schema =="
git clone --depth 1 https://github.com/rime/rime-double-pinyin.git "$DOUBLE_PINYIN"

SHARED="$APP_MAIN/assets/rime/shared"
mkdir -p "$SHARED"
for component in prelude luna-pinyin stroke essay; do
  source_dir="$TRIME/app/data/rime/$component"
  if [ ! -d "$source_dir" ]; then
    echo "Missing Rime data submodule: $source_dir" >&2
    exit 1
  fi
  find -L "$source_dir" -type f \( -name '*.yaml' -o -name '*.txt' \) \
    -exec cp -f {} "$SHARED/" \;
done
cp "$DOUBLE_PINYIN/double_pinyin_mspy.schema.yaml" "$SHARED/"

for required in \
  default.yaml \
  punctuation.yaml \
  key_bindings.yaml \
  luna_pinyin.dict.yaml \
  stroke.schema.yaml \
  stroke.dict.yaml \
  double_pinyin_mspy.schema.yaml; do
  test -s "$SHARED/$required" || {
    echo "Required Rime data file is absent or empty: $SHARED/$required" >&2
    exit 1
  }
done

echo "== Rime inputs staged =="
du -sh "$SHARED"
find "$SHARED" -maxdepth 1 -type f -printf '%f %s bytes\n' | sort
echo "Pinned Trime revision: $TRIME_COMMIT"
