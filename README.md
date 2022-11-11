# A wrapper for the Bluetooth GATT system API on Android

This library wraps the system API for BLE (`BluetoothGatt` etc.) in a more convenient and safer API
for use in apps. It solves the common problems one regularly have with BLE on Android, like race
conditions, queueing commands, and blocking of binder threads.
