package com.vllenin.icamera.camera

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.NonNull
import androidx.collection.ArrayMap
import androidx.collection.SparseArrayCompat
import com.vllenin.icamera.camera.CameraUtils.CalculationSize.IMAGE
import java.util.*

/**
 * Created by Vllenin on 2020-03-28.
 */
object CameraUtils {

  enum class CalculationSize {
    PREVIEW,
    IMAGE
  }

  private const val MAX_HEIGHT_OUTPUT_IMAGE = 1080
  private const val MAX_WIDTH_OUTPUT_IMAGE = 1920

  fun calculationSize(map: StreamConfigurationMap, calculationSize: CalculationSize): Size {
    val supportedPreviewSizes = TreeSet<Size>()
    val previewSizeMap = SizeMap()
    for (size in map.getOutputSizes(SurfaceTexture::class.java)) {
      val s = Size(size.width, size.height)
      supportedPreviewSizes.add(s)
      previewSizeMap.add(s)
    }
    val imageSizeMap = SizeMap()
    for (size in map.getOutputSizes(ImageFormat.JPEG)) {
      val s = Size(size.width, size.height)
      imageSizeMap.add(s)
    }

    val aspectRatio = AspectRatio.of(16, 9)
    var preferred: Size
    val sizesWithAspectRatio = imageSizeMap.sizes(aspectRatio)
    if (sizesWithAspectRatio.size > 0) {
      preferred = sizesWithAspectRatio.last()
      for (aSize in sizesWithAspectRatio) {
        if (aSize.width * aSize.height <= MAX_HEIGHT_OUTPUT_IMAGE * MAX_WIDTH_OUTPUT_IMAGE) {
          preferred = aSize
        } else {
          break
        }
      }
    } else {
      preferred = imageSizeMap.defaultSize()
    }
    if (calculationSize == IMAGE) {
      return preferred
    }

    val surfaceLonger: Int
    val surfaceShorter: Int
    val surfaceWidth = preferred.width
    val surfaceHeight = preferred.height
    if (surfaceWidth < surfaceHeight) {
      surfaceLonger = surfaceHeight
      surfaceShorter = surfaceWidth
    } else {
      surfaceLonger = surfaceWidth
      surfaceShorter = surfaceHeight
    }

    val preferredAspectRatio = AspectRatio.of(surfaceLonger, surfaceShorter)
    // Pick the smallest of those big enough
    for (size in supportedPreviewSizes) {
      if (preferredAspectRatio.matches(size) && size.width >= surfaceLonger && size.height >= surfaceShorter
      ) {
        return size
      }
    }

    // If no size is big enough, pick the largest one which matches the ratio.
    val matchedSizes = previewSizeMap.sizes(preferredAspectRatio)
    return if (matchedSizes.size > 0) {
      matchedSizes.last()
    } else supportedPreviewSizes.last()
  }

  /**********************************************************************************************/

