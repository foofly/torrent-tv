// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "TorrentTV"
include(":app")
