package app.dmmstudio.cinema;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
@CapacitorPlugin(name = "DMMTorrentUpload")
public class DMMTorrentUploadPlugin extends Plugin {
    @PluginMethod public void upload(PluginCall call) {
        final String token = call.getString("key"), encoded = call.getString("data"), suppliedName = call.getString("filename", "upload.torrent");
        if (token == null || token.trim().isEmpty() || token.length() > 4096 || token.contains("\r") || token.contains("\n") || encoded == null || encoded.length() > 7 * 1024 * 1024) { call.reject("Invalid upload input."); return; }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024) throw new IllegalArgumentException();
                String filename = suppliedName.replaceAll("[^a-zA-Z0-9._-]", "_");
                String boundary = "dmm-" + java.util.UUID.randomUUID();
                byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\nContent-Type: application/x-bittorrent\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
                conn = (HttpURLConnection) new URL("https://api.torbox.app/v1/api/torrents/createtorrent").openConnection();
                conn.setInstanceFollowRedirects(false); conn.setConnectTimeout(15000); conn.setReadTimeout(30000); conn.setRequestMethod("POST"); conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Bearer " + token); conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary); conn.setFixedLengthStreamingMode(head.length + bytes.length + tail.length);
                try (OutputStream out = conn.getOutputStream()) { out.write(head); out.write(bytes); out.write(tail); }
                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) { call.reject("TorBox upload failed (HTTP " + status + ")."); return; }
                try (java.io.InputStream in = conn.getInputStream()) {
                    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                    byte[] chunk = new byte[8192]; int n;
                    while ((n = in.read(chunk)) != -1) { if (buffer.size() + n > 1024 * 1024) throw new IllegalStateException(); buffer.write(chunk, 0, n); }
                    byte[] response = buffer.toByteArray();
                    call.resolve(new JSObject(new String(response, StandardCharsets.UTF_8)));
                }
            } catch (Exception e) { call.reject("Torrent upload failed. Check connection and file validity."); }
            finally { if (conn != null) conn.disconnect(); }
        }, "dmm-torrent-upload").start();
    }
}
