/*
 * Copyright (c) 2017 Nicholas van Dyke. All rights reserved.
 */

package com.vandyke.sia.ui.node.modules

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.view.*
import com.vandyke.sia.R
import com.vandyke.sia.dagger.SiaViewModelFactory
import com.vandyke.sia.data.local.Prefs
import com.vandyke.sia.getAppComponent
import com.vandyke.sia.ui.common.BaseFragment
import io.github.tonnyl.light.Light
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.fragment_node_modules.*
import kotlinx.android.synthetic.main.holder_module.*
import java.io.File
import javax.inject.Inject

class NodeModulesFragment : BaseFragment() {
    override val title: String = "Node Modules"
    override val layoutResId: Int = R.layout.fragment_node_modules
    override val hasOptionsMenu: Boolean = true

    @Inject
    lateinit var factory: SiaViewModelFactory
    private lateinit var vm: NodeModulesViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        context!!.getAppComponent().inject(this)

        val adapter = ModulesAdapter()
        modules_list.adapter = adapter

        vm = ViewModelProviders.of(this, factory).get(NodeModulesViewModel::class.java)

        vm.moduleUpdated.observe(this, adapter::notifyUpdate)

        vm.success.observe(this) {
            Light.success(view, it, Snackbar.LENGTH_LONG).show()
        }

        vm.error.observe(this) {
            Light.error(view, it, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onShow() {
        super.onShow()
        vm.onShow()
    }

    override fun onHide() {
        super.onHide()
        vm.onHide()
    }

    private fun showDeleteConfirmationDialog(module: Module, internal: Boolean) {
        AlertDialog.Builder(context!!)
                .setTitle("Confirm")
                .setMessage(
                        "Are you sure you want to delete all ${module.text} files from ${if (internal) "internal" else "external"} storage?"
                                + when (module) {
                            Module.WALLET -> "Ensure your wallet seed is recorded elsewhere first."
                            Module.CONSENSUS -> "You'll have to re-sync the blockchain."
                            else -> ""
                        })
                .setPositiveButton("Yes") { _, _ -> vm.deleteDir(module, File("")) }
                .setNegativeButton("No", null)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.toolbar_node_modules, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.modules_info) {
            AlertDialog.Builder(context!!)
                    .setTitle("Modules info")
                    .setMessage("The switch enables/disables the module on the Sia node. ")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    inner class ModulesAdapter : RecyclerView.Adapter<ModuleHolder>() {
        private val holders = mutableListOf<ModuleHolder>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModuleHolder {
            val holder = ModuleHolder(LayoutInflater.from(parent.context).inflate(R.layout.holder_module, parent, false))
            holders.add(holder)
            return holder
        }

        override fun onBindViewHolder(holder: ModuleHolder, position: Int) {
            holder.bind(vm.modules[position])
        }

        override fun getItemCount() = vm.modules.size

        fun notifyUpdate(module: Module) {
            val data = vm.modules.find { it.type == module }
            val holder = holders.find { it.module == module }
            holder?.bind(data!!)
        }
    }

    inner class ModuleHolder(itemView: View) : RecyclerView.ViewHolder(itemView), LayoutContainer {
        override val containerView: View? = itemView

        lateinit var module: Module
        private val storageAdapter = ModuleStorageAdapter()

        init {
            module_switch.setOnCheckedChangeListener { buttonView, isChecked ->
                if (!isChecked) {
                    Prefs.modulesString = Prefs.modulesString.replace(module.name[0].toString(), "", true)
                } else if (!Prefs.modulesString.contains(module.name[0], true)) {
                    Prefs.modulesString += module.name[0].toLowerCase()
                }
            }

            module_switch.setOnClickListener {
                Light.success(view!!, "${module.text} module ${if (module_switch.isChecked) "enabled" else "disabled"}, restarting Sia node...", Snackbar.LENGTH_LONG).show()
            }

            // so turns out that you can delete node module folders while it's running, but it
            // won't take effect until after restarting the node. Observe the folders from
            // SiadSource and watch for delete event? Or just call restart on it in the vm?
        }

        fun bind(module: ModuleData) {
            this.module = module.type
            module_name.text = module.type.text
            module_switch.isChecked = module.enabled

            storageAdapter.dirs = module.directories
            storageAdapter.notifyDataSetChanged()
        }
    }
}

