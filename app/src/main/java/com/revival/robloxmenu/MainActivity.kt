package com.revival.robloxmenu

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.DatePicker
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * ============================================================
 *  ROBLOX Mobile 1.0 (2011) - recriacao para Android  (v3)
 *  Toda a interface e montada AQUI, por codigo (sem layouts XML).
 *
 *  MAPA DO ARQUIVO (use Ctrl+F nos nomes):
 *   1) CONFIGURACAO ........ BASE_URL, ROBLOX_PACKAGE, DEFAULT_PORT
 *   2) FERRAMENTAS ......... dp, label, field, botoes, topBar, rede
 *   3) ESTADO LOCAL ........ saldo, amigos, mensagens, itens (salvos no celular)
 *   4) TELAS ............... showLogin, showRegister, showMenu, showProfile,
 *                            showOtherProfile, showFriends, showMessages,
 *                            showMessage, showCompose, showCatalog,
 *                            showCharacter, showShop, showFeeds, showBattle
 *   5) JOGAR ............... launchRoblox
 * ============================================================
 */
class MainActivity : Activity() {

    // ---------------------------------------------------------
    // 1) CONFIGURACAO  (e aqui que voce "fixa" o dominio)
    // ---------------------------------------------------------
    companion object {
        // Todos os dados ficam em BASE_URL/<arquivo>.json  (pasta Deprecated no Neocities)
        const val BASE_URL = "https://jadecove.neocities.org/Deprecated"

        // Nome de pacote do APK do Roblox 2014 (a tela Battle abre este app)
        const val ROBLOX_PACKAGE = "com.roblox.client"

        const val DEFAULT_PORT = "53640"
    }

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val prefs by lazy { getSharedPreferences("roblox_menu", Context.MODE_PRIVATE) }
    private val ui = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, List<JSONObject>>()
    private var offlineWarned = false
    private var onBack: (() -> Unit)? = null
    private var flash: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefs.getString("user", null) != null) showMenu() else showLogin()
    }

    // Botao "voltar" do celular: cada tela diz para onde voltar
    override fun onBackPressed() {
        val b = onBack
        if (b != null) b() else super.onBackPressed()
    }

    // ---------------------------------------------------------
    // 2) FERRAMENTAS
    // ---------------------------------------------------------
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun c(hex: String): Int = Color.parseColor(hex)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun user(): String = prefs.getString("user", "") ?: ""

    private fun image(res: Int, scale: ImageView.ScaleType): ImageView {
        val v = ImageView(this)
        v.setImageResource(res)
        v.scaleType = scale
        return v
    }

    /** Texto. size em sp. */
    private fun label(s: String, size: Float, bold: Boolean, color: Int, shadow: Boolean): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        if (shadow) t.setShadowLayer(2f, 1f, 1f, 0x80000000.toInt())
        return t
    }

    /** Campo de texto branco com borda (igual ao do login do iPhone). */
    private fun field(hint: String, password: Boolean): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setBackgroundResource(R.drawable.field_bg)
        e.setPadding(dp(12), 0, dp(12), 0)
        e.setSingleLine(true)
        e.setTextColor(c("#202020"))
        e.setHintTextColor(c("#808080"))
        // inputType por ultimo (senao a senha deixa de ficar escondida)
        e.inputType = if (password)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        return e
    }

    /** Campo de busca: chama onSubmit quando aperta a lupa/enter do teclado. */
    private fun searchField(hint: String, onSubmit: (String) -> Unit): EditText {
        val e = field(hint, false)
        e.imeOptions = EditorInfo.IME_ACTION_SEARCH
        e.setOnEditorActionListener { v, _, _ ->
            onSubmit(v.text.toString().trim())
            true
        }
        return e
    }

    /** Botao vermelho grande (Login / Play). */
    private fun redButton(s: String, onClick: () -> Unit): TextView {
        val b = label(s, 18f, true, Color.WHITE, true)
        b.gravity = Gravity.CENTER
        b.setBackgroundResource(R.drawable.btn_red)
        b.setOnClickListener { onClick() }
        return b
    }

    /** Botao azul pequeno (Back / Continue / Logout / Buy / Remove ...). */
    private fun blueButton(s: String, onClick: () -> Unit): TextView {
        val b = label(s, 14f, true, Color.WHITE, true)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(12), dp(5), dp(12), dp(5))
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(c("#6FB6F2"), c("#1E78D2"))
        )
        bg.cornerRadius = dp(6).toFloat()
        bg.setStroke(dp(1), c("#0B4A8F"))
        b.background = bg
        b.setOnClickListener { onClick() }
        return b
    }

    /** Bolinha vermelha com numero (contador de Amigos / Mensagens). */
    private fun makeBadge(): TextView {
        val t = label("0", 11f, true, Color.WHITE, false)
        t.gravity = Gravity.CENTER
        t.minWidth = dp(20)
        t.minHeight = dp(20)
        t.setPadding(dp(5), 0, dp(5), 0)
        val bg = GradientDrawable()
        bg.setColor(c("#E8503A"))
        bg.cornerRadius = dp(10).toFloat()
        bg.setStroke(dp(1), Color.WHITE)
        t.background = bg
        t.visibility = View.GONE
        return t
    }

    /** Icone de casinha (volta ao menu), igual ao launcherButton.png do IPA. */
    private fun homeButton(): View {
        val v = ImageView(this)
        v.setImageResource(R.drawable.ic_home)
        v.setPadding(dp(8), dp(8), dp(8), dp(8))
        v.setOnClickListener { showMenu() }
        return v
    }

    /** Botao azul "Back" que usa o onBack da tela atual. */
    private fun backButton(): View = blueButton("Back") { val b = onBack; if (b != null) b() }

    /**
     * Barra azul do topo (48dp).
     * title = null  -> mostra o logo ROBLOX no centro
     */
    private fun topBar(title: String?, left: View?, right: View?): FrameLayout {
        val bar = FrameLayout(this)
        bar.addView(image(R.drawable.top_bar, ImageView.ScaleType.FIT_XY), FrameLayout.LayoutParams(MATCH, MATCH))
        if (title != null) {
            bar.addView(
                label(title, 20f, true, Color.WHITE, true),
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER)
            )
        } else {
            bar.addView(
                image(R.drawable.logo_small, ImageView.ScaleType.FIT_CENTER),
                FrameLayout.LayoutParams(dp(130), dp(34), Gravity.CENTER)
            )
        }
        if (left != null) {
            val p = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL)
            p.leftMargin = dp(6)
            bar.addView(left, p)
        }
        if (right != null) {
            val p = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL)
            p.rightMargin = dp(6)
            bar.addView(right, p)
        }
        return bar
    }

    /** Contadores Robux / Tix do canto direito da barra. */
    private inner class Counters {
        val robux = label("0", 12f, true, Color.WHITE, true)
        val tix = label("0", 12f, true, Color.WHITE, true)
        val view = LinearLayout(this@MainActivity)

        init {
            view.orientation = LinearLayout.VERTICAL
            addRow(R.drawable.ic_robux, robux)
            addRow(R.drawable.ic_tix, tix)
            refresh()
        }

        private fun addRow(icon: Int, t: TextView) {
            val r = LinearLayout(this@MainActivity)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            val iv = ImageView(this@MainActivity)
            iv.setImageResource(icon)
            r.addView(iv, lp(dp(16), dp(16)))
            val tp = lp(WRAP, WRAP)
            tp.leftMargin = dp(3)
            r.addView(t, tp)
            view.addView(r)
        }

        fun refresh() {
            robux.text = bal("robux").toString()
            tix.text = bal("tix").toString()
        }
    }

    /** Abas (tabControl do IPA). */
    private inner class TabBar(val names: List<String>, val onSelect: (Int) -> Unit) {
        val view = LinearLayout(this@MainActivity)
        private val tabs = ArrayList<TextView>()

        init {
            view.orientation = LinearLayout.HORIZONTAL
            for (i in names.indices) {
                val tv = label(names[i], 12f, true, c("#333333"), false)
                tv.gravity = Gravity.CENTER
                tv.setOnClickListener {
                    highlight(i)
                    onSelect(i)
                }
                tabs.add(tv)
                view.addView(tv, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
            highlight(-1)
        }

        /** Pinta a aba i como selecionada (-1 = nenhuma). */
        fun highlight(sel: Int) {
            for (i in tabs.indices) {
                if (i == sel) {
                    tabs[i].setBackgroundColor(Color.WHITE)
                    tabs[i].setTextColor(c("#1E78D2"))
                } else {
                    tabs[i].setBackgroundColor(c("#CFE3F5"))
                    tabs[i].setTextColor(c("#333333"))
                }
            }
        }
    }

    /** Linha branca de lista (celula de tabela). */
    private fun cell(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(12), dp(8), dp(12), dp(8))
        r.setBackgroundColor(Color.WHITE)
        return r
    }

    private fun addCell(list: LinearLayout, row: View) {
        val p = lp(MATCH, WRAP)
        p.bottomMargin = 1
        list.addView(row, p)
    }

    private fun emptyText(list: LinearLayout, s: String) {
        list.removeAllViews()
        val t = label(s, 15f, false, c("#555555"), false)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(16), dp(24), dp(16), 0)
        list.addView(t, lp(MATCH, WRAP))
    }

    private fun avatar(size: Int): ImageView {
        val a = ImageView(this)
        a.setImageResource(R.drawable.btn_profile)
        a.layoutParams = lp(dp(size), dp(size))
        return a
    }

    private fun pageRoot(): LinearLayout {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(c("#F2F6FA"))
        return root
    }

    private fun listArea(root: LinearLayout): LinearLayout {
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return list
    }

    // ----- Rede (dados vindos do Neocities) -----

    /** Baixa BASE_URL/path em segundo plano e devolve o texto (ou null se falhar). */
    private fun fetch(path: String, cb: (String?) -> Unit) {
        Thread {
            var result: String? = null
            try {
                val conn = URL("$BASE_URL/$path").openConnection() as HttpURLConnection
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
                if (conn.responseCode == 200) {
                    result = conn.inputStream.bufferedReader().use { it.readText() }
                }
                conn.disconnect()
            } catch (e: Exception) {
                result = null
            }
            ui.post { cb(result) }
        }.start()
    }

    /** Baixa uma lista JSON: ["nome", ...] ou [{"name": "..."}, ...] */
    private fun fetchList(path: String, cb: (List<JSONObject>?) -> Unit) {
        fetch(path) { s ->
            var out: List<JSONObject>? = null
            if (s != null) {
                try {
                    val arr = JSONArray(s)
                    val tmp = ArrayList<JSONObject>()
                    for (i in 0 until arr.length()) {
                        val o = arr.get(i)
                        if (o is JSONObject) tmp.add(o) else tmp.add(JSONObject().put("name", o.toString()))
                    }
                    out = tmp
                } catch (e: Exception) {
                    out = null
                }
            }
            if (out == null && !offlineWarned) {
                offlineWarned = true
                toast("Could not load data from jadecove.neocities.org")
            }
            cb(out)
        }
    }

    /** Igual ao fetchList, mas guarda na memoria enquanto o app esta aberto. */
    private fun loadList(path: String, cb: (List<JSONObject>?) -> Unit) {
        val hit = cache[path]
        if (hit != null) {
            cb(hit)
            return
        }
        fetchList(path) { l ->
            if (l != null) cache[path] = l
            cb(l)
        }
    }

    /** Carrega varios arquivos de uma vez; os que falharem viram lista vazia. */
    private fun loadAll(files: List<String>, cb: (List<List<JSONObject>>) -> Unit) {
        val results = arrayOfNulls<List<JSONObject>>(files.size)
        var remaining = files.size
        for (i in files.indices) {
            loadList(files[i]) { l ->
                results[i] = l ?: ArrayList<JSONObject>()
                remaining--
                if (remaining == 0) cb(results.map { it ?: ArrayList<JSONObject>() })
            }
        }
    }

    private fun fetchBitmap(path: String, cb: (Bitmap?) -> Unit) {
        Thread {
            var bmp: Bitmap? = null
            try {
                val conn = URL("$BASE_URL/$path").openConnection() as HttpURLConnection
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
                if (conn.responseCode == 200) bmp = BitmapFactory.decodeStream(conn.inputStream)
                conn.disconnect()
            } catch (e: Exception) {
                bmp = null
            }
            ui.post { cb(bmp) }
        }.start()
    }

    // ---------------------------------------------------------
    // 3) ESTADO LOCAL (salvo no celular, nao vai para o Neocities)
    // ---------------------------------------------------------
    private fun bal(k: String): Int = prefs.getInt(k, if (k == "robux") 100 else 500)
    private fun setBal(k: String, v: Int) { prefs.edit().putInt(k, v).apply() }

    private fun getSet(k: String): MutableSet<String> {
        val s = prefs.getStringSet(k, null)
        return if (s == null) HashSet<String>() else HashSet<String>(s)
    }
    private fun putSet(k: String, s: Set<String>) { prefs.edit().putStringSet(k, HashSet<String>(s)).apply() }

    private fun names(l: List<JSONObject>): List<String> = l.map { it.optString("name") }

    /** Amigos = lista do site + aceitos aqui - removidos aqui. */
    private fun myFriends(base: List<String>): List<String> {
        val all = LinkedHashSet<String>(base)
        all.addAll(getSet("fr_added"))
        all.removeAll(getSet("fr_removed"))
        return ArrayList<String>(all)
    }

    private fun myBestFriends(base: List<String>): List<String> {
        val all = LinkedHashSet<String>(base)
        all.removeAll(getSet("fr_removed"))
        return ArrayList<String>(all)
    }

    private fun pendingRequests(base: List<String>): List<String> {
        val all = ArrayList<String>(base)
        all.removeAll(getSet("req_done"))
        return all
    }

    private fun removeFriend(name: String) {
        val removed = getSet("fr_removed"); removed.add(name); putSet("fr_removed", removed)
        val added = getSet("fr_added"); added.remove(name); putSet("fr_added", added)
    }

    private fun msgKey(m: JSONObject): String = m.optString("from") + "|" + m.optString("subject")
    private fun isUnread(m: JSONObject): Boolean =
        !m.optBoolean("read", false) && !getSet("msg_read").contains(msgKey(m))

    /** Na primeira vez, entrega os itens iniciais marcados com "owned": true no catalog.json */
    private fun initInventory(items: List<JSONObject>) {
        if (prefs.getBoolean("inv_init", false)) return
        val owned = getSet("owned")
        val worn = getSet("worn")
        for (o in items) {
            if (o.optBoolean("owned", false)) {
                owned.add(o.optString("id"))
                if (o.optBoolean("worn", false)) worn.add(o.optString("id"))
            }
        }
        putSet("owned", owned)
        putSet("worn", worn)
        prefs.edit().putBoolean("inv_init", true).apply()
    }

    private fun priceText(o: JSONObject): String {
        val p = o.optInt("price", 0)
        return if (p <= 0) "Free" else "$p R$"
    }

    private fun today(): String = SimpleDateFormat("M/d/yy", Locale.US).format(Date())

    // ---------------------------------------------------------
    // 4) TELAS
    // ---------------------------------------------------------

    // ===== LOGIN (LoginView.nib) =====
    private fun showLogin() {
        onBack = null
        val root = FrameLayout(this)
        root.addView(image(R.drawable.login_bkg, ImageView.ScaleType.CENTER_CROP), FrameLayout.LayoutParams(MATCH, MATCH))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(40), dp(40), dp(40), dp(24))

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_big)
        logo.adjustViewBounds = true
        col.addView(logo, lp(MATCH, WRAP))

        val user = field("Username", false)
        val pass = field("Password", true)
        val p1 = lp(MATCH, dp(44)); p1.topMargin = dp(40)
        val p2 = lp(MATCH, dp(44)); p2.topMargin = dp(10)
        col.addView(user, p1)
        col.addView(pass, p2)

        val error = label("", 14f, true, c("#B01808"), false)
        error.gravity = Gravity.CENTER
        val ep = lp(MATCH, WRAP); ep.topMargin = dp(8)
        col.addView(error, ep)

        val login = redButton("Login") {
            val n = user.text.toString().trim()
            if (n.isEmpty()) {
                error.text = "Error: enter a username"
            } else {
                // Login de demonstracao: guarda so o nome (nao ha servidor de contas)
                prefs.edit().putString("user", n).apply()
                showMenu()
            }
        }
        val p3 = lp(MATCH, dp(46)); p3.topMargin = dp(8)
        col.addView(login, p3)

        val reg = label("Register New Account", 18f, false, c("#1F4E8C"), false)
        reg.gravity = Gravity.CENTER
        reg.setPadding(0, dp(12), 0, dp(12))
        reg.setOnClickListener { showRegister() }
        col.addView(reg, lp(MATCH, WRAP))

        val sv = ScrollView(this)
        sv.isFillViewport = true
        sv.addView(col)
        root.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
    }

    // ===== REGISTRO (CreateProfileView.nib) =====
    private fun showRegister() {
        onBack = { showLogin() }
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.WHITE)

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(12), dp(16), dp(16))

        val user = field("Enter username", false)
        val pass = field("Password", true)
        val pass2 = field("Confirm password", true)
        val mail = field("e-mail address", false)

        fun row(name: String, f: EditText) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            val t = label(name, 17f, false, Color.BLACK, false)
            r.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
            r.addView(f, LinearLayout.LayoutParams(0, dp(40), 1.3f))
            val rp = lp(MATCH, WRAP)
            rp.topMargin = dp(10)
            body.addView(r, rp)
        }
        row("Username", user)
        row("Password", pass)
        row("Confirm password", pass2)
        row("E-mail (optional)", mail)

        val warn = label("", 14f, true, c("#B01808"), false)
        warn.gravity = Gravity.CENTER
        val wp = lp(MATCH, WRAP); wp.topMargin = dp(8)
        body.addView(warn, wp)

        val bd = label("Select your birthday:", 18f, false, Color.BLACK, false)
        bd.gravity = Gravity.CENTER
        val bdp = lp(MATCH, WRAP)
        bdp.topMargin = dp(16)
        body.addView(bd, bdp)

        val date = DatePicker(this)
        val dpp = lp(WRAP, WRAP)
        dpp.gravity = Gravity.CENTER_HORIZONTAL
        body.addView(date, dpp)

        val bar = topBar(
            "Register",
            blueButton("Back") { showLogin() },
            blueButton("Continue") {
                val n = user.text.toString().trim()
                val a = pass.text.toString()
                val b = pass2.text.toString()
                if (n.isEmpty()) warn.text = "Enter a username"
                else if (a.isEmpty()) warn.text = "Enter a password"
                else if (a != b) warn.text = "Passwords must match"
                else {
                    prefs.edit().putString("user", n).apply()
                    showMenu()
                }
            }
        )
        root.addView(bar, lp(MATCH, dp(48)))
        val sv = ScrollView(this)
        sv.addView(body)
        root.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
    }

    // ===== MENU PRINCIPAL (MainView.nib) =====
    private fun showMenu() {
        onBack = null
        val root = FrameLayout(this)
        root.addView(image(R.drawable.main_bkg, ImageView.ScaleType.CENTER_CROP), FrameLayout.LayoutParams(MATCH, MATCH))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        val counters = Counters()
        col.addView(
            topBar(null, blueButton("Logout") {
                prefs.edit().remove("user").apply()
                showLogin()
            }, counters.view),
            lp(MATCH, dp(48))
        )

        val friendsBadge = makeBadge()
        val msgBadge = makeBadge()

        fun item(row: LinearLayout, icon: Int, name: String, size: Int, badge: TextView?, textColor: Int, go: () -> Unit) {
            val box = FrameLayout(this)
            val inner = LinearLayout(this)
            inner.orientation = LinearLayout.VERTICAL
            inner.gravity = Gravity.CENTER_HORIZONTAL
            val iv = ImageView(this)
            iv.setImageResource(icon)
            inner.addView(iv, lp(dp(size), dp(size)))
            inner.addView(label(name, 13f, true, textColor, textColor == Color.WHITE))
            box.addView(inner, FrameLayout.LayoutParams(WRAP, WRAP))
            if (badge != null) {
                box.addView(badge, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END))
            }
            box.setOnClickListener { go() }
            val cellBox = FrameLayout(this)
            cellBox.addView(box, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL))
            row.addView(cellBox, LinearLayout.LayoutParams(0, WRAP, 1f))
        }

        // linha 1: os tres botoes grandes
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        item(row1, R.drawable.btn_profile, "Profile", 70, null, Color.WHITE) { showProfile() }
        item(row1, R.drawable.btn_friends, "Friends", 70, friendsBadge, Color.WHITE) { showFriends() }
        item(row1, R.drawable.btn_messages, "Messages", 70, msgBadge, Color.WHITE) { showMessages() }
        val r1p = lp(MATCH, WRAP)
        r1p.topMargin = dp(40)
        r1p.leftMargin = dp(12)
        r1p.rightMargin = dp(12)
        col.addView(row1, r1p)

        // linha 2: segunda "pagina" do menu original (Character, Catalog, Shop, Feeds, Battle)
        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        row2.setPadding(0, dp(10), 0, dp(10))
        val panel = GradientDrawable()
        panel.setColor(c("#CCFFFFFF"))
        panel.cornerRadius = dp(12).toFloat()
        row2.background = panel
        val dark = c("#333333")
        item(row2, R.drawable.btn_profile, "Character", 40, null, dark) { showCharacter() }
        item(row2, R.drawable.ic_shop, "Catalog", 40, null, dark) { showCatalog() }
        item(row2, R.drawable.ic_bc, "Shop", 40, null, dark) { showShop() }
        item(row2, R.drawable.ic_feed, "Feeds", 40, null, dark) { showFeeds() }
        item(row2, R.drawable.ic_battle, "Battle", 40, null, dark) { showBattle() }
        val r2p = lp(MATCH, WRAP)
        r2p.topMargin = dp(36)
        r2p.leftMargin = dp(12)
        r2p.rightMargin = dp(12)
        col.addView(row2, r2p)

        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        // ---- dados ----
        if (!prefs.getBoolean("bal_init", false)) {
            fetch("myinfo.json") { s ->
                if (s != null) {
                    try {
                        val o = JSONObject(s)
                        setBal("robux", o.optInt("robux", 100))
                        setBal("tix", o.optInt("tix", 500))
                        prefs.edit().putBoolean("bal_init", true).apply()
                        counters.refresh()
                    } catch (e: Exception) {
                    }
                } else if (!offlineWarned) {
                    offlineWarned = true
                    toast("Could not load data from jadecove.neocities.org")
                }
            }
        }
        loadAll(listOf("friend_requests.json", "messages.json")) { all ->
            val pend = pendingRequests(names(all[0])).size
            if (pend > 0) {
                friendsBadge.text = pend.toString()
                friendsBadge.visibility = View.VISIBLE
            }
            val unread = all[1].count { isUnread(it) }
            if (unread > 0) {
                msgBadge.text = unread.toString()
                msgBadge.visibility = View.VISIBLE
            }
        }
    }

    // ===== PERFIL (ProfileView.nib) =====
    private fun showProfile() {
        onBack = { showMenu() }
        val root = FrameLayout(this)
        root.addView(image(R.drawable.profile_bkg, ImageView.ScaleType.CENTER_CROP), FrameLayout.LayoutParams(MATCH, MATCH))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        val counters = Counters()
        col.addView(topBar("Profile", homeButton(), counters.view), lp(MATCH, dp(48)))

        val av = ImageView(this)
        av.setImageResource(R.drawable.btn_profile)
        av.scaleType = ImageView.ScaleType.FIT_CENTER
        val ap = lp(dp(220), dp(220))
        ap.topMargin = dp(20)
        col.addView(av, ap)

        val nm = label(user(), 22f, true, Color.BLACK, false)
        val np = lp(WRAP, WRAP)
        np.topMargin = dp(6)
        col.addView(nm, np)

        val inv = blueButton("Inventory") { showCharacter() }
        val ip = lp(WRAP, WRAP)
        ip.topMargin = dp(10)
        col.addView(inv, ip)

        // linha "N msg  N friend"
        val counts = LinearLayout(this)
        counts.orientation = LinearLayout.HORIZONTAL
        counts.gravity = Gravity.CENTER_VERTICAL
        val msgTxt = label("0 msg", 15f, false, Color.BLACK, false)
        val frTxt = label("0 friend", 15f, false, Color.BLACK, false)
        val i1 = ImageView(this); i1.setImageResource(R.drawable.ic_msg)
        val i2 = ImageView(this); i2.setImageResource(R.drawable.ic_friend)
        counts.addView(i1, lp(dp(20), dp(20)))
        counts.addView(msgTxt)
        counts.addView(View(this), lp(dp(20), dp(1)))
        counts.addView(i2, lp(dp(20), dp(20)))
        counts.addView(frTxt)
        val cp = lp(WRAP, WRAP)
        cp.topMargin = dp(14)
        col.addView(counts, cp)

        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        fetchBitmap("thumbnail.png") { b -> if (b != null) av.setImageBitmap(b) }
        loadAll(listOf("friends.json", "messages.json")) { all ->
            frTxt.text = myFriends(names(all[0])).size.toString() + " friend"
            msgTxt.text = all[1].count { isUnread(it) }.toString() + " msg"
        }
    }

    // ===== PERFIL DE OUTRO USUARIO (OtherProfileView.nib) =====
    private fun showOtherProfile(name: String) {
        onBack = { showFriends() }
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(c("#F2F6FA"))
        root.addView(topBar("Profile", backButton(), null), lp(MATCH, dp(48)))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(16), dp(20), dp(16), dp(16))
        val av = avatar(200)
        col.addView(av)
        val nm = label(name, 22f, true, Color.BLACK, false)
        val np = lp(WRAP, WRAP); np.topMargin = dp(8)
        col.addView(nm, np)

        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.gravity = Gravity.CENTER
        val bp = lp(WRAP, WRAP); bp.topMargin = dp(20)
        col.addView(btns, bp)
        root.addView(col, lp(MATCH, WRAP))
        setContentView(root)

        btns.addView(blueButton("Message") { showCompose(name, "") })
        loadAll(listOf("friends.json")) { all ->
            val isFriend = myFriends(names(all[0])).contains(name)
            val gap = lp(WRAP, WRAP); gap.leftMargin = dp(16)
            if (isFriend) {
                btns.addView(blueButton("Remove Friend") {
                    removeFriend(name)
                    toast("$name removed from friends")
                    showFriends()
                }, gap)
            } else {
                val sent = getSet("req_sent")
                val b = blueButton(if (sent.contains(name)) "Request sent" else "Add Friend") {}
                b.setOnClickListener {
                    val s = getSet("req_sent"); s.add(name); putSet("req_sent", s)
                    b.text = "Request sent"
                    toast("Friend request sent to $name")
                }
                btns.addView(b, gap)
            }
        }
    }

    // ===== AMIGOS (FriendsView.nib + listas) =====
    private fun showFriends() {
        onBack = { showMenu() }
        val root = pageRoot()
        root.addView(topBar("Friends", homeButton(), null), lp(MATCH, dp(48)))

        var friendsBase: List<String> = ArrayList<String>()
        var bestBase: List<String> = ArrayList<String>()
        var reqBase: List<String> = ArrayList<String>()
        var users: List<String> = ArrayList<String>()
        var selected = 0
        var query: String? = null      // != null -> mostrando resultados da busca
        var message = "Loading..."
        val acceptedNow = HashSet<String>()

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL

        fun render() {
            list.removeAllViews()
            val q = query
            var shown: List<String>
            if (q != null) {
                val mine = myFriends(friendsBase)
                shown = users.filter {
                    it.lowercase().contains(q.lowercase()) && it != user() && !mine.contains(it)
                }
            } else if (selected == 0) {
                shown = myFriends(friendsBase)
            } else if (selected == 1) {
                shown = myBestFriends(bestBase)
            } else {
                val pend = ArrayList<String>(pendingRequests(reqBase))
                pend.addAll(acceptedNow)
                shown = pend
            }
            if (shown.isEmpty()) {
                emptyText(list, if (q != null) "No users found" else message)
                return
            }
            for (name in shown) {
                val r = cell()
                r.addView(avatar(40))
                val nm = label(name, 17f, true, Color.BLACK, false)
                val nmp = LinearLayout.LayoutParams(0, WRAP, 1f)
                nmp.leftMargin = dp(10)
                r.addView(nm, nmp)
                if (q != null) {
                    val sent = getSet("req_sent").contains(name)
                    val b = blueButton(if (sent) "Sent" else "Add") {}
                    b.setOnClickListener {
                        val s = getSet("req_sent"); s.add(name); putSet("req_sent", s)
                        b.text = "Sent"
                        toast("Friend request sent to $name")
                    }
                    r.addView(b)
                } else if (selected == 2) {
                    if (acceptedNow.contains(name)) {
                        r.addView(label("Accepted", 14f, true, c("#2E8B2E"), false))
                    } else {
                        r.addView(blueButton("Accept") {
                            val done = getSet("req_done"); done.add(name); putSet("req_done", done)
                            val added = getSet("fr_added"); added.add(name); putSet("fr_added", added)
                            val removed = getSet("fr_removed"); removed.remove(name); putSet("fr_removed", removed)
                            acceptedNow.add(name)
                            render()
                        })
                        val gap = lp(WRAP, WRAP); gap.leftMargin = dp(6)
                        r.addView(blueButton("Decline") {
                            val done = getSet("req_done"); done.add(name); putSet("req_done", done)
                            render()
                        }, gap)
                    }
                } else {
                    r.addView(blueButton("Remove") {
                        removeFriend(name)
                        render()
                    })
                    nm.setOnClickListener { showOtherProfile(name) }
                    av0(r, name)
                }
                addCell(list, r)
            }
        }

        val tabs = TabBar(listOf("Friends", "Best friends", "Friend requests")) { i ->
            selected = i
            query = null
            render()
        }
        tabs.highlight(0)

        val search = searchField("Find new friends") { q ->
            if (q.isEmpty()) {
                query = null
            } else {
                query = q
                tabs.highlight(-1)
            }
            render()
        }
        val sp = lp(MATCH, dp(44))
        sp.setMargins(dp(8), dp(6), dp(8), dp(6))
        root.addView(search, sp)

        val sv = ScrollView(this)
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(tabs.view, lp(MATCH, dp(40)))
        setContentView(root)

        render()
        loadAll(listOf("friends.json", "best_friends.json", "friend_requests.json", "users.json")) { all ->
            friendsBase = names(all[0])
            bestBase = names(all[1])
            reqBase = names(all[2])
            users = names(all[3])
            message = if (selected == 2) "No pending requests" else "Nobody here yet"
            render()
        }
    }

    /** Toca no avatar da linha para abrir o perfil. */
    private fun av0(r: LinearLayout, name: String) {
        if (r.childCount > 0) r.getChildAt(0).setOnClickListener { showOtherProfile(name) }
    }

    // ===== MENSAGENS: caixa de entrada (MessagesView.nib) =====
    private fun showMessages() {
        onBack = { showMenu() }
        val root = pageRoot()

        val compose = ImageView(this)
        compose.setImageResource(R.drawable.ic_compose)
        compose.setPadding(dp(8), dp(8), dp(8), dp(8))
        compose.setOnClickListener { showCompose("", "") }
        root.addView(topBar("Messages", homeButton(), compose), lp(MATCH, dp(48)))

        val status = label("", 13f, true, c("#2E8B2E"), false)
        status.gravity = Gravity.CENTER
        status.setPadding(0, dp(4), 0, dp(4))
        val f = flash
        if (f != null) {
            status.text = f
            flash = null
            ui.postDelayed({ status.visibility = View.GONE }, 3500)
        } else {
            status.visibility = View.GONE
        }
        root.addView(status, lp(MATCH, WRAP))

        val list = listArea(root)
        emptyText(list, "Loading...")
        setContentView(root)

        loadList("messages.json") { l ->
            if (l == null) {
                emptyText(list, "Could not connect")
            } else if (l.isEmpty()) {
                emptyText(list, "No messages")
            } else {
                list.removeAllViews()
                for (m in l) {
                    val r = cell()
                    val unread = isUnread(m)
                    val dot = ImageView(this)
                    dot.setImageResource(R.drawable.unread_dot)
                    dot.visibility = if (unread) View.VISIBLE else View.INVISIBLE
                    r.addView(dot, lp(dp(10), dp(10)))
                    val av = avatar(44)
                    val avp = lp(dp(44), dp(44)); avp.leftMargin = dp(8)
                    r.addView(av, avp)

                    val txt = LinearLayout(this)
                    txt.orientation = LinearLayout.VERTICAL
                    val head = LinearLayout(this)
                    head.orientation = LinearLayout.HORIZONTAL
                    val from = label(m.optString("from"), 16f, true, Color.BLACK, false)
                    head.addView(from, LinearLayout.LayoutParams(0, WRAP, 1f))
                    head.addView(label(m.optString("date"), 12f, false, c("#777777"), false))
                    txt.addView(head, lp(MATCH, WRAP))
                    val subj = label(m.optString("subject"), 14f, true, c("#444444"), false)
                    subj.setSingleLine(true)
                    subj.ellipsize = TextUtils.TruncateAt.END
                    txt.addView(subj, lp(MATCH, WRAP))
                    val prev = label(m.optString("body"), 13f, false, c("#777777"), false)
                    prev.setSingleLine(true)
                    prev.ellipsize = TextUtils.TruncateAt.END
                    txt.addView(prev, lp(MATCH, WRAP))
                    val tp = LinearLayout.LayoutParams(0, WRAP, 1f)
                    tp.leftMargin = dp(10)
                    r.addView(txt, tp)

                    r.setOnClickListener { showMessage(m) }
                    addCell(list, r)
                }
            }
        }
    }

    // ===== MENSAGEM ABERTA =====
    private fun showMessage(m: JSONObject) {
        onBack = { showMessages() }
        val read = getSet("msg_read")
        read.add(msgKey(m))
        putSet("msg_read", read)

        val root = pageRoot()
        root.addView(topBar("Message", backButton(), null), lp(MATCH, dp(48)))
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(14), dp(16), dp(16))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(avatar(48))
        val who = LinearLayout(this)
        who.orientation = LinearLayout.VERTICAL
        who.addView(label(m.optString("from"), 18f, true, Color.BLACK, false))
        who.addView(label(m.optString("date"), 13f, false, c("#777777"), false))
        val wp = LinearLayout.LayoutParams(0, WRAP, 1f); wp.leftMargin = dp(10)
        head.addView(who, wp)
        body.addView(head, lp(MATCH, WRAP))

        val subj = label(m.optString("subject"), 18f, true, Color.BLACK, false)
        val sp = lp(MATCH, WRAP); sp.topMargin = dp(16)
        body.addView(subj, sp)
        val txt = label(m.optString("body"), 16f, false, c("#222222"), false)
        val tp = lp(MATCH, WRAP); tp.topMargin = dp(10)
        body.addView(txt, tp)
        val rp = lp(WRAP, WRAP); rp.topMargin = dp(20)
        body.addView(blueButton("Reply") {
            showCompose(m.optString("from"), "Re: " + m.optString("subject"))
        }, rp)

        val sv = ScrollView(this)
        sv.addView(body)
        root.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
    }

    // ===== NOVA MENSAGEM (MessageCreateView.nib) =====
    private fun showCompose(toName: String, subjectText: String) {
        onBack = { showMessages() }
        val root = pageRoot()

        val to = field("To", false)
        to.setText(toName)
        val subject = field("Subject", false)
        subject.setText(subjectText)
        val bodyF = field("Message", false)
        bodyF.setSingleLine(false)
        bodyF.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        bodyF.gravity = Gravity.TOP
        bodyF.setPadding(dp(12), dp(8), dp(12), dp(8))

        root.addView(
            topBar(
                "New Message",
                blueButton("Cancel") { showMessages() },
                blueButton("Send") {
                    val dest = to.text.toString().trim()
                    if (dest.isEmpty()) {
                        toast("Choose who to send to")
                    } else {
                        // Guardado so neste celular (o Neocities nao recebe mensagens)
                        val arr = try { JSONArray(prefs.getString("sent_msgs", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
                        val o = JSONObject()
                        o.put("to", dest)
                        o.put("subject", subject.text.toString().trim().ifEmpty { "(no subject)" })
                        o.put("body", bodyF.text.toString())
                        o.put("date", today())
                        arr.put(o)
                        prefs.edit().putString("sent_msgs", arr.toString()).apply()
                        flash = "Message was successfully sent"
                        showMessages()
                    }
                }
            ),
            lp(MATCH, dp(48))
        )

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(10), dp(10), dp(10), dp(10))
        val tp = lp(MATCH, dp(44))
        col.addView(to, tp)
        val sp = lp(MATCH, dp(44)); sp.topMargin = dp(8)
        col.addView(subject, sp)
        val bp = lp(MATCH, dp(140)); bp.topMargin = dp(8)
        col.addView(bodyF, bp)

        val pickLabel = label("Your friends (tap to choose):", 13f, true, c("#555555"), false)
        val pl = lp(MATCH, WRAP); pl.topMargin = dp(14)
        col.addView(pickLabel, pl)
        val pick = LinearLayout(this)
        pick.orientation = LinearLayout.VERTICAL
        col.addView(pick, lp(MATCH, WRAP))

        val sv = ScrollView(this)
        sv.addView(col)
        root.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)

        loadAll(listOf("friends.json")) { all ->
            for (name in myFriends(names(all[0]))) {
                val r = cell()
                r.addView(avatar(32))
                val nm = label(name, 16f, false, Color.BLACK, false)
                val nmp = lp(WRAP, WRAP); nmp.leftMargin = dp(10)
                r.addView(nm, nmp)
                r.setOnClickListener { to.setText(name) }
                addCell(pick, r)
            }
        }
    }

    // ===== CATALOGO (CatalogView.nib) =====
    private fun showCatalog() {
        onBack = { showMenu() }
        val root = pageRoot()
        val counters = Counters()
        root.addView(topBar("Catalog", homeButton(), counters.view), lp(MATCH, dp(48)))

        var items: List<JSONObject> = ArrayList<JSONObject>()
        var selected = 0
        var q = ""
        var message = "Loading..."

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL

        fun render() {
            list.removeAllViews()
            val owned = getSet("owned")
            val shown = if (selected == 0)
                items.filter { it.optBoolean("featured", true) }
            else
                items.filter { q.isEmpty() || it.optString("name").lowercase().contains(q.lowercase()) }
            if (shown.isEmpty()) {
                emptyText(list, if (items.isEmpty()) message else "No items found")
                return
            }
            for (o in shown) {
                val id = o.optString("id")
                val r = cell()
                r.addView(avatar(32))
                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                col.addView(label(o.optString("name"), 16f, true, Color.BLACK, false))
                col.addView(label("Creator: " + o.optString("creator", "ROBLOX"), 12f, false, c("#666666"), false))
                val cp = LinearLayout.LayoutParams(0, WRAP, 1f)
                cp.leftMargin = dp(10)
                r.addView(col, cp)

                val right = LinearLayout(this)
                right.orientation = LinearLayout.VERTICAL
                right.gravity = Gravity.CENTER_HORIZONTAL
                right.addView(label(priceText(o), 13f, true, c("#1B7A1B"), false))
                if (owned.contains(id)) {
                    right.addView(label("Owned", 13f, true, c("#777777"), false))
                } else {
                    right.addView(blueButton("Buy") {
                        val price = o.optInt("price", 0)
                        if (bal("robux") >= price) {
                            setBal("robux", bal("robux") - price)
                            val own = getSet("owned"); own.add(id); putSet("owned", own)
                            counters.refresh()
                            toast("You bought " + o.optString("name"))
                            render()
                        } else {
                            AlertDialog.Builder(this)
                                .setTitle("Not enough Robux")
                                .setMessage("You need " + price + " R$ but have " + bal("robux") + " R$.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Shop") { _, _ -> showShop() }
                                .show()
                        }
                    })
                }
                r.addView(right)
                addCell(list, r)
            }
        }

        val tabs = TabBar(listOf("Featured items", "Search results")) { i ->
            selected = i
            render()
        }
        tabs.highlight(0)

        val search = searchField("Search the catalog") { text ->
            q = text
            selected = 1
            tabs.highlight(1)
            render()
        }
        val sp = lp(MATCH, dp(44))
        sp.setMargins(dp(8), dp(6), dp(8), dp(6))
        root.addView(search, sp)

        val sv = ScrollView(this)
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(tabs.view, lp(MATCH, dp(40)))
        setContentView(root)

        render()
        loadList("catalog.json") { l ->
            if (l == null) {
                message = "Could not connect"
            } else {
                items = l
                initInventory(l)
            }
            render()
        }
    }

    // ===== PERSONAGEM / INVENTARIO (CharacterView.nib) =====
    private fun showCharacter() {
        onBack = { showMenu() }
        val root = pageRoot()
        root.addView(topBar("Character", homeButton(), null), lp(MATCH, dp(48)))

        var items: List<JSONObject> = ArrayList<JSONObject>()
        var type = "Torsos"
        var message = "Loading..."
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL

        val row1 = listOf("Torsos", "L Arms", "R Arms", "L Legs", "R Legs", "Hats")
        val row2 = listOf("Heads", "Faces", "T-Shirts", "Shirts", "Pants", "Gear")
        lateinit var tabs1: TabBar
        lateinit var tabs2: TabBar

        fun render() {
            list.removeAllViews()
            val owned = getSet("owned")
            val worn = getSet("worn")
            val shown = items.filter { owned.contains(it.optString("id")) && it.optString("type") == type }
            if (shown.isEmpty()) {
                emptyText(list, if (items.isEmpty()) message else "You own no $type. Buy some in the Catalog.")
                return
            }
            for (o in shown) {
                val id = o.optString("id")
                val r = cell()
                r.addView(avatar(32))
                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                col.addView(label(o.optString("name"), 16f, true, Color.BLACK, false))
                col.addView(label("Creator: " + o.optString("creator", "ROBLOX"), 12f, false, c("#666666"), false))
                val cp = LinearLayout.LayoutParams(0, WRAP, 1f)
                cp.leftMargin = dp(10)
                r.addView(col, cp)
                if (worn.contains(id)) {
                    r.addView(blueButton("Remove") {
                        val w = getSet("worn"); w.remove(id); putSet("worn", w)
                        render()
                    })
                } else {
                    r.addView(blueButton("Wear") {
                        val w = getSet("worn")
                        // so um item por tipo (exceto Gear e T-Shirts)
                        if (type != "Gear" && type != "T-Shirts") {
                            for (other in items) {
                                if (other.optString("type") == type) w.remove(other.optString("id"))
                            }
                        }
                        w.add(id)
                        putSet("worn", w)
                        render()
                    })
                }
                addCell(list, r)
            }
        }

        tabs1 = TabBar(row1) { i ->
            type = row1[i]
            tabs2.highlight(-1)
            render()
        }
        tabs2 = TabBar(row2) { i ->
            type = row2[i]
            tabs1.highlight(-1)
            render()
        }
        tabs1.highlight(0)

        val sv = ScrollView(this)
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(tabs1.view, lp(MATCH, dp(34)))
        root.addView(tabs2.view, lp(MATCH, dp(34)))
        setContentView(root)

        render()
        loadList("catalog.json") { l ->
            if (l == null) {
                message = "Could not connect"
            } else {
                items = l
                initInventory(l)
            }
            render()
        }
    }

    // ===== LOJA (ShopView.nib) =====
    private fun showShop() {
        onBack = { showMenu() }
        val root = pageRoot()
        val counters = Counters()
        root.addView(topBar("Shop", homeButton(), counters.view), lp(MATCH, dp(48)))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(16), dp(24), dp(16), dp(24))

        col.addView(blueButton("800 Robux  $9.99") {
            AlertDialog.Builder(this)
                .setTitle("Buy 800 Robux")
                .setMessage("Demo only: no real payment is made. Add 800 R$ to your balance?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add") { _, _ ->
                    setBal("robux", bal("robux") + 800)
                    counters.refresh()
                    toast("800 R$ added")
                }
                .show()
        })

        fun club(title: String, img: Int, key: String) {
            val tl = label(title, 17f, true, Color.BLACK, false)
            val tp = lp(WRAP, WRAP); tp.topMargin = dp(28)
            col.addView(tl, tp)
            val iv = ImageView(this)
            iv.setImageResource(img)
            iv.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage("Demo only: no real payment is made. Activate $title for one month?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Activate") { _, _ ->
                        prefs.edit().putString("club", key).apply()
                        toast("$title activated")
                    }
                    .show()
            }
            val ip = lp(dp(130), dp(97)); ip.topMargin = dp(8)
            col.addView(iv, ip)
        }
        club("Builders Club", R.drawable.buy_bc, "BC")
        club("Outrageous Builders Club", R.drawable.buy_obc, "OBC")

        val sv = ScrollView(this)
        sv.addView(col)
        root.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
    }

    // ===== FEEDS (FeedsView.nib) =====
    private fun showFeeds() {
        onBack = { showMenu() }
        val root = pageRoot()
        root.addView(topBar("Feeds", homeButton(), null), lp(MATCH, dp(48)))

        var others: List<JSONObject> = ArrayList<JSONObject>()
        var message = "Loading..."
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL

        fun render() {
            list.removeAllViews()
            val all = ArrayList<JSONObject>()
            val mine = try { JSONArray(prefs.getString("my_feeds", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
            for (i in mine.length() - 1 downTo 0) all.add(mine.getJSONObject(i))
            all.addAll(others)
            if (all.isEmpty()) {
                emptyText(list, message)
                return
            }
            for (o in all) {
                val r = cell()
                r.addView(avatar(36))
                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                col.addView(label(o.optString("user"), 15f, true, Color.BLACK, false))
                col.addView(label(o.optString("text"), 14f, false, c("#333333"), false))
                val cp = LinearLayout.LayoutParams(0, WRAP, 1f)
                cp.leftMargin = dp(10)
                r.addView(col, cp)
                r.addView(label(o.optString("date"), 12f, false, c("#777777"), false))
                addCell(list, r)
            }
        }

        val feed = searchField("Right now i'm") { text ->
            if (text.isNotEmpty()) {
                val arr = try { JSONArray(prefs.getString("my_feeds", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
                val o = JSONObject()
                o.put("user", user())
                o.put("text", text)
                o.put("date", today())
                arr.put(o)
                prefs.edit().putString("my_feeds", arr.toString()).apply()
                render()
                toast("Posted")
            }
        }
        feed.imeOptions = EditorInfo.IME_ACTION_DONE
        val fp = lp(MATCH, dp(44))
        fp.setMargins(dp(8), dp(6), dp(8), dp(6))
        root.addView(feed, fp)

        val sv = ScrollView(this)
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)

        render()
        loadList("feeds.json") { l ->
            if (l == null) message = "Could not connect" else {
                others = l
                message = "No posts yet"
            }
            render()
        }
    }

    // ===== BATTLE (BattleView.nib) = aqui vira a tela de JOGAR =====
    private fun showBattle() {
        onBack = { showMenu() }
        val root = pageRoot()
        root.addView(topBar("Battle", homeButton(), null), lp(MATCH, dp(48)))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(24), dp(32), dp(24), dp(24))

        val iv = ImageView(this)
        iv.setImageResource(R.drawable.ic_battle)
        col.addView(iv, lp(dp(64), dp(80)))
        val t = label("Join a game server", 18f, true, Color.BLACK, false)
        val tp = lp(WRAP, WRAP); tp.topMargin = dp(12)
        col.addView(t, tp)

        val etIp = field("Server IP (e.g. 192.168.0.10)", false)
        etIp.setText(prefs.getString("ip", ""))
        val etPort = field("Port", false)
        etPort.setText(prefs.getString("port", DEFAULT_PORT))
        etPort.inputType = InputType.TYPE_CLASS_NUMBER
        val ip1 = lp(MATCH, dp(44)); ip1.topMargin = dp(20)
        val ip2 = lp(MATCH, dp(44)); ip2.topMargin = dp(10)
        col.addView(etIp, ip1)
        col.addView(etPort, ip2)

        val play = redButton("Play") {
            val ip = etIp.text.toString().trim()
            val port = etPort.text.toString().trim().ifEmpty { DEFAULT_PORT }
            if (ip.isEmpty()) {
                toast("Enter the server IP")
            } else {
                prefs.edit().putString("ip", ip).putString("port", port).apply()
                launchRoblox(ip, port)
            }
        }
        val pp = lp(MATCH, dp(50)); pp.topMargin = dp(20)
        col.addView(play, pp)

        root.addView(col, lp(MATCH, WRAP))
        setContentView(root)
    }

    // ---------------------------------------------------------
    // 5) JOGAR: abre o app do Roblox 2014
    // ---------------------------------------------------------
    private fun launchRoblox(ip: String, port: String) {
        val intent = packageManager.getLaunchIntentForPackage(ROBLOX_PACKAGE)
        if (intent == null) {
            Toast.makeText(this, "App not found: $ROBLOX_PACKAGE (edit ROBLOX_PACKAGE)", Toast.LENGTH_LONG).show()
            return
        }
        intent.data = Uri.parse("roblox://$ip:$port")
        intent.putExtra("ip", ip)
        intent.putExtra("port", port)
        intent.putExtra("user", user())
        startActivity(intent)
    }
}
