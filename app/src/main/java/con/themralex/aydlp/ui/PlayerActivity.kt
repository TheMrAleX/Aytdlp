package con.themralex.aydlp.ui

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import con.themralex.aydlp.core.PlaybackService

class PlayerActivity:ComponentActivity(){
    private var controller:MediaController?=null
    private var view:PlayerView?=null
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val raw=intent.getStringExtra("uri")?:run{finish();return};val uri=Uri.parse(raw);if(uri.scheme!="content"){finish();return}
        setContent { AndroidView(factory={PlayerView(it).apply {view=this;setShowSubtitleButton(true);setShowNextButton(false);setShowPreviousButton(false)}},modifier=Modifier.fillMaxSize()) }
        val future=MediaController.Builder(this,SessionToken(this,ComponentName(this,PlaybackService::class.java))).buildAsync()
        future.addListener({if(isFinishing){MediaController.releaseFuture(future);return@addListener};controller=future.get();view?.player=controller;controller?.setMediaItem(MediaItem.fromUri(uri));controller?.prepare();controller?.seekTo(getPreferences(MODE_PRIVATE).getLong(raw,0));controller?.play()},ContextCompat.getMainExecutor(this))
    }
    override fun onUserLeaveHint(){super.onUserLeaveHint();if(controller?.isPlaying==true)runCatching {enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16,9)).build())}}
    override fun onStop(){val key=intent.getStringExtra("uri");if(key!=null)getPreferences(MODE_PRIVATE).edit().putLong(key,controller?.currentPosition?:0).apply();super.onStop()}
    override fun onDestroy(){view?.player=null;controller?.release();super.onDestroy()}
}
