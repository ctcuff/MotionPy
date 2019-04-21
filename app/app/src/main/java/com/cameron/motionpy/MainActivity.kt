package com.cameron.motionpy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import java.io.IOException

class MainActivity : AppCompatActivity(), ChildEventListener {

    private val tag = MainActivity::class.java.simpleName
    private val client = OkHttpClient()
    private val adapter = ViewAdapter()

    // These lists save deleted items and their properties
    // so they can be added back to the adapter if "Undo" is pressed
    private val deletedEntryQueue = mutableListOf<Entry>()
    private val deletedPositions = mutableListOf<Int>()
    private val deletedIdQueue = mutableListOf<String>()

    private val storageRef = FirebaseStorage.getInstance().getReference("captures")
    private val databaseRef = FirebaseDatabase.getInstance().getReference("/")

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
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.colorSecondaryDark))
        setSupportActionBar(toolbar)

        var useGrid = false
        val linearManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        val gridManager = GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false)

        toggle_layout.setOnClickListener {
            useGrid = !useGrid
            toggle_layout.setImageDrawable(
                    ContextCompat.getDrawable(this,
                            if (useGrid) R.drawable.ic_list_24dp
                            else R.drawable.ic_view_compact_24dp)
            )
            adapter.swapLayout(useGrid)
            recycler_view.layoutManager = if (useGrid) gridManager else linearManager
        }

        databaseRef.addChildEventListener(this)

        recycler_view.layoutManager = linearManager
        recycler_view.addItemDecoration(DividerItemDecoration(this, linearManager.orientation))
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

                showDeleteSnackbar()
            }
        })

        touchHelper.attachToRecyclerView(recycler_view)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_start -> sendCommand("start", item)
            R.id.action_stop -> sendCommand("stop", item)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter(NotificationService.DATA_MSG_BROADCAST))
    }

    override fun onDestroy() {
        super.onDestroy()
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

        Log.i(tag, entry.toString())
    }

    override fun onChildRemoved(dataSnapshot: DataSnapshot) {
    }

    override fun onCancelled(error: DatabaseError) {
    }

    override fun onChildMoved(dataSnapshot: DataSnapshot, prevChild: String?) {
    }

    override fun onChildChanged(dataSnapshot: DataSnapshot, prevChild: String?) {
    }

    private fun sendCommand(command: String, item: MenuItem?) {
        item?.isEnabled = false
        val request = Request.Builder()
                .url(Config.URL)
                .addHeader("key", Config.SERVER_KEY)
                .addHeader("command", command)
                .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.i(tag, "Error", e)
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { item?.isEnabled = true }
                Log.i(tag, "Response: ${response.body()?.string()}")
            }
        })
    }

    private fun showDeleteSnackbar() {
        if (deletedEntryQueue.isEmpty())
            return

        val snackbar = Snackbar.make(root, "Items removed: ${deletedEntryQueue.size}", Snackbar.LENGTH_LONG)
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        // Clears all queues when the snackbar dismisses itself
                        if (event == Snackbar.Callback.DISMISS_EVENT_TIMEOUT) {
                            Toast.makeText(
                                    this@MainActivity,
                                    "Deleted items: ${deletedEntryQueue.size}",
                                    Toast.LENGTH_SHORT).show()
                            clearQueue()
                        }
                    }
                })

        // Add the deleted item back to the adapter and
        // remove it from the queue
        snackbar.setAction("Undo") {
            if (deletedEntryQueue.size >= 1) {
                val removedItem = deletedEntryQueue.last()
                val removedPosition = deletedPositions.last()

                deletedEntryQueue.removeAt(deletedEntryQueue.size - 1)
                deletedPositions.removeAt(deletedPositions.size - 1)
                deletedIdQueue.removeAt(deletedIdQueue.size - 1)

                adapter.addItem(removedItem, removedPosition)
                showDeleteSnackbar()
            }
        }
        snackbar.show()
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