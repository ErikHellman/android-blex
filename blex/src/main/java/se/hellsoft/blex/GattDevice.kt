package se.hellsoft.blex

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.IllegalStateException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

val DEFAULT_GATT_TIMEOUT = 5.seconds

@SuppressLint("InlinedApi")
interface GattDevice {
    companion object {
        val ClientCharacteristicConfigurationID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    val connectionState: StateFlow<ConnectionState>
    val services: StateFlow<List<GattService>>

    suspend fun discoverServices(): ServicesDiscovered

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun registerNotifications(
        characteristic: GattCharacteristic,
        descriptor: UUID = ClientCharacteristicConfigurationID
    ): Boolean

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun unregisterNotifications(
        characteristic: GattCharacteristic,
        descriptor: UUID = ClientCharacteristicConfigurationID
    ): Boolean

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
    ): CharacteristicWritten

    /**
     * Read from a characteristic.
     *
     * @param characteristic The characteristic to read from
     * @return The result of the read operation, including the value read if successful.
     */
    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readCharacteristic(characteristic: GattCharacteristic): CharacteristicRead

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun writeDescriptor(
        characteristic: GattCharacteristic,
        descriptor: GattDescriptor,
        value: ByteArray
    ): DescriptorWritten

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readDescriptor(
        characteristic: GattCharacteristic,
        descriptor: GattDescriptor,
    ): DescriptorRead

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readMtu(mtu: Int): MtuChanged

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readPhy(): PhyRead

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun setPreferredPhy(txPhy: Int, rxPhy: Int, phyOptions: Int): PhyUpdate

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    suspend fun readRemoteRssi(): ReadRemoteRssi
}

@SuppressLint("InlinedApi")
@RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
fun ConnectedGattDevice(
    scope: CoroutineScope,
    bluetoothDevice: BluetoothDevice,
    context: Context,
    transport: Int = BluetoothDevice.TRANSPORT_LE,
    phy: Int = BluetoothDevice.PHY_LE_1M,
    eventBufferCapacity: Int = 10,
    logger: ((level: Int, message: String, throwable: Throwable?) -> Unit) =
        { _, _, _ -> },
): GattDevice {
    val (bluetoothGatt, gattEvents) = bluetoothDevice.connectGattIn(
        context,
        scope,
        transport,
        phy
    )

    return GattDeviceImpl(scope, bluetoothGatt, gattEvents, eventBufferCapacity, logger)
}

