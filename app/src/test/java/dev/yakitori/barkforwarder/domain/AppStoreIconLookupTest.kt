package dev.yakitori.barkforwarder.domain

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test

class AppStoreIconLookupTest {
    private val lookup = AppStoreIconLookup(OkHttpClient())

    @Test
    fun prefersArtwork512WhenNameMatchesExactly() {
        val body = """
            {"resultCount":2,"results":[
              {"trackName":"微信读书","artworkUrl512":"https://is1-ssl.mzstatic.com/weread/512x512bb.jpg"},
              {"trackName":"微信","artworkUrl512":"https://is1-ssl.mzstatic.com/wechat/512x512bb.jpg"}
            ]}
        """.trimIndent()

        assertThat(lookup.pickIconUrl(body, "微信"))
            .isEqualTo("https://is1-ssl.mzstatic.com/wechat/512x512bb.jpg")
    }

    @Test
    fun upscalesArtwork100When512IsMissing() {
        val body = """
            {"resultCount":1,"results":[
              {"trackName":"QQ","artworkUrl100":"https://is1-ssl.mzstatic.com/qq/100x100bb.jpg"}
            ]}
        """.trimIndent()

        assertThat(lookup.pickIconUrl(body, "QQ"))
            .isEqualTo("https://is1-ssl.mzstatic.com/qq/512x512bb.jpg")
    }

    @Test
    fun acceptsNameContainingLabelWhenNoExactMatch() {
        val body = """
            {"resultCount":1,"results":[
              {"trackName":"哔哩哔哩-弹幕番剧","artworkUrl512":"https://is1-ssl.mzstatic.com/bili/512x512bb.jpg"}
            ]}
        """.trimIndent()

        assertThat(lookup.pickIconUrl(body, "哔哩哔哩"))
            .isEqualTo("https://is1-ssl.mzstatic.com/bili/512x512bb.jpg")
    }

    @Test
    fun ignoresUnrelatedResults() {
        val body = """
            {"resultCount":1,"results":[
              {"trackName":"支付宝","artworkUrl512":"https://is1-ssl.mzstatic.com/alipay/512x512bb.jpg"}
            ]}
        """.trimIndent()

        assertThat(lookup.pickIconUrl(body, "微信")).isNull()
    }

    @Test
    fun returnsNullForUnparsableResponse() {
        assertThat(lookup.pickIconUrl("<html>blocked</html>", "微信")).isNull()
        assertThat(lookup.pickIconUrl("""{"resultCount":0,"results":[]}""", "微信")).isNull()
    }
}
