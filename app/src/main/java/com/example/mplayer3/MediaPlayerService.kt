package com.example.mplayer3

import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import java.io.IOException

class MediaPlayerService : Service(), MediaPlayer.OnCompletionListener,
    MediaPlayer.OnPreparedListener,
    MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnInfoListener,
    MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {

    private var mediaPlayer: MediaPlayer? = null

    //Used to pause/resume MediaPlayer
    private var resumePosition = 0
    private var audioManager: AudioManager? = null

    // Binder given to clients
    private val iBinder: IBinder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder {
        return iBinder
    }

    inner class LocalBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

    private var isPrepared = false
    private var audioList: ArrayList<Audio> = arrayListOf()
    private var audioPathList: ArrayList<String> = arrayListOf()
    private var currentIndex = 0

    override fun onCreate() {
        super.onCreate()

        loadAudio()
        initMediaPlayer()
    }

    //The system calls this method when an activity, requests the service be started
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        //Request audio focus
        if (requestAudioFocus() == false) {
            //Could not gain focus
            stopSelf()
        }

        initMediaPlayer()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer != null) {
            stopMedia()
            mediaPlayer!!.release()
        }
        removeAudioFocus()
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()
        //Set up MediaPlayer event listeners
        mediaPlayer!!.setOnCompletionListener(this)
        mediaPlayer!!.setOnErrorListener(this)
        mediaPlayer!!.setOnPreparedListener(this)
        mediaPlayer!!.setOnBufferingUpdateListener(this)
        mediaPlayer!!.setOnSeekCompleteListener(this)
        mediaPlayer!!.setOnInfoListener(this)

        prepareNewAudio()
    }

    private fun prepareNewAudio() {
        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer!!.reset()
        try {
            // Set the data source to the mediaFile location
            if (audioList.size != 0) {
                mediaPlayer!!.setDataSource(audioList.get(currentIndex).data)
                mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
                mediaPlayer!!.prepareAsync()
            }
        } catch (e: NullPointerException) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun loadAudio() {
        val contentResolver = contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0"
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"
        val cursor = contentResolver.query(uri, null, selection, null, sortOrder)
        if (cursor != null && cursor.count > 0) {
            audioList = ArrayList()
            while (cursor.moveToNext()) {
                val data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                val title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                val album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                val artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))

                if (getDuration(data) > 30) {
                    // Save to audioList
                    audioList!!.add(Audio(data, title, album, artist))
                    audioPathList.add(data)
                }
            }
        }
        cursor!!.close()
    }

    private fun getDuration(data: String): Int {
        val uri: Uri = Uri.parse(data)
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(applicationContext, uri)
        val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val millSecond = durationStr!!.toInt()

        return millSecond / 1000
    }

    private fun setDatasourceToMediaPlayer(data: String) {
        try {
            // Set the data source to the mediaFile location
            mediaPlayer!!.setDataSource(data)
        } catch (e: IOException) {
            e.printStackTrace()
            stopSelf()
        }
        mediaPlayer!!.prepareAsync()
    }

    fun isPlaying(): Boolean {
        return mediaPlayer!!.isPlaying
    }

    fun getCurrentAudioTitle(): String {
        var title = "Нет подходящих аудио"
        try {
            title = audioList.get(currentIndex).title
        } catch (e: Exception) {
            Log.e("MediaPlayerService", e.printStackTrace().toString())
        }
        return title
    }

    fun playMedia() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.start()
        }
    }

    private fun stopMedia() {
        if (mediaPlayer == null) return
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.stop()
        }
    }

    fun pauseMedia() {
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.pause()
            resumePosition = mediaPlayer!!.currentPosition
        }
    }

    fun resumeMedia() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.seekTo(resumePosition)
            mediaPlayer!!.start()
        }
    }

    fun playNextMedia() {
        if (currentIndex == audioList.size - 1) {
            currentIndex = 0
        } else {
            currentIndex++
        }

        prepareNewAudio()
    }

    fun playPreviousMedia() {
        if (currentIndex == 0) {
            currentIndex = audioList.size - 1
        } else {
            currentIndex--
        }

        prepareNewAudio()
    }

    override fun onCompletion(mp: MediaPlayer?) {
        //Invoked when playback of a media source has completed.
        stopMedia()
        //stop the service
        stopSelf()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        //Invoked when the media source is ready for playback.
        isPrepared = true
    }

    //Handle errors
    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        //Invoked when there has been an error during an asynchronous operation
        when (what) {
            MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> Log.d(
                "MediaPlayer Error",
                "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK $extra"
            )

            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> Log.d(
                "MediaPlayer Error",
                "MEDIA ERROR SERVER DIED $extra"
            )

            MediaPlayer.MEDIA_ERROR_UNKNOWN -> Log.d(
                "MediaPlayer Error",
                "MEDIA ERROR UNKNOWN $extra"
            )
        }
        return false
    }

    override fun onSeekComplete(p0: MediaPlayer?) {
        TODO("Not yet implemented")
    }

    override fun onInfo(p0: MediaPlayer?, p1: Int, p2: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun onBufferingUpdate(p0: MediaPlayer?, p1: Int) {
        TODO("Not yet implemented")
    }

    override fun onAudioFocusChange(focusState: Int) {
        //Invoked when the audio focus of the system is updated.
        when (focusState) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // resume playback
                if (mediaPlayer == null) initMediaPlayer() else if (!mediaPlayer!!.isPlaying) mediaPlayer!!.start()
                mediaPlayer!!.setVolume(1.0f, 1.0f)
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer!!.isPlaying) mediaPlayer!!.stop()
                mediaPlayer!!.release()
                mediaPlayer = null
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (mediaPlayer!!.isPlaying) mediaPlayer!!.pause()

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer!!.isPlaying) mediaPlayer!!.setVolume(0.1f, 0.1f)
        }
    }

    private fun requestAudioFocus(): Boolean {
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val result = audioManager!!.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            true //Focus gained
        } else
            false //Could not gain focus
    }

    private fun removeAudioFocus(): Boolean {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                audioManager!!.abandonAudioFocus(this)
    }
}