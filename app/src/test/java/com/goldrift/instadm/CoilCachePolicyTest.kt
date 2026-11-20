package com.goldrift.instadm

import com.goldrift.instadm.data.IMAGE_DISK_CACHE_MAX_BYTES
import com.goldrift.instadm.data.IMAGE_MEMORY_CACHE_PERCENT
import com.goldrift.instadm.data.cdnStableCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoilCachePolicyTest {
    @Test
    fun rotatedInstagramSignedUrlsMapToSameCacheKey() {
        val first = "https://scontent-dfw5-1.cdninstagram.com/v/t51.2885-19/412345_n.jpg" +
            "?stp=dst-jpg_s150x150_tt6&_nc_ht=scontent-dfw5-1.cdninstagram.com&_nc_cat=101" +
            "&_nc_oc=AQl123&_nc_ohc=abc1&_nc_gid=xyz&edm=APs17CUBAAAA&ccb=7-5" +
            "&oh=00_AY111&oe=67A00001&_nc_sid=10d13b"
        val refreshed = "https://instagram.ford1-2.fna.fbcdn.net/v/t51.2885-19/412345_n.jpg" +
            "?_nc_cat=104&stp=dst-jpg_s150x150_tt6&_nc_ht=instagram.ford1-2.fna.fbcdn.net" +
            "&_nc_oc=AQl999&_nc_ohc=def2&_nc_gid=uvw&edm=APs17CUBAAAA&ccb=7-5" +
            "&oh=00_AY999&oe=67B00002&_nc_sid=10d13b"

        assertEquals(
            "meta-scontent/v/t51.2885-19/412345_n.jpg?stp=dst-jpg_s150x150_tt6",
            cdnStableCacheKey(first),
        )
        assertEquals(cdnStableCacheKey(first), cdnStableCacheKey(refreshed))
    }

    @Test
    fun distinctTransformVariantsKeepDistinctCacheKeys() {
        val small = "https://scontent-dfw5-1.cdninstagram.com/v/t51.2885-19/412345_n.jpg" +
            "?stp=dst-jpg_s150x150_tt6&oh=111&oe=222"
        val large = "https://scontent-dfw5-1.cdninstagram.com/v/t51.2885-19/412345_n.jpg" +
            "?stp=dst-jpg_s640x640_tt6&oh=111&oe=222"

        assertNotEquals(cdnStableCacheKey(small), cdnStableCacheKey(large))
        assertEquals(
            "meta-scontent/v/t51.2885-19/412345_n.jpg?stp=dst-jpg_s640x640_tt6",
            cdnStableCacheKey(large),
        )
    }

    @Test
    fun nonEdgeMetaHostsPreserveHostName() {
        val staticUrl = "https://static.cdninstagram.com/rsrc.php/v3/y1/r/icon.png?oh=111&oe=222"
        assertEquals(
            "static.cdninstagram.com/rsrc.php/v3/y1/r/icon.png",
            cdnStableCacheKey(staticUrl),
        )
    }

    @Test
    fun nonMetaAndLocalUrlsFallBackToDefaultCoilKeys() {
        assertNull(cdnStableCacheKey("https://example.com/photo.jpg?oh=1&oe=2"))
        assertNull(cdnStableCacheKey("https://evilcdninstagram.com/v/1.jpg?oh=1"))
        assertNull(cdnStableCacheKey("file:///data/user/0/app/cache/photo.jpg"))
        assertNull(cdnStableCacheKey("content://media/external/images/media/42"))
        assertNull(cdnStableCacheKey("https://scontent.cdninstagram.com/"))
        assertNull(cdnStableCacheKey(""))
    }

    @Test
    fun cacheBoundsStayWithinExplicitLimits() {
        assertTrue(IMAGE_MEMORY_CACHE_PERCENT in 0.10..0.25)
        assertEquals(100L * 1024L * 1024L, IMAGE_DISK_CACHE_MAX_BYTES)
    }
}
