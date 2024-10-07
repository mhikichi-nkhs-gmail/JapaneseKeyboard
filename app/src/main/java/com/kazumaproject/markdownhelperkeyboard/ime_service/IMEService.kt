package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.CombinedVibration
import android.os.Handler
import android.os.VibrationEffect
import android.os.VibratorManager
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.android.flexbox.FlexDirection
import com.kazumaproject.android.flexbox.FlexboxLayoutManager
import com.kazumaproject.android.flexbox.JustifyContent
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableEnglishSmall
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableHenkan
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableKanaSmall
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableLanguage
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableNumberSmall
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableOpenBracket
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableReturn
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableRightArrow
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.DrawableSpaceBar
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.MainDispatcher
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertDp2Px
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getCurrentInputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getDakutenSmallChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getNextInputChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getNextReturnInputChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isHiragana
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isLatinAlphabet
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.tenkey.listener.FlickListener
import com.kazumaproject.tenkey.listener.LongPressListener
import com.kazumaproject.tenkey.state.GestureType
import com.kazumaproject.tenkey.state.InputMode
import com.kazumaproject.tenkey.state.Key
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

@RequiresApi(Build.VERSION_CODES.S)
@AndroidEntryPoint
class IMEService : InputMethodService(), LifecycleOwner, InputConnection {

    @Inject
    @Named("main_ime_scope")
    lateinit var scope: CoroutineScope

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var inputMethodManager: InputMethodManager

    @Inject
    @DrawableReturn
    lateinit var drawableReturn: Drawable

    @Inject
    @DrawableKanaSmall
    lateinit var drawableKanaSmall: Drawable

    @Inject
    @DrawableHenkan
    lateinit var drawableHenkan: Drawable

    @Inject
    @DrawableEnglishSmall
    lateinit var drawableEnglishSmall: Drawable

    @Inject
    @DrawableSpaceBar
    lateinit var drawableSpaceBar: Drawable

    @Inject
    @DrawableRightArrow
    lateinit var drawableRightArrow: Drawable

    @Inject
    @DrawableLanguage
    lateinit var drawableLogo: Drawable

    @Inject
    @DrawableNumberSmall
    lateinit var drawableNumberSmall: Drawable

    @Inject
    @DrawableOpenBracket
    lateinit var drawableOpenBracket: Drawable

    @Inject
    lateinit var kanaKanjiEngine: KanaKanjiEngine

    @Inject
    lateinit var suggestionAdapter: SuggestionAdapter

    private var mainLayoutBinding: MainLayoutBinding? = null
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private val _inputString = MutableStateFlow(EMPTY_STRING)
    private var stringInTail = ""
    private val _dakutenPressed = MutableStateFlow(false)
    private val _suggestionList = MutableStateFlow<List<Candidate>>(emptyList())
    private val _suggestionFlag = MutableStateFlow(false)
    private val _suggestionViewStatus = MutableStateFlow(true)
    private var currentInputType: InputTypeForIME = InputTypeForIME.Text
    private var lastFlickConvertedNextHiragana = false
    private var isContinuousTapInputEnabled = false
    private var englishSpaceKeyPressed = false
    private var suggestionClickNum = 0
    private var isHenkan = false
    private var onLeftKeyLongPressUp = false
    private var onRightKeyLongPressUp = false
    private var onDeleteLongPressUp = false
    private var deleteKeyLongKeyPressed = false
    private var rightCursorKeyLongKeyPressed = false
    private var leftCursorKeyLongKeyPressed = false

    private val vibratorManager: VibratorManager by lazy {
        getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    }

    private val shortVibrationEffect: VibrationEffect by lazy {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    }

    private val shortCombinedVibration: CombinedVibration by lazy {
        CombinedVibration.createParallel(shortVibrationEffect)
    }

    private fun setVibrate() {
        appPreference.vibration_preference?.let {
            if (it) vibratorManager.vibrate(shortCombinedVibration)
        }
    }

    companion object {
        const val EMPTY_STRING = ""
        const val DELAY_TIME = 1000L
        const val LONG_DELAY_TIME = 64L
        const val N_BEST = 4
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateInputView(): View? {
        val ctx = ContextThemeWrapper(applicationContext, R.style.Theme_MarkdownKeyboard)
        mainLayoutBinding = MainLayoutBinding.inflate(LayoutInflater.from(ctx))
        return mainLayoutBinding?.root.apply {
            val flexboxLayoutManager = FlexboxLayoutManager(applicationContext).apply {
                flexDirection = FlexDirection.ROW
                justifyContent = JustifyContent.FLEX_START
            }
            setSuggestionRecyclerView(flexboxLayoutManager)
            setTenKeyListeners()
            if (lifecycle.currentState == Lifecycle.State.CREATED) {
                startScope(flexboxLayoutManager)
            } else {
                scope.coroutineContext.cancelChildren()
                startScope(flexboxLayoutManager)
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Timber.d("onUpdate onStartInput called $restarting")
        resetAllFlags()
        setCurrentInputType(attribute)
        _suggestionViewStatus.update { true }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        Timber.d("onUpdate onStartInputView called $restarting")
        mainLayoutBinding?.apply {
            suggestionRecyclerView.isVisible = true
            keyboardView.apply {
                isVisible = true
            }
        }
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onFinishInput() {
        super.onFinishInput()
        Timber.d("onUpdate onFinishInput Called")
        resetAllFlags()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Timber.d("onUpdate onFinishInputView")
        mainLayoutBinding?.keyboardView?.isVisible = true
        mainLayoutBinding?.suggestionRecyclerView?.isVisible = true
    }


    override fun onWindowHidden() {
        super.onWindowHidden()
        resetAllFlags()
    }

    override fun onDestroy() {
        Timber.d("onUpdate onDestroy")
        super.onDestroy()
        actionInDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                finishComposingText()
            }

            Configuration.ORIENTATION_LANDSCAPE -> {
                finishComposingText()
            }

            Configuration.ORIENTATION_UNDEFINED -> {
                finishComposingText()
            }

            else -> {
                finishComposingText()
            }
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        println("onUpdateSelection: $oldSelStart $oldSelEnd $newSelStart $newSelEnd $candidatesStart $candidatesEnd ${_inputString.value} $stringInTail")
        if (candidatesStart == -1 && candidatesEnd == -1) {
            if (stringInTail.isNotEmpty()) {
                _inputString.update { stringInTail }
                stringInTail = EMPTY_STRING
            } else {
                _inputString.update { EMPTY_STRING }
            }
            _suggestionFlag.update { !it }
        }
    }

    private fun setTenKeyListeners() {
        mainLayoutBinding?.keyboardView?.apply {
            setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                    Timber.d("Flick: $char $key $gestureType")
                    val insertString = _inputString.value
                    val sb = StringBuilder()
                    when (gestureType) {
                        GestureType.Null -> {

                        }

                        GestureType.Tap -> {
                            handleTapAndFlick(
                                key = key,
                                char = char,
                                insertString = insertString,
                                sb = sb,
                                isFlick = false,
                                gestureType = gestureType
                            )
                        }

                        else -> {
                            handleTapAndFlick(
                                key = key,
                                char = char,
                                insertString = insertString,
                                sb = sb,
                                isFlick = true,
                                gestureType = gestureType
                            )
                        }
                    }
                }
            })
            setOnLongPressListener(object : LongPressListener {
                override fun onLongPress(key: Key) {
                    handleLongPress(key)
                    Timber.d("Long Press: $key")
                }

            })
        }
    }

