package se.hellsoft.blex

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import java.util.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
class GattService internal constructor(internal val bluetoothGattService: BluetoothGattService) {
    val instanceId = bluetoothGattService.instanceId
    val id: UUID = bluetoothGattService.uuid
    val characteristics: List<GattCharacteristic> = bluetoothGattService.characteristics.map { GattCharacteristic(it) }
    val serviceType = bluetoothGattService.type
    val services =  bluetoothGattService.includedServices.map { GattService(it) }
}

@Suppress("unused")
class GattCharacteristic internal constructor(internal val bluetoothGattCharacteristic: BluetoothGattCharacteristic) {
    val instanceId = bluetoothGattCharacteristic.instanceId
    val id: UUID = bluetoothGattCharacteristic.uuid
    val permissions = bluetoothGattCharacteristic.permissions
    val properties = bluetoothGattCharacteristic.properties
    val descriptors = bluetoothGattCharacteristic.descriptors.map { GattDescriptor(it) }
}

@Suppress("unused")
class GattDescriptor internal constructor(internal val bluetoothGattDescriptor: BluetoothGattDescriptor) {
    val id: UUID = bluetoothGattDescriptor.uuid
    val permissions = bluetoothGattDescriptor.permissions
}
