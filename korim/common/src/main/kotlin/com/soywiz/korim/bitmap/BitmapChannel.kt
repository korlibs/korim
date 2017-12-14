package com.soywiz.korim.bitmap

enum class BitmapChannel(val index: Int) {
	RED(0), GREEN(1), BLUE(2), ALPHA(3);

	val shift = index * 8
	val setMask = (0xFF shl shift)
	val clearMask = setMask.inv()

	fun extract(rgba: Int): Int = (rgba ushr shift) and 0xFF
	fun insert(rgba: Int, value: Int): Int = (rgba and clearMask) or ((value and 0xFF) shl shift)

	companion object {
		val ALL = values()
		operator fun get(index: Int) = ALL[index]
	}
}

val BitmapChannel.Companion.Y get() = BitmapChannel.RED
val BitmapChannel.Companion.Cb get() = BitmapChannel.GREEN
val BitmapChannel.Companion.Cr get() = BitmapChannel.BLUE
val BitmapChannel.Companion.A get() = BitmapChannel.ALPHA
