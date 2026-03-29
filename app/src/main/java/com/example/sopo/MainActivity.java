package com.example.sopo;

import android.Manifest;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.transition.TransitionManager;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.bumptech.glide.Glide;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SopoDownload";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private EditText etUrl;
    private ImageButton btnFetch, btnPaste;
    private ProgressBar pbFetch, btnDownload;
    private MaterialCardView cvPreview;
    private ImageView ivThumbnail;
    private TextView tvTitle, tvChannel, tvDuration, tvProgressStatus, tvProgressDetails, tvDownloadLabel, tvLargeTitle;

    private LinearLayout llOptions, llProgress, llUrlBar, llSelectionControls;
    private ScrollView scrollResults;
    private ConstraintLayout mainContainer;
    private MaterialButtonToggleGroup toggleGroupType;
    private Spinner spinnerQuality, spinnerFormat;

    private Python py;
    private PyObject pythonModule;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<Map<String, String>> videoFormats = new ArrayList<>();
    private List<Map<String, String>> audioFormats = new ArrayList<>();
    private String currentUrl = "";
    private boolean isAudioMode = false;
    private boolean isUIMovedUp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initPython();
        setupListeners();
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            checkPermissions();
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("Storage permission granted!");
            } else {
                showToast("Storage permission is required to save downloads.");
            }
        }
    }

    private void initViews() {
        mainContainer = findViewById(R.id.main_content);
        tvLargeTitle = findViewById(R.id.tvLargeTitle);
        llUrlBar = findViewById(R.id.llUrlBar);
        etUrl = findViewById(R.id.etUrl);
        btnFetch = findViewById(R.id.btnFetch);
        btnPaste = findViewById(R.id.btnPaste);
        pbFetch = findViewById(R.id.pbFetch);
        scrollResults = findViewById(R.id.scrollResults);
        cvPreview = findViewById(R.id.cvPreview);
        ivThumbnail = findViewById(R.id.ivThumbnail);
        tvTitle = findViewById(R.id.tvTitle);
        tvChannel = findViewById(R.id.tvChannel);
        tvDuration = findViewById(R.id.tvDuration);
        llOptions = findViewById(R.id.llOptions);
        llSelectionControls = findViewById(R.id.llSelectionControls);
        toggleGroupType = findViewById(R.id.toggleGroupType);
        spinnerQuality = findViewById(R.id.spinnerQuality);
        spinnerFormat = findViewById(R.id.spinnerFormat);
        btnDownload = findViewById(R.id.btnDownload);
        tvDownloadLabel = findViewById(R.id.tvDownloadLabel);
        llProgress = findViewById(R.id.llProgress);
        tvProgressStatus = findViewById(R.id.tvProgressStatus);
        tvProgressDetails = findViewById(R.id.tvProgressDetails);
    }

    private void initPython() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();
        pythonModule = py.getModule("downloader");
    }

    private void setupListeners() {
        btnFetch.setOnClickListener(v -> fetchMetadata());

        toggleGroupType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                isAudioMode = (checkedId == R.id.btnTypeAudio);
                updateFormatSpinners();
            }
        });

        btnDownload.setOnClickListener(v -> startDownload());

        btnPaste.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    etUrl.setText(text);
                    fetchMetadata();
                } else {
                    showToast("Clipboard is empty");
                }
            } else {
                showToast("Clipboard is empty");
            }
        });
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void moveUIUp() {
        if (isUIMovedUp) return;

        TransitionManager.beginDelayedTransition(mainContainer);
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(mainContainer);

        constraintSet.clear(tvLargeTitle.getId(), ConstraintSet.TOP);
        constraintSet.clear(tvLargeTitle.getId(), ConstraintSet.BOTTOM);
        constraintSet.clear(llUrlBar.getId(), ConstraintSet.TOP);
        constraintSet.clear(llUrlBar.getId(), ConstraintSet.BOTTOM);

        constraintSet.connect(tvLargeTitle.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(16));
        tvLargeTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32); 

        constraintSet.connect(llUrlBar.getId(), ConstraintSet.TOP, tvLargeTitle.getId(), ConstraintSet.BOTTOM, dpToPx(8));

        constraintSet.applyTo(mainContainer);
        scrollResults.setVisibility(View.VISIBLE);
        isUIMovedUp = true;
    }

    private void fetchMetadata() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) {
            showToast("Please enter a URL");
            return;
        }

        currentUrl = url;
        setLoadingState(true);
        moveUIUp();
        
        executorService.execute(() -> {
            try {
                PyObject result = pythonModule.callAttr("get_video_info", url);
                Map<PyObject, PyObject> resultMap = result.asMap();
                
                String status = resultMap.get(py.getBuiltins().get("str").call("status")).toString();

                if ("success".equals(status)) {
                    displayMetadata(resultMap);
                } else {
                    String error = resultMap.get(py.getBuiltins().get("str").call("message")).toString();
                    handleError("Error: " + error);
                }
            } catch (Exception e) {
                handleError("Failed to fetch info: " + e.getMessage());
            }
        });
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "Unknown size";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private void displayMetadata(Map<PyObject, PyObject> data) {
        String title = data.get(py.getBuiltins().get("str").call("title")).toString();
        String channel = data.get(py.getBuiltins().get("str").call("channel")).toString();
        String thumbnail = data.get(py.getBuiltins().get("str").call("thumbnail")).toString();
        long duration = data.get(py.getBuiltins().get("str").call("duration")).toLong();

        videoFormats.clear();
        List<PyObject> pyVideoFormats = data.get(py.getBuiltins().get("str").call("video_formats")).asList();
        for (PyObject f : pyVideoFormats) {
            Map<PyObject, PyObject> fMap = f.asMap();
            Map<String, String> format = new HashMap<>();
            long fs = fMap.get(py.getBuiltins().get("str").call("filesize")).toLong();
            String label = fMap.get(py.getBuiltins().get("str").call("label")).toString();
            
            format.put("label", label + (fs > 0 ? " (" + formatSize(fs) + ")" : ""));
            format.put("id", fMap.get(py.getBuiltins().get("str").call("format_id")).toString());
            format.put("height", fMap.get(py.getBuiltins().get("str").call("height")).toString());
            videoFormats.add(format);
        }

        audioFormats.clear();
        PyObject pyAudioObj = data.get(py.getBuiltins().get("str").call("audio_formats"));
        if (pyAudioObj != null) {
            List<PyObject> pyAudioList = pyAudioObj.asList();
            for (PyObject f : pyAudioList) {
                Map<PyObject, PyObject> fMap = f.asMap();
                Map<String, String> format = new HashMap<>();
                long fs = fMap.get(py.getBuiltins().get("str").call("filesize")).toLong();
                String label = fMap.get(py.getBuiltins().get("str").call("label")).toString();
                
                format.put("label", label + (fs > 0 ? " (" + formatSize(fs) + ")" : ""));
                format.put("id", fMap.get(py.getBuiltins().get("str").call("format_id")).toString());
                audioFormats.add(format);
            }
        }

        mainHandler.post(() -> {
            setLoadingState(false);
            cvPreview.setVisibility(View.VISIBLE);
            llOptions.setVisibility(View.VISIBLE);
            llSelectionControls.setVisibility(View.VISIBLE);
            
            tvTitle.setText(title);
            tvChannel.setText(channel);
            tvDuration.setText(formatDuration(duration));
            Glide.with(this).load(thumbnail).into(ivThumbnail);

            updateFormatSpinners();
            updateDownloadButtonState("Start Download", 0, true, "ready");
        });
    }

    private void updateFormatSpinners() {
        if (isAudioMode) {
            spinnerQuality.setEnabled(true);
            List<String> labels = new ArrayList<>();
            for (Map<String, String> f : audioFormats) labels.add(f.get("label"));
            ArrayAdapter<String> audioAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
            audioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerQuality.setAdapter(audioAdapter);

            String[] aFormats = {"mp3", "m4a", "wav"};
            ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, aFormats);
            formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerFormat.setAdapter(formatAdapter);
        } else {
            spinnerQuality.setEnabled(true);
            List<String> labels = new ArrayList<>();
            for (Map<String, String> f : videoFormats) labels.add(f.get("label"));
            
            ArrayAdapter<String> videoAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
            videoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerQuality.setAdapter(videoAdapter);

            String[] vFormats = {"mp4", "mkv", "webm"};
            ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, vFormats);
            formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerFormat.setAdapter(formatAdapter);
        }
    }

    private void startDownload() {
        if (currentUrl.isEmpty()) return;

        int qualityPos = spinnerQuality.getSelectedItemPosition();
        if (qualityPos < 0) {
            showToast("No quality selected");
            return;
        }

        String formatId;
        int targetHeight = 0;
        if (isAudioMode) {
            formatId = audioFormats.get(qualityPos).get("id");
        } else {
            formatId = videoFormats.get(qualityPos).get("id");
            try {
                targetHeight = Integer.parseInt(videoFormats.get(qualityPos).get("height"));
            } catch (Exception e) {}
        }

        String ext = spinnerFormat.getSelectedItem().toString();
        
        try {
            JSONObject options = new JSONObject();
            options.put("type", isAudioMode ? "audio" : "video");
            options.put("format_id", formatId);
            options.put("height", targetHeight);
            options.put("ext", ext);

            String savePath = getDownloadPath();
            if (savePath == null) {
                showToast("Cannot access storage");
                return;
            }

            setDownloadingState(true);
            
            final int finalTargetHeight = targetHeight;
            executorService.execute(() -> {
                try {
                    PyObject progressCallback = PyObject.fromJava(new DownloadProgressCallback() {
                        @Override
                        public void onProgress(PyObject data) {
                            Map<PyObject, PyObject> map = data.asMap();
                            String percentage = map.get(py.getBuiltins().get("str").call("percentage")).toString();
                            String speed = map.get(py.getBuiltins().get("str").call("speed_str")).toString();
                            String eta = map.get(py.getBuiltins().get("str").call("eta_str")).toString();
                            String downloaded = map.get(py.getBuiltins().get("str").call("downloaded_str")).toString();
                            String total = map.get(py.getBuiltins().get("str").call("total_str")).toString();
                            
                            mainHandler.post(() -> {
                                try {
                                    float progress = Float.parseFloat(percentage);
                                    updateDownloadButtonState("Downloading...", (int) progress, false, "downloading");
                                    tvProgressStatus.setText(String.format("Downloading: %.1f%%", progress));
                                    tvProgressDetails.setText(String.format("%s / %s • %s • ETA: %s", downloaded, total, speed, eta));
                                } catch (Exception e) {}
                            });
                        }
                    });

                    PyObject result = pythonModule.callAttr("download", currentUrl, options.toString(), savePath, progressCallback);
                    Map<PyObject, PyObject> resultMap = result.asMap();
                    String status = resultMap.get(py.getBuiltins().get("str").call("status")).toString();

                    if ("success".equals(status)) {
                        List<PyObject> files = resultMap.get(py.getBuiltins().get("str").call("files")).asList();
                        processDownloadedFiles(files, ext, isAudioMode, finalTargetHeight);
                    } else {
                        String error = resultMap.get(py.getBuiltins().get("str").call("message")).toString();
                        handleError("Download failed: " + error);
                    }
                } catch (Exception e) {
                    handleError("Download error: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            showToast("Failed to prepare download: " + e.getMessage());
        }
    }

    private void processDownloadedFiles(List<PyObject> files, String targetExt, boolean isAudioOnly, int targetHeight) {
        mainHandler.post(() -> {
            tvProgressStatus.setText("Finalizing & Optimizing...");
            tvProgressDetails.setText("Please wait, this may take a moment.");
            updateDownloadButtonState("Processing...", 100, false, "processing");
        });

        executorService.execute(() -> {
            try {
                if (files.isEmpty()) {
                    handleError("No files downloaded");
                    return;
                }

                String finalPath;
                if (isAudioOnly) {
                    finalPath = FFmpegKitHelper.convertToAudio(this, files.get(0).toString(), targetExt, null);
                } else {
                    if (files.size() > 1) {
                        finalPath = FFmpegKitHelper.mergeVideoAudio(this, files.get(0).toString(), files.get(1).toString(), targetExt, targetHeight, null);
                    } else {
                        finalPath = FFmpegKitHelper.processVideo(this, files.get(0).toString(), targetExt, targetHeight, null);
                    }
                }

                if (finalPath != null) {
                    scanFile(finalPath);
                    mainHandler.post(() -> {
                        setDownloadingState(false);
                        showToast("Download completed: " + new File(finalPath).getName());
                        updateDownloadButtonState("Download Finished", 100, true, "finished");
                    });
                } else {
                    handleError("Post-processing failed. FFmpeg task could not complete.");
                }
            } catch (Exception e) {
                handleError("Processing error: " + e.getMessage());
            }
        });
    }

    private String getDownloadPath() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File sopoDir = new File(downloadsDir, "Sopo");
        if (!sopoDir.exists()) {
            if (!sopoDir.mkdirs()) return null;
        }
        return sopoDir.getAbsolutePath();
    }

    private void scanFile(String path) {
        MediaScannerConnection.scanFile(this, new String[]{path}, null, (path1, uri) -> {
            Log.d(TAG, "Scanned " + path1 + ":");
            Log.d(TAG, "-> uri=" + uri);
        });
    }

    private void setLoadingState(boolean loading) {
        pbFetch.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnFetch.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        btnPaste.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        etUrl.setEnabled(!loading);
    }

    private void setDownloadingState(boolean downloading) {
        llSelectionControls.setVisibility(downloading ? View.GONE : View.VISIBLE);
        llProgress.setVisibility(downloading ? View.VISIBLE : View.GONE);
    }

    private void updateDownloadButtonState(String label, int progress, boolean enabled, String state) {
        tvDownloadLabel.setText(label);
        btnDownload.setProgress(progress);
        btnDownload.setEnabled(enabled);
    }

    private void handleError(String message) {
        mainHandler.post(() -> {
            setLoadingState(false);
            setDownloadingState(false);
            showToast(message);
            Log.e(TAG, message);
        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0) return "Unknown";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }

    public interface DownloadProgressCallback {
        void onProgress(PyObject data);
    }
}
