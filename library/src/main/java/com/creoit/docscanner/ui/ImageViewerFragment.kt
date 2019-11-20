package com.creoit.docscanner.ui

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.creoit.docscanner.R
import com.creoit.docscanner.custom.BaseActivity
import com.creoit.docscanner.custom.BaseFragment
import com.creoit.docscanner.ui.CropImageFragment.Companion.ARG_DOC_POS
import com.creoit.docscanner.utils.Document
import com.creoit.docscanner.utils.SimpleAdapter
import com.creoit.docscanner.utils.rotate
import com.creoit.docscanner.utils.showProgressDialog
import kotlinx.android.synthetic.main.fragment_image_viewer.*
import kotlinx.android.synthetic.main.vp_item_docs.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class ImageViewerFragment : BaseFragment() {
    private var listener: OnFragmentInteractionListener? = null

    private var vpPosition: Int? = null

    private lateinit var vpAdapter: SimpleAdapter<Document, DocVH>
    private val documentVM by sharedViewModel<DocumentVM>()

    override fun getLayoutResId() = R.layout.fragment_image_viewer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (vpPosition == null) {
            vpPosition = arguments?.getInt(ARG_DOC_POSITION, 0)
        }
        lifecycleScope.launchWhenResumed {
            initViews()
        }
    }

    private fun initViews() {
        vpAdapter = SimpleAdapter(
            items = documentVM.documentList,
            getLayoutId = { R.layout.vp_item_docs },
            vhBinder = { view, _ -> DocVH(view) },
            binder = { vh, item -> vh.bind(item) }
        )
        with(vpDocs) {
            adapter = vpAdapter
            offscreenPageLimit = 10
            vpPosition?.let { setCurrentItem(it, false) }
        }

        bottomAppBar.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_rotate -> {
                    vpPosition = vpDocs.currentItem
                    vpAdapter.updateItem(vpDocs.currentItem) {
                        it.rotation += 90f
                        it.rotation = it.rotation % 360

                        it.croppedImage = it.croppedImage.rotate(it.rotation)
                    }
                }
                R.id.nav_crop -> {
                    lifecycleScope.launch {
                        vpPosition = vpDocs.currentItem
                        findNavController().navigate(
                            R.id.action_imageViewerFragment_to_cropImageFragment,
                            bundleOf(
                                ARG_DOC_POS to vpDocs.currentItem
                            )
                        )
                    }
                }
                R.id.nav_filter -> {
                    lifecycleScope.launch {
                        vpPosition = vpDocs.currentItem
                        findNavController().navigate(
                            R.id.action_imageViewerFragment_to_imageFilterFragment,
                            bundleOf(
                                ImageFilterFragment.ARG_DOC_POS to vpDocs.currentItem
                            )
                        )
                    }
                }
                R.id.nav_move -> {
                    vpPosition = vpDocs.currentItem
                    lifecycleScope.launch {
                        findNavController().navigate(R.id.action_imageViewerFragment_to_rearrangeFragment)
                    }
                }
                R.id.nav_delete -> {
                    lifecycleScope.launch {
                        vpAdapter.removeItem(vpDocs.currentItem)
                        if (vpAdapter.itemCount == 0) {
                            (context as? BaseActivity)?.onBackPressed()
                        }
                    }
                }
            }
            true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_save) {
            listener?.onSavePdf()
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.save_menu, menu)
        menu.findItem(R.id.menu_save)?.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onResume() {
        super.onResume()
        if (::vpAdapter.isInitialized)
            vpAdapter.notifyDataSetChanged()
    }

    inner class DocVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(document: Document) {
            with(itemView) {
                lifecycleScope.launch {
                    /*val bitmap = BitmapFactory.decodeStream(openFileInput(document.image))
                    bitmap?.let {*/
//                        val doc = getDocument(bitmap, document.rectangle).rotate(document.rotation)
                    ivPreview.setImageBitmap(document.croppedImage)
                    //}
                }
            }
        }
    }

    companion object {
        const val ARG_DOCS = "docs"
        const val ARG_DOC_POSITION = "position"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}
