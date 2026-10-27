package uk.co.asmcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import uk.co.asmcoach.ui.navigation.AsmCoachRoot
import uk.co.asmcoach.ui.theme.AsmCoachTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AsmCoachApp).container
        setContent {
            AsmCoachTheme {
                AsmCoachRoot(container)
            }
        }
    }
}
