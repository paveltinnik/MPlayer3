package com.example.mplayer3

import android.Manifest
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mplayer3.MediaPlayerService.LocalBinder
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private var mediaPlayerService: MediaPlayerService? = null
    var serviceBound = false

    lateinit var title: TextView
    lateinit var previousButton: MaterialButton
    lateinit var playButton: MaterialButton
    lateinit var nextButton: MaterialButton

    private val multiplePermissionId = 14
    private val multiplePermissionNameList = if (Build.VERSION.SDK_INT >= 33) {
        arrayListOf(
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = findViewById(R.id.tv_title)
        previousButton = findViewById(R.id.button_previous)
        playButton = findViewById(R.id.button_play)
        nextButton = findViewById(R.id.button_next)

        try {
            boundService()
        } catch (e: Exception) {
            Log.d("", "")
        }

        playButton.setOnClickListener {
            title.text = mediaPlayerService?.getCurrentAudioTitle()

            if (serviceBound) {
                if (!mediaPlayerService!!.isPlaying()) {
                    mediaPlayerService?.playMedia()
                    updatePlayIcon()
                } else {
                    mediaPlayerService?.pauseMedia()
                    updatePlayIcon()
                }
            }
        }

        nextButton.setOnClickListener {
            if (serviceBound) {
                mediaPlayerService!!.playNextMedia()
                title.text = mediaPlayerService?.getCurrentAudioTitle()
                mediaPlayerService?.playMedia()
                updatePlayIcon()
            }
        }

        previousButton.setOnClickListener {
            if (serviceBound) {
                mediaPlayerService!!.playPreviousMedia()
                title.text = mediaPlayerService?.getCurrentAudioTitle()
                mediaPlayerService?.playMedia()
                updatePlayIcon()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        checkMultiplePermission()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (serviceBound) {
            unbindService(serviceConnection)
            //service is active
            mediaPlayerService!!.stopSelf()
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putBoolean("ServiceState", serviceBound)
        super.onSaveInstanceState(savedInstanceState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        serviceBound = savedInstanceState.getBoolean("ServiceState")
    }

    private fun updatePlayIcon() {
        if (!mediaPlayerService!!.isPlaying()) {
            playButton.icon = ContextCompat.getDrawable(this,R.drawable.icon_play)
        } else {
            playButton.icon = ContextCompat.getDrawable(this,R.drawable.icon_pause)
        }
    }

    private fun boundService() {
        //Check is service is active
        if (!serviceBound) {
            val playerIntent = Intent(this, MediaPlayerService::class.java)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, BIND_AUTO_CREATE)
        } else {
            //Service is active
            //Send media with BroadcastReceiver
        }
    }

    //Binding this Client to the AudioPlayer Service
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as LocalBinder
            mediaPlayerService = binder.service
            serviceBound = true
            Toast.makeText(this@MainActivity, "Service Bound", Toast.LENGTH_SHORT).show()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
        }
    }

    private fun checkMultiplePermission(): Boolean {
        val listPermissionNeeded = arrayListOf<String>()

        for (permission in multiplePermissionNameList) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                listPermissionNeeded.add(permission)
            }
        }

        if (listPermissionNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                listPermissionNeeded.toTypedArray(),
                multiplePermissionId
            )
            return false
        }

        return true
    }

    private fun doOperation() {
        Toast.makeText(
            this,
            "All Permission Granted Successfully!",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == multiplePermissionId) {
            if (grantResults.isNotEmpty()) {
                var isGrant = true
                for (element in grantResults) {
                    if (element == PackageManager.PERMISSION_DENIED) {
                        isGrant = false
                    }
                }
                if (isGrant) {
                    // here all permission granted successfully
                    doOperation()
                } else {
                    var someDenied = false
                    for (permission in permissions) {
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                                this,
                                permission
                            )
                        ) {
                            if (ActivityCompat.checkSelfPermission(
                                    this,
                                    permission
                                ) == PackageManager.PERMISSION_DENIED
                            ) {
                                someDenied = true
                            }
                        }
                    }
                    if (someDenied) {
                        // here app Setting open because all permission is not granted
                        // and permanent denied
                        appSettingOpen(this)
                    } else {
                        // here warning permission show
                        warningPermissionDialog(this) { _: DialogInterface, which: Int ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE ->
                                    checkMultiplePermission()
                            }
                        }
                    }
                }
            }
        }
    }
}