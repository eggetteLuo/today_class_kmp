import UIKit
import SwiftUI
import ComposeApp

private final class ComposeContainerViewController: UIViewController {
    private let composeViewController = MainViewControllerKt.MainViewController()
    private var isFullscreenMode = false {
        didSet {
            setNeedsStatusBarAppearanceUpdate()
            UIViewController.attemptRotationToDeviceOrientation()

            if #available(iOS 16.0, *) {
                let mask: UIInterfaceOrientationMask = isFullscreenMode ? .landscape : .allButUpsideDown
                let preferences = UIWindowScene.GeometryPreferences.iOS(interfaceOrientations: mask)
                view.window?.windowScene?.requestGeometryUpdate(preferences)
            } else {
                let orientation: UIInterfaceOrientation = isFullscreenMode ? .landscapeRight : .portrait
                UIDevice.current.setValue(orientation.rawValue, forKey: "orientation")
            }
        }
    }

    override var prefersStatusBarHidden: Bool { isFullscreenMode }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        isFullscreenMode ? .landscape : .allButUpsideDown
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        addChild(composeViewController)
        composeViewController.view.frame = view.bounds
        composeViewController.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(composeViewController.view)
        composeViewController.didMove(toParent: self)

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleFullscreenEnabled),
            name: Notification.Name("TodayClassFullscreenEnabled"),
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleFullscreenDisabled),
            name: Notification.Name("TodayClassFullscreenDisabled"),
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc
    private func handleFullscreenEnabled() {
        isFullscreenMode = true
    }

    @objc
    private func handleFullscreenDisabled() {
        isFullscreenMode = false
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        ComposeContainerViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}

