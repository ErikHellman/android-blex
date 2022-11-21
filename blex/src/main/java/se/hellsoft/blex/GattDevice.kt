package se.hellsoft.blex

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
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

/**
 * The wrapper for the system BluetoothGattDevice, which provides a safer API for performing GATT
 * operations.
 *
 * All operations are queued and the suspending functions will
 */
@Suppress("DEPRECATION", "unused", "MemberVisibilityCanBePrivate")
@SuppressLint("InlinedApi")
class GattDevice(
    val bluetoothDevice: BluetoothDevice,
    bufferCapacity: Int = DEFAULT_EVENT_BUFFER_CAPACITY,
    private val logger: ((level: Int, message: String, throwable: Throwable?) -> Unit)? = null,
) {
    companion object {
        /**
         * The default capacity for the events Flow.
         */
        const val DEFAULT_EVENT_BUFFER_CAPACITY = 10

        /**
         * The UUID for the standard Client Characteristic Configuration.
         * This is usually used to enable/disable notifications on a characteristic.
         */
        val ClientCharacteristicConfigurationID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var closed = false
    private val callback =
        GattCallback(bufferCapacity) { gatt -> services = gatt.services.map { GattService(it) } }
    private val mutex = Mutex()
    private var bluetoothGatt: BluetoothGatt? = null

    val events = callback.events

    val connectionState: Flow<ConnectionChanged> = callback.events.filterIsInstance()

    var services: List<GattService> = emptyList()
        internal set

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    fun connect(
        context: Context,
        autoConnect: Boolean = true,
        transport: Int = BluetoothDevice.TRANSPORT_LE,
        phy: Int = BluetoothDevice.PHY_LE_1M,
    ): Flow<ConnectionChanged> {
        if (closed) throw IllegalStateException("GattDevice is closed!")

        return callback.events
            .onStart {
                bluetoothGatt?.let {
                    bluetoothGatt?.connect()
                } ?: run {
                    bluetoothGatt = bluetoothDevice.connectGatt(
                        context,
                        autoConnect,
                        callback,
                        transport,
                        phy, // Note: this have no effect when autoConnect is true
                        null
                    )
                }
            }
            .filterIsInstance()
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun discoverServices(): ServicesDiscovered {
        if (closed) throw IllegalStateException("GattDevice is closed!")
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
        characteristic: GattCharacteristic,
        descriptor: UUID,
        enable: Boolean
    ): Boolean {
        if (closed) throw IllegalStateException("GattDevice is closed!")
        val value =
            if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        val targetDesc =
            characteristic.descriptors.find { it.id == descriptor }?.bluetoothGattDescriptor
        return (if (targetDesc != null) {
            bluetoothGatt?.setCharacteristicNotification(
                characteristic.bluetoothGattCharacteristic,
                enable
            )
            mutex.queueWithTimeout("updateNotifications: $characteristic $descriptor $enable") {
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
                        characteristic,
                        descriptor,
                        BluetoothGatt.GATT_FAILURE
                    )
            }
        } else {
            DescriptorWritten(characteristic, descriptor, BluetoothGatt.GATT_FAILURE)
        }).status == BluetoothGatt.GATT_SUCCESS
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun registerNotifications(
        characteristic: GattCharacteristic,
        descriptor: UUID = ClientCharacteristicConfigurationID
    ): Boolean {
        if (closed) throw IllegalStateException("GattDevice is closed!")
        return updateNotifications(characteristic, descriptor, true)
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun unregisterNotifications(
        characteristic: GattCharacteristic,
        descriptor: UUID = ClientCharacteristicConfigurationID
    ): Boolean {
        if (closed) throw IllegalStateException("GattDevice is closed!")
        return updateNotifications(characteristic, descriptor, false)
    }


    /**
     * Write a new value to the specified GATT characteristic.
     *
     * @param characteristic The target for the write operations
     * @param value The value to write to the characteristic
     * @param writeType The type of write operation
     * @return The result of the write operation
     */
    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun writeCharacteristic(
        characteristic: GattCharacteristic,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
    ): CharacteristicWritten {
        if (closed) throw IllegalStateException("GattDevice is closed!")
        return bluetoothGatt?.let {
            mutex.queueWithTimeout("writeCharacteristic $characteristic ${value.size} bytes") {
                callback.events
                    .onStart {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            it.writeCharacteristic(
                                characteristic.bluetoothGattCharacteristic,
                                value,
                                writeType
                            )
                        } else {
                            characteristic.bluetoothGattCharacteristic.writeType = writeType
                            characteristic.bluetoothGattCharacteristic.value = value
                            it.writeCharacteristic(characteristic.bluetoothGattCharacteristic)
                        }
                    }
                    .filterIsInstance<CharacteristicWritten>()
                    .firstOrNull { it.characteristic.instanceId == characteristic.instanceId }
                    ?: CharacteristicWritten(characteristic, BluetoothGatt.GATT_FAILURE)
            }
        } ?: CharacteristicWritten(characteristic, BluetoothGatt.GATT_FAILURE)
    }

    /**
     * Read from, a characteristic.
     *
     * @param characteristic The characteristic to read from
     * @return The result of the read operation, including the value read if successful.
     */
    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readCharacteristic(characteristic: GattCharacteristic): CharacteristicRead {
        if (closed) throw IllegalStateException("GattDevice is closed!")
        return bluetoothGatt?.let {
            mutex.queueWithTimeout("readCharacteristic $characteristic") {
                callback.events
                    .onStart {
                        it.readCharacteristic(characteristic.bluetoothGattCharacteristic)
                    }
                    .filterIsInstance<CharacteristicRead>()
                    .firstOrNull { it.characteristic.instanceId == characteristic.instanceId }
                    ?: CharacteristicRead(
                        characteristic,
                        ByteArray(0),
                        BluetoothGatt.GATT_FAILURE
                    )
            }
        } ?: CharacteristicRead(characteristic, ByteArray(0), BluetoothGatt.GATT_FAILURE)
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun writeDescriptor(
        characteristic: GattCharacteristic,
        descriptor: GattDescriptor,
        value: ByteArray
    ): DescriptorWritten {
        if (closed) throw IllegalStateException("GattDevice is closed!")
        return bluetoothGatt?.let {
            mutex.queueWithTimeout("writeDescriptor $descriptor ${value.size}") {
                callback.events
                    .onStart {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            it.writeDescriptor(descriptor.bluetoothGattDescriptor, value)
                        } else {
                            descriptor.bluetoothGattDescriptor.value = value
                            it.writeDescriptor(descriptor.bluetoothGattDescriptor)
                        }
                    }
                    .filterIsInstance<DescriptorWritten>()
                    .firstOrNull {
                        it.descriptorId == descriptor.id
                    } ?: DescriptorWritten(
                    characteristic,
                    descriptor.id,
                    BluetoothGatt.GATT_FAILURE
                )
            }
        } ?: DescriptorWritten(characteristic, descriptor.id, BluetoothGatt.GATT_FAILURE)
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readDescriptor(
        characteristic: GattCharacteristic,
        descriptor: GattDescriptor,
    ): DescriptorRead {
        if (closed) throw IllegalStateException("GattDevice is closed!")
        return bluetoothGatt?.let {
            mutex.queueWithTimeout("readDescriptor $characteristic $descriptor") {
                callback.events
                    .onStart {
                        it.readDescriptor(descriptor.bluetoothGattDescriptor)
                    }
                    .filterIsInstance<DescriptorRead>()
                    .firstOrNull {
                        it.descriptor.id == descriptor.id &&
                                it.characteristic.instanceId == characteristic.instanceId
                    } ?: DescriptorRead(
                    characteristic,
                    descriptor,
                    ByteArray(0),
                    BluetoothGatt.GATT_FAILURE
                )
            }
        } ?: DescriptorRead(
            characteristic,
            descriptor,
            ByteArray(0),
            BluetoothGatt.GATT_FAILURE
        )
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readMtu(mtu: Int): MtuChanged {
        if (closed) throw IllegalStateException("GattDevice is closed!")
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
        if (closed) throw IllegalStateException("GattDevice is closed!")
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
        if (closed) throw IllegalStateException("GattDevice is closed!")
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
    suspend fun readRemoteRssi(): ReadRemoteRssi {
        if (closed) throw IllegalStateException("GattDevice is closed!")
        return bluetoothGatt?.let {
            mutex.queueWithTimeout("readRemoteRssi") {
                callback.events
                    .onStart { it.readRemoteRssi() }
                    .filterIsInstance<ReadRemoteRssi>()
                    .firstOrNull()
            }
        } ?: ReadRemoteRssi(0, BluetoothGatt.GATT_FAILURE)
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun disconnect(): ConnectionChanged {
        if (closed) throw IllegalStateException("GattDevice is closed!")
        return mutex.queueWithTimeout("close") {
            callback.events
                .onStart {
                    bluetoothGatt?.disconnect()
                }
                .filterIsInstance<ConnectionChanged>()
                .firstOrNull()
        } ?: ConnectionChanged(BluetoothGatt.GATT_FAILURE, ConnectionState.Disconnected)
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun close() {
        if (closed) throw IllegalStateException("GattDevice is closed!")
        closed = true
        mutex.queueWithTimeout("close") {
            services = emptyList()
            bluetoothGatt?.close()
            bluetoothGatt = null
        }
    }

    private suspend fun <T> Mutex.queueWithTimeout(
        action: String,
        timeout: Long = DEFAULT_GATT_TIMEOUT,
        block: suspend CoroutineScope.() -> T
    ): T {
        return try {
            logger?.invoke(Log.DEBUG, "try: $action", null)
            withLock {
                return@withLock withTimeout(timeMillis = timeout, block = block)
            }
        } catch (e: Exception) {
            logger?.invoke(Log.ERROR, "error: $action", null)
            throw e
        }
    }
}

