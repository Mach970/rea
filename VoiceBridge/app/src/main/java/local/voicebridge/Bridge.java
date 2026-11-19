package local.voicebridge;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

public class Bridge extends Activity {
    private EditText key, model, question;
    private TextView status;
    private Button speak, send;
    private Uri file;
    private TextToSpeech voice;
    private boolean ready, busy;

    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(32,32,32,32);
        scroll.addView(form);
        setContentView(scroll);
        key = field(form,"Gemini API key (this session only)");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSaveEnabled(false);
        model = field(form,"Gemini model ID");
        model.setText("gemini-2.5-flash");
        question = field(form,"Speak or type your question");
        Button choose = button(form,"Choose handover file");
        choose.setOnClickListener(v -> {
            if (!busy) startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("text/markdown")
                .putExtra(Intent.EXTRA_TITLE,"Current-Handover.md"),1);
        });
        speak = button(form,"Speak and ask Gemini");
        speak.setOnClickListener(v -> {
            if (!configured()) return;
            try {
                startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),2);
            } catch (ActivityNotFoundException e) { status.setText("Voice input unavailable. Type your question below the model ID."); }
        });
        send = button(form,"Send typed question");
        send.setOnClickListener(v -> ask());
        status = new TextView(this);
        form.addView(status);
        String saved = getPreferences(0).getString("file",null);
        if (saved != null) file = Uri.parse(saved);
        voice = new TextToSpeech(this, result -> ready = result == TextToSpeech.SUCCESS);
    }
    EditText field(LinearLayout form,String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        form.addView(input);
        return input;
    }
    Button button(LinearLayout form,String label) {
        Button b = new Button(this);
        b.setText(label);
        form.addView(b);
        return b;
    }
    protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (result != RESULT_OK || data == null) return;
        if (request == 1 && data.getData() != null) {
            Uri selected = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(selected,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                file = selected;
                getPreferences(0).edit().putString("file",file.toString()).apply();
                status.setText("Handover file selected.");
            } catch (SecurityException e) { status.setText("Choose a writable file in Downloads."); }
        } else if (request == 2) {
            ArrayList<String> words = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (words != null && !words.isEmpty()) { question.setText(words.get(0)); ask(); }
        }
    }
    boolean configured() {
        if (file != null && !key.getText().toString().trim().isEmpty()
                && model.getText().toString().trim().matches("[A-Za-z0-9._-]+")) return true;
        status.setText("Enter your key and model ID, then choose a handover file.");
        return false;
    }
    void ask() {
        String prompt = question.getText().toString().trim();
        if (busy || !configured()) return;
        if (prompt.isEmpty()) { status.setText("Enter a question first."); return; }
        String token = key.getText().toString().trim();
        String name = model.getText().toString().trim();
        String id = UUID.randomUUID().toString();
        Uri destination = file;
        busy = true;
        speak.setEnabled(false);
        send.setEnabled(false);
        status.setText("Asking Gemini…");
        new Thread(() -> {
            String answer = null;
            try {
                answer = generate(name,token,prompt);
                String note = "# Handover " + id + "\n\n## Question\n\n" + prompt + "\n\n## Gemini reply\n\n" + answer + "\n";
                try (OutputStream out = getContentResolver().openOutputStream(destination,"wt")) {
                    if (out == null) throw new IOException("File unavailable");
                    out.write(note.getBytes(StandardCharsets.UTF_8));
                }
                String reply = answer;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    boolean spoken = ready && voice.speak(reply,TextToSpeech.QUEUE_FLUSH,null,id) == TextToSpeech.SUCCESS;
                    status.setText((spoken ? "Saved.\n" : "Saved. Speech unavailable.\n") + reply);
                });
            } catch (Exception e) {
                String message = answer == null ? "Request failed. " + e.getMessage() : "Reply received but file save failed.\n" + answer;
                runOnUiThread(() -> status.setText(message));
            } finally {
                runOnUiThread(() -> { busy=false; speak.setEnabled(true); send.setEnabled(true); });
            }
        }).start();
    }
    String generate(String name,String token,String prompt) throws Exception {
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"+name+":generateContent");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json");
            conn.setRequestProperty("x-goog-api-key",token);
            JSONObject part = new JSONObject().put("text",prompt);
            JSONObject content = new JSONObject().put("parts",new JSONArray().put(part));
            JSONObject body = new JSONObject().put("contents",new JSONArray().put(content))
                .put("generationConfig",new JSONObject().put("maxOutputTokens",1024));
            try (OutputStream out = conn.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP "+code+". Check model ID, key, quota and network.");
            StringBuilder raw = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))) {
                String line;
                while ((line=reader.readLine()) != null) raw.append(line);
            }
            JSONArray parts = new JSONObject(raw.toString()).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts");
            StringBuilder reply = new StringBuilder();
            for (int i=0;i<parts.length();i++) {
                JSONObject p = parts.getJSONObject(i);
                if (!p.optBoolean("thought")) reply.append(p.optString("text",""));
            }
            if (reply.toString().trim().isEmpty()) throw new IOException("No text returned.");
            return reply.toString();
        } finally { conn.disconnect(); }
    }
    protected void onDestroy() {
        if (voice != null) voice.shutdown();
        super.onDestroy();
    }
}
