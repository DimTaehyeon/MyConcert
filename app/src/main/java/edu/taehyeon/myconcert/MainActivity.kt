package edu.taehyeon.myconcert

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnAttach
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.appcompat.app.AppCompatDelegate
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

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = if (getSharedPreferences("myconcert",0).getBoolean("dark",false)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[MyConcertViewModel::class.java]
        locator = RegionLocator(this)
        artistPast = savedInstanceState?.getBoolean("artistPast") ?: false
        enableEdgeToEdge()
        installShell()
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
    private fun mount(layout: Int, parent: ViewGroup = content): View =
        layoutInflater.inflate(layout, parent, false).also(parent::addView)
    private fun View.text(id: Int, value: CharSequence) { findViewById<TextView>(id).text = value }
    private fun View.show(id: Int, visible: Boolean) { findViewById<View>(id).visibility = if (visible) View.VISIBLE else View.GONE }
    private fun View.click(id: Int, action: () -> Unit) { findViewById<View>(id).setOnClickListener { action() } }
    private fun installShell() {
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.app_root)
        content = findViewById(R.id.screen_host)
        pageScroll = findViewById(R.id.page_scroll)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        root.doOnAttach { ViewCompat.requestApplyInsets(it) }
        val scroll = pageScroll!!
        val top = findViewById<View>(R.id.scroll_to_top)
        top.setOnClickListener { scroll.smoothScrollTo(0, 0) }
        scroll.setOnScrollChangeListener { _, _, y, _, _ -> top.visibility = if (y > dp(400)) View.VISIBLE else View.GONE }
        findViewById<View>(R.id.github_link).setOnClickListener { open(getString(R.string.creator_github_url)) }
    }

    private fun splash() {
        findViewById<View>(R.id.splash_overlay).visibility = View.VISIBLE
        findViewById<View>(R.id.selection_done).visibility = View.GONE
    }

    private fun render() {
        dataContainer = null; locationStatus = null
        content.removeAllViews()
        findViewById<View>(R.id.splash_overlay).visibility = View.GONE
        findViewById<View>(R.id.selection_done).visibility = View.GONE
        findViewById<View>(R.id.scroll_to_top).visibility = View.GONE
        pageScroll?.scrollTo(0, 0)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = !model.dark
        WindowCompat.getInsetsController(window, root).isAppearanceLightNavigationBars = !model.dark
        when (model.route.value?.screen) {
            "home" -> home()
            "search" -> search()
            "onboarding", "settings" -> preferences()
            "artist", "list" -> concertList()
            "region" -> region()
            "detail" -> detailHeader()
        }
        renderData(model.state.value ?: LoadState())
    }

    private fun bindFavorite(button: ImageView, artist: Artist) {
        fun update() {
            val active = artist.id in model.favoriteIds
            button.isSelected = active
            button.setImageResource(if (active) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline)
            button.contentDescription = "${artist.name} 선호 아티스트 ${if (active) "해제" else "추가"}"
            ViewCompat.setStateDescription(button, if (active) "선호 아티스트에 추가됨" else "선호 아티스트에 추가하지 않음")
        }
        update()
        button.setOnClickListener {
            if (!model.toggleFavorite(artist.id)) toast("최대 8팀까지 선택할 수 있어요.")
            update()
        }
    }
    private fun toolbar(screen: View, title: String) {
        screen.text(R.id.toolbar_title, title)
        screen.click(R.id.back_button) { if (!model.back()) model.finishSetup() }
    }
    private fun topHeader(screen: View, subtitle: String) {
        screen.text(R.id.header_subtitle, subtitle)
        screen.findViewById<View>(R.id.theme_button).contentDescription = if (model.dark) "라이트모드로 전환" else "다크모드로 전환"
        screen.click(R.id.theme_button) {
            model.toggleDark()
            delegate.localNightMode = if (model.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            render()
        }
        screen.click(R.id.search_entry) { model.navigate(Route("search")) }
    }
    private fun tabs(parent: View, names: List<String>, selected: Int, click: (Int) -> Unit) {
        listOf(R.id.tab_first, R.id.tab_second).forEachIndexed { index, id ->
            parent.findViewById<TextView>(id).apply {
                text = names[index]; isSelected = index == selected
                setOnClickListener { click(index) }
            }
        }
    }

    private fun home() {
        val screen = mount(R.layout.screen_home)
        topHeader(screen, "선호하는 아티스트 공연 일정 모아보기")
        tabs(screen, listOf("내 아티스트", "지역별 공연"), 0) { if (it == 1) model.navigate(Route("region")) }
        screen.click(R.id.edit_favorites) { model.navigate(Route("settings")) }
        val artists = screen.findViewById<LinearLayout>(R.id.favorite_artists)
        model.favorites.forEach { artist ->
            mount(R.layout.item_favorite_artist, artists).apply {
                findViewById<ArtistAvatarView>(R.id.favorite_avatar).bind(artist)
                text(R.id.favorite_name, artist.name)
                contentDescription = "${artist.name} 공연 보기"
                setOnClickListener { artistPast = false; model.selectArtist(artist) }
            }
        }
        dataContainer = screen.findViewById(R.id.data_container)
    }

    private fun inputField(screen: View, hint: String, initial: String = "", onSubmit: ((String) -> Unit)? = null, onText: (String) -> Unit): EditText {
        val field = screen.findViewById<EditText>(R.id.search_field)
        field.hint = hint; field.contentDescription = hint; field.setText(initial)
        field.setOnEditorActionListener { v, _, _ ->
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
            field.clearFocus(); onSubmit?.invoke(field.text.toString()); true
        }
        screen.click(R.id.clear_search) { field.setText("") }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onText(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        return field
    }

    private fun artistRow(parent: LinearLayout, artist: Artist) {
        val card = mount(R.layout.item_artist_row, parent)
        card.findViewById<ArtistAvatarView>(R.id.row_avatar).bind(artist)
        card.text(R.id.row_name, artist.name)
        card.text(R.id.row_kind, "${artist.english} · ${artist.kind}")
        card.findViewById<View>(R.id.open_artist).apply {
            contentDescription = "${artist.name}, ${artist.kind}, 공연 보기"
            setOnClickListener { artistPast = false; model.selectArtist(artist) }
        }
        card.show(R.id.row_delete, artist.userAdded)
        card.findViewById<View>(R.id.row_delete).contentDescription = "${artist.name} 삭제"
        card.click(R.id.row_delete) { confirmDeleteArtist(artist) }
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
        val screen = mount(R.layout.screen_preferences)
        toolbar(screen, "선호 아티스트")
        screen.show(R.id.toolbar, !initial); screen.show(R.id.onboarding_header, initial)
        val list = screen.findViewById<ArtistTileGrid>(R.id.artist_grid)
        val done = findViewById<TextView>(R.id.selection_done)
        fun updateDone() { done.text = if (initial) "${model.favoriteIds.size}팀 선택 · 시작하기" else "선택 완료 (${model.favoriteIds.size}팀)" }
        fun fill(query: String) {
            list.removeAllViews()
            val artists = ArtistMatcher.findArtists(model.catalog.artists, query)
            screen.show(R.id.empty_artists, artists.isEmpty())
            artists.forEach { artist ->
                list.addView(ArtistTile(this, artist, artist.id in model.favoriteIds) { tile ->
                    if (!model.toggleFavorite(artist.id)) toast("최대 8팀까지 선택할 수 있어요.")
                    tile.isChecked = artist.id in model.favoriteIds; updateDone()
                })
            }
        }
        inputField(screen, "한글·영문 아티스트 이름 검색", model.preferenceSearchText) {
            model.preferenceSearchText = it; fill(it)
        }
        done.visibility = View.VISIBLE; updateDone()
        done.setOnClickListener { model.finishSetup() }; fill(model.preferenceSearchText)
    }
    private fun search() {
        val screen = mount(R.layout.screen_search)
        toolbar(screen, "아티스트 검색")
        val body = screen.findViewById<LinearLayout>(R.id.search_results)
        inputField(screen, "아티스트 이름을 입력하세요", model.searchText, onSubmit = { input ->
            runCatching {
                val exact = model.catalog.artists.firstOrNull { artist -> artist.aliases.any { ArtistMatcher.normalize(it) == ArtistMatcher.normalize(input) } }
                if (exact != null) model.selectArtist(exact) else model.directSearch(input)
            }.onFailure { toast(it.message ?: "검색명을 확인해 주세요.") }
        }) { model.searchText = it; searchBody(body, it) }
        searchBody(body, model.searchText)
    }
    private fun searchBody(body: LinearLayout, input: String) {
        body.removeAllViews()
        if (input.isBlank()) {
            val view = mount(R.layout.view_search_landing, body)
            view.click(R.id.clear_recent) { model.removeRecent(); searchBody(body, input) }
            val recent = model.recent()
            view.show(R.id.recent_empty, recent.isEmpty()); view.show(R.id.recent_scroll, recent.isNotEmpty())
            val chips = view.findViewById<LinearLayout>(R.id.recent_chips)
            recent.forEach { artist ->
                mount(R.layout.item_recent_artist, chips).apply {
                    text(R.id.recent_name, artist.name)
                    click(R.id.recent_name) { artistPast = false; model.selectArtist(artist) }
                    findViewById<View>(R.id.remove_recent).contentDescription = "${artist.name} 검색 기록 삭제"
                    click(R.id.remove_recent) { model.removeRecent(artist.id); searchBody(body, input) }
                }
            }
            val recommendations = view.findViewById<LinearLayout>(R.id.recommended_artists)
            listOf("iu", "day6", "aespa").mapNotNull(model.catalog::byId).forEach { artistRow(recommendations, it) }
            view.show(R.id.saved_section, model.catalog.savedArtists.isNotEmpty())
            val saved = view.findViewById<LinearLayout>(R.id.saved_artists)
            model.catalog.savedArtists.forEach { artistRow(saved, it) }
            view.findViewById<View>(R.id.direct_search_info).text(R.id.info_text, "목록에 없는 아티스트도 직접 검색할 수 있어요.\n공연이 확인된 검색명은 이 기기에 저장해요.")
        } else {
            val view = mount(R.layout.view_search_matches, body)
            val artists = ArtistMatcher.findArtists(model.catalog.artists, input)
            view.text(R.id.match_count, "아티스트 ${artists.size}팀")
            val exact = artists.any { a -> a.aliases.any { ArtistMatcher.normalize(it) == ArtistMatcher.normalize(input) } }
            view.show(R.id.direct_search, !exact); view.show(R.id.direct_search_hint, !exact)
            view.text(R.id.direct_search, "‘${input.trim()}’ 공연 직접 검색")
            view.click(R.id.direct_search) {
                runCatching { model.directSearch(input) }.onFailure { toast(it.message ?: "검색명을 확인해 주세요.") }
            }
            val matches = view.findViewById<LinearLayout>(R.id.matched_artists)
            artists.forEach { artistRow(matches, it) }
        }
    }

    private fun concertList() {
        val route = model.route.value!!
        val artist = route.artistId?.let(model.catalog::byId)
        val screen = mount(if (artist == null) R.layout.screen_list else R.layout.screen_artist)
        toolbar(screen, artist?.name ?: if (route.past) "이전 공연 이력" else "예정 공연")
        screen.show(R.id.delete_artist, artist?.userAdded == true)
        if (artist?.userAdded == true) screen.click(R.id.delete_artist) { confirmDeleteArtist(artist) }
        if (artist != null) {
            screen.findViewById<ArtistAvatarView>(R.id.artist_avatar).bind(artist)
            screen.text(R.id.artist_kind, "${artist.english} · ${artist.kind}")
            screen.text(R.id.artist_search_basis, if (artist.userAdded) "입력한 이름·출연진 기준으로 조회" else "공식명·출연진으로 확인한 공연")
            bindFavorite(screen.findViewById(R.id.favorite_button), artist)
            screen.click(R.id.profile_source) { open(ArtistProfileParser.searchUrl(artist)) }
            screen.text(R.id.artist_aliases, "검색 표기 · ${artist.aliases.joinToString(" / ")}")
            screen.click(R.id.artist_aliases) { showSearchInfo(model.catalog.byId(artist.id) ?: artist) }
            tabs(screen, listOf("예정 공연", "이전 공연"), if (artistPast) 1 else 0) { artistPast = it == 1; render() }
            screen.findViewById<CheckBox>(R.id.expanded_search).apply {
                isChecked = model.expanded
                setOnCheckedChangeListener { _, checked -> model.expanded = checked; model.load() }
            }
        }
        screen.text(R.id.range_select, "각 ${model.days}일  ⌄")
        screen.click(R.id.range_select) {
            AlertDialog.Builder(this).setTitle("공연 조회 기간").setItems(arrayOf("최근·향후 30일", "최근·향후 90일", "최근·향후 180일", "최근·향후 1년")) { _, i ->
                model.days = listOf(30L, 90L, 180L, 365L)[i]; model.load(); render()
            }.show()
        }
        dataContainer = screen.findViewById(R.id.data_container)
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
        val container = layoutInflater.inflate(R.layout.dialog_search_aliases, null)
        val input = container.findViewById<EditText>(R.id.aliases_input)
        input.setText(artist.aliases.joinToString("\n"))

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
        val screen = mount(R.layout.screen_region)
        topHeader(screen, "지역별 공연 일정 모아보기")
        tabs(screen, listOf("내 아티스트", "지역별 공연"), 1) { if (it == 0 && !model.back()) model.navigate(Route("home"), false) }
        screen.click(R.id.my_location) { requestCurrentRegion() }
        locationStatus = screen.findViewById<TextView>(R.id.location_status).apply { text = model.locationMessage.value.orEmpty() }
        screen.findViewById<RegionMapView>(R.id.region_map).bind(model.regionCode, model.dark) {
            locator.cancel(); locationRequestRevision = null
            model.selectRegion(it.code); render()
        }
        screen.text(R.id.region_title, "${Regions.byCode(model.regionCode)?.name}의 예정 공연")
        screen.text(R.id.region_range, "${model.days}일 ⌄")
        screen.click(R.id.region_range) {
            AlertDialog.Builder(this).setTitle("향후 조회 기간").setItems(arrayOf("30일", "90일", "180일")) { _, i ->
                model.days = listOf(30L, 90L, 180L)[i]; model.load(); render()
            }.show()
        }
        screen.text(R.id.region_period, "조회 : 향후 ${model.days}일 기준")
        screen.show(R.id.combined_region_hint, model.regionCode in listOf("29", "46"))
        dataContainer = screen.findViewById(R.id.data_container)
        if (model.shouldSuggestLocation) screen.post { if (model.route.value?.screen == "region" && model.shouldSuggestLocation) requestCurrentRegion() }
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
    private fun info(parent: LinearLayout, message: String, layout: Int = R.layout.view_info): View =
        mount(layout, parent).apply { text(R.id.info_text, message) }
    private fun button(parent: LinearLayout, title: String, primary: Boolean = false, click: () -> Unit) =
        mount(if (primary) R.layout.item_primary_button else R.layout.item_secondary_button, parent).apply {
            text(R.id.item_button, title); setOnClickListener { click() }
        }
    private fun heading(parent: LinearLayout, title: String, subtitle: String? = null, more: (() -> Unit)? = null) {
        mount(R.layout.view_section_heading, parent).apply {
            text(R.id.heading_title, title)
            show(R.id.heading_subtitle, subtitle != null)
            subtitle?.let { text(R.id.heading_subtitle, it) }
            show(R.id.heading_more, more != null)
            more?.let { click(R.id.heading_more, it) }
        }
    }
    private fun renderData(state: LoadState) {
        val target = dataContainer ?: return
        target.removeAllViews()
        if (state.loading) {
            mount(R.layout.view_loading, target).apply {
                text(R.id.loading_progress, state.progress)
                click(R.id.cancel_loading) { model.cancelSearch() }
            }; return
        }
        if (state.error != null) {
            info(target, state.error); button(target, "다시 시도") { model.load(true) }; return
        }
        val route = model.route.value ?: return
        if (route.screen == "detail") { state.detail?.let { detailBody(target, it, state.staleDetail) }; return }
        val result = state.result ?: return
        val time = Instant.ofEpochMilli(result.savedAt).atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))
        if (result.warnings.isNotEmpty()) info(target, result.warnings.joinToString("\n"), R.layout.view_warning)
        if (route.screen == "home") {
            if (model.favorites.isEmpty()) {
                info(target, "좋아하는 아티스트의 공연을 모아보세요.\n선호 아티스트를 먼저 선택해 주세요.")
                button(target, "아티스트 선택하기", true) { model.navigate(Route("settings")) }; return
            }
            heading(target, "예정 공연 · 가까운 날짜순", "조회 : 향후 ${model.days}일 기준") { model.navigate(Route("list")) }
            showConcerts(target, Dates.sorted(result.concerts, false, model.today).take(3), false)
            mount(R.layout.view_section_space, target)
            heading(target, "이전 공연 이력", "조회 : 최근 ${model.days}일 기준") { model.navigate(Route("list", past = true)) }
            showConcerts(target, Dates.sorted(result.concerts, true, model.today).take(2), true)
        } else {
            val past = if (route.screen == "artist") artistPast else route.past
            val items = Dates.sorted(result.concerts, past, model.today)
            mount(R.layout.view_result_meta, target).apply {
                text(R.id.result_count, "전체 ${items.size}건")
                text(R.id.result_sort, if (past) "최근 공연순" else "가까운 날짜순")
                show(R.id.result_period, route.screen != "region")
                text(R.id.result_period, "조회 : ${if (past) "최근" else "향후"} ${model.days}일 기준")
            }
            route.artistId?.let(model.catalog::byId)?.takeIf { it.userAdded }?.let { artist ->
                info(target, if (artist.verifiedQueries.isEmpty()) "일치 공연을 찾으면 검색명이 이 기기에 저장돼요. 다른 표기도 검색명 편집에서 추가할 수 있어요." else "확인된 검색명 저장됨 · ${artist.verifiedQueries.joinToString()}", R.layout.view_artist_notice)
            }
            showConcerts(target, items, past)
            if (result.uncertain > 0) info(target, "출연 여부가 불확실한 ${result.uncertain}건은 제외했어요.\n공연명과 출연진을 확인한 결과만 표시해요.", R.layout.view_notice)
        }
        mount(R.layout.view_result_footer, target).apply {
            text(R.id.result_source, "KOPIS 제공 · ${if (result.cached) "저장된 정보" else "조회"} $time\n${model.today.minusDays(if (route.screen == "region") 0 else model.days).format(Dates.display)} ~ ${model.today.plusDays(model.days).format(Dates.display)} · 등록된 공연 기준")
            click(R.id.refresh_results) { model.load(true) }
        }
    }
    private fun showConcerts(parent: LinearLayout, items: List<Concert>, past: Boolean) {
        // Expanding this child list cannot insert cards after its metadata/refresh siblings.
        val list = mount(R.layout.view_concert_list, parent) as LinearLayout
        if (items.isEmpty()) {
            info(list, if (past) "이 기간에 확인된 이전 공연이 없어요.\n조회 기간을 넓혀 확인해 보세요." else "이 기간에 확인된 예정 공연이 없어요.\n새 일정이 KOPIS에 등록되면 조회할 수 있어요.")
        } else {
            fun append(offset: Int) {
                items.drop(offset).take(40).forEach { concertCard(list, it) }
                val next = offset + 40
                if (items.size > next) {
                    val more = mount(R.layout.item_more_button, list)
                    more.text(R.id.more_button, "공연 더 보기 (${next}/${items.size})")
                    more.setOnClickListener { list.removeView(more); append(next) }
                }
            }
            append(0)
        }
    }
    private fun concertCard(parent: LinearLayout, c: Concert) {
        val artist = c.artistIds.firstOrNull()?.let(model.catalog::byId)
        mount(R.layout.item_concert, parent).apply {
            findViewById<ArtistAvatarView>(R.id.concert_avatar).bind(artist)
            text(R.id.concert_artist, artist?.name ?: c.genre.ifBlank { "공연" })
            text(R.id.concert_start, c.start.format(Dates.display))
            text(R.id.concert_title, c.title); text(R.id.concert_venue, c.venue)
            show(R.id.concert_end, c.end != c.start)
            text(R.id.concert_end, "~ ${c.end.format(Dates.display)}${if (!c.start.isAfter(model.today) && !c.end.isBefore(model.today)) " · 진행 중" else ""}")
            contentDescription = "${artist?.name.orEmpty()} ${c.title}, ${c.start}, ${c.venue}, 상세 보기"
            setOnClickListener { model.navigate(Route("detail", concert = c)) }
        }
    }
    private fun detailHeader() {
        val screen = mount(R.layout.screen_detail)
        toolbar(screen, "공연 상세")
        screen.show(R.id.toolbar_action, true); screen.text(R.id.toolbar_action, "공유")
        screen.click(R.id.toolbar_action) {
            val c = model.state.value?.detail ?: model.route.value?.concert ?: return@click
            val text = "${c.title}\n${c.start.format(Dates.display)} ~ ${c.end.format(Dates.display)}\n${c.venue}\n${c.links.firstOrNull()?.url ?: "https://www.kopis.or.kr"}"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "공연 공유"))
        }
        dataContainer = screen.findViewById(R.id.data_container)
    }
    private fun detailBody(target: LinearLayout, c: Concert, stale: Boolean) {
        val view = mount(R.layout.view_concert_detail, target)
        view.show(R.id.stale_notice, stale)
        view.findViewById<View>(R.id.stale_notice).text(R.id.info_text, "저장된 상세 정보예요. 예매 전에 최신 정보를 확인해 주세요.")
        view.text(R.id.detail_genre, c.genre.ifBlank { "공연" })
        view.text(R.id.detail_title, c.title)
        view.text(R.id.detail_period, "${c.start.format(Dates.display)} ~ ${c.end.format(Dates.display)}")
        view.text(R.id.detail_venue, c.venue)
        view.show(R.id.detail_address, c.address.isNotBlank()); view.text(R.id.detail_address, c.address)
        view.show(R.id.detail_poster, c.poster.isNotBlank())
        if (c.poster.isNotBlank()) view.findViewById<ImageView>(R.id.detail_poster).apply {
            contentDescription = "${c.title} 포스터"; PosterLoader.load(c.poster, this)
        }
        listOf(R.id.value_cast to c.cast, R.id.value_time to c.time, R.id.value_runtime to c.runtime,
            R.id.value_age to c.age, R.id.value_price to c.price, R.id.value_area to c.area).forEach { (id, value) ->
            view.text(id, value.ifBlank { "KOPIS 미등록" })
        }
        view.click(R.id.add_calendar) {
            val zone = ZoneId.of("Asia/Seoul")
            val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, c.title).putExtra(CalendarContract.Events.EVENT_LOCATION, c.venue)
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, c.start.atStartOfDay(zone).toInstant().toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, c.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            runCatching { startActivity(intent) }.onFailure { toast("사용 가능한 캘린더 앱이 없어요.") }
        }
        view.findViewById<View>(R.id.booking_heading).text(R.id.heading_title, "예매처")
        val links = view.findViewById<LinearLayout>(R.id.booking_links)
        if (c.links.isEmpty()) info(links, "등록된 예매 링크가 없어요.\nKOPIS 또는 공식 공지를 확인해 주세요.")
        c.links.forEach { link -> button(links, "${link.name}에서 예매하기  ↗", true) { open(link.url) } }
        view.click(R.id.open_kopis) { open("https://www.kopis.or.kr") }
    }

    private fun open(url: String) {
        if(!url.startsWith("https://") && !url.startsWith("http://")) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure { toast("링크를 열 수 있는 앱이 없어요.") }
    }
    private fun toast(text: String) { Toast.makeText(this,text,Toast.LENGTH_SHORT).show() }
}
