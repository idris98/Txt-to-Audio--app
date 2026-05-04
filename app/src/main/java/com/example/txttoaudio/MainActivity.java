package com.example.txttoaudio;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int PICK_TXT_FILE = 1;

    private TextToSpeech tts;
    private TextView tvFileName, tvStatus, tvPreview;
    private Button btnPickFile, btnSpeak, btnSaveAudio, btnStop;
    private SeekBar sbSpeed, sbPitch;
    private TextView tvSpeed, tvPitch;
    private ProgressBar progressBar;

    private String fileContent = "";
    private boolean ttsReady = false;
    private boolean isSpeaking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Views
        tvFileName   = findViewById(R.id.tvFileName);
        tvStatus     = findViewById(R.id.tvStatus);
        tvPreview    = findViewById(R.id.tvPreview);
        btnPickFile  = findViewById(R.id.btnPickFile);
        btnSpeak     = findViewById(R.id.btnSpeak);
        btnSaveAudio = findViewById(R.id.btnSaveAudio);
        btnStop      = findViewById(R.id.btnStop);
        sbSpeed      = findViewById(R.id.sbSpeed);
        sbPitch      = findViewById(R.id.sbPitch);
        tvSpeed      = findViewById(R.id.tvSpeed);
        tvPitch      = findViewById(R.id.tvPitch);
        progressBar  = findViewById(R.id.progressBar);

        btnSpeak.setEnabled(false);
        btnSaveAudio.setEnabled(false);
        btnStop.setEnabled(false);

        // TTS init
        tts = new TextToSpeech(this, this);

        // Speed seekbar (50% – 200%)
        sbSpeed.setMax(150);
        sbSpeed.setProgress(50); // default = 1.0x
        sbSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                float speed = 0.5f + p / 100f;
                tvSpeed.setText(String.format("Tezlik: %.1fx", speed));
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        // Pitch seekbar (50% – 200%)
        sbPitch.setMax(150);
        sbPitch.setProgress(50);
        sbPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                float pitch = 0.5f + p / 100f;
                tvPitch.setText(String.format("Ohang: %.1fx", pitch));
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        // Pick file
        btnPickFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/*");
            startActivityForResult(intent, PICK_TXT_FILE);
        });

        // Speak
        btnSpeak.setOnClickListener(v -> {
            if (isSpeaking) return;
            speakText();
        });

        // Stop
        btnStop.setOnClickListener(v -> {
            if (tts != null) tts.stop();
            isSpeaking = false;
            tvStatus.setText("To'xtatildi.");
            btnStop.setEnabled(false);
            btnSpeak.setEnabled(true);
        });

        // Save audio
        btnSaveAudio.setOnClickListener(v -> saveAudioFile());
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // Try Uzbek, fallback to Russian, then English
            int result = tts.setLanguage(new Locale("uz"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts.setLanguage(new Locale("ru"));
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.ENGLISH);
            }
            ttsReady = true;
            tvStatus.setText("TTS tayyor. Fayl tanlang.");
        } else {
            tvStatus.setText("TTS ishga tushmadi!");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_TXT_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            String fileName = getFileName(uri);
            tvFileName.setText("Fayl: " + fileName);
            fileContent = readTextFromUri(uri);
            if (fileContent != null && !fileContent.isEmpty()) {
                // Preview first 200 chars
                String preview = fileContent.length() > 200
                        ? fileContent.substring(0, 200) + "..."
                        : fileContent;
                tvPreview.setText(preview);
                tvStatus.setText("Fayl yuklandi. " + fileContent.length() + " belgi.");
                btnSpeak.setEnabled(ttsReady);
                btnSaveAudio.setEnabled(ttsReady);
            } else {
                tvStatus.setText("Fayl bo'sh yoki o'qib bo'lmadi.");
            }
        }
    }

    private void speakText() {
        if (!ttsReady || fileContent.isEmpty()) return;
        float speed = 0.5f + sbSpeed.getProgress() / 100f;
        float pitch = 0.5f + sbPitch.getProgress() / 100f;
        tts.setSpeechRate(speed);
        tts.setPitch(pitch);

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String utteranceId) {
                runOnUiThread(() -> {
                    isSpeaking = true;
                    btnStop.setEnabled(true);
                    btnSpeak.setEnabled(false);
                    tvStatus.setText("O'qilmoqda...");
                });
            }
            public void onDone(String utteranceId) {
                runOnUiThread(() -> {
                    isSpeaking = false;
                    btnStop.setEnabled(false);
                    btnSpeak.setEnabled(true);
                    tvStatus.setText("O'qish tugadi!");
                });
            }
            public void onError(String utteranceId) {
                runOnUiThread(() -> {
                    isSpeaking = false;
                    tvStatus.setText("Xatolik yuz berdi.");
                });
            }
        });

        tts.speak(fileContent, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString());
    }

    private void saveAudioFile() {
        if (!ttsReady || fileContent.isEmpty()) return;
        float speed = 0.5f + sbSpeed.getProgress() / 100f;
        float pitch = 0.5f + sbPitch.getProgress() / 100f;
        tts.setSpeechRate(speed);
        tts.setPitch(pitch);

        File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = "tts_audio_" + System.currentTimeMillis() + ".wav";
        File outputFile = new File(outputDir, fileName);

        tvStatus.setText("Audio saqlanmoqda...");
        progressBar.setVisibility(View.VISIBLE);

        Bundle params = new Bundle();
        tts.synthesizeToFile(fileContent, params, outputFile, UUID.randomUUID().toString());

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String id) {}
            public void onDone(String id) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Saqlandi: Music/" + fileName);
                    Toast.makeText(MainActivity.this,
                            "Audio saqlandi:\n" + outputFile.getAbsolutePath(),
                            Toast.LENGTH_LONG).show();
                });
            }
            public void onError(String id) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Saqlashda xatolik!");
                });
            }
        });
    }

    private String readTextFromUri(Uri uri) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private String getFileName(Uri uri) {
        String result = "fayl.txt";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (idx >= 0) result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
