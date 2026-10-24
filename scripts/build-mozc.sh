#!/usr/bin/env bash
set -euo pipefail
# Linux/WSL2 host: Python >=3.12, C++ compiler, Bazelisk, git.
revision=c4b2aef7c65e61be394bacadaec200eb5b97f91c
root=$(cd "$(dirname "$0")/.." && pwd)
src="$root/.local/mozc"
out="$root/.local/mozc-dist"
if [ ! -d "$src/.git" ]; then
  git clone https://github.com/google/mozc.git "$src"
fi
git -C "$src" checkout --detach "$revision"
cd "$src/src"
python3 build_tools/update_deps.py
bazelisk build --config oss_android --config release_build --jobs=3 \
  //android/jni:mozc.arm64
mkdir -p "$out/jniLibs/arm64-v8a" "$out/assets" "$out/licenses"
native=$(bazelisk cquery --config oss_android --config release_build \
  //android/jni:mozc.arm64 --output=files | head -n 1)
cp "$native" "$out/jniLibs/arm64-v8a/libmozc.so"
bazelisk build --config oss_linux --config release_build --jobs=3 \
  //data_manager/oss:mozc_dataset_for_oss
data=$(bazelisk cquery --config oss_linux --config release_build \
  //data_manager/oss:mozc_dataset_for_oss --output=files | head -n 1)
cp "$data" "$out/assets/mozc.data"
cp ../LICENSE "$out/licenses/Mozc-LICENSE.txt"
printf '%s\n' "$revision" > "$out/MOZC_REVISION"
(cd "$out" && sha256sum jniLibs/arm64-v8a/libmozc.so assets/mozc.data > SHA256SUMS)
