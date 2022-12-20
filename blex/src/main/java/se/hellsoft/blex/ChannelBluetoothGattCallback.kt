package se.hellsoft.blex

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.ReceiveChannel
import java.lang.IllegalStateException

@Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
class ChannelBluetoothGattCallback : BluetoothGattCallback() {
    private val events = Channel<GattEvent>(BUFFERED)
    val receiveEvents: ReceiveChannel<GattEvent> = events

    private fun sendOrThrow(event: GattEvent) {
        val result = events.trySend(event)
        if (!result.isSuccess) {
            throw IllegalStateException("Overflow or closed output: $result")
        }
    }

    override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        val phyUpdate = PhyUpdate(txPhy, rxPhy, status)
        sendOrThrow(phyUpdate)
    }

    override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        val phyRead = PhyRead(txPhy, rxPhy, status)
        sendOrThrow(phyRead)
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        val connectionChanged =
            ConnectionChanged(status, ConnectionState.fromState(newState))
        sendOrThrow(connectionChanged)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        val servicesDiscovered = ServicesDiscovered(status)
        sendOrThrow(servicesDiscovered)
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
        sendOrThrow(characteristicRead)
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
        sendOrThrow(characteristicWritten)
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
        sendOrThrow(characteristicChanged)
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
        sendOrThrow(descriptorRead)
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
        sendOrThrow(descriptorWritten)
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
        sendOrThrow(characteristicRead)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val characteristicChanged = CharacteristicChanged(
            GattCharacteristic(characteristic),
            characteristic.value
        )
        sendOrThrow(characteristicChanged)
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
        sendOrThrow(descriptorRead)
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
        val reliableWriteCompleted = ReliableWriteCompleted(status)
        sendOrThrow(reliableWriteCompleted)
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
        val readRemoteRssi = ReadRemoteRssi(rssi, status)
        sendOrThrow(readRemoteRssi)
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        val mtuChanged = MtuChanged(mtu, status)
        sendOrThrow(mtuChanged)
    }

    override fun onServiceChanged(gatt: BluetoothGatt) {
        val serviceChanged = ServiceChanged
        sendOrThrow(serviceChanged)
    }
}
