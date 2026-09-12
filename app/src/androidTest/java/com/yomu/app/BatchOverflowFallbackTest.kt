package com.yomu.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.core.Constants
import com.yomu.core.ModelProfile
import com.yomu.core.TranslatableBubble
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationOutcome
import com.yomu.ml.LlamaBridge
import com.yomu.ml.LlamaTranslationBridge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * ADR-0013's per-page fallback, measured rather than asserted (#198 gate 4).
 *
 * `LlamaTranslationBridgeTest` already covers the branch against a fake that returns
 * [com.yomu.ml.GenerationResult.Overflow] on demand. That proves the wiring, not the criterion: the
 * criterion is that a page the *native* fit check rejects still reaches the user translated, and
 * only the real tokenizer decides what the fit check rejects. The ADR-0004 corpus cannot reach it —
 * its densest page is ~423 prompt tokens against `prompt_fits`' 2048 - 768 - 8 = 1272 — so the page
 * here is constructed to overflow instead of loaded.
 *
 * The assertions are behavioural and device-independent, so this is not bound by the same-device
 * rule the latency and memory gates carry (`eval/README.md`).
 */
@RunWith(AndroidJUnit4::class)
class BatchOverflowFallbackTest {

    @Test
    fun overflowingPageStillRendersTranslatedThroughPerLine() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Read straight from the pushed fixture rather than staging a copy into filesDir: this test
        // loads one model once and needs no DI graph, so a ~600MB copy would buy nothing.
        val model = File(FIXTURE_DIR, "${Constants.LLM_MODELS_DIR}/${LlmModelCatalog.DEFAULT.ggufFileName}")
        check(model.exists()) {
            "${model.path} is missing; run eval/run-benchmark.sh to push fixtures"
        }

