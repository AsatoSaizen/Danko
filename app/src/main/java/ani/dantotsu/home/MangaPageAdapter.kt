package ani.dantotsu.home

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LayoutAnimationController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.dantotsu.MediaPageTransformer
import ani.dantotsu.R
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.databinding.ItemMangaPageBinding
import ani.dantotsu.databinding.LayoutTrendingBinding
import ani.dantotsu.getAppString
import ani.dantotsu.getThemeColor
import ani.dantotsu.loadImage
import ani.dantotsu.media.GenreActivity
import ani.dantotsu.media.Media
import ani.dantotsu.media.MediaAdaptor
import ani.dantotsu.media.MediaListViewActivity
import ani.dantotsu.media.SearchActivity
import ani.dantotsu.profile.ProfileActivity
import ani.dantotsu.px
import ani.dantotsu.setSafeOnClickListener
import ani.dantotsu.setSlideIn
import ani.dantotsu.setSlideUp
import ani.dantotsu.settings.SettingsDialogFragment
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.statusBarHeight
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout

class MangaPageAdapter : RecyclerView.Adapter<MangaPageAdapter.MangaPageViewHolder>() {
    val ready = MutableLiveData(false)
    lateinit var binding: ItemMangaPageBinding
    private lateinit var trendingBinding: LayoutTrendingBinding
    private var trendHandler: Handler? = null
    private lateinit var trendRun: Runnable
    var trendingViewPager: ViewPager2? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaPageViewHolder {
        val binding =
            ItemMangaPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MangaPageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MangaPageViewHolder, position: Int) {
        binding = holder.binding
        trendingBinding = LayoutTrendingBinding.bind(binding.root)
        trendingViewPager = trendingBinding.trendingViewPager

        val textInputLayout = holder.itemView.findViewById<TextInputLayout>(R.id.searchBar)
        val currentColor = textInputLayout.boxBackgroundColor
        val semiTransparentColor = (currentColor and 0x00FFFFFF) or 0xA8000000.toInt()
        textInputLayout.boxBackgroundColor = semiTransparentColor
        val materialCardView =
            holder.itemView.findViewById<MaterialCardView>(R.id.userAvatarContainer)
        materialCardView.setCardBackgroundColor(semiTransparentColor)
        val color = binding.root.context.getThemeColor(android.R.attr.windowBackground)
        textInputLayout.boxBackgroundColor = (color and 0x00FFFFFF) or 0x28000000
        materialCardView.setCardBackgroundColor((color and 0x00FFFFFF) or 0x28000000)

        trendingBinding.titleContainer.updatePadding(top = statusBarHeight)

        if (PrefManager.getVal(PrefName.SmallView)) trendingBinding.trendingContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = (-108f).px
        }

        updateAvatar()
        trendingBinding.notificationCount.isVisible = Anilist.unreadNotificationCount > 0
                && PrefManager.getVal<Boolean>(PrefName.ShowNotificationRedDot) == true
        trendingBinding.notificationCount.text = Anilist.unreadNotificationCount.toString()
        trendingBinding.searchBar.hint = binding.root.context.getString(R.string.search)
        trendingBinding.searchBarText.setOnClickListener {
            val context = binding.root.context
            if (PrefManager.getVal(PrefName.AniMangaSearchDirect) && Anilist.token != null) {
                ContextCompat.startActivity(
                    context,
                    Intent(context, SearchActivity::class.java).putExtra("type", "MANGA"),
                    null
                )
            } else {
                SearchBottomSheet.newInstance().show(
                    (context as AppCompatActivity).supportFragmentManager,
                    "search"
                )
            }
        }

