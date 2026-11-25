#!/usr/bin/env bash
#
# 下载 OpenCV.js 到 frontend/public/opencv/opencv.js
#
# 为什么单独一个脚本，而不是挂进 npm run build：
#   构建一旦依赖外网下载，网络一抖整个镜像就构建失败——本来只是个可选功能。
#   所以做成手动一步，失败也不影响主流程：没有该文件时人脸检测自动降级。
#
# 用法：
#   bash scripts/fetch-opencv.sh
#   bash scripts/fetch-opencv.sh --force     # 已存在也重新下载

set -euo pipefail

URL="https://docs.opencv.org/4.9.0/opencv.js"
EXPECTED_SIZE=10257309
EXPECTED_SHA256="4d7b85e2e12ea0bd088f491c311d620a45b53d1489b7f065b4492a230bda243a"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="$ROOT/frontend/public/opencv"
TARGET="$TARGET_DIR/opencv.js"
FORCE="${1:-}"

# 用校验和比对，而不是「文件存在就跳过」——半个文件也存在
verify() {
  local file="$1"
  local size
  size=$(wc -c < "$file" | tr -d ' ')
  if [ "$size" != "$EXPECTED_SIZE" ]; then
    echo "  体积不符：$size（期望 $EXPECTED_SIZE）"
    return 1
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    local sum
    # Git Bash（MSYS）下 coreutils 可能在哈希前加一个反斜杠前缀，
    # 不剥掉会导致「明明下载成功却判定不符」
    sum=$(sha256sum "$file" | awk '{print $1}' | tr -d '\\')
    if [ "$sum" != "$EXPECTED_SHA256" ]; then
      echo "  校验和不符：$sum"
      return 1
    fi
  else
    echo "  （无 sha256sum，仅校验体积）"
  fi
  return 0
}

mkdir -p "$TARGET_DIR"

if [ -f "$TARGET" ] && [ "$FORCE" != "--force" ]; then
  echo "已存在，检查完整性：$TARGET"
  if verify "$TARGET"; then
    echo "✓ 文件完整，跳过下载"
    exit 0
  fi
  echo "  文件损坏，重新下载"
fi

echo "下载 OpenCV.js（约 10 MB）..."
echo "  来源：$URL"

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

if ! curl -fL --retry 2 --max-time 300 -o "$TMP" "$URL"; then
  echo ""
  echo "✗ 下载失败。"
  echo "  该文件在境外，部分网络下不通。可以："
  echo "    1. 挂代理重试"
  echo "    2. 手动下载后放到 $TARGET"
  echo "    3. 直接放弃——平台没有这个文件也能正常用，只是人脸检测会降级"
  exit 1
fi

if ! verify "$TMP"; then
  echo "✗ 下载内容校验失败，未写入。"
  exit 1
fi

mv "$TMP" "$TARGET"
echo "✓ 已写入 $TARGET"
echo "  体积：$(wc -c < "$TARGET" | tr -d ' ') 字节"
echo ""
echo "重新构建镜像后生效：docker compose up -d --build"
