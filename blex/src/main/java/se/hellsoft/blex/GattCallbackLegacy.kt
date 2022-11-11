package se.hellsoft.blex

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import kotlinx.coroutines.flow.MutableSharedFlow

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class GattCallbackLegacy : GattCallback() {
    private val mutableEvents = events as MutableSharedFlow<GattEvent>

    override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        mutableEvents.tryEmit(PhyUpdate(txPhy, rxPhy, status))
    }

    override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        mutableEvents.tryEmit(PhyRead(txPhy, rxPhy, status))
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        val event = ConnectionChanged(status, ConnectionState.fromState(newState))
        mutableEvents.tryEmit(event)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        mutableEvents.tryEmit(ServicesDiscovered(status))
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        mutableEvents.tryEmit(
            CharacteristicWritten(
                characteristic.service.uuid,
                characteristic.uuid,
                status
            )
        )
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        mutableEvents.tryEmit(
            CharacteristicRead(
                characteristic.service.uuid,
                characteristic.uuid,
                characteristic.value,
                status
            )
        )
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        mutableEvents.tryEmit(
            CharacteristicChanged(
                characteristic.service.uuid,
                characteristic.uuid,
                characteristic.value
            )
        )
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        mutableEvents.tryEmit(
            DescriptorRead(
                descriptor.characteristic.service.uuid,
                descriptor.characteristic.uuid,
                descriptor.uuid,
                descriptor.value,
                status
            )
        )
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        mutableEvents.tryEmit(
            DescriptorWritten(
                descriptor.characteristic.service.uuid,
                descriptor.characteristic.uuid,
                descriptor.uuid,
                status
            )
        )
    }

//    override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
//        mutableEvents.tryEmit(ReliableWriteCompleted(status))
//    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
        mutableEvents.tryEmit(ReadRemoteRssi(rssi, status))
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        mutableEvents.tryEmit(MtuChanged(mtu, status))
    }

    override fun onServiceChanged(gatt: BluetoothGatt) {
        mutableEvents.tryEmit(ServiceChanged)
    }
}