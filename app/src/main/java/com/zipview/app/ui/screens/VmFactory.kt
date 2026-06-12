package com.zipview.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** 简易工厂：用于把构造参数（如压缩包 key）注入 ViewModel。 */
@Suppress("UNCHECKED_CAST")
class SimpleFactory(private val creator: () -> ViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}
