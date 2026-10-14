package eu.studiodeanna.openpantrymanager.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class ShareLinksTest {
    @Test
    fun readsNextcloudLinksWithAPathAndIndexPhp() {
        assertEquals(
            ParsedLink.Nextcloud("https://cloud.example.org/nextcloud", "BXQ9wqoMjN6Hzkj"),
            ShareLinks.parse("https://cloud.example.org/nextcloud/index.php/s/BXQ9wqoMjN6Hzkj"),
        )
    }
}
