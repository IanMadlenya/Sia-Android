/*
 * Copyright (c) 2017 Nicholas van Dyke
 *
 * This file is subject to the terms and conditions defined in 'LICENSE.md'
 */

package vandyke.siamobile.ui.renter.view

import android.app.Fragment
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import kotlinx.android.synthetic.main.fragment_renter.*
import vandyke.siamobile.R
import vandyke.siamobile.backend.data.renter.SiaDir
import vandyke.siamobile.backend.networking.SiaError
import vandyke.siamobile.ui.renter.model.RenterModelTest
import vandyke.siamobile.ui.renter.presenter.IRenterPresenter
import vandyke.siamobile.ui.renter.presenter.RenterPresenter
import vandyke.siamobile.ui.renter.view.list.DirAdapter


class RenterFragment : Fragment(), IRenterView {

    private val presenter: IRenterPresenter = RenterPresenter(this, RenterModelTest())

    private var depth = 0
    private lateinit var adapter: DirAdapter
    private var programmaticallySelecting = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle?): View {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_renter, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(activity)
        filesList.layoutManager = layoutManager
//        filesList.addItemDecoration(new DividerItemDecoration(filesList.getContext(), layoutManager.getOrientation()));
        adapter = DirAdapter(presenter, activity)
        filesList.adapter = adapter

        renterFilepath.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {}
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (!programmaticallySelecting)
                    presenter.goUp(depth - renterFilepath.selectedTabPosition - 1)
            }
        })

        renterSwipeRefresh.setOnRefreshListener { presenter.refresh() }
    }

    override fun changeDisplayedDir(oldDir: SiaDir, newDir: SiaDir) {
        programmaticallySelecting = true
        val oldPath = oldDir.fullPath
        val newPath = newDir.fullPath
        depth = newPath.size
        val breakpoint = (0 until maxOf(newPath.size, oldPath.size)).firstOrNull {
            it > oldPath.size - 1 || it > newPath.size - 1 || newPath[it] != oldPath[it]
        } ?: renterFilepath.tabCount
        if (newPath.size < oldPath.size)
            renterFilepath.getTabAt(newPath.size - 1)?.select()
        for (i in breakpoint until renterFilepath.tabCount)
            renterFilepath.removeTabAt(breakpoint)
        for (i in breakpoint until newPath.size)
            renterFilepath.addTab(renterFilepath.newTab().setText(newPath[i].name), true)
        renterFilepath.postDelayed({ renterFilepath.fullScroll(TabLayout.FOCUS_RIGHT) }, 5)
        adapter.changeDir(newDir)
        programmaticallySelecting = false
    }

    override fun onError(error: SiaError) {
        error.snackbar(view)
        renterSwipeRefresh.isRefreshing = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
//            R.id.actionRefresh -> presenter.refresh()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        presenter.refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            activity.invalidateOptionsMenu()
            presenter.refresh()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.toolbar_renter, menu)
    }
}