        val native = LlamaBridge(context)
        val slot = LlamaTranslationBridge(
            native,
            ModelProfile(
                modelPath = model.absolutePath,
                idKeyedBatch = LlmModelCatalog.DEFAULT.idKeyedBatch,
                promptMode = LlmModelCatalog.DEFAULT.promptMode
            )
        )
        try {
            check(slot.ensureReady()) { "Qwen model unavailable: ${slot.status}" }
            val page = overflowingPage()
            val ids = page.panels.flatten().map { it.bubbleId }

            val result = slot.translatePage(page)

            Log.i(
                TAG,
                "Overflow gate bubbles=${ids.size} outcome=${result.outcome} " +
                    "errorCode=${result.errorCode} translated=${result.byId.size} " +
                    "durationMs=${result.durationMs}"
            )

            // The fallback fired. Without this the rest would pass on a page that simply fit, which
            // is the way this gate would silently stop measuring anything.
            assertEquals(LlamaTranslationBridge.BATCH_OVERFLOW_FALLBACK, result.errorCode)
            // ...and it rendered. ADR-0013 replaced an empty PageTranslation and a silently
            // untranslated page; a fallback that returns nothing would satisfy the errorCode alone.
            assertEquals(TranslationOutcome.SUCCESS, result.outcome)
            assertEquals(ids.toSet(), result.byId.keys)
            assertTrue(
                "Blank translations: ${result.byId.filterValues { it.isBlank() }.keys}",
                result.byId.values.all { it.isNotBlank() }
            )
        } finally {
            slot.close()
            native.release()
        }
    }

    /**
     * A page whose batch prompt cannot fit, built from distinct long lines.
     *
     * Distinct rather than one line repeated: the sampler carries no repetition penalty
     * (`penaltyRepeat = 1.0`), and a bubble built of repeated text would invite the loop #139 owns
     * into a test that is measuring the fallback, not the sampler. Eight bubbles keeps the per-line
     * fallback — one generate call each — inside the harness's connected-test window, so the length
     * is bought by making each bubble longer rather than by adding more of them.
     *
     * Each bubble is three consecutive paragraphs, which is what sets the margin. The first version
     * of this test used one paragraph each — 1648 characters of prompt — and *fit*, because Qwen2.5
     * tokenizes Japanese well under one token per character. Three brings it to ~4900 characters:
     * over the 1272-token ceiling by enough that the ratio would have to fall below 0.26 tokens per
     * character before this silently stopped testing anything.
     */
    private fun overflowingPage(): TranslatablePage {
        val bubbles = OVERFLOW_SOURCES.indices.map { index ->
            val source = (0 until PARAGRAPHS_PER_BUBBLE).joinToString("") { offset ->
                OVERFLOW_SOURCES[(index + offset) % OVERFLOW_SOURCES.size]
            }
            TranslatableBubble(index, source)
        }
        return TranslatablePage(listOf(bubbles))
    }

    companion object {
        private const val TAG = "BatchOverflowFallbackTest"
        private const val FIXTURE_DIR = "/data/local/tmp/yomu-fixtures"

        private const val PARAGRAPHS_PER_BUBBLE = 3

        // ~200 Japanese characters each, eight of them, combined three to a bubble by
        // overflowingPage.
        private val OVERFLOW_SOURCES = listOf(
            "この街に来てからもう三年が経つが、いまだに駅前の景色には慣れないままでいる。朝の通勤の人波に押されながら歩いていると、自分がどこへ向かっているのかふと分からなくなる瞬間がある。そんなとき、決まって思い出すのは故郷の海辺の道と、そこで別れた友人の後ろ姿だった。あの日の言葉をもう一度聞けたなら、今の自分は少しは違っていたのだろうか。答えの出ない問いを抱えたまま、今日もまた改札を抜けていく。",
            "研究室の窓から見える空は、季節が変わるたびに違う色をしていた。締め切りに追われる夜が続くと、その変化にすら気づかない日もある。それでも先輩がふいに差し入れてくれた缶コーヒーの温かさだけは、不思議とはっきり覚えている。人は大きな出来事ではなく、こうした些細な瞬間の積み重ねで誰かを信じるようになるのかもしれない。実験が失敗しても、また明日やり直せばいいと思えたのは、たぶんそのおかげだった。",
            "祖母の家の縁側には、いつも古い扇風機が置かれていた。首を振るたびに小さく軋む音を立てるそれを、夏の間じゅう誰も直そうとしなかった。壊れているわけではないのだから、と祖母は笑っていたが、今思えばあの音そのものが夏の一部だったのだと思う。家を片付けたとき、その扇風機だけはどうしても捨てられずに持ち帰ってしまった。部屋の隅に置かれたまま、もう何年も動かしていない。",
            "彼が最後に描いた絵は、誰にも見せないまま押し入れの奥にしまわれていた。展覧会に出すつもりだったという話を聞いたのは、ずいぶん後になってからのことだ。キャンバスには夜明け前の街が描かれていて、まだ誰も歩いていない通りに街灯だけが並んでいた。完成しているようにも、途中で止めたようにも見える不思議な絵だった。どちらなのか確かめる方法は、もうどこにも残されていない。",
            "雨が降り始めたのは、ちょうど電車が鉄橋にさしかかったときだった。窓ガラスを流れる水滴を眺めていると、向かいの席の子どもが同じように窓に顔を寄せているのが見えた。目が合うと、その子は少し恥ずかしそうに笑って母親の方を向いてしまった。何でもないやり取りなのに、降りる駅に着くまでずっとそのことを考えていた。傘を持っていないことに気づいたのは、ホームに降り立ってからだった。",
            "店を畳むと決めたとき、常連の客たちには何も伝えないつもりでいた。挨拶をすれば引き止められると分かっていたし、そうなれば決心が鈍る気がしたからだ。けれど最終日の夕方、いつもの時間にいつもの席に座った老人が、黙ってカウンターに封筒を置いていった。中に何が入っているのか確かめるのが怖くて、閉店後もしばらくそのままにしていた。結局開けたのは、片付けが全部終わった深夜のことだった。",
            "山道を登り始めて二時間、地図に書かれていたはずの分岐がどこにも見当たらなかった。携帯の電波はとうに届かなくなっていて、頼れるものは足元の踏み跡だけになっていた。引き返すべきだと頭では分かっているのに、なぜかそのまま進み続けてしまった。やがて木々が途切れ、開けた場所に出たとき、そこには朽ちかけた小屋がひとつ建っていた。扉には鍵がかかっておらず、内側から閉められた跡だけが残っていた。",
            "手紙の差出人の名前には覚えがなかったが、筆跡にはどこか懐かしさがあった。読み進めるうちに、それが小学校の頃に転校していった同級生からのものだと分かった。三十年ぶりに届いたその便りには、当時借りたまま返しそびれた本のことが書かれていた。もう本の内容も、貸したことすら忘れていたのに、相手はずっと覚えていたのだ。返事を書こうとして便箋を広げたまま、何を書けばいいのか分からずに夜が明けた。"
        )
    }
}
