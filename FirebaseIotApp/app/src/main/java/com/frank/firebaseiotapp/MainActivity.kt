package com.frank.firebaseiotapp

import com.frank.firebaseiotapp.R
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.frank.firebaseiotapp.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: HistoryItemAdapter

    private var dataListener: ValueEventListener? = null
    private var historyListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            initializeFirebase()
            setupRecyclerView()
            setupClickListeners()
        } catch (e: Exception) {
            Log.e("FirebaseApp", "onCreate failed", e)
            Toast.makeText(this, "App initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initializeFirebase() {
        try {
            // Initialize Firebase with explicit configuration
            auth = FirebaseAuth.getInstance()
            database = Firebase.database

            // Check if already signed in
            if (auth.currentUser != null) {
                updateConnectionStatus("Already connected to Firebase", true)
                startListening()
                return
            }

            // First, test if Firebase is properly initialized
            testFirebaseInitialization()
            
        } catch (e: Exception) {
            Log.e("FirebaseApp", "Firebase initialization failed", e)
            updateConnectionStatus("Firebase initialization failed", false)
            Toast.makeText(this, "Firebase initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun testFirebaseInitialization() {
        // Test database connection first
        database.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    updateConnectionStatus("Firebase connected, trying auth...", false)
                    tryAnonymousAuth()
                } else {
                    updateConnectionStatus("Firebase not connected", false)
                    Toast.makeText(this@MainActivity, "Firebase database not connected", Toast.LENGTH_LONG).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                updateConnectionStatus("Firebase connection failed", false)
                Log.e("FirebaseApp", "Database connection test failed", error.toException())
                Toast.makeText(this@MainActivity, "Database connection failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun tryAnonymousAuth() {
        // Try anonymous authentication
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                updateConnectionStatus("Connected to Firebase", true)
                startListening()
            } else {
                updateConnectionStatus("Authentication failed", false)
                Log.e("FirebaseApp", "Authentication failed", task.exception)
                val errorMsg = task.exception?.message ?: "Unknown error"
                Toast.makeText(this@MainActivity, "Firebase auth failed: $errorMsg", Toast.LENGTH_LONG).show()
                
                // Change button text to allow testing
                binding.btnStartListening.text = "Test Connection"
                
                // Try to connect without authentication for testing
                tryConnectWithoutAuth()
            }
        }
    }

    private fun tryConnectWithoutAuth() {
        try {
            // Try to read from database without authentication
            database.getReference("test").child("data").get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    updateConnectionStatus("Connected (no auth)", true)
                    binding.btnStartListening.text = "Start Listening"
                    startListening()
                } else {
                    updateConnectionStatus("Connection failed", false)
                    Log.e("FirebaseApp", "Database connection failed", task.exception)
                    Toast.makeText(this@MainActivity, "Database connection failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseApp", "Database test failed", e)
            updateConnectionStatus("Database test failed", false)
        }
    }

    private fun testFirebaseConnection() {
        updateConnectionStatus("Testing connection...", false)
        tryConnectWithoutAuth()
    }

    private fun setupRecyclerView() {
        adapter = HistoryItemAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnUpdateValue.setOnClickListener {
            updateData()
        }

        binding.btnStartListening.setOnClickListener {
            if (binding.btnStartListening.text == "Test Connection") {
                testFirebaseConnection()
            } else {
                startListening()
            }
        }

        binding.btnStopListening.setOnClickListener {
            stopListening()
        }
    }

    private fun startListening() {
        stopListening() // Clean up any existing listeners

        // Listen to the main data path (same as ESP32)
        dataListener = database.getReference("test/data").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val value = snapshot.getValue(Int::class.java) ?: 0
                    updateCurrentValue(value)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseApp", "Data listener cancelled", error.toException())
                }
            }
        )

        // Listen to history updates
        historyListener = database.getReference("test/history").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val historyItems = mutableListOf<HistoryItem>()
                    snapshot.children.forEach { child ->
                        val value = child.child("value").getValue(Int::class.java) ?: 0
                        val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0
                        val source = child.child("source").getValue(String::class.java) ?: "Unknown"
                        historyItems.add(HistoryItem(value, timestamp, source))
                    }
                    adapter.updateItems(historyItems)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseApp", "History listener cancelled", error.toException())
                }
            }
        )

        updateConnectionStatus("Listening for updates...", true)
    }

    private fun stopListening() {
        dataListener?.let { database.getReference("test/data").removeEventListener(it) }
        historyListener?.let { database.getReference("test/history").removeEventListener(it) }
        dataListener = null
        historyListener = null
        updateConnectionStatus("Stopped listening", false)
    }

    private fun updateData() {
        val newValueStr = binding.etNewValue.text.toString()
        if (newValueStr.isBlank()) {
            Toast.makeText(this, "Please enter a value", Toast.LENGTH_SHORT).show()
            return
        }

        val newValue = newValueStr.toIntOrNull()
        if (newValue == null) {
            Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update main data value
                database.getReference("test/data").setValue(newValue).await()

                // Add to history
                val historyRef = database.getReference("test/history").push()
                val historyData = mapOf(
                    "value" to newValue,
                    "timestamp" to System.currentTimeMillis(),
                    "source" to "Android App"
                )
                historyRef.setValue(historyData).await()

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Value updated successfully",Toast.LENGTH_SHORT).show()
                    binding.etNewValue.text.clear()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Update failed: ${e.message}",Toast.LENGTH_SHORT).show()
                    Log.e("FirebaseApp", "Update failed", e)
                }
            }
        }
    }

    private fun updateCurrentValue(value: Int) {
        binding.tvCurrentValue.text = "Current Value: $value"
        binding.tvLastUpdate.text = "Last Update: ${getCurrentTime()}"
    }

    private fun updateConnectionStatus(message: String, isConnected: Boolean) {
        binding.tvConnectionStatus.text = message
        binding.tvConnectionStatus.setTextColor(
            if (isConnected) getColor(R.color.green)
            else getColor(R.color.red)
        )
    }

    private fun getCurrentTime(): String {
        return DateFormat.format("dd/MM/yyyy HH:mm:ss",
            Date()).toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }
}
