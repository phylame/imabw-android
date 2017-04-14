package pw.phylame.support

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.support.v7.graphics.Palette
import com.bumptech.glide.DrawableTypeRequest
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.GenericLoaderFactory
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.stream.StreamModelLoader
import com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable
import com.bumptech.glide.load.resource.drawable.GlideDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import rx.Observable
import java.io.InputStream
import java.lang.Exception

object Images {
    fun decode(stream: InputStream, width: Int = -1, height: Int = -1): Bitmap {
        val bmp = BitmapFactory.decodeStream(stream)
        return if (width < 0 || height < 0) {
            bmp
        } else {
            ThumbnailUtils.extractThumbnail(bmp, width, height, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
        }
    }

    fun load(stream: InputStream, width: Int = -1, height: Int = -1): Observable<Bitmap> = Observable.create<Bitmap> {
        it.onNext(decode(stream, width, height))
        it.onCompleted()
    }

    fun glide(context: Context, stream: InputStream): DrawableTypeRequest<InputStream> = Glide.with(context)
            .using(InputStreamLoader())
            .load(stream)

    fun <T> consume(action: (Bitmap?) -> Unit): RequestListener<T, GlideDrawable> {
        return GlideBitmapConsumer(action)
    }

    fun <T> palette(action: (Palette?) -> Unit): RequestListener<T, GlideDrawable> {
        return consume { bmp ->
            if (bmp != null) {
                Palette.from(bmp).generate(action)
            } else {
                action(null)
            }
        }
    }

    private class GlideBitmapConsumer<T>(val action: (Bitmap?) -> Unit) : RequestListener<T, GlideDrawable> {
        override fun onResourceReady(resource: GlideDrawable,
                                     model: T,
                                     target: Target<GlideDrawable>?,
                                     isFromMemoryCache: Boolean,
                                     isFirstResource: Boolean): Boolean {
            action((resource as GlideBitmapDrawable).bitmap)
            return false
        }

        override fun onException(e: Exception?,
                                 model: T,
                                 target: Target<GlideDrawable>?,
                                 isFirstResource: Boolean): Boolean {
            action(null)
            return false
        }
    }
}

class InputStreamLoader() : StreamModelLoader<InputStream> {
    override fun getResourceFetcher(model: InputStream, width: Int, height: Int): DataFetcher<InputStream> {
        return InputStreamFetcher(model)
    }

    class InputStreamFetcher(val input: InputStream) : DataFetcher<InputStream> {
        override fun loadData(priority: Priority?): InputStream = input

        override fun getId(): String = input.hashCode().toString()

        override fun cancel() {}

        override fun cleanup() {
            input.close()
        }
    }

    class Factory : ModelLoaderFactory<InputStream, InputStream> {
        override fun build(context: Context, factories: GenericLoaderFactory): ModelLoader<InputStream, InputStream> {
            return InputStreamLoader()
        }

        override fun teardown() {}
    }
}