@SuppressLint("InlinedApi")
internal class GattDeviceImpl(
    scope: CoroutineScope,
    private val bluetoothGatt: BluetoothGatt,
    gattEvents: ReceiveChannel<GattEvent>,
    eventBufferCapacity: Int,
    private val logger: ((level: Int, message: String, throwable: Throwable?) -> Unit),
) : GattDevice {
    override val connectionState: StateFlow<ConnectionState>
    override val services: StateFlow<List<GattService>>

    /* :scream: */
    private val eventBus = MutableSharedFlow<GattEvent>(extraBufferCapacity = eventBufferCapacity)
    private val eventBusMutex = Mutex()

    init {
        connectionState = MutableStateFlow(ConnectionState.Disconnected)
        services = MutableStateFlow(emptyList())

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            for (gattEvent in gattEvents) {
                logEvent(gattEvent, logger)
                when (gattEvent) {
                    is ConnectionChanged -> connectionState.value = gattEvent.newState
                    is ServicesDiscovered -> services.value =
                        bluetoothGatt.services.map { GattService(it) }

                    else -> {}
                }
                eventBus.emitOrThrow(gattEvent)
            }
        }
    }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    override suspend fun discoverServices(): ServicesDiscovered =
        callGattDevice("discoverServices", { bluetoothGatt.discoverServices() }) {
            it as? ServicesDiscovered
        }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    override suspend fun registerNotifications(
        characteristic: GattCharacteristic,
        descriptor: UUID
    ): Boolean = updateNotifications(characteristic, descriptor, enable = true)

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    override suspend fun unregisterNotifications(
        characteristic: GattCharacteristic,
        descriptor: UUID
    ): Boolean = updateNotifications(characteristic, descriptor, enable = false)

    @Suppress("DEPRECATION")
    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    private suspend fun updateNotifications(
        characteristic: GattCharacteristic,
        descriptor: UUID,
        enable: Boolean
    ): Boolean {
        val value =
            if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        val targetDesc =
            characteristic.descriptors.find { it.id == descriptor }?.bluetoothGattDescriptor
                ?: return false
        val result = callGattDevice(
            "updateNotifications: ${if (enable) "enable" else "disable"}",
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt.writeDescriptor(targetDesc, value)
                } else {
                    targetDesc.value = value
                    bluetoothGatt.writeDescriptor(targetDesc)
                }
            }
        ) {
            it as? DescriptorWritten
        }

        return result.status == BluetoothGatt.GATT_SUCCESS
    }

    @Suppress("DEPRECATION")
    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    override suspend fun writeCharacteristic(
        characteristic: GattCharacteristic,
        value: ByteArray,
        writeType: Int
    ): CharacteristicWritten =
        callGattDevice(
            "writeCharacteristic",
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt.writeCharacteristic(
                        characteristic.bluetoothGattCharacteristic,
                        value,
                        writeType
                    )
                } else {
                    characteristic.bluetoothGattCharacteristic.writeType = writeType
                    characteristic.bluetoothGattCharacteristic.value = value
                    bluetoothGatt.writeCharacteristic(characteristic.bluetoothGattCharacteristic)
                }
            }
        ) {
            (it as? CharacteristicWritten)?.takeIf {
                it.characteristic.instanceId == characteristic.instanceId
            }
        }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    override suspend fun readCharacteristic(characteristic: GattCharacteristic): CharacteristicRead =
        callGattDevice(
            "readCharacteristic",
            { bluetoothGatt.readCharacteristic(characteristic.bluetoothGattCharacteristic) }
        ) {
            (it as? CharacteristicRead)?.takeIf {
                it.characteristic.instanceId == characteristic.instanceId
            }
        }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    override suspend fun writeDescriptor(
        characteristic: GattCharacteristic,
        descriptor: GattDescriptor,
        value: ByteArray
    ): DescriptorWritten =
        callGattDevice(
            "writeDescriptor",
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt.writeDescriptor(descriptor.bluetoothGattDescriptor, value)
                } else {
                    descriptor.bluetoothGattDescriptor.value = value
                    bluetoothGatt.writeDescriptor(descriptor.bluetoothGattDescriptor)
                }

            }
        ) {
            (it as? DescriptorWritten)?.takeIf { it.descriptorId == descriptor.id }
        }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    override suspend fun readDescriptor(
        characteristic: GattCharacteristic,
        descriptor: GattDescriptor
    ): DescriptorRead =
        callGattDevice("readDescriptor", { bluetoothGatt.readDescriptor(descriptor.bluetoothGattDescriptor) }) {
            (it as? DescriptorRead)?.takeIf {
                it.descriptor.id == descriptor.id &&
                        it.characteristic.instanceId == characteristic.instanceId
            }
        }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    override suspend fun readMtu(mtu: Int): MtuChanged =
        callGattDevice("readMtu", { bluetoothGatt.requestMtu(mtu) }) { it as? MtuChanged }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    override suspend fun readPhy(): PhyRead =
        callGattDevice("readPhy", { bluetoothGatt.readPhy() }) { it as? PhyRead }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    override suspend fun setPreferredPhy(txPhy: Int, rxPhy: Int, phyOptions: Int): PhyUpdate =
        callGattDevice("setPreferredPhy", { bluetoothGatt.setPreferredPhy(txPhy, rxPhy, phyOptions) }) {
            it as? PhyUpdate
        }

    @RequiresPermission(anyOf = [BLUETOOTH, BLUETOOTH_CONNECT])
    override suspend fun readRemoteRssi(): ReadRemoteRssi =
        callGattDevice("readRemoteRssi", { bluetoothGatt.readRemoteRssi() }) { it as? ReadRemoteRssi }

    private suspend fun <T : GattEvent> callGattDevice(
        action: String,
        gattCall: () -> Unit,
        filter: (GattEvent) -> T?
    ): T = eventBusMutex.withLock {
        withTimeoutOrNull(DEFAULT_GATT_TIMEOUT) {
            try {
                connectionState.first { it == ConnectionState.Connected }
                eventBus
                    .onStart {
                        logger(Log.DEBUG, "try: $action", null)
                        gattCall()
                    }
                    .mapNotNull { filter(it) }
                    .first()
            } catch (e: Exception) {
                logger(Log.ERROR, "error: $action", null)
                throw e
            }
        } ?: throw IllegalStateException("Gatt timeout")
    }

    private fun logEvent(
        event: GattEvent,
        logger: ((level: Int, message: String, throwable: Throwable?) -> Unit)
    ) {
        val desc = when (event) {
            is ServicesDiscovered -> "onServicesDiscovered"
            is CharacteristicRead -> "onCharacteristicRead"
            is CharacteristicWritten -> "onCharacteristicWritten"
            is DescriptorRead -> "onDescriptorRead"
            is DescriptorWritten -> "onDescriptorWritten"
            is MtuChanged -> "onMtuChanged"
            is PhyRead -> "onPhyRead"
            is PhyUpdate -> "onPhyUpdate"
            is ReadRemoteRssi -> "onReadRemoteRssi"
            else -> "onUnknownEvent"
        }
        logger(Log.INFO, "$desc: $event", null)
    }
}

private fun <T> MutableSharedFlow<T>.emitOrThrow(item: T) {
    if (!tryEmit(item)) throw IllegalStateException("Buffer overflow")
}
