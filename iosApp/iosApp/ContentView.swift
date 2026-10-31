import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeCoordinator() -> IosWindowLayout {
        IosWindowLayout()
    }

    func makeUIViewController(context: Context) -> UIViewController {
        DuoGeometryController(layout: context.coordinator)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Reads window/view geometry, including the Swift-only UIKit ReservedRegion overlay.
/// The coordinator and child controller survive folds; only numeric layout data changes.
private final class DuoGeometryController: UIViewController {
    private let layout: IosWindowLayout
    private let content: UIViewController

    init(layout: IosWindowLayout) {
        self.layout = layout
        content = MainViewControllerKt.MainViewController(windowLayout: layout)
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { return nil }

    override func loadView() {
        let geometryView = DuoGeometryView()
        geometryView.onGeometryChange = { [weak self] snapshot in
            self?.layout.update(
                width: Double(snapshot.bounds.width), height: Double(snapshot.bounds.height),
                safeLeft: Double(snapshot.safeArea.left), safeTop: Double(snapshot.safeArea.top),
                safeRight: Double(snapshot.safeArea.right), safeBottom: Double(snapshot.safeArea.bottom),
                barEdge: snapshot.barEdge, reservedRegions: snapshot.regions
            )
        }
        view = geometryView
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(content)
        content.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(content.view)
        NSLayoutConstraint.activate([
            content.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            content.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            content.view.topAnchor.constraint(equalTo: view.topAnchor),
            content.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        content.didMove(toParent: self)
    }

    override var childForStatusBarStyle: UIViewController? { content }
    override var childForStatusBarHidden: UIViewController? { content }
}

private struct DuoGeometrySnapshot: Equatable {
    let bounds: CGRect
    let safeArea: UIEdgeInsets
    let barEdge: String
    let regions: String
}

private final class DuoGeometryView: UIView {
    var onGeometryChange: ((DuoGeometrySnapshot) -> Void)?
    private var previous: DuoGeometrySnapshot?
    private var previousBounds: CGRect = .null

    override func layoutSubviews() {
        super.layoutSubviews()
        if bounds != previousBounds {
            previousBounds = bounds
            setNeedsUpdateConstraints()
        }
    }

    override func safeAreaInsetsDidChange() {
        super.safeAreaInsetsDidChange()
        setNeedsUpdateConstraints()
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        setNeedsUpdateConstraints()
    }

    override func updateConstraints() {
        // UIKit tracks region reads here and invalidates constraints when regions change.
        if #available(iOS 27.1, *) {
            let regions = reservedRegions(kind: .occlusion).map { ("o", $0.frame) }
                + reservedRegions(kind: .division).map { ("d", $0.frame) }
            let encoded = regions.prefix(32).map { kind, frame in
                "\(kind),\(frame.minX),\(frame.minY),\(frame.maxX),\(frame.maxY)"
            }.joined(separator: ";")
            let edge: String
            switch traitCollection.verticalBarEdge {
            case .leading: edge = "leading"
            case .trailing: edge = "trailing"
            case .unspecified: edge = "bottom"
            @unknown default: edge = "bottom"
            }
            let snapshot = DuoGeometrySnapshot(bounds: bounds, safeArea: safeAreaInsets,
                barEdge: edge, regions: encoded)
            if snapshot != previous {
                previous = snapshot
                // Avoid changing the Compose tree while UIKit is traversing constraints.
                DispatchQueue.main.async { [weak self] in
                    guard let self, self.previous == snapshot else { return }
                    self.onGeometryChange?(snapshot)
                }
            }
        }
        super.updateConstraints()
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
                .ignoresSafeArea()
    }
}
