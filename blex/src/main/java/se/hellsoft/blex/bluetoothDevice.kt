package se.hellsoft.blex

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Connects to a BluetoothGatt in a managed fashion, exposing all events as a [ReceiveChannel]
 * to ensure that all events are handled.
 *
 * The connection to the [BluetoothGatt] is managed within the [CoroutineScope], and will be
 * disconnected and closed upon cancellation.
 */
@SuppressLint("InlinedApi")
@RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
internal fun BluetoothDevice.connectGattIn(
    context: Context,
    coroutineScope: CoroutineScope,
    transport: Int = BluetoothDevice.TRANSPORT_LE,
    phy: Int = BluetoothDevice.PHY_LE_1M,
): Pair<BluetoothGatt, ReceiveChannel<GattEvent>> {
    lateinit var bluetoothGatt: BluetoothGatt
    val callback = ChannelBluetoothGattCallback()

    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
        suspendCancellableCoroutine<Nothing> { continuation ->
            bluetoothGatt = this@connectGattIn.connectGatt(
                /* context */ context,
                /* autoConnect */ true,
                /* callback */
                callback,
                /* transport */ transport,
                /* phy */ phy,
                /* handler */ null,
            )

            continuation.invokeOnCancellation { 
                bluetoothGatt.disconnect()
                bluetoothGatt.close()
            }
        }
    }

    return bluetoothGatt to callback.receiveEvents
}

/**
 * Runs a block of code with a managed GATT connection to this [BluetoothDevice].
 *
 * A connection will be created to this [BluetoothDevice], and torn down at the end of [block] by
 * calling [BluetoothGatt.disconnect] and [BluetoothGatt.close]
 */
@SuppressLint("InlinedApi")
@RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
suspend fun BluetoothDevice.withGattConnection(
    context: Context,
    transport: Int = BluetoothDevice.TRANSPORT_LE,
    phy: Int = BluetoothDevice.PHY_LE_1M,
    eventBufferCapacity: Int = 10,
    logger: ((level: Int, message: String, throwable: Throwable?) -> Unit) =
        { _, _, _ -> },
    block: suspend GattDevice.() -> Unit,
) = coroutineScope {
    val bluetoothJob = Job(coroutineContext[Job])
    val bluetoothScope = this + bluetoothJob

    val gattDevice = ConnectedGattDevice(
        bluetoothScope,
        this@withGattConnection,
        context,
        transport,
        phy,
        eventBufferCapacity,
        logger,
    )

    gattDevice.block()

    bluetoothJob.cancelAndJoin()
}