package com.dopdop.bopdop.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.dopdop.bopdop.data.LibraryStore
import com.google.common.collect.ImmutableList

/**
 * Like, shuffle and repeat in the notification, on the lock screen and in the system media
 * controls (F43). Each is a custom session command; the buttons are rebuilt whenever the song,
 * its like, or shuffle/repeat change, so their icons always show the current state.
 */
@OptIn(UnstableApi::class)
internal object NotificationButtons {
    val LIKE = SessionCommand("bopdop.like", Bundle.EMPTY)
    val SHUFFLE = SessionCommand("bopdop.shuffle", Bundle.EMPTY)
    val REPEAT = SessionCommand("bopdop.repeat", Bundle.EMPTY)
    val all = listOf(LIKE, SHUFFLE, REPEAT)

    fun buttons(p: Player): ImmutableList<CommandButton> {
        val t = p.currentMediaItem?.toTrack()
        val liked = t != null && LibraryStore.isLiked(t.id)
        val like = CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if (liked) "Remove from Liked" else "Like")
            .setSessionCommand(LIKE).setSlots(CommandButton.SLOT_OVERFLOW).build()
        val shuffle = CommandButton.Builder(if (p.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName(if (p.shuffleModeEnabled) "Shuffle on" else "Shuffle off")
            .setSessionCommand(SHUFFLE).setSlots(CommandButton.SLOT_OVERFLOW).build()
        val repeat = CommandButton.Builder(when (p.repeatMode) {
            Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
            Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
            else -> CommandButton.ICON_REPEAT_OFF
        }).setDisplayName(when (p.repeatMode) { Player.REPEAT_MODE_ONE -> "Repeat one"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat off" })
            .setSessionCommand(REPEAT).setSlots(CommandButton.SLOT_OVERFLOW).build()
        return if (t == null) ImmutableList.of() else ImmutableList.of(like, shuffle, repeat)
    }

    /** Runs a button press; true when it was one of ours. */
    fun handle(p: Player, command: SessionCommand): Boolean {
        when (command.customAction) {
            LIKE.customAction -> p.currentMediaItem?.toTrack()?.let { LibraryStore.toggleLike(it) }
            SHUFFLE.customAction -> p.shuffleModeEnabled = !p.shuffleModeEnabled
            REPEAT.customAction -> p.repeatMode = when (p.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            else -> return false
        }
        return true
    }

    fun connect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().apply { all.forEach(::add) }.build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(commands)
            .setMediaButtonPreferences(buttons(session.player))
            .build()
    }

    fun result(ok: Boolean) = SessionResult(if (ok) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_NOT_SUPPORTED)
}
