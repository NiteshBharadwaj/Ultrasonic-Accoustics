/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wifidirect;

import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import com.izforge.izpack.util.*;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.app.*;

import com.example.android.wifidirect.DeviceListFragment.DeviceActionListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DecimalFormat;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {
	private static final int RECORDER_BPP = 16;
	 /** Native methods, implemented in jni folder */
    public static native void createEngine();
    public static native void createBufferQueueAudioPlayer();
    public static native boolean selectClip(int which, int count);
    public static native void shutdown();
    public static native double getTime();
    public static native double getTime11();
    public static native double getTime12();
    public static native double getTime13();
    public static native double getTime14();
    public static native double getTime15();
    public static native double recStartTime();
    public static native double recStopTime();
    public static native double playStartTime();
    public static native double playStopTime();
    public long packetRecTime = -1000;

    /** Load jni .so on initialization */
    static {
         System.loadLibrary("native-audio-jni");
    }

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    ProgressDialog progressDialog = null;
	private DatagramSocket socket1 = null;
	private DatagramSocket socket2 = null;
	private static final int DISCOVERY_PORT = 2561;
	
	static final int CLIP_SIGNAL = 3;
	static final int CLIP_DUMMY = 1;
    static double getT = -1000;
    static long startT = -1000;
    static long endT = -1000;
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
    }
    
    static double sendSoundSignal() {
    	// Native function to send sound signal
    	// 3 refers to 3 signals played one after other 
    	// Each of this is 100ms long and so, the distance between peaks is observed to be 100ms
    	// This is useful in cases when one signal goes undetected, especially for long distances (5-8m)
    	selectClip(CLIP_SIGNAL, 3);  
    	
    	// Let the speaker completely play the above signal
    	// Minimum recommended time is 500ms (300ms for playing + 200ms for delay)
    	try {
			Thread.sleep(700);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	
    	// Get the time from the native layer, when it has actually transferred buffer to driver
    	return getTime();
    }
    
    static void sendCompleteSignal(DatagramSocket socket1,DatagramSocket socket2,DatagramPacket packet, DatagramPacket recPacket, InetAddress address) {
		try {
	    	
			long t1 = System.currentTimeMillis();
			socket2.send(packet);  
			socket1.receive(recPacket);
			long t2 = System.currentTimeMillis();
			t1 = t2-t1;                      // First round trip time of wifi packet
			socket2.send(packet);
			socket1.receive(recPacket);
			long t3 = System.currentTimeMillis();
			t2 = t3-t2;                      // Second round trip time of wifi packet
			socket2.send(packet);
			socket1.receive(recPacket);
			long t4 = System.currentTimeMillis();
			t3 = t4-t3;                      // Third round trip time of wifi packet

			
			t4 = ((t3+t2)/2)/2+t4;           // Half of average of previous round trip times
											 // Do not consider the first packet round trip time
			
			
			
			socket2.send(packet);            // Wifi Packet followed by sound signal
			Double soundTime = sendSoundSignal();
			
			// t4 is the expected packet sending time
			// soundTime+50 is the expected sound signal sending time. (+50 for the midpoint)
			// soundTime+50-t4 should be the difference between the reception times of wifi and sound at the receiver
			// So we send this to the receiver to note the deviation from the expectation
			byte[] buf = Double.toString(soundTime-t4+50).substring(0, 5).getBytes("UTF-8");
			DatagramPacket repacket = new DatagramPacket(buf, buf.length,
					address, DISCOVERY_PORT+1);
			socket2.send(repacket);                  
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}  
		
    }
    /**
     * Required by the receiver to handle the packet communication with sender
     */
    static long handlePacket(DatagramSocket socket1, DatagramSocket socket2, DatagramPacket packet, DatagramPacket recPacket, DatagramPacket recPacket1 ) throws IOException {
    	
    	socket2.receive(recPacket);
    	socket1.send(packet);  							// Complete round trip
    	
    	socket2.receive(recPacket);                    
    	socket1.send(packet);
    	
    	socket2.receive(recPacket);
    	socket1.send(packet);
    	
    	socket2.receive(recPacket);
    	long packetRecTi = System.currentTimeMillis();  // PacketReceiveTime
    	Log.d("packrec",Long.toString(packetRecTi));
    	
    	socket2.receive(recPacket1);                    // The expected gap between peak of sound and wifi reception
    	String stri = new String(recPacket1.getData(),"UTF-8");
    	Double doubl = Double.parseDouble(stri);
    	Log.d("checkathis",stri);
    	packetRecTi = (long) (packetRecTi+doubl);      //  Expected Peak of Signal if there is no audio latency
    	return packetRecTi;
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.device_detail, null);
        createEngine();
        createBufferQueueAudioPlayer();
        createAudioRecorder();

        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
//                        new DialogInterface.OnCancelListener() {
//
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
//                            }
//                        }
                        );
                ((DeviceActionListener) getActivity()).connect(config);

            }
        });

        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((DeviceActionListener) getActivity()).disconnect();
                    }
                });

        mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                    }
                });
        
        /**
         * A number of signals (wifi + audio) are sent on button click 
         * The expected difference between reception times of wifi and audio are calculated and sent to receiver along with each signal
         */
        mContentView.findViewById(R.id.sender).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                    	if (info.isGroupOwner) return;
                    	TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
                    	try {
                    	byte[] data = new byte[1];
                    	data[0] = 6;
                    	InetAddress address = info.groupOwnerAddress;
                    	String p = address.toString();
                    	Log.d("address",p);
                    	
                    	statusText.setText("- " + p);
                    	if (socket1==null){
                			socket1 = new DatagramSocket(null);
                			socket1.setReuseAddress(true);
                			socket1 = new DatagramSocket(DISCOVERY_PORT);
                			socket1.setBroadcast(true);	
                    	}
                    	if (socket2==null) {
                    		socket2 = new DatagramSocket(null);
                			socket2.setReuseAddress(true);
                			socket2 = new DatagramSocket(DISCOVERY_PORT+1);
                			socket2.setBroadcast(true);	
                    	}
                    	
                			DatagramPacket packet = new DatagramPacket(data, 1,
                					address, DISCOVERY_PORT+1);
                			byte[] recBuf = new byte[1024];
                			DatagramPacket recPacket = new DatagramPacket(recBuf,recBuf.length);
                			
                			// Play a dummy clip to warm up the speakers
                			selectClip(CLIP_DUMMY, 1);
                			Thread.sleep(1400);
                			
                			// Send a packet to the groupowner(receiver) to give it the ip address 
                			socket2.send(packet); 
                			
                			// Sending 9 wifi+audio signals 
                			for (int i=0;i!=9;i++) {
                				// Note that it takes 700ms presently for sending one complete signal (wifi+audio)
                				// Can be reduced by changing parameters inside the sendCompleteSignal function
                				sendCompleteSignal(socket1,socket2,packet,recPacket,address);
                			}
                			
                			socket1.disconnect();
                			socket1.close();
                			socket1 = null;
                			socket2.disconnect();
                			socket2.close();
                			socket2 = null;

                			
                		}
                    	
                    	catch (IOException e) {
                			socket1.disconnect();
                			socket1.close();
                			socket1 = null;
                			socket2.disconnect();
                			socket2.close();
                			socket2 = null;
                    	} catch (InterruptedException e) {
							e.printStackTrace();
						}
                    }
                });
        /**
         * On button click, it waits for 10s before trying to receive WiFi and audio signals
         * 
         */
        mContentView.findViewById(R.id.receiver).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                    	long packetRecTime1 = 0;
                    	long packetRecTime2 = 0;
                    	long packetRecTime3 = 0;
                    	long packetRecTime4 = 0;
                    	long packetRecTime5 = 0;
                    	long packetRecTime6 = 0;
                    	long packetRecTime7 = 0;
                    	long packetRecTime8 = 0;
                    	
                    	/**
                    	 * 	Native Function to start recording
                         *  Presently discards 9 buffers of 1s before actually starting recording
                         *  Then it records for 10 seconds
                         *  Can easily be modified by changing the parameters in native-audio-jni.c
                         *  Change countRecord variable to change the number of buffers to be discarded (present value 10)
                         *  Change DUMMY_FRAMES to change length of buffer to be discarded (present value 44100  i.e. 1s)
                         *  Change RECORDER_FRAMES to change length of record buffer (present value 441000 i.e. 10s)
                         *  Refer to bqrecordercallback in native-audio-jni.c
                         */
                    	startRecording();
                
                    	try {
                        	byte[] data = new byte[1];
                        	data[0] = 8;
                        	byte[] recBuf = new byte[1];
                        	byte[] recBuf1 = new byte[5];
                        	DatagramPacket recPacket = new DatagramPacket(recBuf,recBuf.length);
                        	DatagramPacket recPacket1 = new DatagramPacket(recBuf1,recBuf1.length);
                        	if (socket1==null){
                    			socket1 = new DatagramSocket(null);
                    			socket1.setReuseAddress(true);
                    			socket1 = new DatagramSocket(DISCOVERY_PORT);
                    			socket1.setBroadcast(true);	
                        	}
                        	if (socket2==null){
                    			socket2 = new DatagramSocket(null);
                    			socket2.setReuseAddress(true);
                    			socket2 = new DatagramSocket(DISCOVERY_PORT+1);
                    			socket2.setBroadcast(true);	
                    		}  
                        	
                        	
                        	socket2.setSoTimeout(0);
                        	
                        	// Receive packet and get the ip address of the sender
                        	socket2.receive(recPacket);
                        	InetAddress address  = recPacket.getAddress();
                        	DatagramPacket packet = new DatagramPacket(data, 1,
                					address, DISCOVERY_PORT);
                        	Log.d("ip",address.toString() );
                        	
                        	
                        	packetRecTime = handlePacket(socket1,socket2,packet,recPacket,recPacket1);
                        	packetRecTime1 = handlePacket(socket1,socket2,packet,recPacket,recPacket1);
                        	packetRecTime2 = handlePacket(socket1,socket2,packet,recPacket,recPacket1);
                        	packetRecTime3 = handlePacket(socket1,socket2,packet,recPacket,recPacket1);
                        	packetRecTime4 = handlePacket(socket1,socket2,packet,recPacket,recPacket1);
                        	packetRecTime5 = handlePacket(socket1,socket2,packet,recPacket,recPacket1);
                        	packetRecTime6 = handlePacket(socket1,socket2,packet,recPacket,recPacket1);
                        	packetRecTime7 = handlePacket(socket1,socket2,packet,recPacket,recPacket1);
                        	packetRecTime8 = handlePacket(socket1,socket2,packet,recPacket,recPacket1);
                        	
                        	
                        	socket1.disconnect();
                			socket1.close();
                			socket1 = null;
                			socket2.disconnect();
                			socket2.close();
                			socket2 = null;
                        }
                        catch (Exception e) {
                        	socket1.disconnect();
                			socket1.close();
                			socket1 = null;
                			socket2.disconnect();
                			socket2.close();
                			socket2 = null;
                        }
                        
                        
                        // To let the recorder complete recording
                    	// For worst case, when above cycle completes even before recorder starts recording
                        // TODO: Shift writing to wave file to native layer along with recording or use a new Thread to get rid of this thread.sleep 
                    	try {
							Thread.sleep(21000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
                        
                    	short[] recorderBuffer = getBuffer();
                    	double recordstartTime = recStartTime();
                    	double recordstopTime = recStopTime();

                    	DecimalFormat twoDForm = new DecimalFormat("#.##");
                    	packetRecTime = packetRecTime-(long)recordstartTime;
                    	packetRecTime = Long.valueOf(twoDForm.format(packetRecTime));
                    	if(!(packetRecTime1==0))
                    		packetRecTime1 = packetRecTime1-(long)recordstartTime;
                    		packetRecTime1 = Long.valueOf(twoDForm.format(packetRecTime1));
                    	if(!(packetRecTime2==0))
                        	packetRecTime2 = packetRecTime2-(long)recordstartTime;
                    		packetRecTime2 = Long.valueOf(twoDForm.format(packetRecTime2));
                    	if(!(packetRecTime3==0))
                        	packetRecTime3 = packetRecTime3-(long)recordstartTime;
                    		packetRecTime3 = Long.valueOf(twoDForm.format(packetRecTime3));
                    	if(!(packetRecTime4==0))
                        	packetRecTime4 = packetRecTime4-(long)recordstartTime;
                    		packetRecTime4 = Long.valueOf(twoDForm.format(packetRecTime4));
                    	if(!(packetRecTime5==0))
                        	packetRecTime5 = packetRecTime5-(long)recordstartTime;
                    		packetRecTime5 = Long.valueOf(twoDForm.format(packetRecTime5));
                    	if(!(packetRecTime6==0))
                        	packetRecTime6 = packetRecTime6-(long)recordstartTime;
                    		packetRecTime6 = Long.valueOf(twoDForm.format(packetRecTime6));
                    	if(!(packetRecTime7==0))
                        	packetRecTime7 = packetRecTime7-(long)recordstartTime;
                    		packetRecTime7 = Long.valueOf(twoDForm.format(packetRecTime7));
                    	if(!(packetRecTime8==0))
                        	packetRecTime8 = packetRecTime8-(long)recordstartTime;
                    		packetRecTime8 = Long.valueOf(twoDForm.format(packetRecTime8));
                    	
                    	String filename = Environment.getExternalStorageDirectory() + File.separator + packetRecTime+"-"+packetRecTime1+"-"+packetRecTime2+"-"+packetRecTime3+" "+packetRecTime4+"-"+packetRecTime5+"-"+packetRecTime6+"-"+packetRecTime7+"-"+packetRecTime8+"--test.wav";
                    	File file = new File(filename);
                    	try {
							file.createNewFile();
						} catch (IOException e) {
							e.printStackTrace();
						}
                    	
                    	WavWriter writer = new WavWriter();
                    	try {
							writer.export(filename, 44100, recorderBuffer);
						} catch (IOException e) {
							Log.d("tuk","Unable to write");
							e.printStackTrace();
						}
                    }

                });
        
        /**
         * Calibration of delay of the speaker-receiver pair of a single mobile
         * 
         */
        mContentView.findViewById(R.id.calibrate).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        
                    	/**
                    	 * 	Native Function to start recording
                         *  Presently discards 9 buffers of 1s before actually starting recording
                         *  Then it records for 10 seconds
                         *  Can easily be modified by changing the parameters in native-audio-jni.c
                         *  Change countRecord variable to change the number of buffers to be discarded (present value 10)
                         *  Change DUMMY_FRAMES to change length of buffer to be discarded (present value 44100  i.e. 1s)
                         *  Change RECORDER_FRAMES to change length of record buffer (present value 441000 i.e. 10s)
                         *  Refer to bqrecordercallback in native-audio-jni.c
                         */
                    	startRecording();
                    	
                        long t1 = 0;
                        long t2 = 0;
                        long t3 = 0;

                        try {
                        	
                        	// Setup Time. To position the phone etc.
                        	// During this time, recorder also records buffers of 1 second and discards 9 such buffers
                        	// CAUTION: Before changing this, change countRecord variable in native-audio-jni.c.
                        	// Presently countRecord is set to 10. (9 dummy buffers + 1 original buffer). 
                        	// Check bqrecorder callback in native-audio-jni                       				
							Thread.sleep(11000);
							
							// Playing a dummy buffer.. Completely independent of recording
							// CLIP_DUMMY present length is 1s (44100)
							selectClip(CLIP_DUMMY, 1);
                			Thread.sleep(1400);   
                			
                			// Time Stamp where the sound peak is to be observed.
                			// Sound peak is observed at the mid-point of a 100ms buffer
                			t1 = System.currentTimeMillis()+50; 
							sendSoundSignal();
							
	                        Thread.sleep(2100); //To check for periodicity
	                        
	                        t2 = System.currentTimeMillis()+50;
							sendSoundSignal();
	                        
							Thread.sleep(2100);
	                        
	                        t3 = System.currentTimeMillis()+50;
							sendSoundSignal();
	                        
							Thread.sleep(2100);
	                        
	                        // Wait for the recorder to complete the recording of the 10 second buffer
	                        Thread.sleep(3000); 
	                      
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
                        
                        // Native calls to get the parameters after recording
                        short[] recorderBuffer = getBuffer();
                    	double recordstartTime = recStartTime();
                    	double recordstopTime = recStopTime();
                    	
                    	t1 = (long) (t1-recordstartTime);
                    	t2 = (long) (t2-recordstartTime);
                    	t3 = (long) (t3-recordstartTime);
                    	
                    	String filename = Environment.getExternalStorageDirectory() + File.separator + t1+"-"+ t2+"-"+ t3+".wav";
                    	File file = new File(filename,"NewRecorder");
                    	try {
							file.createNewFile();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
                    	String qt1 = Double.toString(recorderBuffer.length);
                    	String qt = Double.toString(recordstopTime-recordstartTime);
                    	
                    	// Write buffer to a wav file for post processing
                    	WavWriter writer = new WavWriter();
                    	try {
							writer.export(filename, 44100, recorderBuffer);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							Log.d("tuk","Unable to write");
							e.printStackTrace();
						}
                    	Log.d("Time",qt);
                    	Log.d("Size",qt1);
                    }
                });

        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        // User has picked an image. Transfer it to group owner i.e peer using
        // FileTransferService.
        Uri uri = data.getData();
        TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
        statusText.setText("Sending: " + uri);
        Log.d(WiFiDirectActivity.TAG, "Intent----------- " + uri);
        Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                info.groupOwnerAddress.getHostAddress());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
        getActivity().startService(serviceIntent);
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);
        Log.d("available","available");
        // The owner IP is now known.
        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(getResources().getString(R.string.group_owner_text)
                + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
                        : getResources().getString(R.string.no)));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {
            new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text))
                    .execute();
        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
            ((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
                    .getString(R.string.client_text));
        }

        // hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
    }

    /**
     * Updates the UI with device data
     * 
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(device.deviceAddress);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(device.toString());

    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;

        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                ServerSocket serverSocket = new ServerSocket(8988);
                Log.d(WiFiDirectActivity.TAG, "Server: Socket opened");
                Socket client = serverSocket.accept();
                Log.d(WiFiDirectActivity.TAG, "Server: connection done");
                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();

                Log.d(WiFiDirectActivity.TAG, "server: copying files " + f.toString());
                InputStream inputstream = client.getInputStream();
                copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
                return f.getAbsolutePath();
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                statusText.setText("File copied - " + result);
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + result), "image/*");
                context.startActivity(intent);
            }

        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a server socket");
        }

    }
    /** Called when the activity is about to be destroyed. */
    @Override
	public void onDestroy()
    {
        shutdown();
        super.onDestroy();
    }
    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);

            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(WiFiDirectActivity.TAG, e.toString());
            return false;
        }
        return true;
    }
   
    public static native boolean createAudioRecorder();
    public static native void startRecording();
    public static native short[] getBuffer();
}
