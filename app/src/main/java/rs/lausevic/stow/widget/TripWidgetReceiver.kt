// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rs.lausevic.stow.MainActivity
import rs.lausevic.stow.R
import rs.lausevic.stow.container
import rs.lausevic.stow.data.model.PackStatus

/**
 * Vidžet na čistim `RemoteViews`, bez Glance-a.
 *
 * Glance je bio prvi izbor dok provera manifesta nije oborila build: `glance-appwidget`
 * vuče WorkManager, a WorkManager deklariše `ACCESS_NETWORK_STATE`, `WAKE_LOCK`,
 * `RECEIVE_BOOT_COMPLETED` i `FOREGROUND_SERVICE`. Prva od njih direktno krši glavno
 * ograničenje ove aplikacije, a nijedna se ne bi videla bez provere spojenog manifesta.
 *
 * `RemoteViews` za ovo i nije skup: naslov, brojač, traka i tri reda teksta.
 * Bez periodičnog osvežavanja — sadržaj se menja samo dok korisnik pakuje, a tada je
 * aplikacija ionako otvorena i sama zove `refresh`.
 */
class TripWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = buildViews(context)
                appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun buildViews(context: Context): RemoteViews {
        val container = context.container
        val trip = container.trips.current()
        val items = trip?.let { container.trips.items(it.id) }.orEmpty()

        val packed = items.count { it.packStatus == PackStatus.PACKED }
        val trackable = items.count {
            it.packStatus == PackStatus.PACKED || it.packStatus == PackStatus.TO_PACK
        }
        val next = items.filter { it.packStatus == PackStatus.TO_PACK }.take(MAX_LINES)

        return RemoteViews(context.packageName, R.layout.widget_trip).apply {
            setTextViewText(R.id.widget_title, trip?.name ?: context.getString(R.string.widget_no_trip))

            if (trip == null) {
                setViewVisibility(R.id.widget_progress_label, View.GONE)
                setViewVisibility(R.id.widget_progress, View.GONE)
            } else {
                setViewVisibility(R.id.widget_progress_label, View.VISIBLE)
                setViewVisibility(R.id.widget_progress, View.VISIBLE)
                setTextViewText(
                    R.id.widget_progress_label,
                    context.getString(R.string.trips_progress, packed, trackable),
                )
                setProgressBar(R.id.widget_progress, trackable.coerceAtLeast(1), packed, false)
            }

            val lineIds = intArrayOf(R.id.widget_line_1, R.id.widget_line_2, R.id.widget_line_3)
            lineIds.forEachIndexed { index, viewId ->
                val item = next.getOrNull(index)
                if (item == null) {
                    setViewVisibility(viewId, View.GONE)
                } else {
                    setViewVisibility(viewId, View.VISIBLE)
                    setTextViewText(viewId, "○  ${item.title}")
                }
            }

            val remaining = (trackable - packed - next.size).coerceAtLeast(0)
            if (trip != null && next.isEmpty()) {
                setViewVisibility(R.id.widget_footer, View.VISIBLE)
                setTextViewText(R.id.widget_footer, context.getString(R.string.widget_nothing_left))
            } else if (remaining > 0) {
                setViewVisibility(R.id.widget_footer, View.VISIBLE)
                setTextViewText(R.id.widget_footer, context.getString(R.string.widget_more_items, remaining))
            } else {
                setViewVisibility(R.id.widget_footer, View.GONE)
            }

            setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
    }

    companion object {
        private const val MAX_LINES = 3

        /** Poziva se kad se stanje putovanja promeni — vidžet se ne budi sam. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TripWidgetReceiver::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, TripWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }
    }
}
