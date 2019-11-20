package com.creoit.docscanner.ui

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.creoit.docscanner.R
import com.creoit.docscanner.custom.BaseFragment
import com.creoit.docscanner.utils.Document
import com.creoit.docscanner.utils.SimpleAdapter
import com.creoit.docscanner.utils.rotate
import kotlinx.android.synthetic.main.fragment_rearrange.*
import kotlinx.android.synthetic.main.rv_item_move_docs.view.*
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class RearrangeFragment : BaseFragment() {
    private var listener: OnFragmentInteractionListener? = null

    private lateinit var rvAdapter: SimpleAdapter<Document, DocVH>
    private val documentVM by sharedViewModel<DocumentVM>()

    private lateinit var itemTouchHelper: ItemTouchHelper

    private val docList = ArrayList<Document>()

    override fun getLayoutResId() = R.layout.fragment_rearrange

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenResumed {
            initViews()
        }
    }

    private fun initViews() {

        docList.addAll(documentVM.documentList)

        rvAdapter = SimpleAdapter(
            items = docList,
            getLayoutId = { R.layout.rv_item_move_docs },
            vhBinder = { view, _ -> DocVH(view) },
            binder = { vh, item -> vh.bind(item) }
        )

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeFlag(
                    ItemTouchHelper.ACTION_STATE_DRAG,
                    ItemTouchHelper.DOWN or ItemTouchHelper.UP or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                )
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val obj = docList[viewHolder.adapterPosition]
                docList.removeAt(viewHolder.adapterPosition)
                docList.add(target.adapterPosition, obj)
                rvAdapter.notifyItemMoved(
                    viewHolder.adapterPosition,
                    target.adapterPosition
                )
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

            }
        })

        with(rvDocs) {
            adapter = rvAdapter
            itemTouchHelper.attachToRecyclerView(this)
            layoutManager = GridLayoutManager(context, 2)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == R.id.menu_save) {
            documentVM.documentList.clear()
            documentVM.documentList.addAll(docList)
            findNavController().popBackStack()
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.save_menu, menu)
        menu.findItem(R.id.menu_save)?.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        super.onCreateOptionsMenu(menu, inflater)
    }


    inner class DocVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(document: Document) {
            with(itemView) {
                lifecycleScope.launch {
                    /*val bitmap = BitmapFactory.decodeStream(openFileInput(document.image))
                    bitmap?.let {*/
                    val doc = document.croppedImage.rotate(document.rotation)
                    val set = ConstraintSet()
                    set.clone(clParent)
                    set.setDimensionRatio(R.id.ivPreview, "${doc.width}:${doc.height}")
                    set.applyTo(clParent)
//                        val doc = getDocument(bitmap, document.rectangle).rotate(document.rotation)
                    ivPreview.setImageBitmap(doc)
                    //tvCount.text = ""
                    //}
                }
                setOnLongClickListener{
                    itemTouchHelper.startDrag(this@DocVH)
                    true
                }
                ivDragHandle.setOnTouchListener { _, motionEvent ->
                    if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                        itemTouchHelper.startDrag(this@DocVH)
                    }
                    true
                }
            }
        }
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
