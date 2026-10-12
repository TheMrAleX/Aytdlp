package con.themralex.aydlp.data

import con.themralex.aydlp.domain.*
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class Backup
    @Inject
    constructor(
        private val queue: QueueRepository,
        private val settings: SettingsStore,
    ) {
        suspend fun export(): String {
            val s = settings.get()
            val rows = JSONArray()
            queue.dao
                .all()
                .filter {
                    it.state != DownloadState.COMPLETED &&
                        it.state != DownloadState.CANCELED
                }.forEach { row ->
                    rows.put(
                        JSONObject()
                            .put(
                                "url",
                                row.url,
                            ).put("profile", row.profile)
                            .put("scheduled", row.scheduled)
                            .put("charging", row.charging),
                    )
                }
            return JSONObject()
                .put(
                    "version",
                    1,
                ).put(
                    "settings",
                    JSONObject()
                        .put(
                            "concurrency",
                            s.concurrency,
                        ).put(
                            "fragments",
                            s.fragments,
                        ).put(
                            "wifi",
                            s.wifi,
                        ).put(
                            "charging",
                            s.charging,
                        ).put("theme", s.theme)
                        .put("accent", s.accent)
                        .put("clients", s.clients)
                        .put("template", s.template),
                ).put("queue", rows)
                .toString(2)
        }

        suspend fun restore(raw: String) {
            require(raw.toByteArray().size <= 2_000_000)
            val j = JSONObject(raw)
            require(j.getInt("version") == 1)
            val s = j.getJSONObject("settings")
            val rows = j.getJSONArray("queue")
            require(rows.length() <= 500)
            val validated =
                buildList {
                    for (i in 0 until rows.length()) {
                        val r = rows.getJSONObject(i)
                        val url = Inputs.normalize(r.getString("url"))
                        val profile = ProfileJson.decode(r.getString("profile"))
                        AdvancedArguments.parse(profile.custom)
                        add(Triple(url, profile, r.optLong("scheduled")))
                    }
                }
            settings.number("concurrency", s.optInt("concurrency", 2).coerceIn(1, 6))
            settings.number("fragments", s.optInt("fragments", 4).coerceIn(1, 16))
            settings.boolean("wifi", s.optBoolean("wifi"))
            settings.boolean("charging", s.optBoolean("charging"))
            settings.text("theme", s.optString("theme", "dark"))
            settings.number("accent", s.optInt("accent").coerceIn(0, 7))
            settings.text("template", FileNames.template(s.getString("template")))
            val clients = s.getString("clients")
            require(clients.matches(Regex("[a-z_,]+")))
            settings.text("clients", clients)
            validated.forEach { queue.enqueue(it.first, it.second, it.third) }
        }
    }
