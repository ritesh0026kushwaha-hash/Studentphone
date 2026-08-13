package com.example.studentphone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandProcessor(private val context: Context) {

    private val contacts = ContactsManager(context)
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("hi", "IN")) ?: TextToSpeech.LANG_MISSING_DATA
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
                ttsReady = true
            }
        }
    }

    fun process(voiceText: String) {
        var text = voiceText.trim().lowercase(Locale.getDefault())

        val wake = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("wake_word", "")?.trim()?.lowercase() ?: ""
        if (wake.isNotEmpty()) {
            text = text.replace(wake, " ").replace("  ", " ").trim()
        }

        Log.d("CommandProcessor", "Processing: $text")

        when {
            containsAny(text, listOf(
                "end call", "cut the call", "cut call", "disconnect", "hang up",
                "reject call", "decline call", "end the call",
                "कॉल काट", "कॉल कट", "कट करो", "काट दो", "कॉल बंद", "कॉल समाप्त",
                "डिस्कनेक्ट", "कॉल काटो"
            )) -> {
                CallEndAccessibilityService.endCurrentCall()
                speak("कॉल काट रहा हूँ")
            }

            containsAny(text, listOf("video", "वीडियो", "विडियो")) -> {
                val name = extractName(text)
                val number = contacts.getNumber(name)
                if (number != null) {
                    makeWhatsAppVideoCall(number)
                    speak("${contacts.displayName(name)} को वीडियो कॉल कर रहा हूँ")
                } else {
                    speak("$name का नंबर नहीं है। पहले कॉन्टैक्ट जोड़ें।")
                }
            }

            containsAny(text, listOf(
                "whatsapp", "whats app",
                "व्हाट्सएप", "व्हाट्सऐप", "व्हाट्सप", "व्हाट्सप्प"
            )) -> {
                val name = extractName(text)
                val number = contacts.getNumber(name)
                if (number != null) {
                    val content = extractContent(text, name)
                    openWhatsAppChat(number, content)
                    speak("${contacts.displayName(name)} के लिए व्हाट्सएप खोल रहा हूँ")
                } else {
                    speak("$name का नंबर नहीं है। पहले कॉन्टैक्ट जोड़ें।")
                }
            }

            containsAny(text, listOf(
                "call", "कॉल", "काल", "फोन", "फ़ोन", "बुलाओ", "बुला"
            )) -> {
                val name = extractName(text)
                val number = contacts.getNumber(name)
                if (number != null) {
                    makePhoneCall(number)
                    speak("${contacts.displayName(name)} को कॉल कर रहा हूँ")
                } else {
                    speak("$name का नंबर नहीं है। पहले कॉन्टैक्ट जोड़ें।")
                }
            }

            containsAny(text, listOf(
                "message", "msg", "sms", "text",
                "मैसेज", "मेसेज", "संदेश", "सन्देश", "भेजो", "भेज", "लिखो"
            )) -> {
                sendSms(text)
            }

            containsAny(text, listOf("open", "खोलो", "खोल", "चालू करो", "चलाओ")) -> {
                openApp(text)
            }

            containsAny(text, listOf("time", "समय", "टाइम", "कितने बजे", "बजे")) -> {
                val now = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
                speak("अभी समय है $now")
            }

            else -> speak("मुझे समझ नहीं आया। कृपया दोबारा बोलें।")
        }
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it) }

    private fun extractName(text: String): String {
        for ((name, _) in contacts.all()) {
            if (text.contains(name.lowercase(Locale.getDefault()))) return name
        }
        var cleaned = " $text "
        for (kw in listOf(
            "video call", "whatsapp", "whats app", "call", "message", "send", "sms", "text",
            "please", "to", "saying", "that", "open", "the",
            "वीडियो", "विडियो", "व्हाट्सएप", "व्हाट्सऐप", "व्हाट्सप", "कॉल", "काल",
            "फोन", "फ़ोन", "मैसेज", "मेसेज", "संदेश", "सन्देश", "भेजो", "भेज", "लिखो",
            "बुलाओ", "बुला", "खोलो", "खोल", "चालू", "करो", "कर", "को", "प्लीज", "कृपया"
        )) {
            cleaned = cleaned.replace(" $kw ", " ")
        }
        return cleaned.trim()
    }

    private fun extractContent(text: String, contactName: String): String {
        var cleaned = " $text "
        for (kw in listOf(
            "whatsapp", "whats app", "message", "send", "saying", "that", "to", "please",
            "व्हाट्सएप", "व्हाट्सऐप", "व्हाट्सप", "मैसेज", "मेसेज", "संदेश", "सन्देश",
            "भेजो", "भेज", "को", "करो", "प्लीज", "कृपया"
        )) {
            cleaned = cleaned.replace(" $kw ", " ")
        }
        cleaned = cleaned.replace(" ${contactName.lowercase(Locale.getDefault())} ", " ")
        return cleaned.trim()
    }

    private fun sendSms(text: String) {
        val contact = contacts.all().entries.firstOrNull {
            text.contains(it.key.lowercase(Locale.getDefault()))
        }
        if (contact == null) {
            speak("किसको मैसेज भेजना है, बताइए।")
            return
        }
        var content = " $text "
        for (kw in listOf(
            "send message", "send a message", "message", "sms", "text", "to", "saying", "that", "please",
            "मैसेज", "मेसेज", "संदेश", "सन्देश", "भेजो", "भेज दो", "भेज", "को", "करो", "कृपया", "प्लीज"
        )) {
            content = content.replace(" $kw ", " ")
        }
        content = content.replace(" ${contact.key.lowercase(Locale.getDefault())} ", " ").trim()

        if (content.isEmpty()) {
            speak("क्या मैसेज करना है, बताइए।")
            return
        }
        try {
            SmsManager.getDefault().sendTextMessage(contact.value, null, content, null, null)
            speak("${contact.key} को मैसेज भेज दिया")
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.value}"))
            intent.putExtra("sms_body", content)
            context.startActivity(intent)
        }
    }

    private fun makePhoneCall(number: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        context.startActivity(intent)
    }

    private fun makeWhatsAppVideoCall(number: String) {
        val clean = number.replace(" ", "")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://video?phone=$clean"))
        intent.setPackage("com.whatsapp")
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$clean")))
        }
    }

    private fun openWhatsAppChat(number: String, text: String) {
        val clean = number.replace("+", "").replace(" ", "")
        val uri = Uri.parse("https://wa.me/$clean?text=${Uri.encode(text)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            speak("व्हाट्सएप नहीं खुल सका")
        }
    }

    private fun openApp(text: String) {
        val apps = mapOf(
            "youtube" to "com.google.android.youtube",
            "यूट्यूब" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "व्हाट्सएप" to "com.whatsapp",
            "व्हाट्सऐप" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "इंस्टाग्राम" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "क्रोम" to "com.android.chrome",
            "camera" to "com.android.camera",
            "कैमरा" to "com.android.camera",
            "photos" to "com.google.android.apps.photos",
            "फोटो" to "com.google.android.apps.photos",
            "maps" to "com.google.android.apps.maps",
            "मैप्स" to "com.google.android.apps.maps",
            "नक्शा" to "com.google.android.apps.maps",
            "settings" to "com.android.settings",
            "सेटिंग्स" to "com.android.settings",
            "play store" to "com.android.vending",
            "प्ले स्टोर" to "com.android.vending"
        )
        for ((key, pkg) in apps) {
            if (text.contains(key)) {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    context.startActivity(intent)
                    speak("$key खोल रहा हूँ")
                } else {
                    speak("$key इंस्टॉल नहीं है")
                }
                return
            }
        }
        speak("ऐप नहीं मिला। जैसे बोलिए - यूट्यूब खोलो, व्हाट्सएप खोलो")
    }

    fun speak(message: String) {
        if (ttsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "cmd")
        }
    }

    fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
