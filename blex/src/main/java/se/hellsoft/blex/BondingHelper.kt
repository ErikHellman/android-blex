@file:Suppress("unused")

package se.hellsoft.blex

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import java.lang.reflect.Method

@RequiresPermission(anyOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH"])
fun BluetoothDevice.releaseBond() {
    val method: Method = this.javaClass.getMethod("removeBond")
    method.invoke(this)
}

data class BondState(val device: BluetoothDevice, val state: Int)

@RequiresPermission(anyOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH"])
fun BluetoothDevice.createBond(context: Context): Flow<BondState> {
    return bondedState(context).onStart { createBond() }
}

@RequiresPermission(anyOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH"])
fun BluetoothDevice.bondedState(context: Context): Flow<BondState> {
    return callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, data: Intent) {
                val bondState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    BondState(
                        data.getParcelableExtra(EXTRA_DEVICE, BluetoothDevice::class.java)!!,
                        data.getIntExtra(EXTRA_BOND_STATE, BOND_NONE)
                    )

                } else {
                    @Suppress("DEPRECATION")
                    BondState(
                        data.getParcelableExtra(EXTRA_DEVICE)!!,
                        data.getIntExtra(EXTRA_BOND_STATE, BOND_NONE)
                    )
                }
                trySendBlocking(bondState)
            }
        }
        val filter = IntentFilter(ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(receiver, filter)
        trySendBlocking(
            BondState(
                this@bondedState,
                this@bondedState.bondState
            )
        )

        awaitClose { context.unregisterReceiver(receiver) }
    }
}