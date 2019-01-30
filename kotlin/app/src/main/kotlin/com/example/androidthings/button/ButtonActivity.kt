/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.button

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent

import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import com.google.android.things.pio.SpiDevice
import java.io.IOException
import kotlin.experimental.and
import android.os.AsyncTask



/**
 * Example of using Button driver for toggling a LED.
 *
 * This activity initialize an InputDriver to emit key events when the button GPIO pin state change
 * and flip the state of the LED GPIO pin.
 *
 * You need to connect an LED and a push button switch to pins specified in [BoardDefaults]
 * according to the schematic provided in the sample README.
 */

class ButtonActivity : Activity() {

    private lateinit var ledGpio: Gpio
    private lateinit var enablePin: Gpio
    private lateinit var directionPin: Gpio
    private lateinit var stepPin: Gpio
    private lateinit var buttonInputDriver: ButtonInputDriver
    private var SPI_DEVICE_NAME: String = "SPI3.0"

    private lateinit var mDevice: SpiDevice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Starting ButtonActivity")

        val pioService = PeripheralManager.getInstance()

        val deviceList = pioService.spiBusList
        if (deviceList.isEmpty()) {
            Log.i(TAG, "No SPI bus available on this device.")
        } else {
            Log.i(TAG, "List of available devices: $deviceList")
        }

        try {
            mDevice = pioService.openSpiDevice(SPI_DEVICE_NAME)
            configureSpiDevice(mDevice)
            //voltage on AIN is current reference -> REG_GCONF
            tmc_write(0x00.toByte(), byteArrayOf(0x00, 0x00, 0x00, 0x01))
            //IHOLD=0x10, IRUN=0x10 -> REG_IHOLD_IRUN
            tmc_write(0x10.toByte(), byteArrayOf(0x00, 0x00, 0x10, 0x10))
            //native 256 microsteps, MRES=0, TBL=1=24, TOFF=8 -> REG_CHOPCONF
            tmc_write(0x6C.toByte(), byteArrayOf(0x00, 0x00, -0x7F, 0x08))
        } catch (e: IOException) {
            Log.w(TAG, "Unable to access SPI device", e)
        }

        Log.i(TAG, "Configuring GPIO pins")
        ledGpio = pioService.openGpio(BoardDefaults.gpioForLED)
        ledGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        enablePin = pioService.openGpio("GPIO1_IO10")
        enablePin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH)

        directionPin = pioService.openGpio("GPIO6_IO12")
        directionPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        stepPin = pioService.openGpio("GPIO6_IO13")
        stepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        enablePin.value = false

        Log.i(TAG, "Registering button driver")
        // Initialize and register the InputDriver that will emit SPACE key events
        // on GPIO state changes.
        buttonInputDriver = ButtonInputDriver(
                BoardDefaults.gpioForButton,
                Button.LogicState.PRESSED_WHEN_LOW,
                KeyEvent.KEYCODE_SPACE)
        buttonInputDriver.register()

        AsyncTask.execute {
            while (true) {
                setLedValue(true)
                for (i in 1..100) {
                    stepPin.value = true
                    Thread.sleep(100)
                    stepPin.value = false
                    Thread.sleep(100)
                }
                setLedValue(false)
                Thread.sleep(1000)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            // Turn on the LED
            setLedValue(true)
            for (i in 1..100) {
                stepPin.value = true
                Thread.sleep(10)
                stepPin.value = false
                Thread.sleep(10)
            }

            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            // Turn off the LED
            setLedValue(false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Update the value of the LED output.
     */
    private fun setLedValue(value: Boolean) {
        Log.d(TAG, "Setting LED value to $value")
        ledGpio.value = value
    }

    override fun onStop() {
        buttonInputDriver.unregister()
        buttonInputDriver.close()

        ledGpio.close()

        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::mDevice.isInitialized) {
            try {
                mDevice.close()
            } catch (e: IOException) {
                Log.w(TAG, "Unable to close SPI device", e)
            }
        }
    }

    @Throws(IOException::class)
    fun configureSpiDevice(device: SpiDevice) {
        device.setMode(SpiDevice.MODE3)
        device.setFrequency(1000000)
        device.setBitsPerWord(8)
        device.setBitJustification(SpiDevice.BIT_JUSTIFICATION_MSB_FIRST)
    }

    fun tmc_write(cmd: Byte, data: ByteArray) {
        val cmdBytes: ByteArray = ByteArray(1)
        cmdBytes[0] = cmd and (0x01 shl 7).toByte()
        mDevice.write(cmdBytes, cmdBytes.size)

        mDevice.write(data.reversedArray(), data.size)
    }



    companion object {
        private val TAG = ButtonActivity::class.java.simpleName
    }
}
