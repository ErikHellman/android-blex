package se.hellsoft.blex

import android.bluetooth.BluetoothProfile
import java.util.*

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
    val service: UUID,
    val characteristic: UUID,
    val value: ByteArray
) : GattEvent() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CharacteristicChanged

        if (service != other.service) return false
        if (characteristic != other.characteristic) return false
        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = service.hashCode()
        result = 31 * result + characteristic.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}

data class CharacteristicRead(
    val service: UUID,
    val characteristic: UUID,
    val value: ByteArray,
    val status: Int
) : GattEvent() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CharacteristicRead

        if (service != other.service) return false
        if (characteristic != other.characteristic) return false
        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = service.hashCode()
        result = 31 * result + characteristic.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}

data class CharacteristicWritten(
    val service: UUID,
    val characteristic: UUID,
    val status: Int
) : GattEvent()

data class DescriptorRead(
    val service: UUID,
    val characteristic: UUID,
    val descriptor: UUID,
    val value: ByteArray,
    val status: Int
) : GattEvent() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DescriptorRead

        if (service != other.service) return false
        if (characteristic != other.characteristic) return false
        if (descriptor != other.descriptor) return false
        if (!value.contentEquals(other.value)) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        var result = service.hashCode()
        result = 31 * result + characteristic.hashCode()
        result = 31 * result + descriptor.hashCode()
        result = 31 * result + value.contentHashCode()
        result = 31 * result + status
        return result
    }
}

data class DescriptorWritten(
    val service: UUID,
    val characteristic: UUID,
    val descriptor: UUID,
    val status: Int
) : GattEvent()

data class MtuChanged(val mtu: Int, val status: Int) : GattEvent()
data class PhyRead(val txPhy: Int, val rxPhy: Int, val status: Int) : GattEvent()
data class PhyUpdate(val txPhy: Int, val rxPhy: Int, val status: Int) : GattEvent()
data class ReadRemoteRssi(val rssi: Int, val status: Int) : GattEvent()
// TODO Figure out how to implement this...
//data class ReliableWriteCompleted(val status: Int) : GattEvent()
object ServiceChanged : GattEvent()

