/*
 * Copyright (c) 2017 Nicholas van Dyke. All rights reserved.
 */

package com.vandyke.sia.ui.wallet.view.childfragments

import com.vandyke.sia.R
import kotlinx.android.synthetic.main.fragment_wallet_sweep.*

class WalletSweepSeedFragment : BaseWalletFragment() {
    override val layout: Int = R.layout.fragment_wallet_sweep

    override fun onCheckPressed(): Boolean {
        vm.sweep(walletSweepSeed.text.toString())
        return true
    }
}
