#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
xcodebuild -project FuxStudio.xcodeproj -scheme FuxStudio -configuration Debug -derivedDataPath build CODE_SIGNING_ALLOWED=NO build
printf '\nBuilt app: %s/build/Build/Products/Debug/FuxStudio.app\n' "$PWD"
