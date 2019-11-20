package com.creoit.docscanner.utils

import android.app.Activity
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.creoit.docscanner.R
import kotlinx.android.synthetic.main.ds_layout_progress_dialog.view.*
import splitties.alertdialog.appcompat.alert
import splitties.views.dsl.core.inflate

fun AppCompatActivity.setupActionBar(
    toolbar: Toolbar, @StringRes titleRes: Int? = null, @DrawableRes homeDrawableRes: Int? = R.drawable.ds_ic_arrow_back_24dp,
    action: ActionBar.() -> Unit = {}
) {
    setSupportActionBar(toolbar)
    supportActionBar?.run {
        this.title = if (titleRes != null) getString(titleRes) else null
        if (homeDrawableRes != null) {
            setHomeAsUpIndicator(homeDrawableRes)
            setDisplayHomeAsUpEnabled(true)
        }
        action()
    }
}



fun Activity.showProgressDialog(@StringRes message: Int) = alert {
    setView(inflate<LinearLayout>(R.layout.ds_layout_progress_dialog).apply {
        tvProgressMessage.setText(message)
    })
    setCancelable(false)
}.apply { show() }