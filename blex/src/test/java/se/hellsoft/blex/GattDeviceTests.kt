package se.hellsoft.blex

import android.bluetooth.BluetoothGatt
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

import org.junit.Assert.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class GattDeviceTests {
    @Test
    fun `Connect GattDevice emits ConnectionChanged`() = runTest(StandardTestDispatcher()) {
        val descriptors = listOf(GattDevice.ClientCharacteristicConfigurationID)
        val characteristics = listOf(UUID.randomUUID() to descriptors)
        val services = listOf(UUID.randomUUID() to characteristics)
        val eventFlow = MutableSharedFlow<GattEvent>()
            .onSubscription {
                emit(ConnectionChanged(BluetoothGatt.GATT_SUCCESS, ConnectionState.Connecting))
                emit(ConnectionChanged(BluetoothGatt.GATT_SUCCESS, ConnectionState.Connected))
            }
        val gattDevice = GattDevice(mockDevice("abc123", services), TestingCallbacks(eventFlow))
        val result = gattDevice.connect(mockk(relaxed = true))
        val (connecting, connected) = result.take(2).toList()
        assertEquals(ConnectionChanged(BluetoothGatt.GATT_SUCCESS, ConnectionState.Connecting), connecting)
        assertEquals(ConnectionChanged(BluetoothGatt.GATT_SUCCESS, ConnectionState.Connected), connected)
    }

    @Test
    fun `Write to characteristic emits CharacteristicWritten`() = runTest(StandardTestDispatcher()) {
        val descriptors = listOf(GattDevice.ClientCharacteristicConfigurationID)
        val characteristics = listOf(UUID.randomUUID() to descriptors)
        val services = listOf(UUID.randomUUID() to characteristics)
        val eventFlow = MutableSharedFlow<GattEvent>()
            .onSubscription {
                emit(ConnectionChanged(BluetoothGatt.GATT_SUCCESS, ConnectionState.Connecting))
                emit(ConnectionChanged(BluetoothGatt.GATT_SUCCESS, ConnectionState.Connected))
                emit(CharacteristicWritten(services[0].first, characteristics[0].first, BluetoothGatt.GATT_SUCCESS))
            }
        val gattDevice = GattDevice(mockDevice("abc123", services), TestingCallbacks(eventFlow))

        val connectResult = gattDevice.connect(mockk(relaxed = true))
        val (connecting, connected) = connectResult.take(2).toList()
        assertEquals(ConnectionChanged(BluetoothGatt.GATT_SUCCESS, ConnectionState.Connecting), connecting)
        assertEquals(ConnectionChanged(BluetoothGatt.GATT_SUCCESS, ConnectionState.Connected), connected)

        val result = gattDevice.writeCharacteristic(services[0].first, characteristics[0].first, ByteArray(5))
        assertEquals(CharacteristicWritten(services[0].first, characteristics[0].first, BluetoothGatt.GATT_SUCCESS), result)
    }


}

class TestingCallbacks(eventFlow: Flow<GattEvent>) : GattCallback() {
    override val events: Flow<GattEvent> = eventFlow
}