import Foundation
import ContactsUI

@objc public class PickContactImpl: NSObject {
  private var resolve: ((Any?) -> Void)?
  private var pickerDelegate: PickerDelegate?
  private let lock = NSLock()
  private var _isPickerActive = false

  private var isPickerActive: Bool {
    get { lock.lock(); defer { lock.unlock() }; return _isPickerActive }
    set { lock.lock(); defer { lock.unlock() }; _isPickerActive = newValue }
  }

  deinit {
    resolve?(nil)
  }

  @objc public func pickContact(
    resolve: @escaping (Any?) -> Void,
    reject: @escaping (String?, String?, Error?) -> Void
  ) {
    lock.lock()
    guard !_isPickerActive else {
      lock.unlock()
      reject("E_PICKER_BUSY", "Contact picker is already open", nil)
      return
    }
    _isPickerActive = true
    lock.unlock()

    self.resolve = resolve

    let localReject = reject

    DispatchQueue.main.async { [weak self] in
      guard let self else {
        localReject("E_MODULE_DEALLOCATED", "Module was deallocated", nil)
        return
      }

      let delegate = PickerDelegate(
        onSelect: { [weak self] result in
          self?.resolve?(result)
          self?.cleanup()
        },
        onCancel: { [weak self] in
          self?.resolve?(nil)
          self?.cleanup()
        }
      )

      self.pickerDelegate = delegate

      let picker = CNContactPickerViewController()
      picker.delegate = delegate
      picker.predicateForEnablingContact = NSPredicate(
        format: "phoneNumbers.@count > 0"
      )

      guard let topVC = Self.topViewController() else {
        localReject("E_NO_VIEW_CONTROLLER", "Could not find root view controller", nil)
        self.cleanup()
        return
      }

      topVC.present(picker, animated: true)
    }
  }

  private func cleanup() {
    resolve = nil
    pickerDelegate = nil
    isPickerActive = false
  }

  private static func topViewController() -> UIViewController? {
    guard let scene = UIApplication.shared.connectedScenes
      .compactMap({ $0 as? UIWindowScene })
      .first(where: { $0.activationState == .foregroundActive }),
      let rootVC = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController
    else {
      return nil
    }

    var topVC = rootVC
    while let presented = topVC.presentedViewController {
      topVC = presented
    }
    return topVC
  }
}

// MARK: - CNContactPickerDelegate

private class PickerDelegate: NSObject, CNContactPickerDelegate {
  let onSelect: ([String: String]) -> Void
  let onCancel: () -> Void

  init(
    onSelect: @escaping ([String: String]) -> Void,
    onCancel: @escaping () -> Void
  ) {
    self.onSelect = onSelect
    self.onCancel = onCancel
  }

  func contactPickerDidCancel(_ picker: CNContactPickerViewController) {
    onCancel()
  }

  func contactPicker(
    _ picker: CNContactPickerViewController,
    didSelect contact: CNContact
  ) {
    let name = [contact.givenName, contact.familyName]
      .filter { !$0.isEmpty }
      .joined(separator: " ")
    let phone = contact.phoneNumbers.first?.value.stringValue ?? ""

    onSelect(["name": name, "phone": phone])
  }
}