    private fun handleTapAndFlick(
        key: Key,
        char: Char?,
        insertString: String,
        sb: StringBuilder,
        isFlick: Boolean,
        gestureType: GestureType,
    ) {
        setVibrate()
        when (key) {
            Key.NotSelected -> {}
            Key.SideKeyEnter -> {
                if (_inputString.value.isNotEmpty()) {
                    handleNonEmptyInputEnterKey()
                } else {
                    handleEmptyInputEnterKey()
                }
            }

            Key.KeyDakutenSmall -> {
                handleDakutenSmallLetterKey(
                    sb = sb, isFlick = isFlick, char = char, insertString = insertString
                )
            }

            Key.SideKeyCursorLeft -> {
                if (!leftCursorKeyLongKeyPressed) {
                    handleLeftCursor(gestureType)
                }
                onLeftKeyLongPressUp = true
                leftCursorKeyLongKeyPressed = false
            }

            Key.SideKeyCursorRight -> {
                if (!rightCursorKeyLongKeyPressed) {
                    actionInRightKeyPressed(gestureType)
                }
                onRightKeyLongPressUp = true
                rightCursorKeyLongKeyPressed = false
            }

            Key.SideKeyDelete -> {
                if (!deleteKeyLongKeyPressed) {
                    handleDeleteKeyTap()
                }
                onDeleteLongPressUp = true
                deleteKeyLongKeyPressed = false
            }

            Key.SideKeyInputMode -> {
                setTenkeyIconsInHenkan()
            }

            Key.SideKeyPreviousChar -> {
                mainLayoutBinding?.keyboardView?.let {
                    when (it.currentInputMode) {
                        is InputMode.ModeNumber -> {

                        }

                        else -> {
                            setNextReturnInputCharacter()
                        }
                    }
                }
            }

            Key.SideKeySpace -> {
                if (!isSpaceKeyLongPressed) {
                    handleSpaceKeyClick()
                }
                isSpaceKeyLongPressed = false
            }

            Key.SideKeySymbol -> {

            }

            else -> {
                if (isFlick) {
                    char?.let {
                        sendCharFlick(
                            charToSend = it, insertString = insertString, sb = sb
                        )
                    }
                    isContinuousTapInputEnabled = true
                    lastFlickConvertedNextHiragana = true
                } else {
                    char?.let {
                        sendCharTap(
                            charToSend = it, insertString = insertString, sb = sb
                        )
                    }
                }
            }
        }
    }

    private fun handleLongPress(
        key: Key,
    ) {
        when (key) {
            Key.NotSelected -> {}
            Key.SideKeyEnter -> {}
            Key.KeyDakutenSmall -> {}
            Key.SideKeyCursorLeft -> {
                handleLeftLongPress()
                leftCursorKeyLongKeyPressed = true
            }

            Key.SideKeyCursorRight -> {
                handleRightLongPress()
                rightCursorKeyLongKeyPressed = true
            }

            Key.SideKeyDelete -> {
                if (isHenkan) {
                    cancelHenkanByLongPressDeleteKey()
                } else {
                    onDeleteLongPressUp = false
                    deleteLongPress()
                    _dakutenPressed.value = false
                    englishSpaceKeyPressed = false
                    deleteKeyLongKeyPressed = true
                }
            }

            Key.SideKeyInputMode -> {}
            Key.SideKeyPreviousChar -> {}
            Key.SideKeySpace -> {
                if (_inputString.value.isEmpty() && stringInTail.isEmpty()) {
                    isSpaceKeyLongPressed = true
                    showKeyboardPicker()
                }
            }

            Key.SideKeySymbol -> {}
            else -> {}
        }
    }

    private var isSpaceKeyLongPressed = false

