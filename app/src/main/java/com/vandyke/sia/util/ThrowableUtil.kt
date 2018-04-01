/*
 * Copyright (c) 2017 Nicholas van Dyke. All rights reserved.
 */

package com.vandyke.sia.util

import android.database.sqlite.SQLiteConstraintException
import android.support.design.widget.Snackbar
import android.util.Log
import android.view.View
import com.vandyke.sia.BuildConfig
import com.vandyke.sia.data.local.Prefs
import com.vandyke.sia.data.remote.SiaException
import io.github.tonnyl.light.Light
import io.reactivex.exceptions.CompositeException

/* so that we can customize error messages for non-SiaExceptions.
 * Could probably require a context here and then be able to retrieve
 * localized/translated strings too. If this is a SiaException, then should
 * call a method on it that takes a context and uses it to call getString with
 * a string resource identifier that it's passed in its constructor */
fun Throwable.customMsg(): String? {
    return when (this) {
        is CompositeException -> {
            if (exceptions.all { it.javaClass == exceptions[0].javaClass }) {
                exceptions[0].customMsg()
            } else {
                var msg = "Multiple errors -"
                exceptions.forEachIndexed { index, throwable ->
                    msg += " $index: ${throwable.customMsg()}"
                }
                msg
            }
        }
        is SQLiteConstraintException -> {
            if (BuildConfig.DEBUG)
                localizedMessage
            else
                "Database conflict. Try clearing cached data from settings if this persists."
        }
        else -> {
            if (this !is SiaException)
                Log.d("CustomMsg", "customMsg() called on ${this.javaClass.simpleName} without a custom text implemented")
            localizedMessage
        }
    }
}

fun Throwable.snackbar(view: View, length: Int = Snackbar.LENGTH_SHORT) {
    Light.error(view, this.customMsg() ?: "Error", length).apply {
        if (Prefs.siaManuallyStopped) // TODO: ideally, check state, and if it's MANUALLY_STOPPED, then put this here. Not sure how to access that from here though. Can I still inject?
            setAction("Start") { Prefs.siaManuallyStopped = false } // because right now, if there's another reason it's stopped, pressing Start won't start it
        setActionTextColor(view.context.getColorRes(android.R.color.white))
    }.show()
}