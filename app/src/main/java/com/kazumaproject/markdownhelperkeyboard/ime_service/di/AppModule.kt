package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.graphics.drawable.Drawable
import android.transition.Slide
import android.transition.Transition
import android.view.Gravity
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.bitset.rank0GetIntArray
import com.kazumaproject.bitset.rank0GetShortArray
import com.kazumaproject.bitset.rank1GetIntArray
import com.kazumaproject.bitset.rank1GetShortArray
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.converter.graph.GraphBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.PressedKeyStatus
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.toBooleanArray
import com.kazumaproject.viterbi.FindPath
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import java.io.BufferedInputStream
import java.io.ObjectInputStream
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideSuggestionAdapter(): SuggestionAdapter = SuggestionAdapter()

    @MainDispatcher
    @Singleton
    @Provides
    fun providesMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Singleton
    @Provides
    fun providesStringBuilder(): StringBuilder = StringBuilder()

    @Singleton
    @Provides
    fun providesTransition(): Transition = Slide(Gravity.BOTTOM)

    @Singleton
    @Provides
    @Named("main_ime_scope")
    fun providesIMEScope(
        @MainDispatcher mainDispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob(Job()) + mainDispatcher)

    @Singleton
    @Provides
    fun providesPreference(@ApplicationContext context: Context): AppPreference {
        return AppPreference.apply {
            init(context)
        }
    }

    @Singleton
    @Provides
    fun extractedText(): ExtractedTextRequest = ExtractedTextRequest()

    @Singleton
    @Provides
    fun provideGraphBuilder(): GraphBuilder = GraphBuilder()

    @Singleton
    @Provides
    fun provideFindPath(): FindPath = FindPath()

    @Singleton
    @Provides
    @ConnectionIds
    fun provideConnectionIds(@ApplicationContext context: Context): ShortArray {
        val input = BufferedInputStream(context.assets.open("connectionId.dat"))
        return ConnectionIdBuilder().readShortArrayFromBytes(input)
    }

    @SystemTangoTrie
    @Singleton
    @Provides
    fun provideTangoTrie(@ApplicationContext context: Context): LOUDS {
        val objectInputTango =
            ObjectInputStream(BufferedInputStream(context.assets.open("system/tango.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @SystemYomiTrie
    @Singleton
    @Provides
    fun provideYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId {
        val objectInputYomi =
            ObjectInputStream(BufferedInputStream(context.assets.open("system/yomi.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @SystemTokenArray
    @Singleton
    @Provides
    fun providesTokenArray(@ApplicationContext context: Context): TokenArray {
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(context.assets.open("system/token.dat")))
        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @SystemRank0ArrayLBSYomi
    fun provideRank0ArrayLBSYomi(@SystemYomiTrie yomiTrie: LOUDSWithTermId): IntArray =
        yomiTrie.LBS.rank0GetIntArray()

    @Singleton
    @Provides
    @SystemRank1ArrayLBSYomi
    fun provideRank1ArrayLBSYomi(@SystemYomiTrie yomiTrie: LOUDSWithTermId): IntArray =
        yomiTrie.LBS.rank1GetIntArray()

    @Singleton
    @Provides
    @SystemRank1ArrayIsLeafYomi
    fun provideRank1ArrayIsLeaf(@SystemYomiTrie yomiTrie: LOUDSWithTermId): IntArray =
        yomiTrie.isLeaf.rank1GetIntArray()

    @Singleton
    @Provides
    @SystemYomiLBSBooleanArray
    fun providesYomiLBSBooleanArray(@SystemYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray =
        yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @SystemRank0ArrayTokenArrayBitvector
    fun provideRank0ArrayTokenArrayBitvector(@SystemTokenArray tokenArray: TokenArray): IntArray =
        tokenArray.bitvector.rank0GetIntArray()

    @Singleton
    @Provides
    @SystemRank1ArrayTokenArrayBitvector
    fun provideRank1ArrayTokenArrayBitvector(@SystemTokenArray tokenArray: TokenArray): IntArray =
        tokenArray.bitvector.rank1GetIntArray()

    @Singleton
    @Provides
    @SystemRank0ArrayTangoLBS
    fun provideRank0ArrayLBSTango(@SystemTangoTrie tangoTrie: LOUDS): IntArray =
        tangoTrie.LBS.rank0GetIntArray()

    @Singleton
    @Provides
    @SystemRank1ArrayTangoLBS
    fun provideRank1ArrayLBSTango(@SystemTangoTrie tangoTrie: LOUDS): IntArray =
        tangoTrie.LBS.rank1GetIntArray()


    @SingleKanjiTangoTrie
    @Singleton
    @Provides
    fun provideSingleKanjiTangoTrie(@ApplicationContext context: Context): LOUDS {
        val objectInputTango =
            ObjectInputStream(BufferedInputStream(context.assets.open("single_kanji/tango_singleKanji.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @SingleKanjiYomiTrie
    @Singleton
    @Provides
    fun provideSingleKanjiYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId {
        val objectInputYomi =
            ObjectInputStream(BufferedInputStream(context.assets.open("single_kanji/yomi_singleKanji.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @SingleKanjiTokenArray
    @Singleton
    @Provides
    fun providesSingleKanjiTokenArray(@ApplicationContext context: Context): TokenArray {
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(context.assets.open("single_kanji/token_singleKanji.dat")))
        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @SingleKanjiRank0ArrayLBSYomi
    fun provideSingleKanjiRank0ArrayLBSYomi(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank1ArrayLBSYomi
    fun provideSingleKanjiRank1ArrayLBSYomi(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank1ArrayIsLeafYomi
    fun provideSingleKanjiRank1ArrayIsLeaf(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.isLeaf.rank1GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiYomiLBSBooleanArray
    fun providesSingleKanjiYomiLBSBooleanArray(@SingleKanjiYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray =
        yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @SingleKanjiRank0ArrayTokenArrayBitvector
    fun provideSingleKanjiRank0ArrayTokenArrayBitvector(@SingleKanjiTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank0GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank1ArrayTokenArrayBitvector
    fun provideSingleKanjiRank1ArrayTokenArrayBitvector(@SingleKanjiTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank1GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank0ArrayTangoLBS
    fun provideSingleKanjiRank0ArrayLBSTango(@SingleKanjiTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @SingleKanjiRank1ArrayTangoLBS
    fun provideSingleKanjiRank1ArrayLBSTango(@SingleKanjiTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank1GetShortArray()


    @EmojiTangoTrie
    @Singleton
    @Provides
    fun provideEmojiTangoTrie(@ApplicationContext context: Context): LOUDS {
        val objectInputTango =
            ObjectInputStream(BufferedInputStream(context.assets.open("emoji/tango_emoji.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @EmojiYomiTrie
    @Singleton
    @Provides
    fun provideEmojiYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId {
        val objectInputYomi =
            ObjectInputStream(BufferedInputStream(context.assets.open("emoji/yomi_emoji.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @EmojiTokenArray
    @Singleton
    @Provides
    fun providesEmojiTokenArray(@ApplicationContext context: Context): TokenArray {
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(context.assets.open("emoji/token_emoji.dat")))
        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @EmojiRank0ArrayLBSYomi
    fun provideEmojiRank0ArrayLBSYomi(@EmojiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @EmojiRank1ArrayLBSYomi
    fun provideEmojiRank1ArrayLBSYomi(@EmojiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    @EmojiRank1ArrayIsLeafYomi
    fun provideEmojiRank1ArrayIsLeaf(@EmojiYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.isLeaf.rank1GetShortArray()

    @Singleton
    @Provides
    @EmojiYomiLBSBooleanArray
    fun providesEmojiYomiLBSBooleanArray(@EmojiYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray =
        yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @EmojiRank0ArrayTokenArrayBitvector
    fun provideEmojiRank0ArrayTokenArrayBitvector(@EmojiTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank0GetShortArray()

    @Singleton
    @Provides
    @EmojiRank1ArrayTokenArrayBitvector
    fun provideEmojiRank1ArrayTokenArrayBitvector(@EmojiTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank1GetShortArray()

    @Singleton
    @Provides
    @EmojiRank0ArrayTangoLBS
    fun provideEmojiRank0ArrayLBSTango(@EmojiTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @EmojiRank1ArrayTangoLBS
    fun provideEmojiRank1ArrayLBSTango(@EmojiTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank1GetShortArray()

    @EmoticonTangoTrie
    @Singleton
    @Provides
    fun provideEmoticonTangoTrie(@ApplicationContext context: Context): LOUDS {
        val objectInputTango =
            ObjectInputStream(BufferedInputStream(context.assets.open("emoticon/tango_emoticon.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @EmoticonYomiTrie
    @Singleton
    @Provides
    fun provideEmoticonYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId {
        val objectInputYomi =
            ObjectInputStream(BufferedInputStream(context.assets.open("emoticon/yomi_emoticon.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @EmoticonTokenArray
    @Singleton
    @Provides
    fun providesEmoticonTokenArray(@ApplicationContext context: Context): TokenArray {
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(context.assets.open("emoticon/token_emoticon.dat")))
        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @EmoticonRank0ArrayLBSYomi
    fun provideEmoticonRank0ArrayLBSYomi(@EmoticonYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @EmoticonRank1ArrayLBSYomi
    fun provideEmoticonRank1ArrayLBSYomi(@EmoticonYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    @EmoticonRank1ArrayIsLeafYomi
    fun provideEmoticonRank1ArrayIsLeaf(@EmoticonYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.isLeaf.rank1GetShortArray()

    @Singleton
    @Provides
    @EmoticonYomiLBSBooleanArray
    fun providesEmoticonYomiLBSBooleanArray(@EmoticonYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray =
        yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @EmoticonRank0ArrayTokenArrayBitvector
    fun provideEmoticonRank0ArrayTokenArrayBitvector(@EmoticonTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank0GetShortArray()

    @Singleton
    @Provides
    @EmoticonRank1ArrayTokenArrayBitvector
    fun provideEmoticonRank1ArrayTokenArrayBitvector(@EmoticonTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank1GetShortArray()

    @Singleton
    @Provides
    @EmoticonRank0ArrayTangoLBS
    fun provideEmoticonRank0ArrayLBSTango(@EmoticonTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @EmoticonRank1ArrayTangoLBS
    fun provideEmoticonRank1ArrayLBSTango(@EmoticonTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank1GetShortArray()


    @SymbolTangoTrie
    @Singleton
    @Provides
    fun provideSymbolTangoTrie(@ApplicationContext context: Context): LOUDS {
        val objectInputTango =
            ObjectInputStream(BufferedInputStream(context.assets.open("symbol/tango_symbol.dat")))
        return LOUDS().readExternalNotCompress(objectInputTango)
    }

    @SymbolYomiTrie
    @Singleton
    @Provides
    fun provideSymbolYomiTrie(@ApplicationContext context: Context): LOUDSWithTermId {
        val objectInputYomi =
            ObjectInputStream(BufferedInputStream(context.assets.open("symbol/yomi_symbol.dat")))
        return LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
    }

    @SymbolTokenArray
    @Singleton
    @Provides
    fun providesSymbolTokenArray(@ApplicationContext context: Context): TokenArray {
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(context.assets.open("symbol/token_symbol.dat")))
        val objectInputReadPOSTable =
            ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        return tokenArray
    }

    @Singleton
    @Provides
    @SymbolRank0ArrayLBSYomi
    fun provideSymbolRank0ArrayLBSYomi(@SymbolYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @SymbolRank1ArrayLBSYomi
    fun provideSymbolRank1ArrayLBSYomi(@SymbolYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.LBS.rank1GetShortArray()

    @Singleton
    @Provides
    @SymbolRank1ArrayIsLeafYomi
    fun provideSymbolRank1ArrayIsLeaf(@SymbolYomiTrie yomiTrie: LOUDSWithTermId): ShortArray =
        yomiTrie.isLeaf.rank1GetShortArray()

    @Singleton
    @Provides
    @SymbolYomiLBSBooleanArray
    fun providesSymbolYomiLBSBooleanArray(@SymbolYomiTrie yomiTrie: LOUDSWithTermId): BooleanArray =
        yomiTrie.LBS.toBooleanArray()

    @Singleton
    @Provides
    @SymbolRank0ArrayTokenArrayBitvector
    fun provideSymbolRank0ArrayTokenArrayBitvector(@SymbolTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank0GetShortArray()

    @Singleton
    @Provides
    @SymbolRank1ArrayTokenArrayBitvector
    fun provideSymbolRank1ArrayTokenArrayBitvector(@SymbolTokenArray tokenArray: TokenArray): ShortArray =
        tokenArray.bitvector.rank1GetShortArray()

    @Singleton
    @Provides
    @SymbolRank0ArrayTangoLBS
    fun provideSymbolRank0ArrayLBSTango(@SymbolTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank0GetShortArray()

    @Singleton
    @Provides
    @SymbolRank1ArrayTangoLBS
    fun provideSymbolRank1ArrayLBSTango(@SymbolTangoTrie tangoTrie: LOUDS): ShortArray =
        tangoTrie.LBS.rank1GetShortArray()


    @Singleton
    @Provides
    fun provideKanaKanjiHenkanEngine(
        graphBuilder: GraphBuilder,
        findPath: FindPath,
        @ConnectionIds connectionIds: ShortArray,
        @SystemTangoTrie systemTangoTrie: LOUDS,
        @SystemYomiTrie systemYomiTrie: LOUDSWithTermId,
        @SystemTokenArray systemTokenArray: TokenArray,
        @SystemRank0ArrayLBSYomi systemRank0ArrayLBSYomi: IntArray,
        @SystemRank1ArrayLBSYomi systemRank1ArrayLBSYomi: IntArray,
        @SystemRank1ArrayIsLeafYomi systemRank1ArrayIsLeaf: IntArray,
        @SystemRank0ArrayTokenArrayBitvector systemRank0ArrayTokenArrayBitvector: IntArray,
        @SystemRank1ArrayTokenArrayBitvector systemRank1ArrayTokenArrayBitvector: IntArray,
        @SystemRank0ArrayTangoLBS systemRank0ArrayTangoLBS: IntArray,
        @SystemRank1ArrayTangoLBS systemRank1ArrayTangoLBS: IntArray,
        @SystemYomiLBSBooleanArray systemYomiLBSBooleanArray: BooleanArray,

        @SingleKanjiTangoTrie singleKanjiTangoTrie: LOUDS,
        @SingleKanjiYomiTrie singleKanjiYomiTrie: LOUDSWithTermId,
        @SingleKanjiTokenArray singleKanjiTokenArray: TokenArray,
        @SingleKanjiRank0ArrayLBSYomi singleKanjiRank0ArrayLBSYomi: ShortArray,
        @SingleKanjiRank1ArrayLBSYomi singleKanjiRank1ArrayLBSYomi: ShortArray,
        @SingleKanjiRank1ArrayIsLeafYomi singleKanjiRank1ArrayIsLeaf: ShortArray,
        @SingleKanjiRank0ArrayTokenArrayBitvector singleKanjiRank0ArrayTokenArrayBitvector: ShortArray,
        @SingleKanjiRank1ArrayTokenArrayBitvector singleKanjiRank1ArrayTokenArrayBitvector: ShortArray,
        @SingleKanjiRank0ArrayTangoLBS singleKanjiRank0ArrayTangoLBS: ShortArray,
        @SingleKanjiRank1ArrayTangoLBS singleKanjiRank1ArrayTangoLBS: ShortArray,
        @SingleKanjiYomiLBSBooleanArray singleKanjiYomiLBSBooleanArray: BooleanArray,

        @EmojiTangoTrie emojiTangoTrie: LOUDS,
        @EmojiYomiTrie emojiYomiTrie: LOUDSWithTermId,
        @EmojiTokenArray emojiTokenArray: TokenArray,
        @EmojiRank0ArrayLBSYomi emojiRank0ArrayLBSYomi: ShortArray,
        @EmojiRank1ArrayLBSYomi emojiRank1ArrayLBSYomi: ShortArray,
        @EmojiRank1ArrayIsLeafYomi emojiRank1ArrayIsLeaf: ShortArray,
        @EmojiRank0ArrayTokenArrayBitvector emojiRank0ArrayTokenArrayBitvector: ShortArray,
        @EmojiRank1ArrayTokenArrayBitvector emojiRank1ArrayTokenArrayBitvector: ShortArray,
        @EmojiRank0ArrayTangoLBS emojiRank0ArrayTangoLBS: ShortArray,
        @EmojiRank1ArrayTangoLBS emojiRank1ArrayTangoLBS: ShortArray,
        @EmojiYomiLBSBooleanArray emojiYomiLBSBooleanArray: BooleanArray,

        @EmoticonTangoTrie emoticonTangoTrie: LOUDS,
        @EmoticonYomiTrie emoticonYomiTrie: LOUDSWithTermId,
        @EmoticonTokenArray emoticonTokenArray: TokenArray,
        @EmoticonRank0ArrayLBSYomi emoticonRank0ArrayLBSYomi: ShortArray,
        @EmoticonRank1ArrayLBSYomi emoticonRank1ArrayLBSYomi: ShortArray,
        @EmoticonRank1ArrayIsLeafYomi emoticonRank1ArrayIsLeaf: ShortArray,
        @EmoticonRank0ArrayTokenArrayBitvector emoticonRank0ArrayTokenArrayBitvector: ShortArray,
        @EmoticonRank1ArrayTokenArrayBitvector emoticonRank1ArrayTokenArrayBitvector: ShortArray,
        @EmoticonRank0ArrayTangoLBS emoticonRank0ArrayTangoLBS: ShortArray,
        @EmoticonRank1ArrayTangoLBS emoticonRank1ArrayTangoLBS: ShortArray,
        @EmoticonYomiLBSBooleanArray emoticonYomiLBSBooleanArray: BooleanArray,

        @SymbolTangoTrie symbolTangoTrie: LOUDS,
        @SymbolYomiTrie symbolYomiTrie: LOUDSWithTermId,
        @SymbolTokenArray symbolTokenArray: TokenArray,
        @SymbolRank0ArrayLBSYomi symbolRank0ArrayLBSYomi: ShortArray,
        @SymbolRank1ArrayLBSYomi symbolRank1ArrayLBSYomi: ShortArray,
        @SymbolRank1ArrayIsLeafYomi symbolRank1ArrayIsLeaf: ShortArray,
        @SymbolRank0ArrayTokenArrayBitvector symbolRank0ArrayTokenArrayBitvector: ShortArray,
        @SymbolRank1ArrayTokenArrayBitvector symbolRank1ArrayTokenArrayBitvector: ShortArray,
        @SymbolRank0ArrayTangoLBS symbolRank0ArrayTangoLBS: ShortArray,
        @SymbolRank1ArrayTangoLBS symbolRank1ArrayTangoLBS: ShortArray,
        @SymbolYomiLBSBooleanArray symbolYomiLBSBooleanArray: BooleanArray,
    ): KanaKanjiEngine {
        val kanaKanjiEngine = KanaKanjiEngine()

        kanaKanjiEngine.buildEngine(
            graphBuilder = graphBuilder,
            findPath = findPath,
            connectionIdList = connectionIds,
            systemTangoTrie = systemTangoTrie,
            systemYomiTrie = systemYomiTrie,
            systemTokenArray = systemTokenArray,
            systemRank0ArrayLBSYomi = systemRank0ArrayLBSYomi,
            systemRank1ArrayLBSYomi = systemRank1ArrayLBSYomi,
            systemRank1ArrayIsLeaf = systemRank1ArrayIsLeaf,
            systemRank0ArrayTokenArrayBitvector = systemRank0ArrayTokenArrayBitvector,
            systemRank1ArrayTokenArrayBitvector = systemRank1ArrayTokenArrayBitvector,
            systemRank0ArrayLBSTango = systemRank0ArrayTangoLBS,
            systemRank1ArrayLBSTango = systemRank1ArrayTangoLBS,
            systemYomiLBSBooleanArray = systemYomiLBSBooleanArray,

            singleKanjiTangoTrie = singleKanjiTangoTrie,
            singleKanjiYomiTrie = singleKanjiYomiTrie,
            singleKanjiTokenArray = singleKanjiTokenArray,
            singleKanjiRank0ArrayLBSYomi = singleKanjiRank0ArrayLBSYomi,
            singleKanjiRank1ArrayLBSYomi = singleKanjiRank1ArrayLBSYomi,
            singleKanjiRank1ArrayIsLeaf = singleKanjiRank1ArrayIsLeaf,
            singleKanjiRank0ArrayTokenArrayBitvector = singleKanjiRank0ArrayTokenArrayBitvector,
            singleKanjiRank1ArrayTokenArrayBitvector = singleKanjiRank1ArrayTokenArrayBitvector,
            singleKanjiRank0ArrayLBSTango = singleKanjiRank0ArrayTangoLBS,
            singleKanjiRank1ArrayLBSTango = singleKanjiRank1ArrayTangoLBS,
            singleKanjiYomiLBSBooleanArray = singleKanjiYomiLBSBooleanArray,

            emojiTangoTrie = emojiTangoTrie,
            emojiYomiTrie = emojiYomiTrie,
            emojiTokenArray = emojiTokenArray,
            emojiRank0ArrayLBSYomi = emojiRank0ArrayLBSYomi,
            emojiRank1ArrayLBSYomi = emojiRank1ArrayLBSYomi,
            emojiRank1ArrayIsLeaf = emojiRank1ArrayIsLeaf,
            emojiRank0ArrayTokenArrayBitvector = emojiRank0ArrayTokenArrayBitvector,
            emojiRank1ArrayTokenArrayBitvector = emojiRank1ArrayTokenArrayBitvector,
            emojiRank0ArrayLBSTango = emojiRank0ArrayTangoLBS,
            emojiRank1ArrayLBSTango = emojiRank1ArrayTangoLBS,
            emojiYomiLBSBooleanArray = emojiYomiLBSBooleanArray,

            emoticonTangoTrie = emoticonTangoTrie,
            emoticonYomiTrie = emoticonYomiTrie,
            emoticonTokenArray = emoticonTokenArray,
            emoticonRank0ArrayLBSYomi = emoticonRank0ArrayLBSYomi,
            emoticonRank1ArrayLBSYomi = emoticonRank1ArrayLBSYomi,
            emoticonRank1ArrayIsLeaf = emoticonRank1ArrayIsLeaf,
            emoticonRank0ArrayTokenArrayBitvector = emoticonRank0ArrayTokenArrayBitvector,
            emoticonRank1ArrayTokenArrayBitvector = emoticonRank1ArrayTokenArrayBitvector,
            emoticonRank0ArrayLBSTango = emoticonRank0ArrayTangoLBS,
            emoticonRank1ArrayLBSTango = emoticonRank1ArrayTangoLBS,
            emoticonYomiLBSBooleanArray = emoticonYomiLBSBooleanArray,

            symbolTangoTrie = symbolTangoTrie,
            symbolYomiTrie = symbolYomiTrie,
            symbolTokenArray = symbolTokenArray,
            symbolRank0ArrayLBSYomi = symbolRank0ArrayLBSYomi,
            symbolRank1ArrayLBSYomi = symbolRank1ArrayLBSYomi,
            symbolRank1ArrayIsLeaf = symbolRank1ArrayIsLeaf,
            symbolRank0ArrayTokenArrayBitvector = symbolRank0ArrayTokenArrayBitvector,
            symbolRank1ArrayTokenArrayBitvector = symbolRank1ArrayTokenArrayBitvector,
            symbolRank0ArrayLBSTango = symbolRank0ArrayTangoLBS,
            symbolRank1ArrayLBSTango = symbolRank1ArrayTangoLBS,
            symbolYomiLBSBooleanArray = symbolYomiLBSBooleanArray,
        )

        return kanaKanjiEngine
    }

    @Singleton
    @Provides
    fun providePressedKeyStatus(): PressedKeyStatus = PressedKeyStatus()

    @Singleton
    @Provides
    fun providesInputManager(@ApplicationContext context: Context): InputMethodManager =
        context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

    @DrawableReturn
    @Provides
    fun providesDrawableReturn(@ApplicationContext context: Context): Drawable =
        ContextCompat.getDrawable(
            context,
            R.drawable.baseline_keyboard_return_24
        )!!

    @DrawableKanaSmall
    @Provides
    fun providesDrawableKanaSmall(@ApplicationContext context: Context): Drawable =
        ContextCompat.getDrawable(
            context,
            R.drawable.kana_small
        )!!

    @DrawableEnglishSmall
    @Provides
    fun providesDrawableEnglishSmall(@ApplicationContext context: Context): Drawable =
        ContextCompat.getDrawable(
            context,
            R.drawable.english_small
        )!!

    @DrawableHenkan
    @Provides
    fun providesDrawableHenkan(@ApplicationContext context: Context): Drawable =
        ContextCompat.getDrawable(
            context,
            R.drawable.henkan
        )!!

    @DrawableSpaceBar
    @Provides
    fun providesDrawableSpaceBar(@ApplicationContext context: Context): Drawable =
        ContextCompat.getDrawable(
            context,
            R.drawable.baseline_space_bar_24
        )!!

    @DrawableRightArrow
    @Provides
    fun providesDrawableRightArrow(@ApplicationContext context: Context): Drawable =
        ContextCompat.getDrawable(
            context,
            R.drawable.baseline_arrow_right_alt_24
        )!!

    @DrawableLanguage
    @Provides
    fun providesDrawableLanguage(@ApplicationContext context: Context): Drawable =
        ContextCompat.getDrawable(
            context,
            R.drawable.logo_key
        )!!

    @DrawableNumberSmall
    @Provides
    fun providesDrawableNumberSmall(@ApplicationContext context: Context): Drawable =
        ContextCompat.getDrawable(
            context,
            R.drawable.number_small
        )!!

    @DrawableOpenBracket
    @Provides
    fun providesDrawableOpenBracket(@ApplicationContext context: Context): Drawable =
        ContextCompat.getDrawable(
            context,
            R.drawable.open_bracket
        )!!

}