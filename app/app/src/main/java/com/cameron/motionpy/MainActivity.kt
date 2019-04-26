package com.cameron.motionpy

import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), ChildEventListener {

    private val tag = MainActivity::class.java.simpleName

    private val adapter = ViewAdapter()
    private val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

    // These lists save deleted items and their properties
    // so they can be added back to the adapter if "Undo" is pressed
    private val deletedEntryQueue = mutableListOf<Entry>()
    private val deletedPositions = mutableListOf<Int>()
    private val deletedIdQueue = mutableListOf<String>()

    private val storageRef = FirebaseStorage.getInstance().getReference("captures")
    private val databaseRef = FirebaseDatabase.getInstance().getReference("/")

    private lateinit var prefs: SharedPreferences

    // This is the receiver that listens for changes in status or
    // configuration of the Raspberry Pi
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isPaused = intent?.getBooleanExtra("isPaused", true)
            val delay = intent?.getFloatExtra("delay", -1f)
            val picsTaken = intent?.getIntExtra("picsTaken", -1)
            Log.i(tag, "isPaused: $isPaused")
            Log.i(tag, "delay: $delay")
            Log.i(tag, "picsTaken: $picsTaken")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)

        var useGrid = false
        val linearManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        val gridManager = GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false)
        val itemDecor = DividerItemDecoration(this, linearManager.orientation)

        toggle_layout.setOnClickListener {
            useGrid = !useGrid
            toggle_layout.setImageDrawable(
                    ContextCompat.getDrawable(this,
                            if (useGrid) R.drawable.ic_list_24dp
                            else R.drawable.ic_view_compact_24dp)
            )
            adapter.swapLayout(useGrid)
            recycler_view.layoutManager = if (useGrid) gridManager else linearManager

            if (useGrid) {
                recycler_view.removeItemDecoration(itemDecor)
            } else {
                recycler_view.addItemDecoration(itemDecor)
            }
        }

        databaseRef.addChildEventListener(this)

        recycler_view.layoutManager = linearManager
        recycler_view.addItemDecoration(itemDecor)
        recycler_view.setHasFixedSize(true)
        recycler_view.adapter = adapter

        // Delete the selected item from the Firebase database when
        // swiped left or right
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT) {

            override fun onMove(recyclerView: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val entryId = viewHolder.itemView.tag as String
                val position = viewHolder.adapterPosition

                // Add the deleted item to a queue so it can be
                // re-added if "Undo" is pressed
                deletedEntryQueue.add(adapter.getItem(position))
                deletedPositions.add(position)
                deletedIdQueue.add(entryId)

                adapter.removeAtPosition(position)

                if (adapter.itemCount == 0)
                    toggleEmptyView(true)

                showDeleteSnackbar()
            }
        })

        touchHelper.attachToRecyclerView(recycler_view)

        tv_send_command.setOnClickListener {
            val fragment = CommandsFragment()
            fragment.setClickListener { command ->
                // Give the "power_off" command a confirmation dialog just
                // in case it was press accidentally
                if (command == Commands.POWER_OFF) {
                    with(AlertDialog.Builder(this, R.style.AlertDialogCustom)) {
                        setTitle(getString(R.string.dialog_title))
                        setMessage(getString(R.string.dialog_message))
                        setNegativeButton(android.R.string.cancel) { _, _ -> }
                        setPositiveButton(android.R.string.yes) { _, _ -> sendCommand(command) }
                        show()
                    }
                } else {
                    sendCommand(command)
                }
                fragment.dismiss()
            }
            fragment.show(supportFragmentManager, fragment.tag)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Makes sure the alert icon updates in the toolbar when
        // the app is first launched
        menuInflater.inflate(R.menu.menu_main, menu)
        menu?.findItem(R.id.action_settings)?.icon = ContextCompat.getDrawable(this,
                if (getNotificationPref()) R.drawable.ic_notifications_active
                else R.drawable.ic_notifications_off
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.action_settings) {
            prefs.edit()
                    .putBoolean(getString(R.string.pref_notif_key), !getNotificationPref())
                    .apply()

            item.icon = ContextCompat.getDrawable(this,
                    if (getNotificationPref()) R.drawable.ic_notifications_active
                    else R.drawable.ic_notifications_off)

            Snackbar.make(root,
                    getString(
                            if (getNotificationPref()) R.string.notification_alert_on
                            else R.string.notification_alert_off),
                    Snackbar.LENGTH_LONG
            ).show()
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter(NotificationService.DATA_MSG_BROADCAST))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onChildAdded(dataSnapshot: DataSnapshot, prevChild: String?) {
        var data = dataSnapshot.value ?: return
        data = data as Map<String, String>

        val entry = Entry(data["id"], data["time"], data["url"], data["image_name"])
        adapter.addItem(entry)
        // Makes sure the newest item always appears at the top of the list
        recycler_view.smoothScrollToPosition(0)
        toggleEmptyView(false)

        Log.i(tag, entry.toString())
    }

    override fun onChildRemoved(dataSnapshot: DataSnapshot) {}

    override fun onCancelled(error: DatabaseError) {}

    override fun onChildMoved(dataSnapshot: DataSnapshot, prevChild: String?) {}

    override fun onChildChanged(dataSnapshot: DataSnapshot, prevChild: String?) {}

    private fun toggleEmptyView(show: Boolean) {
        tv_no_captures.visibility = if (show) View.VISIBLE else View.INVISIBLE
        iv_empty_box.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    private fun getNotificationPref(): Boolean =
            prefs.getBoolean(
                    getString(R.string.pref_notif_key),
                    resources.getBoolean(R.bool.show_notifications)
            )

    /**
     * Sends the specified command to the server which
     * sends that command to the Raspberry PI
     * */
    private fun sendCommand(command: String) {
        // Disable the command menu so multiple commands
        // can't be sent at once
        tv_send_command.isEnabled = false
        tv_send_command.alpha = 0.25f

        val request = Request.Builder()
                .url(Config.URL)
                .addHeader("key", Config.SERVER_KEY)
                .addHeader("command", command)
                .build()

        client.newCall(request).enqueue(object : Callback {
            private fun showErrorSnackbar(msg: String) {
                Snackbar.make(root, msg, Snackbar.LENGTH_LONG)
                        .setActionTextColor(ContextCompat.getColor(this@MainActivity, R.color.snackbarAction))
                        .setAction("Retry") { sendCommand(command) }
                        .show()
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tv_send_command.isEnabled = true
                    tv_send_command.alpha = 1f
                    showErrorSnackbar("An error occurred: ${e.message}")
                }
                Log.i(tag, "Error", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val json = JSONObject(response.body()?.string())
                runOnUiThread {
                    tv_send_command.isEnabled = true
                    tv_send_command.alpha = 1f
                }
                Log.i(tag, "$json")

                if (json["status"] != 200) {
                    runOnUiThread { showErrorSnackbar("${json["error_msg"]}") }
                }
            }
        })
    }

    /**
     * Shows a Snackbar with the option to
     * un-delete a previously deleted item.
     * */
    private fun showDeleteSnackbar() {
        if (deletedEntryQueue.isEmpty())
            return

        val size = deletedEntryQueue.size
        val formattedRemoved = resources.getQuantityString(R.plurals.items_removed, size, size)
        val formattedDeleted = resources.getQuantityString(R.plurals.items_deleted, size, size)

        Snackbar.make(root, formattedRemoved, Snackbar.LENGTH_LONG)
                .setActionTextColor(ContextCompat.getColor(this, R.color.snackbarAction))
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        // Clears all queues when the snackbar dismisses itself
                        if (event == Snackbar.Callback.DISMISS_EVENT_TIMEOUT) {
                            Toast.makeText(this@MainActivity, formattedDeleted, Toast.LENGTH_SHORT).show()
                            clearQueue()
                        }
                    }
                }).setAction("Undo") {
                    // Add the deleted item back to the adapter and
                    // remove it from the queue
                    if (deletedEntryQueue.isNotEmpty()) {
                        val removedItem = deletedEntryQueue.last()
                        val removedPosition = deletedPositions.last()

                        deletedEntryQueue.removeAt(deletedEntryQueue.lastIndex)
                        deletedPositions.removeAt(deletedPositions.lastIndex)
                        deletedIdQueue.removeAt(deletedIdQueue.lastIndex)

                        adapter.addItem(removedItem, removedPosition)
                        toggleEmptyView(false)
                        // Keep showing the Snackbar while there
                        // are still items in the queue
                        showDeleteSnackbar()
                    }
                }.show()
    }

    private fun clearQueue() {
        deletedEntryQueue.forEachIndexed { index, entry ->
            val imageName = entry.imageName ?: ""
            val id = deletedIdQueue[index]

            storageRef.child(imageName).delete().addOnSuccessListener {
                Log.i(tag, "Deleted captures/$imageName")
            }.addOnFailureListener { e ->
                Log.i(tag, "Failed to delete captures/$imageName", e)
            }

            databaseRef.child(id).removeValue().addOnSuccessListener {
                Log.i(tag, "Deleted entry with id $id")
            }.addOnFailureListener { e ->
                Log.i(tag, "Failed to delete entry with is $id", e)
            }
        }

        deletedPositions.clear()
        deletedEntryQueue.clear()
        deletedIdQueue.clear()
    }
}