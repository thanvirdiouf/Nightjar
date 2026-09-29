// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Nightjar"
include(":app")
