package me.magnum.melonds.domain.model

import android.net.Uri
import java.util.*

data class SaveStateSlot(val slot: Int, val exists: Boolean, val lastUsedDate: Date?, val screenshot: Uri?) {

    companion object {
        const val QUICK_SAVE_SLOT = 0
        const val AUTO_SAVE_SLOT = -1  // Auto Save Slot 使用特殊值 -1
    }
}