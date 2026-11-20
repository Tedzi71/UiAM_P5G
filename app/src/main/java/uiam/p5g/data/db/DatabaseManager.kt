package uiam.p5g.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import uiam.p5g.data.model.MeasurementSample
import uiam.p5g.data.model.TestSession

class DatabaseManager(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "p5g_measurements.db"
        const val DATABASE_VERSION = 1

        // Table Sessions
        const val TABLE_SESSIONS = "sessions"
        const val COL_SESSION_ID = "id"
        const val COL_SESSION_NAME = "name"
        const val COL_SESSION_START = "start_time"
        const val COL_SESSION_END = "end_time"
        const val COL_SESSION_SAMPLE_COUNT = "sample_count"
        const val COL_SESSION_DISTANCE = "distance_meters"
        const val COL_SESSION_DOMINANT_NET = "dominant_net"
        const val COL_SESSION_AVG_RSRP = "avg_rsrp"
        const val COL_SESSION_AVG_SINR = "avg_sinr"
        const val COL_SESSION_AVG_LATENCY = "avg_latency"
        const val COL_SESSION_MAX_DL = "max_dl"
        const val COL_SESSION_NOTES = "notes"

        // Table Samples
        const val TABLE_SAMPLES = "samples"
        const val COL_SAMPLE_ID = "id"
        const val COL_SAMPLE_SESSION_ID = "session_id"
        const val COL_SAMPLE_TIMESTAMP = "timestamp"
        const val COL_SAMPLE_LAT = "latitude"
        const val COL_SAMPLE_LON = "longitude"
        const val COL_SAMPLE_ALT = "altitude"
        const val COL_SAMPLE_SPEED = "speed_kmh"
        const val COL_SAMPLE_ACCURACY = "accuracy_meters"
        const val COL_SAMPLE_BEARING = "bearing_degrees"
        const val COL_SAMPLE_NET_TYPE = "network_type"
        const val COL_SAMPLE_OPERATOR = "operator_name"
        const val COL_SAMPLE_MCCMNC = "mcc_mnc"
        const val COL_SAMPLE_NR_BAND = "nr_band"
        const val COL_SAMPLE_NR_ARFCN = "nr_arfcn"
        const val COL_SAMPLE_NR_PCI = "nr_pci"
        const val COL_SAMPLE_NR_NCI = "nr_nci"
        const val COL_SAMPLE_NR_RSRP = "nr_ss_rsrp"
        const val COL_SAMPLE_NR_RSRQ = "nr_ss_rsrq"
        const val COL_SAMPLE_NR_SINR = "nr_ss_sinr"
        const val COL_SAMPLE_LTE_BAND = "lte_band"
        const val COL_SAMPLE_LTE_PCI = "lte_pci"
        const val COL_SAMPLE_LTE_EARFCN = "lte_earfcn"
        const val COL_SAMPLE_LTE_RSRP = "lte_rsrp"
        const val COL_SAMPLE_LTE_RSRQ = "lte_rsrq"
        const val COL_SAMPLE_LTE_SINR = "lte_sinr"
        const val COL_SAMPLE_GW_IP = "gateway_ip"
        const val COL_SAMPLE_GW_LATENCY = "gateway_latency_ms"
        const val COL_SAMPLE_GW_JITTER = "gateway_jitter_ms"
        const val COL_SAMPLE_LOSS_PCT = "packet_loss_pct"
        const val COL_SAMPLE_PROBE_METHOD = "probe_method"
        const val COL_SAMPLE_IPERF_DL = "iperf_dl_mbps"
        const val COL_SAMPLE_IPERF_UL = "iperf_ul_mbps"

        @Volatile
        private var instance: DatabaseManager? = null

        fun getInstance(context: Context): DatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: DatabaseManager(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createSessionsTable = """
            CREATE TABLE $TABLE_SESSIONS (
                $COL_SESSION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SESSION_NAME TEXT NOT NULL,
                $COL_SESSION_START INTEGER NOT NULL,
                $COL_SESSION_END INTEGER DEFAULT 0,
                $COL_SESSION_SAMPLE_COUNT INTEGER DEFAULT 0,
                $COL_SESSION_DISTANCE REAL DEFAULT 0.0,
                $COL_SESSION_DOMINANT_NET TEXT DEFAULT '5G_SA',
                $COL_SESSION_AVG_RSRP REAL DEFAULT 0.0,
                $COL_SESSION_AVG_SINR REAL DEFAULT 0.0,
                $COL_SESSION_AVG_LATENCY REAL DEFAULT 0.0,
                $COL_SESSION_MAX_DL REAL DEFAULT 0.0,
                $COL_SESSION_NOTES TEXT DEFAULT ''
            );
        """.trimIndent()

        val createSamplesTable = """
            CREATE TABLE $TABLE_SAMPLES (
                $COL_SAMPLE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SAMPLE_SESSION_ID INTEGER NOT NULL,
                $COL_SAMPLE_TIMESTAMP INTEGER NOT NULL,
                $COL_SAMPLE_LAT REAL NOT NULL,
                $COL_SAMPLE_LON REAL NOT NULL,
                $COL_SAMPLE_ALT REAL DEFAULT 0.0,
                $COL_SAMPLE_SPEED REAL DEFAULT 0.0,
                $COL_SAMPLE_ACCURACY REAL DEFAULT 0.0,
                $COL_SAMPLE_BEARING REAL DEFAULT 0.0,
                $COL_SAMPLE_NET_TYPE TEXT DEFAULT 'UNKNOWN',
                $COL_SAMPLE_OPERATOR TEXT DEFAULT '',
                $COL_SAMPLE_MCCMNC TEXT DEFAULT '',
                $COL_SAMPLE_NR_BAND TEXT DEFAULT '',
                $COL_SAMPLE_NR_ARFCN INTEGER DEFAULT 0,
                $COL_SAMPLE_NR_PCI INTEGER DEFAULT -1,
                $COL_SAMPLE_NR_NCI INTEGER DEFAULT -1,
                $COL_SAMPLE_NR_RSRP INTEGER DEFAULT -140,
                $COL_SAMPLE_NR_RSRQ INTEGER DEFAULT -40,
                $COL_SAMPLE_NR_SINR INTEGER DEFAULT -23,
                $COL_SAMPLE_LTE_BAND TEXT DEFAULT '',
                $COL_SAMPLE_LTE_PCI INTEGER DEFAULT -1,
                $COL_SAMPLE_LTE_EARFCN INTEGER DEFAULT 0,
                $COL_SAMPLE_LTE_RSRP INTEGER DEFAULT -140,
                $COL_SAMPLE_LTE_RSRQ INTEGER DEFAULT -40,
                $COL_SAMPLE_LTE_SINR INTEGER DEFAULT -23,
                $COL_SAMPLE_GW_IP TEXT DEFAULT '',
                $COL_SAMPLE_GW_LATENCY REAL DEFAULT 0.0,
                $COL_SAMPLE_GW_JITTER REAL DEFAULT 0.0,
                $COL_SAMPLE_LOSS_PCT REAL DEFAULT 0.0,
                $COL_SAMPLE_PROBE_METHOD TEXT DEFAULT 'ICMP',
                $COL_SAMPLE_IPERF_DL REAL DEFAULT 0.0,
                $COL_SAMPLE_IPERF_UL REAL DEFAULT 0.0,
                FOREIGN KEY ($COL_SAMPLE_SESSION_ID) REFERENCES $TABLE_SESSIONS($COL_SESSION_ID) ON DELETE CASCADE
            );
        """.trimIndent()

        val createIndexSession = "CREATE INDEX idx_samples_session_id ON $TABLE_SAMPLES ($COL_SAMPLE_SESSION_ID);"
        val createIndexTimestamp = "CREATE INDEX idx_samples_timestamp ON $TABLE_SAMPLES ($COL_SAMPLE_TIMESTAMP);"

        db.execSQL(createSessionsTable)
        db.execSQL(createSamplesTable)
        db.execSQL(createIndexSession)
        db.execSQL(createIndexTimestamp)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SAMPLES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }

    // --- Session Operations ---

    fun createSession(name: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SESSION_NAME, name)
            put(COL_SESSION_START, System.currentTimeMillis())
        }
        return db.insert(TABLE_SESSIONS, null, values)
    }

    fun closeSession(sessionId: Long, endTime: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        // Calculate aggregations from samples
        val cursor = db.rawQuery(
            """
            SELECT 
                COUNT(*) as cnt,
                AVG(CASE WHEN $COL_SAMPLE_NR_RSRP > -140 THEN $COL_SAMPLE_NR_RSRP ELSE (CASE WHEN $COL_SAMPLE_LTE_RSRP > -140 THEN $COL_SAMPLE_LTE_RSRP ELSE NULL END) END) as avg_rsrp,
                AVG(CASE WHEN $COL_SAMPLE_NR_SINR > -23 THEN $COL_SAMPLE_NR_SINR ELSE (CASE WHEN $COL_SAMPLE_LTE_SINR > -23 THEN $COL_SAMPLE_LTE_SINR ELSE NULL END) END) as avg_sinr,
                AVG(CASE WHEN $COL_SAMPLE_GW_LATENCY > 0 THEN $COL_SAMPLE_GW_LATENCY ELSE NULL END) as avg_lat,
                MAX($COL_SAMPLE_IPERF_DL) as max_dl
            FROM $TABLE_SAMPLES WHERE $COL_SAMPLE_SESSION_ID = ?
            """.trimIndent(),
            arrayOf(sessionId.toString())
        )

        var count = 0
        var avgRsrp = 0.0
        var avgSinr = 0.0
        var avgLat = 0.0
        var maxDl = 0.0

        if (cursor.moveToFirst()) {
            count = cursor.getInt(cursor.getColumnIndexOrThrow("cnt"))
            avgRsrp = cursor.getDouble(cursor.getColumnIndexOrThrow("avg_rsrp"))
            avgSinr = cursor.getDouble(cursor.getColumnIndexOrThrow("avg_sinr"))
            avgLat = cursor.getDouble(cursor.getColumnIndexOrThrow("avg_lat"))
            maxDl = cursor.getDouble(cursor.getColumnIndexOrThrow("max_dl"))
        }
        cursor.close()

        val values = ContentValues().apply {
            put(COL_SESSION_END, endTime)
            put(COL_SESSION_SAMPLE_COUNT, count)
            put(COL_SESSION_AVG_RSRP, avgRsrp)
            put(COL_SESSION_AVG_SINR, avgSinr)
            put(COL_SESSION_AVG_LATENCY, avgLat)
            put(COL_SESSION_MAX_DL, maxDl)
        }
        db.update(TABLE_SESSIONS, values, "$COL_SESSION_ID = ?", arrayOf(sessionId.toString()))
    }

    fun getAllSessions(): List<TestSession> {
        val list = mutableListOf<TestSession>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SESSIONS,
            null,
            null,
            null,
            null,
            null,
            "$COL_SESSION_ID DESC"
        )
        while (cursor.moveToNext()) {
            list.add(cursorToSession(cursor))
        }
        cursor.close()
        return list
    }

    fun getSessionById(id: Long): TestSession? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SESSIONS,
            null,
            "$COL_SESSION_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        var session: TestSession? = null
        if (cursor.moveToFirst()) {
            session = cursorToSession(cursor)
        }
        cursor.close()
        return session
    }

    fun deleteSession(sessionId: Long) {
        val db = writableDatabase
        db.delete(TABLE_SAMPLES, "$COL_SAMPLE_SESSION_ID = ?", arrayOf(sessionId.toString()))
        db.delete(TABLE_SESSIONS, "$COL_SESSION_ID = ?", arrayOf(sessionId.toString()))
    }

    // --- Sample Operations ---

    fun insertSample(sample: MeasurementSample): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SAMPLE_SESSION_ID, sample.sessionId)
            put(COL_SAMPLE_TIMESTAMP, sample.timestamp)
            put(COL_SAMPLE_LAT, sample.latitude)
            put(COL_SAMPLE_LON, sample.longitude)
            put(COL_SAMPLE_ALT, sample.altitude)
            put(COL_SAMPLE_SPEED, sample.speedKmh)
            put(COL_SAMPLE_ACCURACY, sample.accuracyMeters)
            put(COL_SAMPLE_BEARING, sample.bearingDegrees)
            put(COL_SAMPLE_NET_TYPE, sample.networkType)
            put(COL_SAMPLE_OPERATOR, sample.operatorName)
            put(COL_SAMPLE_MCCMNC, sample.mccMnc)
            put(COL_SAMPLE_NR_BAND, sample.nrBand)
            put(COL_SAMPLE_NR_ARFCN, sample.nrArfcn)
            put(COL_SAMPLE_NR_PCI, sample.nrPci)
            put(COL_SAMPLE_NR_NCI, sample.nrNci)
            put(COL_SAMPLE_NR_RSRP, sample.nrSsRsrp)
            put(COL_SAMPLE_NR_RSRQ, sample.nrSsRsrq)
            put(COL_SAMPLE_NR_SINR, sample.nrSsSinr)
            put(COL_SAMPLE_LTE_BAND, sample.lteBand)
            put(COL_SAMPLE_LTE_PCI, sample.ltePci)
            put(COL_SAMPLE_LTE_EARFCN, sample.lteEarfcn)
            put(COL_SAMPLE_LTE_RSRP, sample.lteRsrp)
            put(COL_SAMPLE_LTE_RSRQ, sample.lteRsrq)
            put(COL_SAMPLE_LTE_SINR, sample.lteSinr)
            put(COL_SAMPLE_GW_IP, sample.gatewayIp)
            put(COL_SAMPLE_GW_LATENCY, sample.gatewayLatencyMs)
            put(COL_SAMPLE_GW_JITTER, sample.gatewayJitterMs)
            put(COL_SAMPLE_LOSS_PCT, sample.packetLossPct)
            put(COL_SAMPLE_PROBE_METHOD, sample.probeMethod)
            put(COL_SAMPLE_IPERF_DL, sample.iperfDlMbps)
            put(COL_SAMPLE_IPERF_UL, sample.iperfUlMbps)
        }
        return db.insert(TABLE_SAMPLES, null, values)
    }

    fun getSamplesForSession(sessionId: Long): List<MeasurementSample> {
        val list = mutableListOf<MeasurementSample>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SAMPLES,
            null,
            "$COL_SAMPLE_SESSION_ID = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            "$COL_SAMPLE_TIMESTAMP ASC"
        )
        while (cursor.moveToNext()) {
            list.add(cursorToSample(cursor))
        }
        cursor.close()
        return list
    }

    private fun cursorToSession(cursor: Cursor): TestSession {
        return TestSession(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SESSION_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(COL_SESSION_NAME)),
            startTime = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SESSION_START)),
            endTime = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SESSION_END)),
            sampleCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SESSION_SAMPLE_COUNT)),
            distanceMeters = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SESSION_DISTANCE)),
            dominantNetworkType = cursor.getString(cursor.getColumnIndexOrThrow(COL_SESSION_DOMINANT_NET)),
            avgRsrp = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SESSION_AVG_RSRP)),
            avgSinr = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SESSION_AVG_SINR)),
            avgGatewayLatency = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SESSION_AVG_LATENCY)),
            maxDlThroughput = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SESSION_MAX_DL)),
            notes = cursor.getString(cursor.getColumnIndexOrThrow(COL_SESSION_NOTES))
        )
    }

    private fun cursorToSample(cursor: Cursor): MeasurementSample {
        return MeasurementSample(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SAMPLE_ID)),
            sessionId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SAMPLE_SESSION_ID)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SAMPLE_TIMESTAMP)),
            latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SAMPLE_LAT)),
            longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SAMPLE_LON)),
            altitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SAMPLE_ALT)),
            speedKmh = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_SAMPLE_SPEED)),
            accuracyMeters = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_SAMPLE_ACCURACY)),
            bearingDegrees = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_SAMPLE_BEARING)),
            networkType = cursor.getString(cursor.getColumnIndexOrThrow(COL_SAMPLE_NET_TYPE)),
            operatorName = cursor.getString(cursor.getColumnIndexOrThrow(COL_SAMPLE_OPERATOR)),
            mccMnc = cursor.getString(cursor.getColumnIndexOrThrow(COL_SAMPLE_MCCMNC)),
            nrBand = cursor.getString(cursor.getColumnIndexOrThrow(COL_SAMPLE_NR_BAND)),
            nrArfcn = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAMPLE_NR_ARFCN)),
            nrPci = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAMPLE_NR_PCI)),
            nrNci = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SAMPLE_NR_NCI)),
            nrSsRsrp = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAMPLE_NR_RSRP)),
            nrSsRsrq = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAMPLE_NR_RSRQ)),
            nrSsSinr = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAMPLE_NR_SINR)),
            lteBand = cursor.getString(cursor.getColumnIndexOrThrow(COL_SAMPLE_LTE_BAND)),
            ltePci = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAMPLE_LTE_PCI)),
            lteEarfcn = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAMPLE_LTE_EARFCN)),
            lteRsrp = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAMPLE_LTE_RSRP)),
            lteRsrq = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAMPLE_LTE_RSRQ)),
            lteSinr = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAMPLE_LTE_SINR)),
            gatewayIp = cursor.getString(cursor.getColumnIndexOrThrow(COL_SAMPLE_GW_IP)),
            gatewayLatencyMs = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SAMPLE_GW_LATENCY)),
            gatewayJitterMs = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SAMPLE_GW_JITTER)),
            packetLossPct = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SAMPLE_LOSS_PCT)),
            probeMethod = cursor.getString(cursor.getColumnIndexOrThrow(COL_SAMPLE_PROBE_METHOD)),
            iperfDlMbps = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SAMPLE_IPERF_DL)),
            iperfUlMbps = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SAMPLE_IPERF_UL))
        )
    }
}
