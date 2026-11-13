package dev.tomasflopes.musicplayer.data.repo

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Folder
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.data.db.dao.AlbumRow
import dev.tomasflopes.musicplayer.data.db.dao.ArtistRow
import dev.tomasflopes.musicplayer.data.db.dao.FolderRow
import dev.tomasflopes.musicplayer.data.db.dao.SongRefRow
import dev.tomasflopes.musicplayer.data.db.dao.SongRow

internal fun SongRow.toSong() =
    Song(
        id = SongId(song.id),
        title = song.title,
        artistId = ArtistId(song.artistId),
        artistName = artistName,
        albumId = song.albumId?.let(::AlbumId),
        albumTitle = albumTitle,
        albumArtist = song.albumArtist,
        trackNumber = song.trackNumber,
        discNumber = song.discNumber,
        durationMs = song.durationMs,
        volumeName = song.volumeName,
        path = song.path,
        fileName = song.fileName,
        folderPath = song.folderPath,
        dateAddedEpochSec = song.dateAdded,
        contentUri = song.contentUri(),
        hasCover = song.hasCover,
    )

internal fun SongRefRow.toSongRef() = SongRef(SongId(id), ArtistId(artistId))

internal fun ArtistRow.toArtist() = Artist(ArtistId(id), name, songCount, albumCount)

internal fun AlbumRow.toAlbum() = Album(AlbumId(id), title, albumArtist, songCount, coverSongId?.let(::SongId))

internal fun FolderRow.toFolder() = Folder(path, name, parentPath, songCount)
