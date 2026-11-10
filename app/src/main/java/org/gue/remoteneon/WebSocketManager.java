package org.gue.remoteneon;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.nio.ByteBuffer;

public class WebSocketManager {
    private static final String TAG = "WebSocketManager";

    private WebSocketClient client;
    private String serverIP;
    private boolean connected = false;

    public WebSocketManager(String serverIP) {
        this.serverIP = serverIP;
    }

    public void connect() {
        try {
            String uri = "ws://" + serverIP + ":8081/phone";
            client = new WebSocketClient(new URI(uri)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.i(TAG, "WebSocket connected");
                    connected = true;
                    sendHello();
                }

                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "Received: " + message);
                    handleCommand(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.i(TAG, "WebSocket closed: " + reason);
                    connected = false;
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error", ex);
                    connected = false;
                }
            };
            client.connect();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create WebSocket", e);
        }
    }

    private void sendHello() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "hello");
            obj.put("width", android.content.res.Resources.getSystem().getDisplayMetrics().widthPixels);
            obj.put("height", android.content.res.Resources.getSystem().getDisplayMetrics().heightPixels);
            send(obj.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to send hello", e);
        }
    }

    public void sendFrame(byte[] jpegBytes) {
        if (!connected || client == null) return;

        byte[] payload = new byte[jpegBytes.length + 3];
        payload[0] = 'J';
        payload[1] = 'P';
        payload[2] = 'G';
        System.arraycopy(jpegBytes, 0, payload, 3, jpegBytes.length);

        client.send(ByteBuffer.wrap(payload));
    }

    public void sendAudio(byte[] pcmBytes) {
        if (!connected || client == null) return;

        byte[] payload = new byte[pcmBytes.length + 3];
        payload[0] = 'P';
        payload[1] = 'C';
        payload[2] = 'M';
        System.arraycopy(pcmBytes, 0, payload, 3, pcmBytes.length);

        client.send(ByteBuffer.wrap(payload));
    }

    public void send(String message) {
        if (connected && client != null) {
            client.send(message);
        }
    }

    private void handleCommand(String message) {
        try {
            JSONObject obj = new JSONObject(message);
            String type = obj.optString("type", "");

            NeonAccessibilityService acc = NeonAccessibilityService.getInstance();
            if (acc == null) {
                Log.w(TAG, "Accessibility service not connected");
                return;
            }

            switch (type) {
                case "tap":
                    float x = (float) obj.optDouble("x", 0.5);
                    float y = (float) obj.optDouble("y", 0.5);
                    acc.performTap(x, y);
                    break;

                case "swipe":
                    float x1 = (float) obj.optDouble("x1", 0.5);
                    float y1 = (float) obj.optDouble("y1", 0.5);
                    float x2 = (float) obj.optDouble("x2", 0.5);
                    float y2 = (float) obj.optDouble("y2", 0.5);
                    long duration = obj.optLong("duration", 300);
                    acc.performSwipe(x1, y1, x2, y2, duration);
                    break;

                case "back":
                    acc.performBack();
                    break;

                case "home":
                    acc.performHome();
                    break;

                case "recents":
                    acc.performRecents();
                    break;

                default:
                    Log.w(TAG, "Unknown command: " + type);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse command", e);
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        if (client != null) {
            client.close();
        }
        connected = false;
    }
}
