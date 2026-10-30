package dev.utmity.titanime

import android.app.Activity
import android.os.Bundle

/** Granting permission never starts recording. Return to the editor and explicitly start. */
class MicrophonePermissionActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (state == null) requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        android.widget.Toast.makeText(this, "入力欄へ戻り、必要なら音声入力を再度開始してください", android.widget.Toast.LENGTH_LONG).show()
        finish()
    }
}
