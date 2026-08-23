package com.pakkabaat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
                // enableEdgeToEdge() makes the status bar / notch / nav bar fully
                // transparent so the app can draw behind them. The previous code only
                // applied safeDrawingPadding() to the *content* Surface, which correctly
                // keeps content from being clipped — but it also means the content's
                // background colour stops exactly at the edge of the notch/status/nav
                // bar. Nothing else was painting that region, so it fell through to the
                // plain window background (from themes.xml, which has no background/
                // dark-mode override), showing up as a colour that doesn't match the
                // app (the bug reported).
                //
                // Fix: an outer Box with no inset padding paints the *entire* window,
                // edge-to-edge, in the theme's background colour (so it's correct in
                // both light and dark mode). safeDrawingPadding() then only applies to
                // the inner content, which is inset away from the system bars as before.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
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
}
