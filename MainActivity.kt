package com.example.studentphone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), RecognitionListener {

    private lateinit var statusText: TextView
    private lateinit var tvContacts: TextView
    private lateinit var etWakeWord: EditText
    private lateinit var etName: EditText
    private lateinit var etNumber: EditText
    private var testRecognizer: SpeechRecognizer? = null
    private val contacts by lazy { ContactsManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        tvContacts = findViewById(R.id.tvContacts)
        etWakeWord = findViewById(R.id.etWakeWord)
        etName = findViewById(R.id.etName)
        etNumber = findViewById(R.id.etNumber)

        etWakeWord.setText(getSharedPreferences("settings", MODE_PRIVATE).getString("wake_word", ""))

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            requestNeededPermissions()
            getSharedPreferences("settings", MODE_PRIVATE)
                .edit().putString("wake_word", etWakeWord.text.toString().trim()).apply()
            val intent = Intent(this, VoiceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            statusText.text = "स्टेटस: बैकग्राउंड में सुन रहा हूँ"
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, VoiceService::class.java))
            statusText.text = "स्टेटस: बंद"
        }

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            requestNeededPermissions()
            testVoice()
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            val name = etName.text.toString().trim()
            val number = etNumber.text.toString().trim()
            if (name.isEmpty() || number.isEmpty()) {
                Toast.makeText(this, "नाम और नंबर दोनों भरें", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            contacts.add(name, number)
            etName.text.clear()
            etNumber.text.clear()
            refreshContacts()
        }

        refreshContacts()
    }

    private fun testVoice() {
        statusText.text = "स्टेटस: सुन रहा हूँ... बोलिए"
        testRecognizer?.destroy()
        testRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        testRecognizer?.setRecognitionListener(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
        }
        testRecognizer?.startListening(intent)
    }

    override fun onResults(results: Bundle?) {
        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
        statusText.text = "आपने कहा: $heard"
        CommandProcessor(this).process(heard)
    }

    override fun onError(error: Int) {
        statusText.text = "स्टेटस: कुछ सुनाई नहीं दिया (error $error)"
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}

    private fun refreshContacts() {
        val all = contacts.all()
        tvContacts.text = if (all.isEmpty()) "अभी कोई कॉन्टैक्ट नहीं है। मम्मा, पापा, दोस्त जोड़ें..."
        else all.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 100)
        }
    }

    override fun onDestroy() {
        testRecognizer?.destroy()
        super.onDestroy()
    }
}
