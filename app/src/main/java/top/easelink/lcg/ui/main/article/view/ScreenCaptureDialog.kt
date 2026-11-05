package top.easelink.lcg.ui.main.article.view

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import timber.log.Timber
import top.easelink.lcg.R
import top.easelink.lcg.databinding.DialogScreenCaptureBinding
import java.io.File
import java.io.FileInputStream

class ScreenCaptureDialog : DialogFragment() {
    private var _binding: DialogScreenCaptureBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "ScreenCaptureDialog"
        private const val IMAGE_PATH = "image_path"

        fun newInstance(imagePath: String): ScreenCaptureDialog {
            return ScreenCaptureDialog().apply {
                arguments = Bundle().apply { putString(IMAGE_PATH, imagePath) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppTheme_Dialog_FullScreen_BottomInOut)
        dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog?.window?.setDimAmount(0.5f)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogScreenCaptureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            arguments?.getString(IMAGE_PATH)?.let { path ->
                loadImage(path)
                setupButtons(path)
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun loadImage(path: String) {
        try {
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(requireContext(), "图片文件不存在", Toast.LENGTH_SHORT).show()
                return
            }
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, options)
            var inSampleSize = 1
            while (options.outWidth / inSampleSize > 1080) { inSampleSize *= 2 }
            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            if (bitmap != null) {
                binding.imgScreenCapture.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun setupButtons(path: String) {
        val fileName = "WTF_Screenshot_${System.currentTimeMillis()}.png"
        val file = File(path)

        binding.btnClose.setOnClickListener { dismiss() }

        binding.btnSave.setOnClickListener {
            val success = saveImageToGalleryAndroid10(file, fileName)
            if (success) {
                Toast.makeText(requireContext(), "已保存到相册 (DCIM/Camera)", Toast.LENGTH_LONG).show()
                dismiss()
            } else {
                Toast.makeText(requireContext(), "保存失败，请检查权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveImageToGalleryAndroid10(file: File, fileName: String): Boolean {
        return try {
            val resolver = requireContext().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                true
            } else false
        } catch (e: Exception) {
            Timber.e(e, "保存相册失败")
            false
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
