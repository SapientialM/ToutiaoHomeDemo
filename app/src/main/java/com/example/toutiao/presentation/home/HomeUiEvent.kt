package com.example.toutiao.presentation.home

sealed class HomeUiEvent {
    data class OnTabSelected(val tab: String) : HomeUiEvent()
    data object OnRefresh : HomeUiEvent()
    data object OnLoadMore : HomeUiEvent()
    data class OnCardClick(val cardId: String) : HomeUiEvent()
    data object OnRetry : HomeUiEvent()
}