  class AspectRatio constructor(private val x: Int, private val y: Int) : Comparable<AspectRatio>,
    Parcelable {

    fun matches(size: Size): Boolean {
      val gcd = gcd(size.width, size.height)
      val x = size.width / gcd
      val y = size.height / gcd
      return this.x == x && this.y == y
    }

    override fun equals(other: Any?): Boolean {
      if (other == null) {
        return false
      }
      if (this === other) {
        return true
      }
      if (other is AspectRatio) {
        val ratio = other as AspectRatio?
        return x == ratio!!.x && y == ratio.y
      }
      return false
    }

    override fun toString(): String {
      return "$x:$y"
    }

    private fun toFloat(): Float {
      return x.toFloat() / y
    }

    override fun hashCode(): Int {
      // assuming most sizes are <2^16, doing background_dialog rotate will give us perfect hashing
      return y xor (x shl Integer.SIZE / 2 or x.ushr(Integer.SIZE / 2))
    }

    override fun compareTo(@NonNull other: AspectRatio): Int {
      if (equals(other)) {
        return 0
      } else if (toFloat() - other.toFloat() > 0) {
        return 1
      }
      return -1
    }

    /**
     * @return The inverse of this [AspectRatio].
     */
    fun inverse(): AspectRatio {

      return of(y, x)
    }

    override fun describeContents(): Int {
      return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeInt(x)
      dest.writeInt(y)
    }

    companion object {

      private val sCache = SparseArrayCompat<SparseArrayCompat<AspectRatio>>(16)

      /**
       * Returns an instance of [AspectRatio] specified by `width` and `height` values.
       * The values `width` and `` will be reduced by their greatest common divider.
       *
       * @param width The width
       * @param height The height
       * @return An instance of [AspectRatio]
       */
      fun of(width: Int, height: Int): AspectRatio {
        var x = width
        var y = height
        val gcd = gcd(x, y)
        x /= gcd
        y /= gcd
        var arrayX = sCache.get(x)
        return if (arrayX == null) {
          val ratio = AspectRatio(x, y)
          arrayX = SparseArrayCompat()
          arrayX.put(y, ratio)
          sCache.put(x, arrayX)
          ratio
        } else {
          var ratio = arrayX.get(y)
          if (ratio == null) {
            ratio = AspectRatio(x, y)
            arrayX.put(y, ratio)
          }
          ratio
        }
      }

      fun of(size: Size): AspectRatio {
        return of(size.width, size.height)
      }

      /**
       * Parse an [AspectRatio] from background_dialog [String] formatted like "4:3".
       *
       * @param s The string representation of the aspect ratio
       * @return The aspect ratio
       * @throws IllegalArgumentException when the format is incorrect.
       */
      fun parse(s: String): AspectRatio {
        val position = s.indexOf(':')
        require(position != -1) { "Malformed aspect ratio: $s" }
        try {
          val x = Integer.parseInt(s.substring(0, position))
          val y = Integer.parseInt(s.substring(position + 1))
          return of(x, y)
        } catch (e: NumberFormatException) {
          throw IllegalArgumentException("Malformed aspect ratio: $s", e)
        }

      }

      private fun gcd(width: Int, height: Int): Int {
        var a = width
        var b = height
        while (b != 0) {
          val c = b
          b = a % b
          a = c
        }
        return a
      }

      @JvmField
      val CREATOR: Parcelable.Creator<AspectRatio> = object : Parcelable.Creator<AspectRatio> {

        override fun createFromParcel(source: Parcel): AspectRatio {
          val x = source.readInt()
          val y = source.readInt()
          return of(x, y)
        }

        override fun newArray(size: Int): Array<AspectRatio?> {
          return arrayOfNulls(size)
        }
      }
    }
  }


  class SizeMap {

    private val mRatios = ArrayMap<AspectRatio, SortedSet<Size>>()

    val isEmpty: Boolean
      get() = mRatios.isEmpty

    /**
     * Add background_dialog new [Size] to this collection.
     *
     * @param size The size to add.
     * @return `true` if it is added, `false` if it already exists and is not added.
     */
    fun add(size: Size): Boolean {
      for (ratio in mRatios.keys) {
        if (ratio.matches(size)) {
          val sizes = mRatios[ratio] as SortedSet<Size>
          return if (sizes.contains(size)) {
            false
          } else {
            sizes.add(size)
            true
          }
        }
      }
      // None of the existing ratio matches the provided size; add background_dialog new key
      val sizes = TreeSet<Size>()
      sizes.add(size)
      mRatios[AspectRatio.of(size.width, size.height)] = sizes
      return true
    }

    /**
     * Removes the specified aspect ratio and all sizes associated with it.
     *
     * @param ratio The aspect ratio to be removed.
     */
    fun remove(ratio: AspectRatio) {
      mRatios.remove(ratio)
    }

    fun ratios(): Set<AspectRatio> {
      return mRatios.keys
    }

    fun sizes(ratio: AspectRatio): SortedSet<Size> {
      return mRatios[ratio]!!
    }

    fun defaultSize(): Size {
      return mRatios[AspectRatio.parse("4:3")]!!.last()
    }

    fun clear() {
      mRatios.clear()
    }

  }

  class Size
  internal constructor(val width: Int, val height: Int) : Comparable<Size> {

    private val areaSize: Int
      get() = width * height

    override fun equals(o: Any?): Boolean {
      if (o == null) {
        return false
      }
      if (this === o) {
        return true
      }
      if (o is Size) {
        val size = o as Size?
        return width == size!!.width && height == size.height
      }
      return false
    }

    override fun toString(): String {
      return width.toString() + "x" + height
    }

    override fun hashCode(): Int {
      // assuming most sizes are <2^16, doing background_dialog rotate will give us perfect hashing
      return height xor (width shl Integer.SIZE / 2 or width.ushr(Integer.SIZE / 2))
    }

    override fun compareTo(@NonNull another: Size): Int {
      val area = areaSize
      val anotherArea = another.areaSize
      return if (area != anotherArea) {
        area - anotherArea
      } else {
        width - another.width
      }
    }
  }
}


