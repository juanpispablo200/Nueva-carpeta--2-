package com.jagoo.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * SplashActivity — muestra el splash screen (definido en themes.xml)
 * y lanza MainActivity inmediatamente. El "splash" es solo el
 * windowBackground del tema, no hay delay artificial.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
