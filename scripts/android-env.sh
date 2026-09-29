#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Nightjar contributors
# Source this file before building from Ubuntu WSL.
if [ -r "$HOME/.config/sleepProject/android-env.sh" ]; then
  . "$HOME/.config/sleepProject/android-env.sh"
else
  export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
fi
