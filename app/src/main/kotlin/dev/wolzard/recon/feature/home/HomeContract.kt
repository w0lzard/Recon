package dev.wolzard.recon.feature.home

import dev.wolzard.recon.data.update.UpdateInfo

data class HomeState(val rows: List<HomeRow> = emptyList(), val update: UpdateInfo? = null)

sealed interface HomeIntent {
    data class OpenJob(val id: String) : HomeIntent
    data object OpenSettings : HomeIntent
    data class OpenLink(val url: String) : HomeIntent
    data class DismissUpdate(val version: String) : HomeIntent
}

sealed interface HomeEffect {
    data class NavigateToJob(val id: String) : HomeEffect
    data object NavigateToSettings : HomeEffect
    data class OpenInBrowser(val url: String) : HomeEffect
}
