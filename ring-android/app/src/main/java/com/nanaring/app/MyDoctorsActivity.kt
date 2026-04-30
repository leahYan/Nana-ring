package com.nanaring.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nanaring.app.ui.screens.MyDoctorsScreen
import com.nanaring.app.ui.theme.NanaRingTheme

class MyDoctorsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val syncAuth = (application as NanaRingApplication).container.supabaseSyncAuth

        setContent {
            NanaRingTheme {
                MyDoctorsScreen(
                    syncAuth = syncAuth,
                    onBack   = { finish() },
                )
            }
        }
    }
}