        trendingBinding.userAvatar.setSafeOnClickListener {
            val dialogFragment =
                SettingsDialogFragment.newInstance(SettingsDialogFragment.Companion.PageType.MANGA)
            dialogFragment.show((it.context as AppCompatActivity).supportFragmentManager, "dialog")
        }
        trendingBinding.userAvatar.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            ContextCompat.startActivity(
                view.context,
                Intent(view.context, ProfileActivity::class.java)
                    .putExtra("userId", Anilist.userid), null
            )
            false
        }

        trendingBinding.searchBar.setEndIconOnClickListener {
            trendingBinding.searchBarText.performClick()
        }

        binding.mangaGenreImage.loadImage("https://s4.anilist.co/file/anilistcdn/media/manga/banner/105778-wk5qQ7zAaTGl.jpg")
        binding.mangaTopScoreImage.loadImage("https://s4.anilist.co/file/anilistcdn/media/manga/banner/30002-3TuoSMl20fUX.jpg")

        binding.mangaGenre.setOnClickListener {
            ContextCompat.startActivity(
                it.context,
                Intent(it.context, GenreActivity::class.java).putExtra("type", "MANGA"),
                null
            )
        }
        binding.mangaTopScore.setOnClickListener {
            ContextCompat.startActivity(
                it.context,
                Intent(it.context, SearchActivity::class.java)
                    .putExtra("type", "MANGA")
                    .putExtra("sortBy", Anilist.sortBy[0])
                    .putExtra("search", true),
                null
            )
        }

        binding.mangaIncludeList.isVisible = Anilist.userid != null

        binding.mangaIncludeList.isChecked = PrefManager.getVal(PrefName.PopularMangaList)

        binding.mangaIncludeList.setOnCheckedChangeListener { _, isChecked ->
            onIncludeListClick.invoke(isChecked)

            PrefManager.setVal(PrefName.PopularMangaList, isChecked)
        }
        if (ready.value == false)
            ready.postValue(true)
    }

    lateinit var onIncludeListClick: ((Boolean) -> Unit)

    override fun getItemCount(): Int = 1

    fun updateHeight() {
        trendingViewPager!!.updateLayoutParams { height += statusBarHeight }
    }

    fun updateTrending(adaptor: MediaAdaptor) {
        trendingBinding.trendingProgressBar.visibility = View.GONE
        trendingBinding.trendingViewPager.adapter = adaptor
        trendingBinding.trendingViewPager.offscreenPageLimit = 3
        trendingBinding.trendingViewPager.getChildAt(0).overScrollMode =
            RecyclerView.OVER_SCROLL_NEVER
        trendingBinding.trendingViewPager.setPageTransformer(MediaPageTransformer())
        trendHandler = Handler(Looper.getMainLooper())
        trendRun = Runnable {
            trendingBinding.trendingViewPager.currentItem += 1
        }
        trendingBinding.trendingViewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    trendHandler?.removeCallbacks(trendRun)
                    if (PrefManager.getVal(PrefName.TrendingScroller))
                        trendHandler!!.postDelayed(trendRun, 4000)
                }
            }
        )

        trendingBinding.trendingViewPager.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)
        trendingBinding.titleContainer.startAnimation(setSlideUp())
        binding.mangaListContainer.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)

    }

    fun updateTrendingManga(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                mangaTrendingMangaRecyclerView,
                mangaTrendingMangaProgressBar,
                mangaTrendingManga,
                mangaTrendingMangaMore,
                getAppString(R.string.trending_manga),
                media
            )
        }
    }

    fun updateTrendingManhwa(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                mangaTrendingManhwaRecyclerView,
                mangaTrendingManhwaProgressBar,
                mangaTrendingManhwa,
                mangaTrendingManhwaMore,
                getAppString(R.string.trending_manhwa),
                media
            )
        }
    }

    fun updateNovel(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                mangaNovelRecyclerView,
                mangaNovelProgressBar,
                mangaNovel,
                mangaNovelMore,
                getAppString(R.string.trending_novel),
                media
            )
        }
    }

    fun updateTopRated(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                mangaTopRatedRecyclerView,
                mangaTopRatedProgressBar,
                mangaTopRated,
                mangaTopRatedMore,
                getAppString(R.string.top_rated),
                media
            )
        }
    }

    fun updateMostFav(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                mangaMostFavRecyclerView,
                mangaMostFavProgressBar,
                mangaMostFav,
                mangaMostFavMore,
                getAppString(R.string.most_favourite),
                media
            )
            mangaPopular.visibility = View.VISIBLE
            mangaPopular.startAnimation(setSlideUp())
        }
    }

    fun init(
        adaptor: MediaAdaptor,
        recyclerView: RecyclerView,
        progress: View,
        title: View,
        more: View,
        string: String,
        media: MutableList<Media>
    ) {
        progress.visibility = View.GONE
        recyclerView.adapter = adaptor
        recyclerView.layoutManager =
            LinearLayoutManager(
                recyclerView.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
        more.setOnClickListener {
            MediaListViewActivity.passedMedia = media.toCollection(ArrayList())
            ContextCompat.startActivity(
                it.context, Intent(it.context, MediaListViewActivity::class.java)
                    .putExtra("title", string),
                null
            )
        }
        recyclerView.visibility = View.VISIBLE
        title.visibility = View.VISIBLE
        more.visibility = View.VISIBLE
        title.startAnimation(setSlideUp())
        more.startAnimation(setSlideUp())
        recyclerView.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)
    }

    fun updateAvatar() {
        if (Anilist.avatar != null && ready.value == true) {
            trendingBinding.userAvatar.loadImage(Anilist.avatar)
            trendingBinding.userAvatar.imageTintList = null
        }
    }

    fun updateNotificationCount() {
        if (this::binding.isInitialized) {
            trendingBinding.notificationCount.isVisible = Anilist.unreadNotificationCount > 0
                    && PrefManager.getVal<Boolean>(PrefName.ShowNotificationRedDot) == true
            trendingBinding.notificationCount.text = Anilist.unreadNotificationCount.toString()
        }
    }

    inner class MangaPageViewHolder(val binding: ItemMangaPageBinding) :
        RecyclerView.ViewHolder(binding.root)
}
