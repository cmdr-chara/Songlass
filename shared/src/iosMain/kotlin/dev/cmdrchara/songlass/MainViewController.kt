@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.cmdrchara.songlass

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.MainScope
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

// iOS still uses the demo library and silent player until AVFoundation playback is added.
private val graph by lazy { AppGraph.demo(MainScope(), UserDefaultsStore(), device = dev.cmdrchara.songlass.core.onboarding.DeviceInfo.Apple) }

fun MainViewController(): UIViewController = SonglassViewController()

/** Owns status-bar appearance without changing the system appearance observed by Compose. */
internal class SonglassViewController : UIViewController(nibName = null, bundle = null) {
    private val composeController = ComposeUIViewController { App(graph) }
    private var lightStatusBarIcons = false

    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(composeController)
        val composeView = composeController.view
        view.addSubview(composeView)
        composeView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activateConstraints(
            listOf(
                composeView.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                composeView.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
                composeView.topAnchor.constraintEqualToAnchor(view.topAnchor),
                composeView.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor),
            ),
        )
        composeController.didMoveToParentViewController(this)
    }

    override fun preferredStatusBarStyle(): UIStatusBarStyle =
        if (lightStatusBarIcons) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent

    fun updateStatusBarAppearance(lightIcons: Boolean) {
        if (lightStatusBarIcons == lightIcons) return
        lightStatusBarIcons = lightIcons
        setNeedsStatusBarAppearanceUpdate()
    }
}
