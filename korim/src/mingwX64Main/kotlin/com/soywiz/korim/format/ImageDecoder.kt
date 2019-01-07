package com.soywiz.korim.format

import com.soywiz.korim.bitmap.*
import com.soywiz.korim.color.*
import com.soywiz.korim.vector.*
import com.soywiz.korio.file.*
import kotlinx.cinterop.*
import platform.gdiplus.*
import platform.windows.*

actual val nativeImageFormatProvider: NativeImageFormatProvider = object : BaseNativeNativeImageFormatProvider() {
    override suspend fun decode(data: ByteArray): NativeImage = wrapNative(decodeImageSync(data))
}

private var initializedGdiPlus = false
private fun initGdiPlusOnce() {
    if (initializedGdiPlus) return
    initializedGdiPlus = true
    memScoped {
        val ptoken = allocArray<ULONG_PTRVar>(1)
        val si = alloc<GdiplusStartupInput>().apply {
            GdiplusVersion = 1.convert()
            DebugEventCallback = null
            SuppressExternalCodecs = FALSE
            SuppressBackgroundThread = FALSE
        }
        GdiplusStartup(ptoken, si.ptr, null)
    }
}

private fun decodeImageSync(data: ByteArray): Bitmap32 = memScoped {
    val width = alloc<FloatVar>()
    val height = alloc<FloatVar>()
    val pimage = allocArray<COpaquePointerVar>(1)

    initGdiPlusOnce()
    data.usePinned { datap ->
        val pdata = datap.addressOf(0)
        val pstream = SHCreateMemStream(pdata.reinterpret(), data.size.convert())!!
        try {
            if (GdipCreateBitmapFromStream(pstream, pimage) != 0.convert()) {
                throw RuntimeException("Can't load image from byte array")
            }
        } finally {
            pstream.pointed.lpVtbl?.pointed?.Release?.invoke(pstream)
        }
    }

    GdipGetImageDimension(pimage[0], width.ptr, height.ptr)

    val rect = alloc<GpRect>().apply {
        X = 0
        Y = 0
        Width = width.value.toInt()
        Height = height.value.toInt()
    }
    val bmpData = alloc<BitmapData>()
    if (GdipBitmapLockBits(pimage[0], rect.ptr.reinterpret(), ImageLockModeRead, PixelFormat32bppARGB, bmpData.ptr.reinterpret()) != 0.convert()) {
        throw RuntimeException("Can't lock image")
    }

    val bmpWidth = bmpData.Width.toInt()
    val bmpHeight = bmpData.Height.toInt()
    val out = IntArray((bmpWidth * bmpHeight).toInt())
    out.usePinned { outp ->
        val o = outp.addressOf(0)
        for (y in 0 until bmpHeight) {
            memcpy(o.reinterpret<IntVar>(), (bmpData.Scan0.toLong() + (bmpData.Stride * y)).toCPointer<IntVar>(), (bmpData.Width * 4).convert())
        }
    }

    GdipBitmapUnlockBits(pimage[0], bmpData.ptr)
    GdipDisposeImage(pimage[0])

    //println(out.toList())
    Bitmap32(bmpWidth, bmpHeight, out, premult = false)
}
