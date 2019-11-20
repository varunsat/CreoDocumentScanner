package com.creoit.docscanner.utils

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

suspend fun onIO(block: suspend CoroutineScope.() -> Unit) = withContext(Dispatchers.IO, block)

suspend fun onUI(block: suspend CoroutineScope.() -> Unit) = withContext(Dispatchers.Main, block)


suspend inline fun <reified T> LiveData<T>.awaitValue(lifecycleOwner: LifecycleOwner) =
    suspendCancellableCoroutine<T> { cont ->

        /*lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_STOP)
                    cont.cancel()
            }
        })*/
        /*   cont.invokeOnCancellation {
               Timber.i("awaitValue cont.invokeOnCancellation() called with $it")
               this.removeObservers(lifecycleOwner)
           }*/

        if(hasObservers())
            removeObservers(lifecycleOwner)

        observe(lifecycleOwner, Observer {
           // Timber.i("awaitValue observe() called with $it")
            if (cont.isActive) cont.resumeWith(Result.success(it))
        })
    }