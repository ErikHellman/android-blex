package se.hellsoft.blex

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
internal class GattCallback(
    bufferCapacity: Int,
    private val logger: ((level: Int, message: String, throwable: Throwable?) -> Unit)? = null,
    private val servicesChanged: (gatt: BluetoothGatt) -> Unit
) : BluetoothGattCallback() {
    internal val events: Flow<GattEvent> = MutableSharedFlow(bufferCapacity)
    private val mutableEvents = events as MutableSharedFlow<GattEvent>

    override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        val phyUpdate = PhyUpdate(txPhy, rxPhy, status)
        logger?.invoke(Log.INFO, "onPhyUpdate: $phyUpdate", null)
        mutableEvents.tryEmit(phyUpdate)
    }

    override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        val phyRead = PhyRead(txPhy, rxPhy, status)
        logger?.invoke(Log.INFO, "onPhyRead: $phyRead", null)
        mutableEvents.tryEmit(phyRead)
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        val connectionChanged = ConnectionChanged(status, ConnectionState.fromState(newState))
        logger?.invoke(Log.INFO, "onConnectionStateChange: $connectionChanged", null)
        mutableEvents.tryEmit(connectionChanged)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        val servicesDiscovered = ServicesDiscovered(status)
        logger?.invoke(Log.INFO, "onServicesDiscovered: $servicesDiscovered", null)
        mutableEvents.tryEmit(servicesDiscovered)
        servicesChanged(gatt)
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        val characteristicRead = CharacteristicRead(
            GattCharacteristic(characteristic),
            value,
            status
        )
        logger?.invoke(Log.INFO, "onCharacteristicRead: $characteristicRead", null)
        mutableEvents.tryEmit(characteristicRead)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        val characteristicWritten = CharacteristicWritten(
            GattCharacteristic(characteristic),
            status
        )
        logger?.invoke(Log.INFO, "onCharacteristicWrite: $characteristicWritten", null)
        mutableEvents.tryEmit(characteristicWritten)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        val characteristicChanged = CharacteristicChanged(
            GattCharacteristic(characteristic),
            value
        )
        logger?.invoke(Log.INFO, "onCharacteristicChanged: $characteristicChanged", null)
        mutableEvents.tryEmit(characteristicChanged)
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
        value: ByteArray
    ) {
        val descriptorRead = DescriptorRead(
            GattCharacteristic(descriptor.characteristic),
            GattDescriptor(descriptor),
            value,
            status
        )
        logger?.invoke(Log.INFO, "onDescriptorRead: $descriptorRead", null)
        mutableEvents.tryEmit(descriptorRead)
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        val descriptorWritten = DescriptorWritten(
            GattCharacteristic(descriptor.characteristic),
            descriptor.uuid,
            status
        )
        logger?.invoke(Log.INFO, "onDescriptorWrite: $descriptorWritten", null)
        mutableEvents.tryEmit(descriptorWritten)
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        val characteristicRead = CharacteristicRead(
            GattCharacteristic(characteristic),
            characteristic.value,
            status
        )
        logger?.invoke(Log.INFO, "onCharacteristicRead: $characteristicRead", null)
        mutableEvents.tryEmit(characteristicRead)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val characteristicChanged = CharacteristicChanged(
            GattCharacteristic(characteristic),
            characteristic.value
        )
        logger?.invoke(Log.INFO, "onCharacteristicChanged: $characteristicChanged", null)
        mutableEvents.tryEmit(characteristicChanged)
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        val descriptorRead = DescriptorRead(
            GattCharacteristic(descriptor.characteristic),
            GattDescriptor(descriptor),
            descriptor.value,
            status
        )
        logger?.invoke(Log.INFO, "onDescriptorRead: $descriptorRead", null)
        mutableEvents.tryEmit(descriptorRead)
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
        val reliableWriteCompleted = ReliableWriteCompleted(status)
        logger?.invoke(Log.INFO, "onReliableWriteCompleted: $reliableWriteCompleted", null)
        mutableEvents.tryEmit(reliableWriteCompleted)
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
        val readRemoteRssi = ReadRemoteRssi(rssi, status)
        logger?.invoke(Log.INFO, "onReadRemoteRssi: $readRemoteRssi", null)
        mutableEvents.tryEmit(readRemoteRssi)
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        val mtuChanged = MtuChanged(mtu, status)
        logger?.invoke(Log.INFO, "onMtuChanged: $mtuChanged", null)
        mutableEvents.tryEmit(mtuChanged)
    }

    override fun onServiceChanged(gatt: BluetoothGatt) {
        val serviceChanged = ServiceChanged
        logger?.invoke(Log.INFO, "onServiceChanged: $serviceChanged", null)
        mutableEvents.tryEmit(serviceChanged)
        servicesChanged(gatt)
    }
}