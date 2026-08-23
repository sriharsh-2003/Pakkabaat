package com.pakkabaat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pakkabaat.app.ui.navigation.PakkaBaatNavGraph
import com.pakkabaat.app.ui.theme.PakkaBaatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PakkaBaatTheme {
                // enableEdgeToEdge() draws behind the status bar/notch by design, but that
                // means something has to consume those insets or content gets clipped under
                // them (the bug reported). safeDrawingPadding() pushes content below the
                // notch/status bar and above the nav bar, on every screen, in one place.
                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PakkaBaatNavGraph()
                }
            }
        }
    }
}
