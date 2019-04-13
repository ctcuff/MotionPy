package com.cameron.motionpy

data class Entry(val id: String?, val time: String?, val url: String?) {
    override fun toString(): String = "Entry: {id=$id, time=$time, url=$url}"
}