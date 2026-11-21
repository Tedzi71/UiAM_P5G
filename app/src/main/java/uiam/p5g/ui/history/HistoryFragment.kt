package uiam.p5g.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uiam.p5g.data.db.DatabaseManager
import uiam.p5g.data.model.TestSession
import uiam.p5g.databinding.FragmentHistoryBinding
import uiam.p5g.export.ExportManager
import uiam.p5g.ui.MainActivity
import uiam.p5g.ui.map.MapActivity

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbManager: DatabaseManager
    private lateinit var exportManager: ExportManager
    private lateinit var adapter: SessionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbManager = DatabaseManager.getInstance(requireContext())
        exportManager = ExportManager(requireContext())

        adapter = SessionAdapter(
            onMapClick = { session ->
                val intent = Intent(requireContext(), MapActivity::class.java).apply {
                    putExtra(MapActivity.EXTRA_SESSION_ID, session.id)
                }
                startActivity(intent)
            },
            onCsvClick = { session ->
                exportCsv(session)
            },
            onGeoJsonClick = { session ->
                exportGeoJson(session)
            },
            onDeleteClick = { session ->
                confirmDelete(session)
            }
        )

        binding.rvSessions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSessions.adapter = adapter

        loadSessions()
    }

    private fun loadSessions() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sessions = dbManager.getAllSessions()
            withContext(Dispatchers.Main) {
                adapter.submitList(sessions)
                binding.tvSessionCount.text = "${sessions.size} Sessions"
                binding.layoutEmptyHistory.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun exportCsv(session: TestSession) {
        lifecycleScope.launch(Dispatchers.IO) {
            val samples = dbManager.getSamplesForSession(session.id)
            val result = exportManager.exportToCsv(session, samples)
            withContext(Dispatchers.Main) {
                if (result.isSuccess && result.uri != null) {
                    Toast.makeText(requireContext(), "CSV saved to Downloads/P5G_Logs", Toast.LENGTH_SHORT).show()
                    val shareIntent = exportManager.createShareIntent(result.uri, "text/csv")
                    startActivity(Intent.createChooser(shareIntent, "Share CSV Drive Test Log"))
                } else {
                    Toast.makeText(requireContext(), "Export failed: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportGeoJson(session: TestSession) {
        lifecycleScope.launch(Dispatchers.IO) {
            val samples = dbManager.getSamplesForSession(session.id)
            val result = exportManager.exportToGeoJson(session, samples)
            withContext(Dispatchers.Main) {
                if (result.isSuccess && result.uri != null) {
                    Toast.makeText(requireContext(), "GeoJSON saved to Downloads/P5G_Logs", Toast.LENGTH_SHORT).show()
                    val shareIntent = exportManager.createShareIntent(result.uri, "application/geo+json")
                    startActivity(Intent.createChooser(shareIntent, "Share GeoJSON Drive Test Log"))
                } else {
                    Toast.makeText(requireContext(), "Export failed: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmDelete(session: TestSession) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Session")
            .setMessage("Are you sure you want to delete '${session.name}' and all its recorded samples?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    dbManager.deleteSession(session.id)
                    loadSessions()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
