package com.jhaiian.clint.browser

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jhaiian.clint.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ImageLongPressSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onImageOpenInNewTab(imageUrl: String)
        fun onImageOpenIncognito(imageUrl: String)
        fun onImageOpenInCurrentTab(imageUrl: String)
        fun onImagePreview(imageUrl: String)
        fun onImageCopy(imageUrl: String)
        fun onImageDownload(imageUrl: String, altText: String)
        fun onImageShare(imageUrl: String)
    }

    private var listener: Listener? = null
    private var thumbnailClient: OkHttpClient? = null
    private var thumbnailThread: Thread? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = requireActivity() as? Listener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (prefs.getBoolean("hide_status_bar", false)) {
            @Suppress("DEPRECATION")
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.bottom_sheet_image_actions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageUrl = arguments?.getString(ARG_IMAGE_URL) ?: ""
        val pageTitle = arguments?.getString(ARG_PAGE_TITLE) ?: ""
        val isStandalone = arguments?.getBoolean(ARG_IS_STANDALONE) ?: false
        val isPreviewContext = arguments?.getBoolean(ARG_IS_PREVIEW_CONTEXT) ?: false
        val referer = arguments?.getString(ARG_REFERER) ?: ""

        val urlFilename = imageUrl.substringAfterLast("/").substringBefore("?")
            .takeIf { it.length > 4 && it.contains(".") }
            ?: run {
                try {
                    val uri = android.net.Uri.parse(imageUrl)
                    listOf("u", "url", "src", "img", "imgurl").firstNotNullOfOrNull { key ->
                        uri.getQueryParameter(key)?.let { param ->
                            java.net.URLDecoder.decode(param, "UTF-8")
                                .substringAfterLast("/").substringBefore("?")
                                .takeIf { it.contains(".") }
                        }
                    }
                } catch (_: Exception) { null }
            }
        val displayTitle = pageTitle.ifEmpty { urlFilename ?: imageUrl }

        view.findViewById<TextView>(R.id.image_title).text = displayTitle

        val userAgent = android.webkit.WebSettings.getDefaultUserAgent(requireContext())
        loadThumbnail(view.findViewById(R.id.image_thumbnail), imageUrl, referer, userAgent)

        val openInNewTab = view.findViewById<View>(R.id.action_open_in_new_tab)
        val openIncognito = view.findViewById<View>(R.id.action_open_incognito)
        val openInCurrentTab = view.findViewById<View>(R.id.action_open_in_current_tab)
        val previewItem = view.findViewById<View>(R.id.action_preview)
        val dividerTop = view.findViewById<View>(R.id.divider_top)
        val dividerAfterNewTab = view.findViewById<View>(R.id.divider_after_new_tab)
        val dividerAfterIncognito = view.findViewById<View>(R.id.divider_after_incognito)
        val dividerAfterCurrentTab = view.findViewById<View>(R.id.divider_after_current_tab)
        val dividerAfterPreviewItem = view.findViewById<View>(R.id.divider_after_preview_item)

        val showTabActions = !isStandalone || isPreviewContext

        dividerTop.visibility = if (showTabActions) View.VISIBLE else View.GONE
        openInNewTab.visibility = if (showTabActions) View.VISIBLE else View.GONE
        dividerAfterNewTab.visibility = if (showTabActions) View.VISIBLE else View.GONE
        openIncognito.visibility = if (showTabActions) View.VISIBLE else View.GONE
        dividerAfterIncognito.visibility = if (showTabActions) View.VISIBLE else View.GONE
        openInCurrentTab.visibility = if (isPreviewContext) View.VISIBLE else View.GONE
        dividerAfterCurrentTab.visibility = if (isPreviewContext) View.VISIBLE else View.GONE
        previewItem.visibility = if (showTabActions && !isPreviewContext) View.VISIBLE else View.GONE
        dividerAfterPreviewItem.visibility = if (showTabActions && !isPreviewContext) View.VISIBLE else View.GONE

        openInNewTab.setOnClickListener { dismiss(); listener?.onImageOpenInNewTab(imageUrl) }
        openIncognito.setOnClickListener { dismiss(); listener?.onImageOpenIncognito(imageUrl) }
        openInCurrentTab.setOnClickListener { dismiss(); listener?.onImageOpenInCurrentTab(imageUrl) }
        previewItem.setOnClickListener { dismiss(); listener?.onImagePreview(imageUrl) }
        view.findViewById<View>(R.id.action_copy).setOnClickListener { dismiss(); listener?.onImageCopy(imageUrl) }
        view.findViewById<View>(R.id.action_download).setOnClickListener { dismiss(); listener?.onImageDownload(imageUrl, pageTitle) }
        view.findViewById<View>(R.id.action_share).setOnClickListener { dismiss(); listener?.onImageShare(imageUrl) }
    }

    private fun loadThumbnail(imageView: ImageView, url: String, referer: String, userAgent: String) {
        if (url.isEmpty()) return
        val handler = Handler(Looper.getMainLooper())
        thumbnailClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        val t = Thread {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .apply { if (referer.isNotEmpty()) header("Referer", referer) }
                    .build()
                val response = thumbnailClient?.newCall(request)?.execute() ?: return@Thread
                val bytes = response.body?.bytes() ?: return@Thread
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@Thread
                handler.post {
                    if (isAdded) {
                        imageView.setPadding(0, 0, 0, 0)
                        imageView.imageTintList = null
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) {}
        }
        thumbnailThread = t
        t.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        thumbnailThread?.interrupt()
        thumbnailThread = null
        thumbnailClient = null
    }

    companion object {
        private const val ARG_IMAGE_URL = "image_url"
        private const val ARG_PAGE_TITLE = "page_title"
        private const val ARG_IS_STANDALONE = "is_standalone"
        private const val ARG_REFERER = "referer"
        private const val ARG_IS_PREVIEW_CONTEXT = "is_preview_context"

        fun newInstance(imageUrl: String, pageTitle: String, isStandaloneImage: Boolean, referer: String = "", isPreviewContext: Boolean = false): ImageLongPressSheet {
            return ImageLongPressSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_URL, imageUrl)
                    putString(ARG_PAGE_TITLE, pageTitle)
                    putBoolean(ARG_IS_STANDALONE, isStandaloneImage)
                    putString(ARG_REFERER, referer)
                    putBoolean(ARG_IS_PREVIEW_CONTEXT, isPreviewContext)
                }
            }
        }
    }
}
