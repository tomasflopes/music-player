package dev.tomasflopes.musicplayer.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.tomasflopes.musicplayer.core.artistimage.AlbumCollageSource
import dev.tomasflopes.musicplayer.core.artistimage.ArtistCovers
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageCandidates
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageChain
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageEditor
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageLookupCache
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageOverrideStore
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageResolver
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageSource
import dev.tomasflopes.musicplayer.core.artistimage.ArtistNameMatcher
import dev.tomasflopes.musicplayer.core.artistimage.ArtistPhotoSettings
import dev.tomasflopes.musicplayer.core.artistimage.DeezerClient
import dev.tomasflopes.musicplayer.core.artistimage.DeezerSource
import dev.tomasflopes.musicplayer.core.artistimage.InitialsSource
import dev.tomasflopes.musicplayer.core.artistimage.LocalImageFiles
import dev.tomasflopes.musicplayer.core.artistimage.OnlinePolicy
import dev.tomasflopes.musicplayer.core.artistimage.TimeSource
import dev.tomasflopes.musicplayer.core.artistimage.UserChoiceSource
import dev.tomasflopes.musicplayer.ui.art.ArtistImageHost
import dev.tomasflopes.musicplayer.ui.art.GalleryImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import java.time.Clock
import javax.inject.Singleton

private const val PHOTO_CACHE_BYTES = 50L * 1024 * 1024

/**
 * Artist photos (ADR 0028-0030). One `@IntoSet` line per source is the whole registration; the chain sorts them
 * by priority, so nothing here depends on the order of the lines.
 */
@Module
@InstallIn(SingletonComponent::class)
object ArtistImageModule {
    @Provides
    @IntoSet
    fun userChoice(
        store: ArtistImageOverrideStore,
        policy: OnlinePolicy,
    ): ArtistImageSource = UserChoiceSource(store, policy)

    @Provides
    @IntoSet
    fun deezer(
        client: DeezerClient,
        cache: ArtistImageLookupCache,
        policy: OnlinePolicy,
        matcher: ArtistNameMatcher,
        time: TimeSource,
    ): ArtistImageSource = DeezerSource(client, cache, policy, matcher, time)

    @Provides
    @IntoSet
    fun albumCollage(covers: ArtistCovers): ArtistImageSource = AlbumCollageSource(covers)

    @Provides
    @IntoSet
    fun initials(): ArtistImageSource = InitialsSource()

    @Provides
    @Singleton
    fun chain(sources: Set<@JvmSuppressWildcards ArtistImageSource>) = ArtistImageChain(sources)

    /** Answers are forgotten when the setting changes, so Off takes effect on the rows already on screen. */
    @Provides
    @Singleton
    fun resolver(
        chain: ArtistImageChain,
        settings: ArtistPhotoSettings,
    ): ArtistImageResolver {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return ArtistImageResolver(chain, scope).also { resolver ->
            scope.launch { settings.mode.drop(1).collect { resolver.invalidateAll() } }
        }
    }

    @Provides
    @Singleton
    fun editor(
        store: ArtistImageOverrideStore,
        files: LocalImageFiles,
        resolver: ArtistImageResolver,
    ) = ArtistImageEditor(store, files, resolver)

    @Provides
    @Singleton
    fun candidates(
        client: DeezerClient,
        policy: OnlinePolicy,
    ) = ArtistImageCandidates(client, policy)

    @Provides
    @Singleton
    fun galleryImageStore(
        @ApplicationContext context: Context,
        clock: Clock,
    ) = GalleryImageStore(context, clock)

    @Provides
    fun localImageFiles(store: GalleryImageStore): LocalImageFiles = store

    /** Downloads photos with OkHttp and keeps them in a 50 MB disk cache that the system may clear (ADR 0030). */
    @Provides
    @Singleton
    fun photoLoader(
        @ApplicationContext context: Context,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve("artist_photos").toOkioPath())
                    .maxSizeBytes(PHOTO_CACHE_BYTES)
                    .build()
            }.crossfade(true)
            .build()

    @Provides
    @Singleton
    fun host(
        resolver: ArtistImageResolver,
        loader: ImageLoader,
        gallery: GalleryImageStore,
    ) = ArtistImageHost(resolver, loader, gallery)
}
