package uiam.p5g.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import uiam.p5g.data.model.TestSession
import uiam.p5g.databinding.ItemSessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionAdapter(
    private val onMapClick: (TestSession) -> Unit,
    private val onCsvClick: (TestSession) -> Unit,
    private val onGeoJsonClick: (TestSession) -> Unit,
    private val onDeleteClick: (TestSession) -> Unit
) : ListAdapter<TestSession, SessionAdapter.SessionViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    inner class SessionViewHolder(private val binding: ItemSessionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(session: TestSession) {
            binding.tvSessionName.text = session.name
            binding.tvDominantNetBadge.text = session.dominantNetworkType

            val dateStr = dateFormat.format(Date(session.startTime))
            val durationSec = if (session.endTime > session.startTime) {
                (session.endTime - session.startTime) / 1000
            } else 0
            val durationStr = String.format(Locale.US, "%02dm %02ds", durationSec / 60, durationSec % 60)
            binding.tvSessionDateDuration.text = "$dateStr | $durationStr | ${session.sampleCount} Samples"

            binding.tvAvgRsrp.text = if (session.avgRsrp < 0) String.format(Locale.US, "%.0f dBm", session.avgRsrp) else "-- dBm"
            binding.tvAvgSinr.text = if (session.avgSinr > -23) String.format(Locale.US, "%.1f dB", session.avgSinr) else "-- dB"
            binding.tvAvgLatency.text = if (session.avgGatewayLatency > 0) String.format(Locale.US, "%.1f ms", session.avgGatewayLatency) else "-- ms"
            binding.tvMaxDl.text = if (session.maxDlThroughput > 0) String.format(Locale.US, "%.1f Mbps", session.maxDlThroughput) else "-- Mbps"

            binding.root.setOnClickListener { onMapClick(session) }
            binding.btnViewOnMap.setOnClickListener { onMapClick(session) }
            binding.btnExportCsv.setOnClickListener { onCsvClick(session) }
            binding.btnExportGeoJson.setOnClickListener { onGeoJsonClick(session) }
            binding.btnDeleteSession.setOnClickListener { onDeleteClick(session) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object DiffCallback : DiffUtil.ItemCallback<TestSession>() {
        override fun areItemsTheSame(oldItem: TestSession, newItem: TestSession): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TestSession, newItem: TestSession): Boolean =
            oldItem == newItem
    }
}
