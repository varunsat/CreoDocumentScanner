package com.creoit.docscanner.custom

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.anko.intentFor
import java.util.logging.Level
import java.util.logging.Logger


data class ActivityResult(val resultCode: Int, val data: Intent?)

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class SaveToInstance

open class BaseActivity : AppCompatActivity() {

    //val isTablet by lazy { resources.getBoolean(R.bool.isTablet) }
    var currentCode: Int = 0
    var resultByCode = mutableMapOf<Int, CompletableDeferred<ActivityResult?>>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        Logger.getLogger("com.amazonaws").level = Level.OFF
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        resultByCode[requestCode]?.let {
            it.complete(ActivityResult(resultCode, data))
            resultByCode.remove(requestCode)
        } ?: run {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }


    // have synchronous activity results
    suspend inline fun <reified T : Activity> start(
        vararg params: Pair<String, Any?>,
        requestCode: Int? = null
    ): ActivityResult? {

        val activityResult = CompletableDeferred<ActivityResult?>()
        val intent = intentFor<T>(*params)

        if (intent.resolveActivity(packageManager) != null) {
            val rc = requestCode ?: currentCode++
            resultByCode[rc] = activityResult
            startActivityForResult(intent, rc)
        } else {
            activityResult.complete(null)
        }
        return activityResult.await()
    }

    // have synchronous activity results
    suspend inline fun <reified T : Activity> AppCompatActivity.start(
        vararg params: Pair<String, Any?>,
        requestCode: Int? = null
    ): ActivityResult? {

        val activityResult = CompletableDeferred<ActivityResult?>()
        val intent = intentFor<T>(*params)

        if (intent.resolveActivity(packageManager) != null) {
            val rc = requestCode ?: currentCode++
            resultByCode[rc] = activityResult
            startActivityForResult(intent, rc)
        } else {
            activityResult.complete(null)
        }
        return activityResult.await()
    }

    suspend inline fun startAction(
        intent: Intent,
        vararg params: Pair<String, Any?>
    ): ActivityResult? {

        val activityResult = CompletableDeferred<ActivityResult?>()

        intent.putExtras(intentFor<Any>(*params))

        if (intent.resolveActivity(packageManager) != null) {
            val rc = currentCode++
            resultByCode[rc] = activityResult
            startActivityForResult(intent, rc)
        } else {
            activityResult.complete(null)
        }
        return activityResult.await()
    }


    fun setToolbarTitle(@StringRes titleRes: Int) {
        supportActionBar?.setTitle(titleRes)
    }

    fun setToolbarTitle(title: String): Unit? {
        return supportActionBar?.setTitle(title)
    }
    fun setToolbarSubTitle(@StringRes subTitleRes: Int) = supportActionBar?.setSubtitle(subTitleRes)
    fun setToolbarSubTitle(subTitle: String?): Unit? {
        return supportActionBar?.setSubtitle(subTitle)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
