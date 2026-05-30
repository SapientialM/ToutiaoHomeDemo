package com.example.toutiao.presentation.home

sealed class HomeUiEvent {
    data class OnTabSelected(val tab: String) : HomeUiEvent()
    data object OnRefresh : HomeUiEvent()
    data object OnLoadMore : HomeUiEvent()
    data class OnCardClick(val cardId: String) : HomeUiEvent()
    data object OnRetry : HomeUiEvent()
    data object OnSearchClicked : HomeUiEvent()
    data class OnSearchQueryChanged(val query: String) : HomeUiEvent()
    data object OnSearchSubmit : HomeUiEvent()
    data object OnSearchDismiss : HomeUiEvent()
    data object OnMoreChannelsClicked : HomeUiEvent()
}
