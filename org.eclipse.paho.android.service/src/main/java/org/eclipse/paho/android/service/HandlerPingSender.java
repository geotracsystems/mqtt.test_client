package org.eclipse.paho.android.service;

import android.app.Service;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttPingSender;
import org.eclipse.paho.client.mqttv3.internal.ClientComms;

import java.util.Date;

/**
 * Ping sender implementation using Android Handlers.
 *
 * <p>This class implements the {@link MqttPingSender} pinger interface
 * allowing applications to send ping packet to server every keep alive interval.
 * </p>
 *
 * @see MqttPingSender
 */
class HandlerPingSender implements MqttPingSender, Runnable {
    // Identifier for Intents, log messages, etc..
    private static final String TAG = "AlarmPingSender";
    private final String wakeLockTag = MqttServiceConstants.PING_WAKELOCK + ":PingSender";
    // TODO: Add log.
    private ClientComms comms;
    private MqttService service;
    private Handler handler;
    private WakeLock wakelock;
    private volatile boolean hasStarted = false;
    
    public HandlerPingSender(MqttService service) {
        if (service == null) {
            throw new IllegalArgumentException(
                "Neither service nor client can be null.");
        }
        this.service = service;
        this.handler = new Handler();
    }
    
    @Override
    public void init(ClientComms comms) {
        this.comms = comms;
    }
    
    @Override
    public void start() {
        String action = MqttServiceConstants.PING_SENDER
            + comms.getClient().getClientId();
        Log.d(TAG, "Register handler to MqttService" + action);
        
        schedule(comms.getKeepAlive());
        hasStarted = true;
    }
    
    @Override
    public void stop() {
        Log.d(TAG, "Unregister handler to MqttService" + comms.getClient().getClientId());
        if (hasStarted) {
            handler.removeCallbacks(this);
            
            hasStarted = false;
        }
    }
    
    @Override
    public void schedule(long delayInMilliseconds) {
        Date nextSchedule = new Date(System.currentTimeMillis() + delayInMilliseconds);
        Log.d(TAG, "Schedule next ping at " + nextSchedule.toString());
        
        handler.postDelayed(this, delayInMilliseconds);
    }
    
    @Override
    public void run() {
        Log.d(TAG, "Sending ping at: " + System.currentTimeMillis());
        
        PowerManager pm = (PowerManager) service.getSystemService(Service.POWER_SERVICE);
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakeLockTag);
        wakelock.acquire();
        
        // Assign new callback to token to execute code after PingResq
        // arrives. Get another wakelock even receiver already has one,
        // release it until ping response returns.
        IMqttToken token = comms.checkForActivity(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Log.d(TAG, "Success. Release lock(" + wakeLockTag + "):" + System.currentTimeMillis());
                //Release wakelock when it is done.
                wakelock.release();
            }
            
            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.d(TAG, "Failure. Release lock(" + wakeLockTag + "):" + System.currentTimeMillis());
                //Release wakelock when it is done.
                wakelock.release();
            }
        });
        
        if (token == null) {
            Log.d(TAG, "The java client doesn't think it's necessary to do a ping yet");
            if (wakelock.isHeld()) wakelock.release();
        }
    }
}
