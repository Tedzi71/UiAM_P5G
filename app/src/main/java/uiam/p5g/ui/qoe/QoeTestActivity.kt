package uiam.p5g.ui.qoe

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import uiam.p5g.databinding.ActivityQoeTestBinding

class QoeTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQoeTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQoeTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // WindowInsets for camera cutout, status bar, and bottom buttons
        ViewCompat.setOnApplyWindowInsetsListener(binding.qoeRoot) { view, windowInsets ->
            val statusBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(statusBars.left, statusBars.top, statusBars.right, navBars.bottom)
            windowInsets
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnQuitQoe.setOnClickListener {
            finish()
        }
    }
}
