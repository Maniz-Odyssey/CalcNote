package com.manish.calcnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.manish.calcnote.ui.note.CalcNoteScreen
import com.manish.calcnote.ui.theme.CalcNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalcNoteTheme {
                CalcNoteScreen()
            }
        }
    }
}
