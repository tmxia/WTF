package top.easelink.lcg.ui.splash.view

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.MainActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        
        
        super.onCreate(savedInstanceState)
        Handler().postDelayed({
            startActivity(Intent(baseContext, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 600)
    }
}