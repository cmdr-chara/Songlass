@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.cmdrchara.songlass

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import dev.cmdrchara.songlass.designsystem.WindowBarEdge
import dev.cmdrchara.songlass.designsystem.WindowLayout
import dev.cmdrchara.songlass.designsystem.foundation.LocalArtworkImageLoader
import kotlinx.coroutines.MainScope
import platform.UIKit.supportsAlternateIcons
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UITraitCollection
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

private val appScope = MainScope()
private val launcherIcons = IosLauncherIcons()
private val musicSource by lazy { IosMusicSource(appScope) }
private var systemNowPlaying: IosNowPlaying? = null
private val graph by lazy {
    AppGraph(
        IosAudioPlayer(appScope), musicSource, UserDefaultsStore(), appScope,
        device = dev.cmdrchara.songlass.core.onboarding.DeviceInfo.Apple,
        applyLauncherIcon = if (UIApplication.sharedApplication.supportsAlternateIcons) launcherIcons::select else null,
    ).also {
        musicSource.onLibraryChanged = it::requestLibraryRefresh
        systemNowPlaying = IosNowPlaying(it, musicSource, appScope)
    }
}
private val artworkImages by lazy { IosArtworkImages(musicSource, graph.artworkAvailability) }

/** Per-host bridge: no geometry is stored in the process-wide playback graph. */
class IosWindowLayout {
    internal var layout by mutableStateOf(WindowLayout())
        private set

    fun update(width: Double, height: Double, safeLeft: Double, safeTop: Double, safeRight: Double,
        safeBottom: Double, barEdge: String, reservedRegions: String) {
        val next = WindowLayout(width.toFloat(), height.toFloat(), safeLeft.toFloat(), safeTop.toFloat(),
            safeRight.toFloat(), safeBottom.toFloat(), when (barEdge) {
                "leading" -> WindowBarEdge.Leading
                "trailing" -> WindowBarEdge.Trailing
                else -> WindowBarEdge.Bottom
            }, WindowLayout.decodeRegions(reservedRegions))
        layout = next.takeIf { it.specified } ?: WindowLayout()
    }
}

fun MainViewController(): UIViewController = SonglassViewController(IosWindowLayout())

fun MainViewController(windowLayout: IosWindowLayout): UIViewController = SonglassViewController(windowLayout)

/** Owns status-bar appearance without changing the system appearance observed by Compose. */
internal class SonglassViewController(private val windowLayout: IosWindowLayout) : UIViewController(nibName = null, bundle = null) {
    private val composeController = ComposeUIViewController {
        CompositionLocalProvider(LocalArtworkImageLoader provides artworkImages) {
            App(graph, onRequestLibraryAccess = musicSource::requestAccess, windowLayout = windowLayout.layout)
        }
    }
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

    override fun viewWillAppear(animated: Boolean) {
        super.viewWillAppear(animated)
        updateLauncherIconAppearance()
    }

    override fun traitCollectionDidChange(previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        updateLauncherIconAppearance()
    }

    private fun updateLauncherIconAppearance() {
        launcherIcons.updateSystemAppearance(traitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark)
    }

    override fun preferredStatusBarStyle(): UIStatusBarStyle =
        if (lightStatusBarIcons) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent

    fun updateStatusBarAppearance(lightIcons: Boolean) {
        if (lightStatusBarIcons == lightIcons) return
        lightStatusBarIcons = lightIcons
        setNeedsStatusBarAppearanceUpdate()
    }
}
