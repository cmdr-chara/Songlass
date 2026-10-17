package dev.cmdrchara.songlass

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.MainScope

// iOS still uses the demo library and silent player until AVFoundation playback is added.
private val graph by lazy { AppGraph.demo(MainScope()) }

fun MainViewController() = ComposeUIViewController { App(graph) }
