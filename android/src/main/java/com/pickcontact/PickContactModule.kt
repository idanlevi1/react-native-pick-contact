package com.pickcontact

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import java.util.concurrent.atomic.AtomicBoolean

@ReactModule(name = PickContactModule.NAME)
class PickContactModule(reactContext: ReactApplicationContext) :
  NativePickContactSpec(reactContext), LifecycleEventListener {

  companion object {
    const val NAME = "PickContact"
  }

  private val isPickerActive = AtomicBoolean(false)
  @Volatile private var pendingPromise: Promise? = null
  @Volatile private var activeLauncher: ActivityResultLauncher<Void?>? = null

  init {
    reactContext.addLifecycleEventListener(this)
  }

  override fun getName(): String = NAME

  override fun invalidate() {
    reactApplicationContext.removeLifecycleEventListener(this)
    super.invalidate()
  }

  // Finding #3 fix: reject pending promise if host Activity is destroyed
  override fun onHostDestroy() {
    if (isPickerActive.compareAndSet(true, false)) {
      activeLauncher?.unregister()
      activeLauncher = null
      pendingPromise?.reject("E_ACTIVITY_DESTROYED", "Activity was destroyed while picker was open")
      pendingPromise = null
    }
  }

  override fun onHostResume() {}
  override fun onHostPause() {}

  override fun pickContact(promise: Promise) {
    // Finding #2 fix: prevent concurrent picker launches
    if (!isPickerActive.compareAndSet(false, true)) {
      promise.reject("E_PICKER_BUSY", "Contact picker is already open")
      return
    }

    val activity = reactApplicationContext.currentActivity as? ComponentActivity
    if (activity == null) {
      isPickerActive.set(false)
      promise.reject("E_NO_ACTIVITY", "Activity not available")
      return
    }

    pendingPromise = promise

    activity.runOnUiThread {
      try {
        val registry = activity.activityResultRegistry
        val key = "pick_contact_${System.nanoTime()}"

        var launcher: ActivityResultLauncher<Void?>? = null
        launcher = registry.register(key, ActivityResultContracts.PickContact()) { uri: Uri? ->
          launcher?.unregister()
          activeLauncher = null

          val currentPromise = pendingPromise
          pendingPromise = null
          isPickerActive.set(false)

          if (currentPromise == null) return@register

          if (uri == null) {
            currentPromise.resolve(null)
            return@register
          }

          try {
            val result = resolveContact(uri)
            if (!result.hasKey("phone") || result.getString("phone")?.isEmpty() != false) {
              // Phone empty — try fallback with READ_CONTACTS permission
              requestContactsPermissionAndResolvePhone(activity, uri, result, currentPromise)
            } else {
              currentPromise.resolve(result)
            }
          } catch (e: Exception) {
            currentPromise.reject("E_CONTACT_RESOLVE", "Failed to read contact data", e)
          }
        }

        activeLauncher = launcher
        launcher.launch(null)
      } catch (e: Exception) {
        activeLauncher = null
        pendingPromise = null
        isPickerActive.set(false)
        promise.reject("E_LAUNCH_PICKER", "Failed to launch contact picker", e)
      }
    }
  }

  private fun resolveContact(uri: Uri): WritableMap {
    val result = Arguments.createMap()
    val resolver = reactApplicationContext.contentResolver

    // Read display name from the picked contact URI (covered by temp permission)
    var nameFound = false
    resolver.query(
      uri,
      arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
      null, null, null
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        result.putString(
          "name",
          cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
        )
        nameFound = true
      }
    }
    if (!nameFound) {
      result.putString("name", "")
    }

    // Read phone via Data sub-path (zero-permission approach)
    try {
      val dataUri = Uri.withAppendedPath(uri, ContactsContract.Contacts.Data.CONTENT_DIRECTORY)
      resolver.query(
        dataUri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${ContactsContract.Data.MIMETYPE} = ?",
        arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
        null
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          result.putString(
            "phone",
            cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
          )
        }
      }
    } catch (_: SecurityException) {
      // Temp URI doesn't cover Data sub-path on some devices (Android 16+ Samsung)
      // Phone will be empty — caller handles fallback with permission request
    }

    if (!result.hasKey("phone")) {
      result.putString("phone", "")
    }

    return result
  }

  // Fallback: request READ_CONTACTS at runtime, then query phone by contact ID.
  // SECURITY: contactId MUST originate from the system-provided picker URI.
  private fun requestContactsPermissionAndResolvePhone(
    activity: ComponentActivity,
    contactUri: Uri,
    result: WritableMap,
    promise: Promise
  ) {
    val hasPermission = ContextCompat.checkSelfPermission(
      activity, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    if (hasPermission) {
      resolvePhoneByContactId(contactUri, result)
      promise.resolve(result)
      return
    }

    activity.runOnUiThread {
      try {
        val key = "read_contacts_perm_${System.nanoTime()}"
        var permLauncher: ActivityResultLauncher<String>? = null
        permLauncher = activity.activityResultRegistry.register(
          key, ActivityResultContracts.RequestPermission()
        ) { granted ->
          permLauncher?.unregister()
          if (granted) {
            resolvePhoneByContactId(contactUri, result)
          }
          promise.resolve(result)
        }
        permLauncher.launch(Manifest.permission.READ_CONTACTS)
      } catch (_: Exception) {
        promise.resolve(result)
      }
    }
  }

  private fun resolvePhoneByContactId(contactUri: Uri, result: WritableMap) {
    try {
      val resolver = reactApplicationContext.contentResolver
      val contactId = getContactId(contactUri, resolver) ?: return
      resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
        arrayOf(contactId),
        null
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          result.putString(
            "phone",
            cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
          )
        }
      }
    } catch (_: SecurityException) {
      // Permission denied or revoked — phone stays empty
    }
  }

  private fun getContactId(uri: Uri, resolver: android.content.ContentResolver): String? {
    resolver.query(
      uri,
      arrayOf(ContactsContract.Contacts._ID),
      null, null, null
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        return cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
      }
    }
    return null
  }
}
