#!/bin/sh
# Runs INSIDE the aarch64 Alpine container (qemu-emulated on the Linux CI runner).
set -euo pipefail
cd /rt
apk add --no-cache bash curl unzip git tar gzip findutils file >/dev/null
sh runtime/build-proot.sh
sh runtime/build-rootfs.sh
