package com.typosbro.multilevel.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.features.inference.OnnxRuntimeManager
import kotlinx.coroutines.launch

class MainViewModel(context: Context) : ViewModel() {
    init {
        viewModelScope.launch {
            OnnxRuntimeManager.initialize(context.applicationContext)
        }
    }

    fun getSession() = OnnxRuntimeManager.getSession()
}