    private fun showKeyboardPicker() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showInputMethodPicker()
    }

    private fun handleLeftCursor(gestureType: GestureType) {
        handleLeftKeyPress(gestureType)
        onLeftKeyLongPressUp = true
        if (_inputString.value.isBlank() || _inputString.value.isEmpty()) {
            _suggestionList.value = emptyList()
        }
    }

    private fun cancelHenkanByLongPressDeleteKey() {
        val selectedSuggestion = suggestionAdapter.suggestions[suggestionClickNum]
        deleteKeyLongKeyPressed = true
        suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionClickNum = 0
        val spannableString = if (_inputString.value.length == selectedSuggestion.length.toInt()) {
            SpannableString(_inputString.value + stringInTail)
        } else {
            stringInTail = EMPTY_STRING
            SpannableString(_inputString.value)
        }
        setComposingTextAfterEdit(_inputString.value, spannableString)
        isContinuousTapInputEnabled = true
        lastFlickConvertedNextHiragana = true
        isHenkan = false
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            post { scrollToPosition(0) }
        }
    }

    private fun startScope(
        flexboxLayoutManager: FlexboxLayoutManager
    ) = scope.launch {
        mainLayoutBinding?.let { mainView ->
            launch {
                _suggestionFlag.asStateFlow().buffer().collectLatest {
                    setSuggestionOnView(mainView)
                }
            }

            launch {
                _suggestionViewStatus.asStateFlow().collectLatest { isVisible ->
                    updateSuggestionViewVisibility(mainView, flexboxLayoutManager, isVisible)
                }
            }

            launch {
                _suggestionList.asStateFlow().buffer().collectLatest { suggestions ->
                    updateSuggestionList(mainView, suggestions)
                }
            }

            launch {
                _inputString.asStateFlow().buffer().collectLatest { inputString ->
                    processInputString(inputString)
                }
            }
        }
    }

    private fun updateSuggestionViewVisibility(
        mainView: MainLayoutBinding, flexboxLayoutManager: FlexboxLayoutManager, isVisible: Boolean
    ) {
        mainView.keyboardView.isVisible = isVisible
        mainView.suggestionVisibility.setImageDrawable(
            ContextCompat.getDrawable(
                applicationContext,
                if (isVisible) R.drawable.outline_arrow_drop_down_24 else R.drawable.outline_arrow_drop_up_24
            )
        )
        mainView.suggestionRecyclerView.layoutManager = flexboxLayoutManager.apply {
            flexDirection = if (isVisible) FlexDirection.COLUMN else FlexDirection.ROW
            justifyContent =
                if (isVisible) JustifyContent.SPACE_AROUND else JustifyContent.FLEX_START
        }

        val marginsSuggestionView =
            (mainView.suggestionViewParent.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin = 0
                topMargin = 0
                bottomMargin = calculateBottomMargin(isVisible)
                height = calculateHeight(isVisible)
            }
        mainView.suggestionViewParent.layoutParams = marginsSuggestionView
    }

    private fun calculateBottomMargin(isVisible: Boolean): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (isVisible) 200f.convertDp2Px(applicationContext) else 0
        } else {
            if (isVisible) 280f.convertDp2Px(applicationContext) else 0
        }
    }

    private fun calculateHeight(isVisible: Boolean): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (isVisible) 52f.convertDp2Px(applicationContext) else 252f.convertDp2Px(
                applicationContext
            )
        } else {
            if (isVisible) 54f.convertDp2Px(applicationContext) else 336f.convertDp2Px(
                applicationContext
            )
        }
    }

    private fun updateSuggestionList(
        mainView: MainLayoutBinding,
        suggestions: List<Candidate>,
    ) {
        suggestionAdapter.suggestions = suggestions
        mainView.apply {
            suggestionVisibility.isVisible = suggestions.isNotEmpty()
            suggestionRecyclerView.scrollToPosition(0)
        }
    }

    private suspend fun processInputString(inputString: String) {
        Timber.d("launchInputString: inputString: $inputString stringTail: $stringInTail")
        if (inputString.isNotEmpty()) {
            _suggestionFlag.update { !it }
            val spannableString = SpannableString(inputString + stringInTail)
            setComposingTextPreEdit(inputString, spannableString)
            delay(DELAY_TIME)
            if (shouldEnableContinuousTap(inputString)) {
                isContinuousTapInputEnabled = true
                lastFlickConvertedNextHiragana = true
                setComposingTextAfterEdit(inputString, spannableString)
            }
        } else {
            if (stringInTail.isNotEmpty()) {
                setComposingText(stringInTail, 0)
                onLeftKeyLongPressUp = true
                onDeleteLongPressUp = true
            } else {
                resetInputString()
                setTenkeyIconsEmptyInputString()
                onLeftKeyLongPressUp = true
                onRightKeyLongPressUp = true
                onDeleteLongPressUp = true
            }
        }
    }

    private fun shouldEnableContinuousTap(inputString: String): Boolean {
        return !isHenkan && inputString.isNotEmpty() && !onDeleteLongPressUp && !englishSpaceKeyPressed && !deleteKeyLongKeyPressed
    }

    private fun resetInputString() {
        _suggestionList.update { emptyList() }
    }

    private fun setCurrentInputType(attribute: EditorInfo?) {
        attribute?.apply {
            currentInputType = getCurrentInputTypeForIME(inputType)
            Timber.d("setCurrentInputType: $currentInputType $inputType")
            when (currentInputType) {
                InputTypeForIME.Text, InputTypeForIME.TextAutoComplete, InputTypeForIME.TextAutoCorrect, InputTypeForIME.TextCapCharacters, InputTypeForIME.TextCapSentences, InputTypeForIME.TextCapWords, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextFilter, InputTypeForIME.TextMultiLine, InputTypeForIME.TextImeMultiLine, InputTypeForIME.TextShortMessage, InputTypeForIME.TextLongMessage, InputTypeForIME.TextNoSuggestion, InputTypeForIME.TextPersonName, InputTypeForIME.TextPhonetic, InputTypeForIME.TextWebEditText, InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                    mainLayoutBinding?.keyboardView?.apply {
                        currentInputMode = InputMode.ModeJapanese
                        setInputModeSwitchState(InputMode.ModeJapanese)
                        setSideKeyPreviousState(true)
                    }
                }

                InputTypeForIME.TextEditTextInBookingTDBank,
                InputTypeForIME.TextUri,
                InputTypeForIME.TextPostalAddress,
                InputTypeForIME.TextEmailAddress,
                InputTypeForIME.TextWebEmailAddress,
                InputTypeForIME.TextPassword,
                InputTypeForIME.TextVisiblePassword,
                InputTypeForIME.TextWebPassword,
                -> {
                    mainLayoutBinding?.keyboardView?.apply {
                        currentInputMode = InputMode.ModeEnglish
                        setInputModeSwitchState(InputMode.ModeEnglish)
                        setSideKeyPreviousState(true)
                    }
                }

                InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate -> {

                }

                InputTypeForIME.Number,
                InputTypeForIME.NumberDecimal,
                InputTypeForIME.NumberPassword,
                InputTypeForIME.NumberSigned,
                InputTypeForIME.Phone,
                InputTypeForIME.Date,
                InputTypeForIME.Datetime,
                InputTypeForIME.Time,
                -> {
                    mainLayoutBinding?.keyboardView?.apply {
                        currentInputMode = InputMode.ModeNumber
                        setInputModeSwitchState(InputMode.ModeNumber)
                        setSideKeyPreviousState(false)
                    }
                }

            }
        }
    }

    private fun setSuggestionRecyclerView(
        flexboxLayoutManager: FlexboxLayoutManager
    ) {
        suggestionAdapter.apply {
            this.setOnItemClickListener {
                setVibrate()
                setCandidateClick(it)
            }
        }
        mainLayoutBinding?.let { mainView ->
            mainView.suggestionRecyclerView.apply {
                itemAnimator = null
                focusable = View.NOT_FOCUSABLE
            }
            suggestionAdapter.apply {
                mainView.suggestionRecyclerView.adapter = this
                mainView.suggestionRecyclerView.layoutManager = flexboxLayoutManager
            }
            mainView.suggestionVisibility.setOnClickListener {
                _suggestionViewStatus.update { !it }
            }
        }
    }

    private fun setCandidateClick(candidate: Candidate) {
        if (_inputString.value.isNotEmpty()) {
            commitCandidateText(candidate)
            resetFlagsSuggestionClick()
        }
    }

    private fun commitCandidateText(candidate: Candidate) {
        println("clicked candidate: $candidate ${_inputString.value}")
        val candidateType = candidate.type.toInt()
        if (candidateType == 5 || candidateType == 7 || candidateType == 8) {
            stringInTail = _inputString.value.substring(candidate.length.toInt())
        }
        if (stringInTail.isNotEmpty()) {
            commitText(candidate.string, 1)
        } else {
            commitText(candidate.string, 1)
            _inputString.update { EMPTY_STRING }
        }
    }

    private fun resetAllFlags() {
        Timber.d("onUpdate resetAllFlags called")
        _inputString.update { EMPTY_STRING }
        _suggestionList.update { emptyList() }
        stringInTail = EMPTY_STRING
        suggestionClickNum = 0
        isHenkan = false
        isContinuousTapInputEnabled = false
        deleteKeyLongKeyPressed = false
        leftCursorKeyLongKeyPressed = false
        rightCursorKeyLongKeyPressed = false
        _dakutenPressed.value = false
        englishSpaceKeyPressed = false
        lastFlickConvertedNextHiragana = false
        onDeleteLongPressUp = false
        isSpaceKeyLongPressed = false
        suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
    }

    private fun actionInDestroy() {
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            layoutManager = null
            adapter = null
        }
        mainLayoutBinding = null
        closeConnection()
        scope.coroutineContext.cancelChildren()
    }

    private fun resetFlagsSuggestionClick() {
        isHenkan = false
        suggestionClickNum = 0
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
        _suggestionViewStatus.update { true }
        suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
    }

    private fun resetFlagsEnterKey() {
        println("enter key reset is called")
        isHenkan = false
        suggestionClickNum = 0
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
        suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
    }

    private fun resetFlagsEnterKeyNotHenkan() {
        isHenkan = false
        suggestionClickNum = 0
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
        _inputString.update { EMPTY_STRING }
        stringInTail = EMPTY_STRING
        suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
    }

    private fun resetFlagsKeySpace() {
        onDeleteLongPressUp = false
        _dakutenPressed.value = false
        isContinuousTapInputEnabled = false
        lastFlickConvertedNextHiragana = false
        englishSpaceKeyPressed = false
    }

    private fun resetFlagsDeleteKey() {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        isHenkan = false
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
        suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
    }

    private fun setComposingTextPreEdit(
        inputString: String, spannableString: SpannableString
    ) {
        if (isContinuousTapInputEnabled && lastFlickConvertedNextHiragana) {
            spannableString.apply {
                setSpan(
                    BackgroundColorSpan(getColor(R.color.green)),
                    0,
                    inputString.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    UnderlineSpan(),
                    0,
                    inputString.length + stringInTail.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else {
            spannableString.apply {
                setSpan(
                    BackgroundColorSpan(getColor(R.color.green)),
                    0,
                    inputString.length - 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    BackgroundColorSpan(getColor(R.color.char_in_edit_color)),
                    inputString.length - 1,
                    inputString.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    UnderlineSpan(),
                    0,
                    inputString.length + stringInTail.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        Timber.d("launchInputString: setComposingTextPreEdit $spannableString")
        setComposingText(spannableString, 1)
    }

    private fun setComposingTextAfterEdit(
        inputString: String, spannableString: SpannableString
    ) {
        spannableString.apply {
            setSpan(
                BackgroundColorSpan(getColor(R.color.blue)),
                0,
                inputString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(UnderlineSpan(), 0, inputString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        Timber.d("launchInputString: setComposingTextAfterEdit $spannableString")
        setComposingText(spannableString, 1)
    }

    private fun setEnterKeyAction(listIterator: ListIterator<Candidate>) {
        _suggestionList.update { emptyList() }
        val nextSuggestion = listIterator.next()
        commitText(nextSuggestion.string, 1)
        println("enter key pressed: $stringInTail")
        if (stringInTail.isEmpty()) {
            _inputString.update { EMPTY_STRING }
        }
        resetFlagsEnterKey()
    }

    private fun setTenkeyIconsEmptyInputString() {
        mainLayoutBinding?.keyboardView?.apply {
            setSideKeyEnterDrawable(drawableRightArrow)
            setSideKeySpaceDrawable(drawableSpaceBar)
            when (currentInputMode) {
                is InputMode.ModeJapanese, is InputMode.ModeEnglish -> {
                    setBackgroundSmallLetterKey(drawableLogo)
                }

                is InputMode.ModeNumber -> {
                    setBackgroundSmallLetterKey(drawableNumberSmall)
                }
            }
        }
    }

    private fun setTenkeyIconsInHenkan() {
        mainLayoutBinding?.keyboardView?.apply {
            when (currentInputMode) {
                is InputMode.ModeJapanese -> {
                    setSideKeySpaceDrawable(drawableSpaceBar)
                    setSideKeyPreviousState(true)
                    if (_inputString.value.isNotEmpty()) {
                        if (_inputString.value.isNotEmpty() && _inputString.value.last()
                                .isLatinAlphabet()
                        ) {
                            setBackgroundSmallLetterKey(drawableEnglishSmall)
                        } else {
                            setBackgroundSmallLetterKey(drawableLogo)
                        }
                    } else {
                        setBackgroundSmallLetterKey(drawableLogo)
                    }
                }

                is InputMode.ModeEnglish -> {
                    setSideKeySpaceDrawable(drawableSpaceBar)
                    setBackgroundSmallLetterKey(drawableNumberSmall)
                    setSideKeyPreviousState(false)
                }

                is InputMode.ModeNumber -> {
                    setSideKeyPreviousState(true)
                    if (_inputString.value.isNotEmpty()) {
                        setSideKeySpaceDrawable(drawableHenkan)
                        if (_inputString.value.last().isHiragana()) {
                            setBackgroundSmallLetterKey(drawableKanaSmall)
                        } else {
                            setBackgroundSmallLetterKey(drawableLogo)
                        }
                    } else {
                        setSideKeySpaceDrawable(drawableSpaceBar)
                        setBackgroundSmallLetterKey(drawableLogo)
                    }
                }
            }
        }
    }

    private fun updateUIinHenkan(mainView: MainLayoutBinding) {
        mainView.keyboardView.apply {
            setSideKeyEnterDrawable(drawableReturn)
            when (currentInputMode) {
                InputMode.ModeJapanese -> {
                    if (_inputString.value.isNotEmpty() && _inputString.value.last().isHiragana()) {
                        setBackgroundSmallLetterKey(drawableKanaSmall)
                    } else {
                        setBackgroundSmallLetterKey(drawableLogo)
                    }
                    setSideKeySpaceDrawable(drawableHenkan)
                }

                InputMode.ModeEnglish -> {

                    if (_inputString.value.isNotEmpty() && _inputString.value.last()
                            .isLatinAlphabet()
                    ) {
                        setBackgroundSmallLetterKey(drawableEnglishSmall)
                    } else {
                        setBackgroundSmallLetterKey(drawableLogo)
                    }
                    setSideKeySpaceDrawable(drawableSpaceBar)
                }

                InputMode.ModeNumber -> {
                    setBackgroundSmallLetterKey(drawableNumberSmall)
                    setSideKeySpaceDrawable(drawableSpaceBar)
                }
            }
        }
    }

    private suspend fun setSuggestionOnView(
        mainView: MainLayoutBinding,
    ) {
        when {
            _inputString.value.isNotEmpty() -> {
                if (suggestionClickNum != 0) return
                setCandidates(mainView)
            }
        }
    }

    private suspend fun setCandidates(mainView: MainLayoutBinding) {
        val candidates = getSuggestionList()
        val filteredCandidates = if (stringInTail.isNotEmpty()) {
            candidates.filter { it.length.toInt() == _inputString.value.length }
        } else {
            candidates
        }
        _suggestionList.update {
            filteredCandidates
        }
        updateUIinHenkan(mainView)
    }

    private suspend fun getSuggestionList(): List<Candidate> {
        val queryText = _inputString.value
        return kanaKanjiEngine.getCandidates(queryText, N_BEST)
    }

    private fun deleteLongPress() = scope.launch {
        while (isActive) {
            if (_inputString.value.isEmpty() && stringInTail.isNotEmpty()) {
                enableContinuousTapInput()
                _suggestionList.update { emptyList() }
                return@launch
            }
            if (onDeleteLongPressUp) {
                enableContinuousTapInput()
                return@launch
            }
            if (_inputString.value.isNotEmpty()) {
                if (_inputString.value.length == 1) {
                    _suggestionList.update { emptyList() }
                    _inputString.update { EMPTY_STRING }
                    if (stringInTail.isEmpty()) setComposingText("", 0)
                } else {
                    _inputString.update { it.dropLast(1) }
                }
            } else {
                if (stringInTail.isEmpty()) {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                }
            }
            delay(LONG_DELAY_TIME)
        }
    }

    private fun enableContinuousTapInput() {
        isContinuousTapInputEnabled = true
        lastFlickConvertedNextHiragana = true
    }

    private fun setEnterKeyPress() {
        setVibrate()
        when (currentInputType) {
            InputTypeForIME.TextMultiLine, InputTypeForIME.TextImeMultiLine -> {
                commitText("\n", 1)
            }

            InputTypeForIME.None, InputTypeForIME.Text, InputTypeForIME.TextAutoComplete, InputTypeForIME.TextAutoCorrect, InputTypeForIME.TextCapCharacters, InputTypeForIME.TextCapSentences, InputTypeForIME.TextCapWords, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextFilter, InputTypeForIME.TextShortMessage, InputTypeForIME.TextLongMessage, InputTypeForIME.TextNoSuggestion, InputTypeForIME.TextPersonName, InputTypeForIME.TextPhonetic, InputTypeForIME.TextWebEditText, InputTypeForIME.TextUri, InputTypeForIME.TextPostalAddress, InputTypeForIME.TextEmailAddress, InputTypeForIME.TextWebEmailAddress, InputTypeForIME.TextPassword, InputTypeForIME.TextVisiblePassword, InputTypeForIME.TextWebPassword, InputTypeForIME.TextWebSearchView, InputTypeForIME.TextNotCursorUpdate, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextEditTextInBookingTDBank -> {
                Timber.d("Enter key: called 3\n")
                sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER
                    )
                )
            }

            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
            -> {
                performEditorAction(EditorInfo.IME_ACTION_DONE)
            }

            InputTypeForIME.TextSearchView -> {
                Timber.d(
                    "enter key search: ${EditorInfo.IME_ACTION_SEARCH}" + "\n${currentInputEditorInfo.inputType}" + "\n${currentInputEditorInfo.imeOptions}" + "\n${currentInputEditorInfo.actionId}" + "\n${currentInputEditorInfo.privateImeOptions}"
                )
                performEditorAction(EditorInfo.IME_ACTION_SEARCH)
            }

        }
    }

    private fun handleDeleteKeyTap() {
        setVibrate()
        when {
            _inputString.value.isNotEmpty() -> {
                if (isHenkan) {
                    if (suggestionClickNum == 0) suggestionClickNum = _suggestionList.value.size
                    suggestionClickNum -= 1
                    setConvertLetterInJapaneseFromButton(_suggestionList.value)
                    mainLayoutBinding?.suggestionRecyclerView?.apply {
                        smoothScrollToPosition(suggestionClickNum)
                        suggestionAdapter.updateHighlightPosition(suggestionClickNum)
                    }
                } else {
                    deleteStringCommon()
                    resetFlagsDeleteKey()
                }
            }

            else -> {
                if (stringInTail.isNotEmpty()) return
                sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            }
        }
    }

    private fun handleSpaceKeyClick() {
        if (_inputString.value.isNotBlank()) {
            mainLayoutBinding?.apply {
                keyboardView.let { tenkey ->
                    when (tenkey.currentInputMode) {
                        InputMode.ModeJapanese -> handleJapaneseModeSpaceKey(this)
                        else -> setSpaceKeyActionEnglishAndNumberNotEmpty()
                    }
                }
            }
        } else {
            if (stringInTail.isNotEmpty()) return
            setSpaceKeyActionEnglishAndNumberEmpty()
        }
        resetFlagsKeySpace()
    }

    private fun handleJapaneseModeSpaceKey(mainView: MainLayoutBinding) {
        isHenkan = true
        setConvertLetterInJapaneseFromButton(_suggestionList.value)
        mainView.suggestionRecyclerView.apply {
            smoothScrollToPosition(suggestionClickNum)
            suggestionAdapter.updateHighlightPosition(suggestionClickNum)
        }
        suggestionClickNum += 1
    }

    private fun handleNonEmptyInputEnterKey() {
        mainLayoutBinding?.keyboardView?.apply {
            when (currentInputMode) {
                InputMode.ModeJapanese -> {
                    if (isHenkan) {
                        handleHenkanModeEnterKey()
                    } else {
                        finishInputEnterKey()
                    }
                }

                else -> finishInputEnterKey()
            }
        }
    }

    private fun handleHenkanModeEnterKey() {
        if (suggestionClickNum > _suggestionList.value.size) suggestionClickNum = 0
        val listIterator =
            _suggestionList.value.listIterator(suggestionClickNum.coerceAtLeast(1) - 1)
        setEnterKeyAction(listIterator)
    }

    private fun handleEmptyInputEnterKey() {
        if (stringInTail.isNotEmpty()) {
            finishComposingText()
            stringInTail = EMPTY_STRING
        } else {
            setEnterKeyPress()
            isHenkan = false
            suggestionClickNum = 0
            suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
        }
    }

    private fun finishInputEnterKey() {
        finishComposingText()
        _inputString.update { EMPTY_STRING }
        resetFlagsEnterKeyNotHenkan()
    }

    private fun handleLeftKeyPress(gestureType: GestureType) {
        if (_inputString.value.isEmpty() && stringInTail.isEmpty()) {
            when (gestureType) {
                GestureType.FlickRight -> {
                    if (!isCursorAtBeginning()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT
                        )
                    )
                }

                GestureType.FlickTop -> {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
                }

                GestureType.FlickLeft -> {
                    if (!isCursorAtBeginning()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT
                        )
                    )
                }

                GestureType.FlickBottom -> {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
                }

                GestureType.Null -> {}
                GestureType.Tap -> {
                    if (!isCursorAtBeginning()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT
                        )
                    )
                }
            }
        } else if (!isHenkan) {
            lastFlickConvertedNextHiragana = true
            isContinuousTapInputEnabled = true
            englishSpaceKeyPressed = false
            suggestionClickNum = 0
            if (_inputString.value.isNotEmpty()) {
                stringInTail =
                    StringBuilder(stringInTail).insert(0, _inputString.value.last()).toString()
                _inputString.update { it.dropLast(1) }
            }
        }
    }

    private fun handleLeftLongPress() {
        if (!isHenkan) {
            lastFlickConvertedNextHiragana = true
            isContinuousTapInputEnabled = true
            onLeftKeyLongPressUp = false
            suggestionClickNum = 0
            asyncLeftLongPress()
        }
    }

    private fun asyncLeftLongPress() = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            if (onLeftKeyLongPressUp) return@launch
            if (stringInTail.isNotEmpty() && _inputString.value.isEmpty()) return@launch
            if (_inputString.value.isNotEmpty()) {
                updateLeftInputString()
            } else {
                _suggestionList.update { emptyList() }
                if (stringInTail.isEmpty()) {
                    if (!isCursorAtBeginning()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT
                        )
                    )
                }
            }
            delay(LONG_DELAY_TIME)
        }
    }

    private fun handleRightLongPress() {
        if (!isHenkan) {
            onRightKeyLongPressUp = false
            suggestionClickNum = 0
            lastFlickConvertedNextHiragana = true
            isContinuousTapInputEnabled = true
            asyncRightLongPress()
        } else {
            sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        }
    }

    private fun asyncRightLongPress() = scope.launch {
        while (isActive) {
            if (onRightKeyLongPressUp) return@launch
            if (stringInTail.isEmpty() && _inputString.value.isNotEmpty()) return@launch
            actionInRightKeyPressed()
            delay(LONG_DELAY_TIME)
        }
    }

    private fun updateLeftInputString() {
        if (_inputString.value.isNotEmpty()) {
            if (_inputString.value.length == 1) {
                stringInTail = StringBuilder(stringInTail).insert(0, _inputString.value).toString()
                _inputString.update { EMPTY_STRING }
                _suggestionList.update { emptyList() }
            } else {
                stringInTail =
                    StringBuilder(stringInTail).insert(0, _inputString.value.last()).toString()
                _inputString.update { it.dropLast(1) }
            }
        }
    }

    private fun actionInRightKeyPressed(gestureType: GestureType) {
        when {
            _inputString.value.isEmpty() -> handleEmptyInputString(gestureType)
            !isHenkan -> handleNonHenkan()
        }
    }

    private fun actionInRightKeyPressed() {
        when {
            _inputString.value.isEmpty() -> handleEmptyInputString()
            !isHenkan -> handleNonHenkan()
        }
    }

    private fun handleEmptyInputString(gestureType: GestureType) {
        if (stringInTail.isEmpty()) {
            when (gestureType) {
                GestureType.FlickRight -> {
                    if (!isCursorAtEnd()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
                        )
                    )
                }

                GestureType.FlickTop -> {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
                }

                GestureType.FlickLeft -> {
                    if (!isCursorAtEnd()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
                        )
                    )
                }

                GestureType.FlickBottom -> {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
                }

                GestureType.Null -> {}
                GestureType.Tap -> {
                    if (!isCursorAtEnd()) sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
                        )
                    )
                }
            }
        } else {
            val dropString = stringInTail.first()
            stringInTail = stringInTail.drop(1)
            _inputString.update { dropString.toString() }
        }
    }

    private fun isCursorAtBeginning(): Boolean {
        val extractedText = currentInputConnection.getExtractedText(ExtractedTextRequest(), 0)
        return extractedText?.selectionStart == 0
    }

    private fun isCursorAtEnd(): Boolean {
        if (currentInputConnection != null) {
            val extractedText = currentInputConnection.getExtractedText(ExtractedTextRequest(), 0)
            extractedText?.let {
                val textLength = it.text.length
                val cursorPosition = it.selectionEnd
                return cursorPosition == textLength
            }
        }
        return false
    }

    private fun handleEmptyInputString() {
        if (stringInTail.isEmpty()) {
            if (!isCursorAtEnd()) {
                sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
                    )
                )
            }
        } else {
            val dropString = stringInTail.first()
            stringInTail = stringInTail.drop(1)
            _inputString.update { dropString.toString() }
        }
    }

    private fun handleNonHenkan() {
        englishSpaceKeyPressed = false
        lastFlickConvertedNextHiragana = true
        isContinuousTapInputEnabled = true
        suggestionClickNum = 0
        if (stringInTail.isNotEmpty()) {
            val dropString = stringInTail.first()
            stringInTail = stringInTail.drop(1)
            _inputString.update { it + dropString }
        }
    }

    private fun appendCharToStringBuilder(
        char: Char, insertString: String, stringBuilder: StringBuilder
    ) {
        if (insertString.length == 1) {
            stringBuilder.append(char)
            _inputString.value = stringBuilder.toString()
        } else {
            try {
                stringBuilder.append(insertString).deleteCharAt(insertString.lastIndex).append(char)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            _inputString.update {
                stringBuilder.toString()
            }
        }
    }

    private fun deleteStringCommon() {
        val length = _inputString.value.length
        when {
            length > 1 -> {
                println("deleteStringCommon: ${_inputString.value}")
                _inputString.update {
                    it.dropLast(1)
                }
            }

            else -> {
                _inputString.update { EMPTY_STRING }
                _suggestionList.update { emptyList() }
                if (stringInTail.isEmpty()) setComposingText("", 0)
            }
        }
    }

    private fun setCurrentInputCharacterContinuous(
        char: Char, insertString: String, sb: StringBuilder
    ) {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed = false
        onDeleteLongPressUp = false
        if (insertString.isNotEmpty()) {
            sb.append(insertString).append(char)
            _inputString.update {
                sb.toString()
            }
        } else {
            _inputString.update {
                char.toString()
            }
        }
    }

    private fun setCurrentInputCharacter(
        char: Char, inputForInsert: String, sb: StringBuilder
    ) {
        if (inputForInsert.isNotEmpty()) {
            val hiraganaAtInsertPosition = inputForInsert.last()
            val nextChar = hiraganaAtInsertPosition.getNextInputChar(char)

            if (nextChar == null) {
                _inputString.value = sb.append(inputForInsert).append(char).toString()
            } else {
                appendCharToStringBuilder(nextChar, inputForInsert, sb)
            }
        } else {
            _inputString.update {
                char.toString()
            }
        }
    }

    private fun sendCharTap(
        charToSend: Char, insertString: String, sb: StringBuilder
    ) {
        when (currentInputType) {
            InputTypeForIME.None,
            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
            -> {
                sendKeyChar(charToSend)
            }

            else -> {
                if (isContinuousTapInputEnabled && lastFlickConvertedNextHiragana) {
                    setCurrentInputCharacterContinuous(
                        charToSend, insertString, sb
                    )
                    lastFlickConvertedNextHiragana = false
                } else {
                    setKeyTouch(
                        charToSend, insertString, sb
                    )
                }
            }
        }
    }

    private fun sendCharFlick(
        charToSend: Char, insertString: String, sb: StringBuilder
    ) {
        when (currentInputType) {
            InputTypeForIME.None,
            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
            -> {
                sendKeyChar(charToSend)
            }

            else -> {
                setCurrentInputCharacterContinuous(
                    charToSend, insertString, sb
                )
            }
        }
    }

    private fun setStringBuilderForConvertStringInHiragana(
        inputChar: Char, sb: StringBuilder
    ) {
        if (_inputString.value.length == 1) {
            sb.append(inputChar)
            _inputString.value = sb.toString()
        } else {
            sb.append(_inputString.value).deleteAt(_inputString.value.length - 1).append(inputChar)
            _inputString.value = sb.toString()
        }
    }

    private fun dakutenSmallLetter(
        sb: StringBuilder
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed = false
        if (_inputString.value.isNotEmpty()) {
            val insertPosition = _inputString.value.last()
            insertPosition.let { c ->
                if (c.isHiragana()) {
                    c.getDakutenSmallChar()?.let { dakutenChar ->
                        setStringBuilderForConvertStringInHiragana(dakutenChar, sb)
                    }
                }
            }
        }
    }

    private fun smallBigLetterConversionEnglish(
        sb: StringBuilder
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed = false

        if (_inputString.value.isNotEmpty()) {
            val insertPosition = _inputString.value.last()
            insertPosition.let { c ->
                if (!c.isHiragana()) {
                    c.getDakutenSmallChar()?.let { dakutenChar ->
                        setStringBuilderForConvertStringInHiragana(dakutenChar, sb)
                    }
                }
            }

        }
    }

    private fun handleDakutenSmallLetterKey(
        sb: StringBuilder, isFlick: Boolean, char: Char?, insertString: String
    ) {
        mainLayoutBinding?.keyboardView?.let {
            when (it.currentInputMode) {
                InputMode.ModeJapanese -> {
                    dakutenSmallLetter(sb)
                }

                InputMode.ModeEnglish -> {
                    smallBigLetterConversionEnglish(sb)
                }

                InputMode.ModeNumber -> {
                    if (isFlick) {
                        char?.let { c ->
                            sendCharFlick(
                                charToSend = c, insertString = insertString, sb = sb
                            )
                        }
                        isContinuousTapInputEnabled = true
                        lastFlickConvertedNextHiragana = true
                    } else {
                        char?.let { c ->
                            sendCharTap(
                                charToSend = c, insertString = insertString, sb = sb
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setKeyTouch(
        key: Char, insertString: String, sb: StringBuilder
    ) {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed = false
        lastFlickConvertedNextHiragana = false
        onDeleteLongPressUp = false
        isContinuousTapInputEnabled = false
        if (isHenkan) {
            finishComposingText()
            _inputString.value = key.toString()
            isHenkan = false
            suggestionAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
        } else {
            setCurrentInputCharacter(
                key, insertString, sb
            )
        }
    }

    private fun setConvertLetterInJapaneseFromButton(
        suggestions: List<Candidate>,
    ) {
        if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
        val listIterator = suggestions.listIterator(suggestionClickNum)
        println("setConvertLetterInJapaneseFromButton $suggestionClickNum")
        when {
            listIterator.hasNext() -> {
                setSuggestionComposingText(listIterator)
            }

            !listIterator.hasNext() -> {
                setSuggestionComposingTextIteratorLast(suggestions)
                suggestionClickNum = 0
            }
        }
    }

    private fun setSpaceKeyActionEnglishAndNumberNotEmpty() {
        if (stringInTail.isNotEmpty()) {
            commitText(_inputString.value + " " + stringInTail, 1)
            stringInTail = EMPTY_STRING
        } else {
            commitText(_inputString.value + " ", 1)
        }
        _inputString.value = EMPTY_STRING
    }

    private fun setSpaceKeyActionEnglishAndNumberEmpty() {
        if (stringInTail.isNotEmpty()) {
            commitText(" $stringInTail", 1)
            stringInTail = EMPTY_STRING
        } else {
            mainLayoutBinding?.keyboardView?.apply {
                if (currentInputMode == InputMode.ModeJapanese) {
                    commitText("　", 1)
                } else {
                    commitText(" ", 1)
                }
            }
        }
        _inputString.value = EMPTY_STRING
    }

    private fun setSuggestionComposingText(listIterator: ListIterator<Candidate>) {
        val nextSuggestion = listIterator.next()
        val candidateType = nextSuggestion.type.toInt()
        if (candidateType == 5 || candidateType == 7 || candidateType == 8) {
            val suggestionLength = nextSuggestion.length.toInt()
            val inputString = _inputString.value
            if (stringInTail.isNotEmpty()) {
                if (inputString.length != suggestionLength) {
                    stringInTail = inputString.substring(suggestionLength)
                }
            } else {
                stringInTail = inputString.substring(suggestionLength)
            }
        }
        val spannableString2 =
            if (stringInTail.isEmpty()) SpannableString(nextSuggestion.string) else SpannableString(
                nextSuggestion.string + stringInTail
            )
        spannableString2.apply {
            setSpan(
                BackgroundColorSpan(getColor(R.color.orange)),
                0,
                nextSuggestion.string.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        setComposingText(spannableString2, 1)
    }

    private fun setSuggestionComposingTextIteratorLast(suggestions: List<Candidate>) {
        val currentSuggestion = suggestions.last()
        val nextSuggestion = suggestions.first()
        if (nextSuggestion.length > currentSuggestion.length) stringInTail = EMPTY_STRING

        val candidateType = nextSuggestion.type.toInt()
        if (candidateType == 5 || candidateType == 7 || candidateType == 8) {
            stringInTail = _inputString.value.substring(nextSuggestion.length.toInt())
        }
        val spannableString2 =
            if (stringInTail.isEmpty()) SpannableString(nextSuggestion.string) else SpannableString(
                nextSuggestion.string + stringInTail
            )
        spannableString2.apply {
            setSpan(
                BackgroundColorSpan(getColor(R.color.orange)),
                0,
                nextSuggestion.string.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        setComposingText(spannableString2, 1)
    }

    private fun setNextReturnInputCharacter() {
        _dakutenPressed.value = true
        englishSpaceKeyPressed = false
        val insertForString = _inputString.value
        val sb = StringBuilder()
        if (insertForString.isNotEmpty()) {
            val insertPosition = _inputString.value.last()
            insertPosition.let { c ->
                c.getNextReturnInputChar()?.let { charForReturn ->
                    appendCharToStringBuilder(
                        charForReturn, insertForString, sb
                    )
                }
            }
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun getTextBeforeCursor(p0: Int, p1: Int): CharSequence? {
        println("getTextBeforeCursor")
        return currentInputConnection?.getTextBeforeCursor(p0, p1)
    }

    override fun getTextAfterCursor(p0: Int, p1: Int): CharSequence? {
        println("getTextAfterCursor")
        return currentInputConnection?.getTextAfterCursor(p0, p1)
    }

    override fun getSelectedText(p0: Int): CharSequence {
        println("getSelectedText")
        return currentInputConnection.getSelectedText(p0)
    }

    override fun getCursorCapsMode(p0: Int): Int {
        println("getCursorCapsMode")
        if (currentInputConnection == null) return 0
        return currentInputConnection.getCursorCapsMode(p0)
    }

    override fun getExtractedText(p0: ExtractedTextRequest?, p1: Int): ExtractedText? {
        println("getExtractedText")
        return currentInputConnection.getExtractedText(p0, p1)
    }

    override fun deleteSurroundingText(p0: Int, p1: Int): Boolean {
        println("deleteSurroundingText")
        if (currentInputConnection == null) return false
        return currentInputConnection.deleteSurroundingText(p0, p1)
    }

    override fun deleteSurroundingTextInCodePoints(p0: Int, p1: Int): Boolean {
        println("deleteSurroundingTextInCodePoints")
        if (currentInputConnection == null) return false
        return currentInputConnection.deleteSurroundingTextInCodePoints(p0, p1)
    }

    override fun setComposingText(p0: CharSequence?, p1: Int): Boolean {
        println("setComposingText : $p0 $p1 ")
        if (currentInputConnection == null) return false
        return currentInputConnection.setComposingText(p0, p1)
    }

    override fun setComposingRegion(p0: Int, p1: Int): Boolean {
        println("setComposingRegion")
        if (currentInputConnection == null) return false
        return currentInputConnection.setComposingRegion(p0, p1)
    }

    override fun finishComposingText(): Boolean {
        if (currentInputConnection == null) return false
        println("finishComposingText")
        return currentInputConnection.finishComposingText()
    }

    override fun commitText(p0: CharSequence?, p1: Int): Boolean {
        if (currentInputConnection == null) return false
        println("commitText")
        return currentInputConnection.commitText(p0, p1)
    }

    override fun commitCompletion(p0: CompletionInfo?): Boolean {
        println("commitCompletion")
        if (currentInputConnection == null) return false
        return currentInputConnection.commitCompletion(p0)
    }

    override fun commitCorrection(p0: CorrectionInfo?): Boolean {
        println("commitCorrection")
        if (currentInputConnection == null) return false
        return currentInputConnection.commitCorrection(p0)
    }

    override fun setSelection(p0: Int, p1: Int): Boolean {
        println("setSelection")
        if (currentInputConnection == null) return false
        return currentInputConnection.setSelection(p0, p1)
    }

    override fun performEditorAction(p0: Int): Boolean {
        println("performEditorAction")
        if (currentInputConnection == null) return false
        return currentInputConnection.performEditorAction(p0)
    }

    override fun performContextMenuAction(p0: Int): Boolean {
        println("performContextMenuAction")
        if (currentInputConnection == null) return false
        return currentInputConnection.performContextMenuAction(p0)
    }

    override fun beginBatchEdit(): Boolean {
        println("beginBatchEdit")
        if (currentInputConnection == null) return false
        return currentInputConnection.beginBatchEdit()
    }

    override fun endBatchEdit(): Boolean {
        println("endBatchEdit")
        if (currentInputConnection == null) return false
        return currentInputConnection.endBatchEdit()
    }

    override fun sendKeyEvent(p0: KeyEvent?): Boolean {
        println("sendKeyEvent")
        if (currentInputConnection == null) return false
        return currentInputConnection.sendKeyEvent(p0)
    }

    override fun clearMetaKeyStates(p0: Int): Boolean {
        println("clearMetaKeyStates")
        if (currentInputConnection == null) return false
        return currentInputConnection.clearMetaKeyStates(p0)
    }

    override fun reportFullscreenMode(p0: Boolean): Boolean {
        println("reportFullscreenMode")
        if (currentInputConnection == null) return false
        return currentInputConnection.reportFullscreenMode(p0)
    }

    override fun performPrivateCommand(p0: String?, p1: Bundle?): Boolean {
        println("performPrivateCommand")
        if (currentInputConnection == null) return false
        return currentInputConnection.performPrivateCommand(p0, p1)
    }

    override fun requestCursorUpdates(p0: Int): Boolean {
        println("requestCursorUpdates")
        if (currentInputConnection == null) return false
        return currentInputConnection.requestCursorUpdates(p0)
    }

    override fun getHandler(): Handler? {
        println("getHandler")
        return currentInputConnection?.handler
    }

    override fun closeConnection() {
        println("closeConnection")
        if (currentInputConnection == null) return
        return currentInputConnection.closeConnection()
    }

    override fun commitContent(p0: InputContentInfo, p1: Int, p2: Bundle?): Boolean {
        println("commitContent")
        if (currentInputConnection == null) return false
        return currentInputConnection.commitContent(p0, p1, p2)
    }
}
