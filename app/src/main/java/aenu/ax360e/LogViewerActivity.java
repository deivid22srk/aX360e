package aenu.ax360e;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public class LogViewerActivity extends AppCompatActivity {

    private static final int REQUEST_CREATE_DOCUMENT = 9901;
    private TextView logTextView;
    private File logFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.log_viewer);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        logTextView = findViewById(R.id.log_text_view);
        logFile = new File(Application.get_app_data_dir(), "xe.log");

        loadLogs();
    }

    private void loadLogs() {
        if (!logFile.exists() || logFile.length() == 0) {
            logTextView.setText(R.string.log_empty);
            return;
        }

        new Thread(() -> {
            String content = readLogTail(logFile);
            runOnUiThread(() -> logTextView.setText(content));
        }).start();
    }

    private String readLogTail(File file) {
        long fileLength = file.length();
        long maxReadBytes = 150 * 1024; // 150KB limit to avoid OOM/lag

        if (fileLength <= maxReadBytes) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = new byte[(int) fileLength];
                int bytesRead = fis.read(data);
                if (bytesRead > 0) {
                    return new String(data, 0, bytesRead, "UTF-8");
                }
                return "";
            } catch (IOException e) {
                return "Error reading log file: " + e.getMessage();
            }
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(fileLength - maxReadBytes);
            byte[] data = new byte[(int) maxReadBytes];
            raf.readFully(data);
            String content = new String(data, "UTF-8");
            int firstNewLine = content.indexOf('\n');
            if (firstNewLine != -1 && firstNewLine < content.length() - 1) {
                return "[LOG TRUNCATED - SHOWING LAST 150KB]\n\n" + content.substring(firstNewLine + 1);
            }
            return content;
        } catch (IOException e) {
            return "Error reading log file: " + e.getMessage();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.log_viewer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_copy) {
            copyToClipboard();
            return true;
        } else if (id == R.id.menu_export) {
            exportLogFile();
            return true;
        } else if (id == R.id.menu_delete) {
            deleteLogFile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void copyToClipboard() {
        CharSequence logText = logTextView.getText();
        if (logText == null || logText.length() == 0 || logText.equals(getString(R.string.log_empty))) {
            Toast.makeText(this, R.string.log_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("ax360e_xe_log", logText);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteLogFile() {
        if (logFile.exists()) {
            if (logFile.delete()) {
                logTextView.setText(R.string.log_empty);
                Toast.makeText(this, R.string.log_deleted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to delete log file!", Toast.LENGTH_SHORT).show();
            }
        } else {
            logTextView.setText(R.string.log_empty);
        }
    }

    private void exportLogFile() {
        if (!logFile.exists() || logFile.length() == 0) {
            Toast.makeText(this, R.string.log_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "ax360e_xe.log");
        startActivityForResult(intent, REQUEST_CREATE_DOCUMENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CREATE_DOCUMENT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                writeLogToUri(uri);
            }
        }
    }

    private void writeLogToUri(Uri uri) {
        if (!logFile.exists()) {
            Toast.makeText(this, R.string.log_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        try (InputStream in = new FileInputStream(logFile);
             OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out != null) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                Toast.makeText(this, String.format(getString(R.string.export_success), uri.getPath()), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
