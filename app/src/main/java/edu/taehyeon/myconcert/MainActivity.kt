package edu.taehyeon.myconcert

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatImageButton
import androidx.lifecycle.ViewModelProvider
import edu.taehyeon.myconcert.data.*
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var model: MyConcertViewModel
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var pageScroll: ScrollView? = null
    private var dataContainer: LinearLayout? = null
    private var artistPast = false
    private lateinit var locator: RegionLocator
    private var locationStatus: TextView? = null
    private var permissionRegionRevision: Int? = null
    private var locationRequestRevision: Int? = null
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val revision=permissionRegionRevision ?: return@registerForActivityResult
        permissionRegionRevision=null
        if(locator.hasPermission()) startLocating(revision)
        else model.locationMessage.value="위치 권한 없이도 지도에서 지역을 선택할 수 있어요."
    }
    private val bg: Int get() = color(if (model.dark) "#10151F" else "#FFFFFF")
    private val ink: Int get() = color(if (model.dark) "#F3F4F6" else "#111827")
    private val muted: Int get() = color(if (model.dark) "#A4AFC0" else "#6B7280")
    private val faint: Int get() = color(if (model.dark) "#A0AABE" else "#9CA3AF")
    private val line: Int get() = color(if (model.dark) "#344054" else "#E5E7EB")
    private val surface: Int get() = color(if (model.dark) "#1B2432" else "#F9FAFB")
    private val blue: Int get() = color(if (model.dark) "#77A9FF" else "#3B82F6")

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = if (getSharedPreferences("myconcert",0).getBoolean("dark",false)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[MyConcertViewModel::class.java]
        locator = RegionLocator(this)
        artistPast = savedInstanceState?.getBoolean("artistPast") ?: false
        enableEdgeToEdge()
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!model.back()) finish() }
        })
        model.route.observe(this) { render() }
        model.state.observe(this) { renderData(it) }
        model.locationMessage.observe(this) { locationStatus?.text=it.orEmpty() }
        if (savedInstanceState == null) {
            splash()
            root.postDelayed({ if (!isFinishing) { render(); model.load() } }, 650)
        } else if (model.state.value == LoadState()) model.load()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("artistPast", artistPast); super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        if(locationRequestRevision!=null) {
            locator.cancel();locationRequestRevision=null
            model.locationMessage.value="위치 확인이 중단됐어요. 내 위치를 눌러 다시 시도해 주세요."
        }
        super.onStop()
    }
    override fun onDestroy() { locator.close();super.onDestroy() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun color(hex: String) = Color.parseColor(hex)
    private fun rounded(fill: Int = bg, radius: Int = 16, stroke: Int? = line) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), it) }
    }
    private fun label(value: String, size: Int = 14, bold: Boolean = false, tint: Int = ink) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(tint); includeFontPadding = false
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun View.pad(n: Int) { setPadding(dp(n), dp(n), dp(n), dp(n)) }
    private fun LinearLayout.add(view: View, height: Int = -2, top: Int = 0, width: Int = -1) {
        addView(view, LinearLayout.LayoutParams(if (width < 0) width else dp(width), if (height < 0) height else dp(height)).apply { topMargin = dp(top) })
    }
    private fun space(parent: LinearLayout, height: Int) { parent.add(Space(this), height) }
    private fun action(value: String, onClick: () -> Unit): TextView = label(value, 13, true, blue).apply {
        gravity = Gravity.CENTER; minHeight = dp(48); setPadding(dp(8), 0, dp(8), 0)
        isClickable = true; isFocusable = true; setOnClickListener { onClick() }
    }
    private fun button(value: String, primary: Boolean = false, onClick: () -> Unit) = action(value, onClick).apply {
        textSize = 14f; background = rounded(if (primary) color("#3B82F6") else surface, 12, if (primary) null else line)
        if (primary) setTextColor(Color.WHITE)
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }
    private fun icon(resource: Int, description: String, size: Int = 20, click: (() -> Unit)? = null): View {
        val image = ImageView(this).apply {
            setImageResource(resource); scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = description
            if (model.dark) imageTintList = ColorStateList.valueOf(if (resource == R.drawable.ic_search_blue || resource == R.drawable.ic_next || resource == R.drawable.ic_calendar) blue else muted)
        }
        return if (click == null) image.apply { layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)) }
        else FrameLayout(this).apply {
            image.contentDescription=null;image.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            minimumWidth = dp(48); minimumHeight = dp(48); contentDescription = description
            isClickable = true; isFocusable = true; setOnClickListener { click() }
            addView(image, FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER))
        }
    }
    private fun avatar(artist: Artist?, size: Int = 32) = ArtistAvatarView(this,artist,size,surface,muted).apply {
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }
    private fun splash() {
        root.removeAllViews(); root.setBackgroundColor(bg)
        val frame = FrameLayout(this)
        val logo = ImageView(this).apply { setImageResource(R.drawable.brand_logo); contentDescription = "MyConcert" }
        frame.addView(logo, FrameLayout.LayoutParams(dp(166), dp(166), Gravity.CENTER))
        root.addView(frame, LinearLayout.LayoutParams(-1, -1))
    }
    private fun render() {
        root.removeAllViews(); dataContainer = null; locationStatus=null;root.setBackgroundColor(bg)
        WindowCompat.getInsetsController(window,root).isAppearanceLightStatusBars = !model.dark
        WindowCompat.getInsetsController(window,root).isAppearanceLightNavigationBars = !model.dark
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        pageScroll=scroll
        val viewport=FrameLayout(this)
        content = column().apply { setPadding(dp(20), dp(24), dp(20), dp(104)) }
        scroll.addView(content)
        viewport.addView(scroll,FrameLayout.LayoutParams(-1,-1))
        val top=button("↑ 맨 위로",true) { scroll.smoothScrollTo(0,0) }.apply {
            contentDescription="맨 위로 이동";visibility=View.GONE;elevation=dp(6).toFloat()
        }
        viewport.addView(top,FrameLayout.LayoutParams(-2,dp(48),Gravity.BOTTOM or Gravity.END).apply {
            rightMargin=dp(16);bottomMargin=dp(16)
        })
        scroll.setOnScrollChangeListener { _, _, y, _, _ -> top.visibility=if(y>dp(400)) View.VISIBLE else View.GONE }
        root.addView(viewport,LinearLayout.LayoutParams(-1,0,1f))
        when (model.route.value?.screen) {
            "home" -> home()
            "search" -> search()
            "onboarding", "settings" -> preferences()
            "artist", "list" -> concertList()
            "region" -> region()
            "detail" -> detailHeader()
        }
        content.add(projectCredits(),top=32)
        renderData(model.state.value ?: LoadState())
    }
    private fun projectCredits() = column().apply {
        add(View(this@MainActivity).apply { setBackgroundColor(line) },1)
        add(label(getString(R.string.project_credit),11,tint=muted).apply { gravity=Gravity.CENTER },top=20)
        add(label(getString(R.string.creator_credit),11,tint=muted).apply { gravity=Gravity.CENTER },top=8)
    }
    private fun favoriteButton(artist: Artist): View = AppCompatImageButton(this).apply {
        fun update() {
            val active=artist.id in model.favoriteIds
            isSelected=active
            setImageResource(if(active) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline)
            imageTintList=ColorStateList.valueOf(blue)
            background=rounded(color(if(model.dark) "#20334E" else "#EDF4FF"),24,color(if(model.dark) "#48658F" else "#C4D9FF"))
            contentDescription="${artist.name} 선호 아티스트 ${if(active) "해제" else "추가"}"
            ViewCompat.setStateDescription(this,if(active) "선호 아티스트에 추가됨" else "선호 아티스트에 추가하지 않음")
        }
        layoutParams=LinearLayout.LayoutParams(dp(48),dp(48)).apply { leftMargin=dp(8) }
        setPadding(dp(12),dp(12),dp(12),dp(12));scaleType=ImageView.ScaleType.FIT_CENTER
        update()
        setOnClickListener {
            if(!model.toggleFavorite(artist.id)) toast("최대 8팀까지 선택할 수 있어요.")
            update()
        }
    }
    private fun toolbar(title: String, trailing: View? = null) {
        val bar = row()
        bar.addView(icon(R.drawable.ic_back, "뒤로가기", 24) { if (!model.back()) model.finishSetup() }, LinearLayout.LayoutParams(dp(36), dp(48)))
        bar.addView(label(title, 24, true), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
        trailing?.let { bar.addView(it) }; content.add(bar); space(content, 24)
    }
    private fun topHeader(subtitle: String) {
        val bar = row()
        val heading = column().apply { add(label(subtitle, 14, tint = muted)); add(label("MyConcert", 24, true), top = 4) }
        bar.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(icon(R.drawable.ic_moon, if (model.dark) "라이트모드로 전환" else "다크모드로 전환") {
            model.toggleDark()
            delegate.localNightMode = if (model.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            render()
        }.apply { background = rounded(surface, 24, null) })
        content.add(bar); space(content, 24)
    }
    private fun searchBar() {
        val bar = row().apply { background = rounded(surface, 12); setPadding(dp(16), 0, dp(16), 0); minimumHeight = dp(52) }
        bar.addView(icon(R.drawable.ic_search, "아티스트 검색"))
        bar.addView(label("다른 아티스트 검색", 14, tint = faint), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        bar.contentDescription = "다른 아티스트 검색"; bar.isFocusable = true
        bar.setOnClickListener { model.navigate(Route("search")) }; content.add(bar, 52)
    }
    private fun tabs(parent: LinearLayout, names: List<String>, selected: Int, click: (Int) -> Unit) {
        val bar = row().apply { background = rounded(surface, 12, null); pad(4) }
        names.forEachIndexed { index, name ->
            bar.addView(action(name) { click(index) }.apply {
                setTextColor(if (index == selected) blue else muted)
                if (index == selected) background = rounded(bg, 9, line)
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
        }; parent.add(bar)
    }
    private fun home() {
        topHeader("선호하는 아티스트 공연 일정 모아보기"); searchBar(); space(content, 16)
        tabs(content, listOf("내 아티스트", "지역별 공연"), 0) { if (it == 1) model.navigate(Route("region")) }
        val section = row()
        section.addView(label("선호 아티스트", 14, true), LinearLayout.LayoutParams(0, -2, 1f))
        section.addView(action("편집") { model.navigate(Route("settings")) }); content.add(section, top = 12)
        val artists = row()
        model.favorites.forEach { artist ->
            val a = column().apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(4), dp(8), dp(4)) }
            a.addView(avatar(artist, 44)); a.add(label(artist.name, 11), top = 6, width = -2)
            a.contentDescription = "${artist.name} 공연 보기"; a.isFocusable = true
            a.setOnClickListener { artistPast = false; model.selectArtist(artist) }; artists.addView(a)
        }
        content.add(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(artists) })
        dataContainer = column(); content.add(dataContainer!!, top = 20)
    }
    private fun inputField(hint: String, initial: String = "", onSubmit: ((String)->Unit)? = null, onText: (String) -> Unit): EditText {
        val field = EditText(this).apply {
            setSingleLine(); textSize = 14f; setTextColor(ink); setHintTextColor(faint)
            this.hint = hint; background = null; setPadding(dp(12), 0, 0, 0)
            inputType = android.text.InputType.TYPE_CLASS_TEXT; imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setText(initial); contentDescription = hint
            setOnEditorActionListener { v, _, _ ->
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                clearFocus();onSubmit?.invoke(text.toString());true
            }
        }
        val bar = row().apply { background = rounded(surface, 12, blue); setPadding(dp(16), 0, dp(8), 0) }
        bar.addView(icon(R.drawable.ic_search_blue, "검색")); bar.addView(field, LinearLayout.LayoutParams(0, dp(52), 1f))
        bar.addView(icon(R.drawable.ic_close, "입력 지우기", 14) { field.setText("") }, LinearLayout.LayoutParams(dp(40), dp(48)))
        content.add(bar, 52)
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onText(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        }); return field
    }
    private fun artistRow(artist: Artist): View {
        val card = row().apply { background = rounded(bg); pad(12); minimumHeight = dp(76) }
        val open = row().apply { minimumHeight=dp(52) }
        open.addView(avatar(artist, 44))
        val text = column().apply { add(label(artist.name, 16, true)); add(label("${artist.english} · ${artist.kind}", 12, tint = muted), top = 4) }
        open.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12); rightMargin = dp(4) })
        open.addView(label("공연 보기",12,true,blue))
        open.addView(icon(R.drawable.ic_next, "공연 보기", 16))
        open.contentDescription = "${artist.name}, ${artist.kind}, 공연 보기"; open.isFocusable = true; open.isClickable = true
        open.setOnClickListener { artistPast = false; model.selectArtist(artist) }
        card.addView(open,LinearLayout.LayoutParams(0,-2,1f))
        if(artist.userAdded) card.addView(icon(R.drawable.ic_delete_outline,"${artist.name} 삭제",22) { confirmDeleteArtist(artist) })
        return card
    }
    private fun confirmDeleteArtist(artist:Artist) {
        AlertDialog.Builder(this).setTitle("${artist.name} 삭제")
            .setMessage("직접 추가한 아티스트와 저장된 검색명을 삭제할까요? 선호 아티스트와 최근 검색에서도 제거돼요. 나중에 다시 검색할 수 있어요.")
            .setNegativeButton("취소",null).setPositiveButton("삭제") { _, _ ->
                if(model.deleteCustomArtist(artist.id)) { render();toast("추가한 아티스트를 삭제했어요.") }
            }.show()
    }
    private fun preferences() {
        val initial = model.route.value?.screen == "onboarding"
        if (!initial) toolbar("선호 아티스트") else {
            val brand = ImageView(this).apply { setImageResource(R.drawable.brand_logo); contentDescription = "MyConcert" }
            content.add(brand, 64, width = 64); space(content, 24)
            content.add(label("좋아하는 아티스트를\n선택해 주세요", 26, true)); space(content, 12)
        }
        content.add(label("최대 8팀 · 언제든지 변경할 수 있어요.", 14, tint = muted)); space(content, 20)
        val list = ArtistTileGrid(this); lateinit var done: TextView
        val empty=label("일치하는 아티스트가 없어요. 아티스트 검색에서 직접 조회하고 추가할 수 있어요.",13,tint=muted)
        fun fill(query: String) {
            list.removeAllViews()
            val artists=ArtistMatcher.findArtists(model.catalog.artists, query)
            empty.visibility=if(artists.isEmpty()) View.VISIBLE else View.GONE
            artists.forEach { a ->
                list.addView(ArtistTile(this,a,ArtistTileColors(bg,color(if(model.dark) "#20334E" else "#EDF4FF"),surface,ink,muted,line,blue),a.id in model.favoriteIds) { tile ->
                    if(!model.toggleFavorite(a.id)) toast("최대 8팀까지 선택할 수 있어요.")
                    tile.isChecked=a.id in model.favoriteIds
                    done.text = if (initial) "${model.favoriteIds.size}팀 선택 · 시작하기" else "선택 완료 (${model.favoriteIds.size}팀)"
                })
            }
        }
        inputField("한글·영문 아티스트 이름 검색",model.preferenceSearchText) {
            model.preferenceSearchText=it;fill(it)
        }; content.add(empty,top=12);content.add(list,top=12)
        done = button(if (initial) "${model.favoriteIds.size}팀 선택 · 시작하기" else "선택 완료 (${model.favoriteIds.size}팀)", true) { model.finishSetup() }
        done.setPadding(dp(20), dp(16), dp(20), dp(16))
        root.addView(done, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(20), dp(8), dp(20), dp(12)) }); fill(model.preferenceSearchText)
    }
    private fun search() {
        toolbar("아티스트 검색"); content.add(label("궁금한 아티스트의 공연을 찾아보세요.", 14, tint = muted)); space(content, 12)
        val body = column()
        inputField("아티스트 이름을 입력하세요", model.searchText,onSubmit={ input ->
            runCatching {
                val exact=model.catalog.artists.firstOrNull { artist -> artist.aliases.any { ArtistMatcher.normalize(it)==ArtistMatcher.normalize(input) } }
                if(exact!=null) model.selectArtist(exact) else model.directSearch(input)
            }.onFailure { toast(it.message ?: "검색명을 확인해 주세요.") }
        }) { model.searchText = it; searchBody(body, it) }
        content.add(label("한글·영문 공식 표기로 예정 공연과 이전 공연까지", 12, tint = muted), top = 12)
        content.add(body, top = 28); searchBody(body, model.searchText)
    }
    private fun searchBody(body: LinearLayout, input: String) {
        body.removeAllViews()
        if (input.isBlank()) {
            val header = row(); header.addView(label("최근 검색", 18, true), LinearLayout.LayoutParams(0, -2, 1f))
            header.addView(action("전체 삭제") { model.removeRecent(); searchBody(body, input) }); body.add(header)
            val chips = row()
            model.recent().forEach { a ->
                val chip = row().apply { background = rounded(bg, 24); setPadding(dp(12), 0, dp(2), 0) }
                chip.addView(action(a.name) { artistPast = false; model.selectArtist(a) }.apply { setTextColor(ink); textSize = 13f })
                chip.addView(icon(R.drawable.ic_close, "${a.name} 검색 기록 삭제", 14) { model.removeRecent(a.id); searchBody(body, input) }, LinearLayout.LayoutParams(dp(40), dp(48)))
                chips.addView(chip, LinearLayout.LayoutParams(-2, dp(48)).apply { rightMargin = dp(8) })
            }
            if (model.recent().isEmpty()) body.add(label("아티스트를 선택하면 여기에 기록돼요.", 13, tint = faint), top = 12)
            else body.add(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, top = 12)
            body.add(label("이 아티스트는 어때요?", 18, true), top = 32)
            body.add(label("새로운 아티스트의 공연 일정도 만나보세요.", 13, tint = muted), top = 6)
            listOf("iu", "day6", "aespa").mapNotNull(model.catalog::byId).forEach { body.add(artistRow(it), top = 12) }
            if(model.catalog.savedArtists.isNotEmpty()) {
                body.add(label("내가 추가한 아티스트",18,true),top=28)
                model.catalog.savedArtists.forEach { body.add(artistRow(it),top=12) }
            }
            body.add(info("목록에 없는 아티스트도 직접 검색할 수 있어요.\n공연이 확인된 검색명은 이 기기에 저장해요."), top = 32)
        } else {
            val artists = ArtistMatcher.findArtists(model.catalog.artists, input); body.add(label("아티스트 ${artists.size}팀", 18, true))
            val exact=artists.any { a -> a.aliases.any { ArtistMatcher.normalize(it)==ArtistMatcher.normalize(input) } }
            if(!exact) {
                body.add(button("‘${input.trim()}’ 공연 직접 검색",true) {
                    runCatching { model.directSearch(input) }.onFailure { toast(it.message ?: "검색명을 확인해 주세요.") }
                },top=16)
                body.add(label("공연명·출연진에서 일치를 확인한 검색명은 자동 저장돼요.",12,tint=muted),top=8)
            }
            artists.forEach { body.add(artistRow(it), top = 12) }
        }
    }
    private fun concertList() {
        val route = model.route.value!!; val artist = route.artistId?.let(model.catalog::byId)
        toolbar(artist?.name ?: if (route.past) "이전 공연 이력" else "예정 공연")
        if (artist != null) {
            val bar = row(); bar.addView(avatar(artist, 52))
            val bio = column().apply {
                add(label("${artist.english} · ${artist.kind}", 14, tint = muted))
                add(label(if(artist.userAdded) "입력한 이름·출연진 기준으로 조회" else "공식명·출연진으로 확인한 공연", 12, tint = muted), top = 6)
            }
            bar.addView(bio, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
            bar.addView(favoriteButton(artist))
            content.add(bar); space(content, 16)
            content.add(action("프로필 사진 출처") { open(ArtistProfileParser.searchUrl(artist)) })
            content.add(button("검색 표기 · ${artist.aliases.joinToString(" / ")}") { showSearchInfo(model.catalog.byId(artist.id) ?: artist) }); space(content, 20)
            if(artist.userAdded) {
                content.add(button("추가한 아티스트 삭제") { confirmDeleteArtist(artist) });space(content,16)
            }
            tabs(content, listOf("예정 공연", "이전 공연"), if (artistPast) 1 else 0) { artistPast = it == 1; render() }
        }
        val range = row(); range.addView(label("조회 기간", 13, tint = muted), LinearLayout.LayoutParams(0, -2, 1f))
        range.addView(action("각 ${model.days}일  ⌄") {
            AlertDialog.Builder(this).setTitle("공연 조회 기간").setItems(arrayOf("최근·향후 30일", "최근·향후 90일", "최근·향후 180일", "최근·향후 1년")) { _, i ->
                model.days = listOf(30L,90L,180L,365L)[i]; model.load(); render()
            }.show()
        }); content.add(range, top = 12)
        if (artist != null) {
            content.add(CheckBox(this).apply {
                text = "축제·공동 공연의 출연진까지 검색"; textSize = 13f; setTextColor(muted)
                buttonTintList = ColorStateList.valueOf(blue); isChecked = model.expanded
                setOnCheckedChangeListener { _, checked -> model.expanded = checked; model.load() }
            })
            content.add(label("기본 검색은 공식명·투어명 기준이에요. 축제 검색은 멤버 출연 정보까지 확인하므로 시간이 더 걸릴 수 있어요.", 12, tint = muted), top = 4)
        }; dataContainer = column(); content.add(dataContainer!!, top = 16)
    }
    private fun showSearchInfo(a: Artist) {
        if(a.userAdded) {
            val current=model.catalog.byId(a.id) ?: a
            val status=if(current.verifiedQueries.isEmpty()) "아직 일치 공연을 확인하지 못한 검색명이에요." else "공연 일치 확인 · 이 기기에 저장됨: ${current.verifiedQueries.joinToString()}"
            AlertDialog.Builder(this).setTitle("${current.name} 검색명")
                .setMessage("검색 표기: ${current.aliases.joinToString()}\n\n$status\n\n입력한 이름과 공연명·출연진을 비교해 조회해요. 같은 아티스트의 다른 한글·영문 표기를 추가할 수 있어요. 멤버 개인 이름을 그룹 검색명으로 넣지 마세요.")
                .setPositiveButton("확인",null).setNeutralButton("검색명 편집") { _, _ -> editSearchNames(current) }.show()
            return
        }
        val verified = if (a.verifiedQueries.isEmpty()) "표본 기간에 일치한 공연 미확인 · 공식 표기 사전 등록" else "표본 응답 확인: ${a.verifiedQueries.joinToString()}"
        AlertDialog.Builder(this).setTitle("${a.name} 검색 기준").setMessage(
            "공식 표기: ${a.aliases.joinToString()}\n\n$verified\n\n멤버 이름은 후보를 찾는 데만 사용하고, 멤버 1명의 개인 공연은 그룹 공연으로 확정하지 않아요.\n\n투어명만 일치하거나 출연 여부가 불확실하면 결과에서 제외해요. KOPIS에 등록되지 않은 일정은 표시되지 않아요.")
            .setPositiveButton("확인", null).setNeutralButton("공식 사이트") { _, _ -> open(a.source) }.show()
    }
    private fun editSearchNames(artist:Artist) {
        val input=EditText(this).apply {
            setText(artist.aliases.joinToString("\n"));hint="같은 아티스트의 이름을 한 줄에 하나씩 입력";minLines=3
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            contentDescription="아티스트 검색명 편집"
        }
        val container=column().apply { pad(20);add(input) }
        val dialog=AlertDialog.Builder(this).setTitle("검색명 편집").setView(container).setNegativeButton("취소",null)
            .setPositiveButton("저장 후 조회",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val values=input.text.toString().split(Regex("[\\n,]")).map(String::trim).filter(String::isNotEmpty)
            runCatching { model.editSearchAliases(artist.id,values) }.onSuccess { dialog.dismiss();render() }
                .onFailure { input.error=it.message }
        } }
        dialog.show()
    }
    private fun region() {
        topHeader("지역별 공연 일정 모아보기"); searchBar(); space(content, 16)
        tabs(content, listOf("내 아티스트", "지역별 공연"), 1) { if (it == 0) { if (!model.back()) model.navigate(Route("home"), false) } }
        val panel = column().apply { background = rounded(bg); pad(16) }
        val mapHeading=row()
        mapHeading.addView(label("공연 지역 선택",16,true),LinearLayout.LayoutParams(0,-2,1f))
        mapHeading.addView(action("내 위치") { requestCurrentRegion() })
        panel.add(mapHeading)
        panel.add(label("연결된 점이 각 지역의 지도상 위치예요.", 12, tint = faint), top = 4)
        locationStatus=label(model.locationMessage.value.orEmpty(),12,tint=muted)
        panel.add(locationStatus!!,top=8)
        panel.add(RegionMapView(this,model.regionCode,model.dark) {
            locator.cancel();locationRequestRevision=null
            model.selectRegion(it.code);render()
        },top=8)
        content.add(panel,top=16)
        val bar = row(); bar.addView(label("${Regions.byCode(model.regionCode)?.name}의 예정 공연", 18, true), LinearLayout.LayoutParams(0,-2,1f))
        bar.addView(action("${model.days}일 ⌄") {
            AlertDialog.Builder(this).setTitle("향후 조회 기간").setItems(arrayOf("30일", "90일", "180일")) { _, i -> model.days = listOf(30L,90L,180L)[i]; model.load(); render() }.show()
        }); content.add(bar, top = 16)
        content.add(label("조회 : 향후 ${model.days}일 기준",12,tint=muted),top=4)
        content.add(label("대중음악·복합 장르 · 모든 아티스트", 12, tint = muted),top=6)
        if(model.regionCode in listOf("29","46")) content.add(label("광주·전남은 통합 지역의 공연장 주소를 기준으로 구분해요.",12,tint=muted),top=8)
        dataContainer = column(); content.add(dataContainer!!, top = 16)
        if(model.shouldSuggestLocation) content.post { if(model.route.value?.screen=="region" && model.shouldSuggestLocation) requestCurrentRegion() }
    }
    private fun requestCurrentRegion() {
        model.markLocationPrompted()
        if(locator.hasPermission()) startLocating(model.regionRevision)
        else {
            permissionRegionRevision=model.regionRevision
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }
    private fun startLocating(revision:Int) {
        if(model.route.value?.screen!="region" || model.regionRevision!=revision) return
        locationRequestRevision=revision
        model.locationMessage.value="현재 위치의 시·도를 확인하고 있어요…"
        locator.locate({ region ->
            locationRequestRevision=null
            if(model.regionRevision==revision) { model.selectRegion(region.code,fromLocation=true);if(model.route.value?.screen=="region") render() }
        },{ message ->
            locationRequestRevision=null
            if(model.regionRevision==revision) model.locationMessage.value=message
        })
    }
    private fun info(message: String): View = row().apply {
        background = rounded(surface, 12, null); pad(16); addView(icon(R.drawable.ic_calendar, "공연 안내"))
        addView(label(message, 13, tint = muted), LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin = dp(12) })
    }
    private fun heading(parent: LinearLayout, name: String, subtitle:String?=null, more: (() -> Unit)? = null) {
        val bar = row(); bar.addView(label(name,18,true), LinearLayout.LayoutParams(0,-2,1f))
        more?.let { bar.addView(action("더보기", it)) }; parent.add(bar)
        subtitle?.let { parent.add(label(it,12,tint=muted),top=4) }
        space(parent, 12)
    }
    private fun renderData(state: LoadState) {
        val target = dataContainer ?: return; target.removeAllViews()
        if (state.loading) {
            val box = column().apply { gravity = Gravity.CENTER; pad(24) }
            box.add(ProgressBar(this).apply { indeterminateTintList = ColorStateList.valueOf(blue) }, 32, width = 32)
            box.add(label(state.progress, 13, tint = muted), top = 16)
            box.add(button("조회 중단") { model.cancelSearch() }, top = 16)
            target.add(box); return
        }
        if (state.error != null) { target.add(info(state.error)); target.add(button("다시 시도") { model.load(true) }, top = 12); return }
        val route = model.route.value ?: return
        if (route.screen == "detail") { state.detail?.let { detailBody(target,it,state.staleDetail) }; return }
        val result = state.result ?: return
        val time = Instant.ofEpochMilli(result.savedAt).atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))
        if (result.warnings.isNotEmpty()) target.add(info(result.warnings.joinToString("\n")), top = 8)
        if (route.screen == "home") {
            if (model.favorites.isEmpty()) {
                target.add(info("좋아하는 아티스트의 공연을 모아보세요.\n선호 아티스트를 먼저 선택해 주세요."))
                target.add(button("아티스트 선택하기", true) { model.navigate(Route("settings")) }, top = 16); return
            }
            heading(target,"예정 공연 · 가까운 날짜순","조회 : 향후 ${model.days}일 기준") { model.navigate(Route("list")) }
            showConcerts(target, Dates.sorted(result.concerts,false,model.today).take(3), false); space(target,24)
            heading(target,"이전 공연 이력","조회 : 최근 ${model.days}일 기준") { model.navigate(Route("list",past=true)) }
            showConcerts(target, Dates.sorted(result.concerts,true,model.today).take(2), true)
        } else {
            val past = if (route.screen == "artist") artistPast else route.past; val items = Dates.sorted(result.concerts,past,model.today)
            val meta = row(); meta.addView(label("전체 ${items.size}건",14,true,blue), LinearLayout.LayoutParams(0,-2,1f))
            meta.addView(label(if (past) "최근 공연순" else "가까운 날짜순",12,tint=muted)); target.add(meta)
            if(route.screen!="region") target.add(label("조회 : ${if(past) "최근" else "향후"} ${model.days}일 기준",12,tint=muted),top=6)
            route.artistId?.let(model.catalog::byId)?.takeIf { it.userAdded }?.let { artist ->
                target.add(info(if(artist.verifiedQueries.isEmpty()) "일치 공연을 찾으면 검색명이 이 기기에 저장돼요. 다른 표기도 검색명 편집에서 추가할 수 있어요." else "확인된 검색명 저장됨 · ${artist.verifiedQueries.joinToString() }"),top=12)
            }
            space(target,12)
            showConcerts(target,items,past)
            if (result.uncertain > 0) target.add(info("출연 여부가 불확실한 ${result.uncertain}건은 제외했어요.\n공연명과 출연진을 확인한 결과만 표시해요."),top=16)
        }
        target.add(label("KOPIS 제공 · ${if (result.cached) "저장된 정보" else "조회"} $time\n${model.today.minusDays(if (route.screen == "region") 0 else model.days).format(Dates.display)} ~ ${model.today.plusDays(model.days).format(Dates.display)} · 등록된 공연 기준",11,tint=faint),top=20)
        target.add(button("새로고침") { model.load(true) },top=12)
    }
    private fun showConcerts(parent: LinearLayout, items: List<Concert>, past: Boolean) {
        if (items.isEmpty()) parent.add(info(if (past) "이 기간에 확인된 이전 공연이 없어요.\n조회 기간을 넓혀 확인해 보세요." else "이 기간에 확인된 예정 공연이 없어요.\n새 일정이 KOPIS에 등록되면 조회할 수 있어요."))
        else {
            // Expanded cards live inside this container. Metadata and refresh remain its following siblings.
            val list=column();parent.add(list)
            fun append(offset: Int) {
                items.drop(offset).take(40).forEach { list.add(concertCard(it),top=12) }
                val next = offset + 40
                if (items.size > next) {
                    lateinit var more: TextView
                    more = button("공연 더 보기 (${next}/${items.size})") { list.removeView(more); append(next) }
                    list.add(more,top=16)
                }
            }
            append(0)
        }
    }
    private fun concertCard(c: Concert): View {
        val card = column().apply { background = rounded(bg); pad(16) }
        val artist = c.artistIds.firstOrNull()?.let(model.catalog::byId); val header = row(); header.addView(avatar(artist))
        header.addView(label(artist?.name ?: c.genre.ifBlank { "공연" },14,true),LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(8) })
        header.addView(label(c.start.format(Dates.display),12,tint=faint)); card.add(header)
        card.add(label(c.title,16,true),top=12); card.add(label(c.venue,14,tint=muted),top=4)
        if (c.end != c.start) card.add(label("~ ${c.end.format(Dates.display)}${if (!c.start.isAfter(model.today) && !c.end.isBefore(model.today)) " · 진행 중" else ""}",11,tint=faint),top=6)
        card.contentDescription="${artist?.name.orEmpty()} ${c.title}, ${c.start}, ${c.venue}, 상세 보기"
        card.isFocusable=true; card.setOnClickListener { model.navigate(Route("detail",concert=c)) }; return card
    }
    private fun detailHeader() {
        toolbar("공연 상세",action("공유") {
            val c=model.state.value?.detail ?: model.route.value?.concert ?: return@action
            val text="${c.title}\n${c.start.format(Dates.display)} ~ ${c.end.format(Dates.display)}\n${c.venue}\n${c.links.firstOrNull()?.url ?: "https://www.kopis.or.kr"}"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,text) },"공연 공유"))
        }); dataContainer=column();content.add(dataContainer!!)
    }
    private fun detailBody(target: LinearLayout,c: Concert,stale: Boolean) {
        if (stale) target.add(info("저장된 상세 정보예요. 예매 전에 최신 정보를 확인해 주세요."))
        target.add(label(c.genre.ifBlank { "공연" },13,true,blue)); target.add(label(c.title,24,true),top=12)
        target.add(label("${c.start.format(Dates.display)} ~ ${c.end.format(Dates.display)}",14,tint=muted),top=12)
        target.add(label(c.venue,16,true),top=8)
        if(c.address.isNotBlank()) target.add(label(c.address,13,tint=muted),top=8)
        if(c.poster.isNotBlank()) {
            val poster=ImageView(this).apply { contentDescription="${c.title} 포스터"; scaleType=ImageView.ScaleType.FIT_CENTER; adjustViewBounds=true; background=rounded(surface) }
            target.add(poster,360,top=24); PosterLoader.load(c.poster,poster)
        }
        listOf("출연진" to c.cast,"공연 시간" to c.time,"관람 시간" to c.runtime,"관람 연령" to c.age,"가격" to c.price,"지역" to c.area).forEach { (name,value) ->
            target.add(label(name,13,true,tint=muted),top=24); target.add(label(value.ifBlank { "KOPIS 미등록" },15),top=8)
        }
        if(c.matchReason.isNotBlank()) target.add(info(c.matchReason),top=24)
        target.add(button("캘린더에 추가") {
            val zone=ZoneId.of("Asia/Seoul")
            val intent=Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE,c.title).putExtra(CalendarContract.Events.EVENT_LOCATION,c.venue)
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY,true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,c.start.atStartOfDay(zone).toInstant().toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,c.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            runCatching { startActivity(intent) }.onFailure { toast("사용 가능한 캘린더 앱이 없어요.") }
        },top=24)
        space(target,24); heading(target,"예매처")
        if(c.links.isEmpty()) target.add(info("등록된 예매 링크가 없어요.\nKOPIS 또는 공식 공지를 확인해 주세요."))
        c.links.forEach { link -> target.add(button("${link.name}에서 예매하기  ↗",true) { open(link.url) },top=12) }
        target.add(button("KOPIS 홈페이지 열기") { open("https://www.kopis.or.kr") },top=12)
        target.add(label("공연 정보 제공: KOPIS(공연예술통합전산망)\n일정·출연진·가격은 변경될 수 있어요. 예매처의 최종 안내를 확인해 주세요.",12,tint=muted),top=20)
    }
    private fun open(url: String) {
        if(!url.startsWith("https://") && !url.startsWith("http://")) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure { toast("링크를 열 수 있는 앱이 없어요.") }
    }
    private fun toast(text: String) { Toast.makeText(this,text,Toast.LENGTH_SHORT).show() }
}
