/*
 * Copyright (c) 2017 Nicholas van Dyke. All rights reserved.
 */

package com.vandyke.sia.ui.wallet.view.transactionslist

import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.view.LayoutInflater
import android.view.ViewGroup
import com.vandyke.sia.R
import com.vandyke.sia.data.models.wallet.TransactionData

class TransactionAdapter : ListAdapter<TransactionData, TransactionHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionHolder {
        return TransactionHolder(LayoutInflater.from(parent.context).inflate(R.layout.holder_transaction, parent, false))
    }

    override fun onBindViewHolder(holder: TransactionHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TransactionData>() {
            override fun areItemsTheSame(oldItem: TransactionData, newItem: TransactionData): Boolean {
                return oldItem.transactionid == newItem.transactionid
            }

            override fun areContentsTheSame(oldItem: TransactionData, newItem: TransactionData): Boolean {
                return oldItem.transactionid == newItem.transactionid
                        && oldItem.confirmationDate == newItem.confirmationDate
                        && oldItem.netValue == newItem.netValue
            }
        }
    }
}