package com.rnlib.adyen.ui

import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.adyen.checkout.components.base.lifecycle.viewModelFactory

@MainThread
internal inline fun <reified ViewModelT : ViewModel> AppCompatActivity.getViewModel(crossinline factoryProducer: () -> ViewModelT): ViewModelT {
    return ViewModelProvider(this, viewModelFactory(factoryProducer)).get(ViewModelT::class.java)
}

@MainThread
internal inline fun <reified ViewModelT : ViewModel> Fragment.getViewModel(
    crossinline f: () -> ViewModelT
): ViewModelT {
    return ViewModelProvider(this, viewModelFactory(f)).get(ViewModelT::class.java)
}

@MainThread
internal inline fun <reified ViewModelT : ViewModel> Fragment.getActivityViewModel(crossinline f: () -> ViewModelT): ViewModelT {
    return ViewModelProvider(requireActivity(), viewModelFactory(f)).get(ViewModelT::class.java)
}

@MainThread
internal inline fun <reified VM : ViewModel> AppCompatActivity.viewModelsFactory(
    crossinline factoryProducer: () -> VM
): Lazy<VM> {
    return viewModels { viewModelFactory(factoryProducer) }
}

@MainThread
internal inline fun <reified VM : ViewModel> Fragment.viewModelsFactory(
    crossinline factoryProducer: () -> VM
): Lazy<VM> {
    return viewModels { viewModelFactory(factoryProducer) }
}
