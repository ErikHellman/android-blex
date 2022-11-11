package se.hellsoft.blex

import android.bluetooth.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.*

typealias GattServices = List<Pair<UUID, List<Pair<UUID, List<UUID>>>>>

fun mockDevice(hwAddress: String, gattServices: GattServices): BluetoothDevice {
    val uuidSlot = slot<UUID>()
    val callbackSlot = slot<GattCallback>()
    val byteArraySlot = slot<ByteArray>()
    val characteristicSlot = slot<BluetoothGattCharacteristic>()

    val gatt = mockk<BluetoothGatt>(relaxed = true) {
        every { writeCharacteristic(any(), any(), any()) }
        every { readCharacteristic(capture(characteristicSlot)) } coAnswers {
            val char = characteristicSlot.captured
            (callbackSlot.captured.events as MutableSharedFlow<GattEvent>).tryEmit(
                CharacteristicRead(
                    char.service.uuid,
                    char.uuid,
                    ByteArray(20),
                    BluetoothGatt.GATT_SUCCESS
                )
            )
        }
        every { services } returns gattServices.map { mockService(it.first, it.second) }
        every { setCharacteristicNotification(any(), any()) } returns true
    }

    return mockk(relaxed = true) {
        every {
            connectGatt(
                any(),
                any(),
                capture(callbackSlot),
                any(),
                any()
            )
        } coAnswers {
            gatt
        }
        every { address } returns hwAddress
    }
}

fun mockService(id: UUID, serviceIds: List<Pair<UUID, List<UUID>>>) =
    mockk<BluetoothGattService>(relaxed = true) {
        every { uuid } returns id
        every { characteristics } returns serviceIds.map {
            mockCharacteristic(
                this,
                it.first,
                it.second
            )
        }
    }

fun mockCharacteristic(gattService: BluetoothGattService, id: UUID, descriptorIds: List<UUID>) =
    mockk<BluetoothGattCharacteristic>(relaxed = true) {
        every { service } returns gattService
        every { uuid } returns id
        every { descriptors } returns descriptorIds.map { mockDescriptor(this, it) }
    }

fun mockDescriptor(gattCharacteristic: BluetoothGattCharacteristic, id: UUID) =
    mockk<BluetoothGattDescriptor>(relaxed = true) {
        every { uuid } returns id
        every { characteristic } returns gattCharacteristic
        every { value } returns ByteArray(20)
    }