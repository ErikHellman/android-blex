package se.hellsoft.blex

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.*

const val DEFAULT_GATT_TIMEOUT = 5000L

@Suppress("DEPRECATION")
class GattDevice(private val bluetoothDevice: BluetoothDevice) {
    companion object {
        /**
         * The UUID for the standard Client Characteristic Configuration.
         * This is usually used to enable/disable notifications on a characteristic.
         */
        val ClientCharacteristicConfigurationID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private fun createCallback(): GattCallback {
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                GattCallbackLegacy()
            } else {
                GattCallbackV33()
            }
        }
    }

    private val mutex = Mutex()
    private val callback = createCallback()
    private var bluetoothGatt: BluetoothGatt? = null

    val events = callback.events

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun connect(context: Context): Flow<ConnectionChanged> {
        return callback.events
            .onStart {
                bluetoothGatt = bluetoothDevice.connectGatt(
                    context, true, callback,
                    BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_1M, // Note: this have no effect when autoConnect is true
                    null
                )
            }
            .filterIsInstance()
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun discoverServices(): ServicesDiscovered {
        return bluetoothGatt?.let {
            mutex.queueWithTimeout("discoverServices") {
                callback.events
                    .onStart { it.discoverServices() }
                    .filterIsInstance<ServicesDiscovered>()
                    .firstOrNull() ?: ServicesDiscovered(BluetoothGatt.GATT_FAILURE)
            }
        } ?: ServicesDiscovered(BluetoothGatt.GATT_FAILURE)
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    private suspend fun updateNotifications(
        service: UUID,
        characteristic: UUID,
        descriptor: UUID,
        enable: Boolean
    ): Boolean {
        val value =
            if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        val targetChar = bluetoothGatt
            ?.getService(service)
            ?.getCharacteristic(characteristic)
        val targetDesc = targetChar
            ?.getDescriptor(descriptor)
        return if (targetDesc != null) {
            bluetoothGatt?.setCharacteristicNotification(targetChar, enable)
            mutex.queueWithTimeout("updateNotifications: $service $characteristic $descriptor $enable") {
                callback.events
                    .onStart {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            bluetoothGatt?.writeDescriptor(targetDesc, value)
                        } else {
                            targetDesc.value = value
                            bluetoothGatt?.writeDescriptor(targetDesc)
                        }
                    }
                    .filterIsInstance<DescriptorWritten>()
                    .firstOrNull()
                    ?: DescriptorWritten(
                        service,
                        characteristic,
                        descriptor,
                        BluetoothGatt.GATT_FAILURE
                    )
            }
        } else {
            DescriptorWritten(service, characteristic, descriptor, BluetoothGatt.GATT_FAILURE)
        }?.status == BluetoothGatt.GATT_SUCCESS
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun registerNotifications(
        service: UUID,
        characteristic: UUID,
        descriptor: UUID = ClientCharacteristicConfigurationID
    ): Boolean = updateNotifications(service, characteristic, descriptor, true)

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun unregisterNotifications(
        service: UUID,
        characteristic: UUID,
        descriptor: UUID = ClientCharacteristicConfigurationID
    ): Boolean = updateNotifications(service, characteristic, descriptor, false)

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun writeCharacteristic(
        service: UUID,
        characteristic: UUID, value: ByteArray,
    ): CharacteristicWritten {
        val targetChar = bluetoothGatt?.getService(service)?.getCharacteristic(characteristic)
            ?: return CharacteristicWritten(service, characteristic, BluetoothGatt.GATT_FAILURE)

        return bluetoothGatt?.let {
            mutex.queueWithTimeout("writeCharacteristic $service $characteristic ${value.size} bytes") {
                callback.events
                    .onStart {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            it.writeCharacteristic(
                                targetChar,
                                value,
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            )
                        } else {
                            targetChar.value = value
                            it.writeCharacteristic(targetChar)
                        }
                    }
                    .filterIsInstance<CharacteristicWritten>()
                    .firstOrNull { it.service == service && it.characteristic == characteristic }
                    ?: CharacteristicWritten(service, characteristic, BluetoothGatt.GATT_FAILURE)
            }
        } ?: CharacteristicWritten(service, characteristic, BluetoothGatt.GATT_FAILURE)
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readCharacteristic(
        service: UUID,
        characteristic: UUID
    ): CharacteristicRead {
        val targetChar = bluetoothGatt?.getService(service)?.getCharacteristic(characteristic)
            ?: return CharacteristicRead(
                service,
                characteristic,
                ByteArray(0),
                BluetoothGatt.GATT_FAILURE
            )

        return bluetoothGatt?.let {
            mutex.queueWithTimeout("readCharacteristic $service $characteristic") {
                callback.events
                    .onStart {
                        it.readCharacteristic(targetChar)
                    }
                    .filterIsInstance<CharacteristicRead>()
                    .firstOrNull { it.service == service && it.characteristic == characteristic }
                    ?: CharacteristicRead(
                        service,
                        characteristic,
                        ByteArray(0),
                        BluetoothGatt.GATT_FAILURE
                    )
            }
        } ?: CharacteristicRead(service, characteristic, ByteArray(0), BluetoothGatt.GATT_FAILURE)
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun writeDescriptor(
        service: UUID,
        characteristic: UUID,
        descriptor: UUID,
        value: ByteArray
    ): DescriptorWritten {
        val targetChar = bluetoothGatt?.getService(service)?.getCharacteristic(characteristic)
        val targetDesc = targetChar?.getDescriptor(descriptor) ?: return DescriptorWritten(
            service,
            characteristic,
            descriptor,
            BluetoothGatt.GATT_FAILURE
        )

        return bluetoothGatt?.let {
            mutex.queueWithTimeout("writeDescriptor $service $characteristic $descriptor ${value.size}") {
                callback.events
                    .onStart {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            it.writeDescriptor(targetDesc, value)
                        } else {
                            targetDesc.value = value
                            it.writeDescriptor(targetDesc)
                        }
                    }
                    .filterIsInstance<DescriptorWritten>()
                    .firstOrNull {
                        it.service == service && it.characteristic == characteristic && it.descriptor == descriptor
                    } ?: DescriptorWritten(
                    service,
                    characteristic,
                    descriptor,
                    BluetoothGatt.GATT_FAILURE
                )
            }
        } ?: DescriptorWritten(service, characteristic, descriptor, BluetoothGatt.GATT_FAILURE)
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readDescriptor(
        service: UUID,
        characteristic: UUID,
        descriptor: UUID,
    ): DescriptorRead {
        val targetChar = bluetoothGatt?.getService(service)?.getCharacteristic(characteristic)
        val targetDesc = targetChar?.getDescriptor(descriptor) ?: return DescriptorRead(
            service,
            characteristic,
            descriptor,
            ByteArray(0),
            BluetoothGatt.GATT_FAILURE
        )

        return bluetoothGatt?.let {
            mutex.queueWithTimeout("readDescriptor $service $characteristic $descriptor") {
                callback.events
                    .onStart {
                        it.readDescriptor(targetDesc)
                    }
                    .filterIsInstance<DescriptorRead>()
                    .firstOrNull {
                        it.service == service && it.characteristic == characteristic && it.descriptor == descriptor
                    } ?: DescriptorRead(
                    service,
                    characteristic,
                    descriptor,
                    ByteArray(0),
                    BluetoothGatt.GATT_FAILURE
                )
            }
        } ?: DescriptorRead(
            service,
            characteristic,
            descriptor,
            ByteArray(0),
            BluetoothGatt.GATT_FAILURE
        )
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readMtu(mtu: Int): MtuChanged {
        return bluetoothGatt?.let {
            mutex.queueWithTimeout("requestMtu $mtu") {
                callback.events
                    .onStart { it.requestMtu(mtu) }
                    .filterIsInstance<MtuChanged>()
                    .firstOrNull()
            }
        } ?: MtuChanged(-1, BluetoothGatt.GATT_FAILURE)
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readPhy(): PhyRead {
        return bluetoothGatt?.let {
            mutex.queueWithTimeout("readPhy") {
                callback.events
                    .onStart { it.readPhy() }
                    .filterIsInstance<PhyRead>()
                    .firstOrNull()
            }
        } ?: PhyRead(0, 0, BluetoothGatt.GATT_FAILURE)
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun setPreferredPhy(txPhy: Int, rxPhy: Int, phyOptions: Int): PhyUpdate {
        return bluetoothGatt?.let {
            mutex.queueWithTimeout("setPreferredPhy $txPhy $rxPhy $phyOptions") {
                callback.events
                    .onStart { it.setPreferredPhy(txPhy, rxPhy, phyOptions) }
                    .filterIsInstance<PhyUpdate>()
                    .firstOrNull()
            }
        } ?: PhyUpdate(0, 0, BluetoothGatt.GATT_FAILURE)
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readREmoteRssi(txPhy: Int, rxPhy: Int, phyOptions: Int): ReadRemoteRssi {
        return bluetoothGatt?.let {
            mutex.queueWithTimeout("readRemoteRssi") {
                callback.events
                    .onStart { it.readRemoteRssi() }
                    .filterIsInstance<ReadRemoteRssi>()
                    .firstOrNull()
            }
        } ?: ReadRemoteRssi(0, BluetoothGatt.GATT_FAILURE)
    }
}

private suspend fun <T> Mutex.queueWithTimeout(
    action: String,
    timeout: Long = DEFAULT_GATT_TIMEOUT,
    block: suspend CoroutineScope.() -> T
): T {
    return try {
        Log.d("BLEx", "try: $action")
        withLock {
            return@withLock withTimeout(timeMillis = timeout, block = block)
        }
    } catch (e: Exception) {
        Log.e("BLEx", "error: $action", e)
        throw e
    }
}

abstract class GattCallback : BluetoothGattCallback() {
    val events: Flow<GattEvent> = MutableSharedFlow()
}

