package com.cameron.motionpy

/**
 * These are the commands that are sent
 * to the Raspberry Pi
 **/
object Commands {
    const val START = "start"
    /**
     * This will pause the motion sensor on the Pi
     * but will keep the program running
     * */
    const val PAUSE = "pause"

    /**
     * This completely stops the sensor on the Pi
     * and exits the program
     * */
    const val STOP = "stop"
    const val POWER_OFF = "power_off"
}