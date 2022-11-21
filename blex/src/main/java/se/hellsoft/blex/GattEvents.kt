package se.hellsoft.blex

import android.bluetooth.BluetoothProfile
import java.util.UUID

enum class ConnectionState(val state: Int) {
    Connected(BluetoothProfile.STATE_CONNECTED),
    Connecting(BluetoothProfile.STATE_CONNECTING),
    Disconnected(BluetoothProfile.STATE_DISCONNECTED),
    Disconnecting(BluetoothProfile.STATE_DISCONNECTING);

    companion object {
        fun fromState(state: Int): ConnectionState {
            return when (state) {
                BluetoothProfile.STATE_CONNECTED -> Connected
                BluetoothProfile.STATE_CONNECTING -> Connecting
                BluetoothProfile.STATE_DISCONNECTED -> Disconnected
                BluetoothProfile.STATE_DISCONNECTING -> Disconnecting
                else -> throw IllegalArgumentException("Illegal connection state $state")
            }
        }
    }
}

sealed class GattEvent

data class ConnectionChanged(
    val status: Int,
    val newState: ConnectionState,
) : GattEvent()

data class ServicesDiscovered(val status: Int) : GattEvent()

data class CharacteristicChanged(
    val characteristics: GattCharacteristic,
    val value: ByteArray
) : GattEvent() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CharacteristicChanged

        if (characteristics != other.characteristics) return false
        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = characteristics.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}

data class CharacteristicRead(
    val characteristic: GattCharacteristic,
    val value: ByteArray,
    val status: Int
) : GattEvent() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CharacteristicRead

        if (characteristic != other.characteristic) return false
        if (!value.contentEquals(other.value)) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        var result = characteristic.hashCode()
        result = 31 * result + value.contentHashCode()
        result = 31 * result + status
        return result
    }
}

data class CharacteristicWritten(
    val characteristic: GattCharacteristic,
    val status: Int
) : GattEvent()

data class DescriptorRead(
    val characteristic: GattCharacteristic,
    val descriptor: GattDescriptor,
    val value: ByteArray,
    val status: Int
) : GattEvent() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DescriptorRead

        if (descriptor != other.descriptor) return false
        if (!value.contentEquals(other.value)) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        var result = descriptor.hashCode()
        result = 31 * result + value.contentHashCode()
        result = 31 * result + status
        return result
    }
}

data class DescriptorWritten(
    val characteristic: GattCharacteristic,
    val descriptorId: UUID,
    val status: Int
) : GattEvent()

data class MtuChanged(val mtu: Int, val status: Int) : GattEvent()
data class PhyRead(val txPhy: Int, val rxPhy: Int, val status: Int) : GattEvent()
data class PhyUpdate(val txPhy: Int, val rxPhy: Int, val status: Int) : GattEvent()
data class ReadRemoteRssi(val rssi: Int, val status: Int) : GattEvent()
data class ReliableWriteCompleted(val status: Int) : GattEvent()
object ServiceChanged : GattEvent()
