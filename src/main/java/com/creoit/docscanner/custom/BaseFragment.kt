package com.creoit.docscanner.custom

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import org.jetbrains.anko.startActivity

abstract class BaseFragment : Fragment() {

    @LayoutRes
    abstract fun getLayoutResId(): Int

//    val isTablet by lazy { resources.getBoolean(R.bool.isTablet) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(getLayoutResId(), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    inline fun <reified T : Activity> startActivity(vararg args: Pair<String, Any?>) {
        context?.startActivity<T>(*args)
    }

    suspend inline fun <reified T : Activity> start(
        vararg params: Pair<String, Any?>,
        requestCode: Int? = null
    ) = (activity as? BaseActivity?)?.start<T>(*params, requestCode = requestCode)


    suspend inline fun startAction(
        intent: Intent,
        vararg params: Pair<String, Any?>
    ) = (activity as? BaseActivity?)?.startAction(intent, *params)


    fun setToolbarTitle(@StringRes titleRes: Int) = (activity as? BaseActivity)?.setToolbarTitle(titleRes)
    fun setToolbarTitle(title: String) = (activity as? BaseActivity)?.setToolbarTitle(title)
    fun setToolbarSubTitle(@StringRes subTitleRes: Int) = (activity as? BaseActivity)?.setToolbarSubTitle(subTitleRes)
    fun setToolbarSubTitle(subTitle: String?) = (activity as? BaseActivity)?.setToolbarSubTitle(subTitle)

}