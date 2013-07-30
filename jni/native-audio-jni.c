/*
 * Copyright (C) 2010 The Android Open Source Project
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
 *
 */

/* This is a JNI example where we use native methods to play sounds
 * using OpenSL ES. See the corresponding Java source file located at:
 *
 *   src/com/example/nativeaudio/NativeAudio/NativeAudio.java
 */
 
 
 
 
 /**
 * To control the ultrasonic app, use the following variables
 * SAWTOOTH_FRAMES: Dummy audio buffer size (presently 44100 or 1s at 44.1KHz sampling rate)
 * SAWTOOTH_FRAMES1: Audio Signal buffer size. Mid one-fifths of this buffer contains actual signal. (presently 4410 or 100ms with mid 20ms as the required signal)
 * Change values in onDlOpen to change the frequency of the signal
 * DUMMY_FRAMES: Dummy recorder buffer size (currently 1s or 44100)
 * RECORDER_FRAMES: Actual recorder buffer size (currently 10s or 441000)
 * countRecord: number of dummy recorder buffers that are to be recorded and discarded
 * now_ms() function to get systemtime
 * From the java framework, the following functions are supported
 * startRecording(), selectClip(clip,number of times), getTime(), getBuffer(), recStartTime(), recStopTime()
 */
 
 /**
 * startRecording()
 * Records countRecord number of dummy buffers and discards them and finally records one actual recorder buffer
 * See bqRecorderCallback(SLAndroidSimpleBufferQueueItf bq, void *context), Java_com_example_android_wifidirect_DeviceDetailFragment_startRecording(JNIEnv* env, jclass clazz) 
 */
 
 /**
 * getBuffer(), recStartTime(), recStopTime()
 * These methods are to be called when we are sure that recording is finished. Otherwise they return the values corresponding to previous record or zero
 */
 
 /**
 * selectClip(clip, n)
 * Plays the clip "clip" for n number of times
 */
 
 /**
 * getTime()
 * This method is to be called once we are sure that playing is finished. Otherwise it retuerns previous values or zero
 */
 
 /**
 * There is no filtering of data occuring here. To filter in this layer itself, rename the other native-audio-jni-filter to native-audio-jni
 */
 

#include <assert.h>
#include <jni.h>
#include <string.h>
#include <sys/time.h>
#include <time.h>
#include <Math.h>

//for __android_log_print(ANDROID_LOG_INFO, "YourApp", "formatted message");
// #include <android/log.h>

// for native audio
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#define PI 3.14159265
// engine interfaces
static SLObjectItf engineObject = NULL;
static SLEngineItf engineEngine;
static int countRecord = 1;
// output mix interfaces
static SLObjectItf outputMixObject = NULL;
static SLEnvironmentalReverbItf outputMixEnvironmentalReverb = NULL;

// buffer queue player interfaces
static SLObjectItf bqPlayerObject = NULL;
static SLPlayItf bqPlayerPlay;
static SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue;
static SLEffectSendItf bqPlayerEffectSend;
static SLMuteSoloItf bqPlayerMuteSolo;
static SLVolumeItf bqPlayerVolume;

static double presTime = 0.0;
static double presTime1 = 0.0;

static double presTime11 = 0.0;
static double presTime12 = 0.0;
static double presTime13 = 0.0;
static double presTime14 = 0.0;
static double presTime15 = 0.0;

static double recStart = 0.0;
static double recStop = 0.0;
static double playStart = 0.0;
static double playStop = 0.0;

// recorder interfaces
static SLObjectItf recorderObject = NULL;
static SLRecordItf recorderRecord;
static SLAndroidSimpleBufferQueueItf recorderBufferQueue;

// 5 seconds of recorded audio at 16 kHz mono, 16-bit signed little endian
#define RECORDER_FRAMES (441000)
#define DUMMY_FRAMES (44100)
static short dummyBuffer[DUMMY_FRAMES];
static short recorderBuffer[RECORDER_FRAMES];
static unsigned recorderSize = 0;
static SLmilliHertz recorderSR;


// aux effect on the output mix, used by the buffer queue player
static const SLEnvironmentalReverbSettings reverbSettings =
    SL_I3DL2_ENVIRONMENT_PRESET_STONECORRIDOR;

static double now_ms(void)
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return tv.tv_sec*1000. + tv.tv_usec/1000.;
}

// synthesized sawtooth clip
#define SAWTOOTH_FRAMES 44100
static short sawtoothBuffer[SAWTOOTH_FRAMES];
#define SAWTOOTH_FRAMES1 4410
static short sawtoothBuffer1[SAWTOOTH_FRAMES1];


// pointer and size of the next player buffer to enqueue, and number of remaining buffers
static short *nextBuffer;
static unsigned nextSize;
static short *nextBuffer1;
static unsigned nextSize1;
static int nextCount;
short int dumb = 0;



// synthesize a mono sawtooth wave and place it into a buffer (called automatically on load)
__attribute__((constructor)) static void onDlOpen(void)
{
    unsigned i;
    for (i = 0; i < SAWTOOTH_FRAMES; ++i) {
        sawtoothBuffer[i] = sin(2 * PI * i*19100/44100 )*32767;
    }
    
    for (i = 0; i < 2*SAWTOOTH_FRAMES1/5; ++i) {
        sawtoothBuffer1[i] = sin(2 * PI * i*19100/44100 )*32767;
    }
    for (i = 2*SAWTOOTH_FRAMES1/5; i < 3*SAWTOOTH_FRAMES1/5; ++i) {
        sawtoothBuffer1[i] = sin(2 * PI * i*20100/44100 )*32767;
    }
    for (i = 3*SAWTOOTH_FRAMES1/5; i < SAWTOOTH_FRAMES1; ++i) {
        sawtoothBuffer1[i] = sin(2 * PI * i*19100/44100 )*32767;
    }
	
}



// this callback handler is called every time a buffer finishes playing
void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context)
{
    assert(bq == bqPlayerBufferQueue);
    assert(NULL == context);

	//	presTime = now_ms()-playStart;

    // for streaming playback, replace this test by logic to find and fill the next buffer
    if (--nextCount > 0 && NULL != nextBuffer && 0 != nextSize) {
    	if (nextCount>5) {
    		//presTime = now_ms();
        	SLresult result;
        	// enqueue another buffer
        	result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextBuffer, nextSize);
        	// the most likely other result is SL_RESULT_BUFFER_INSUFFICIENT,
        	// which for this code example would indicate a programming error
        	assert(SL_RESULT_SUCCESS == result);
        	(void)result;
        }
		else if (nextCount==5) {
			presTime11 = now_ms();
        	SLresult result;
        	// enqueue another buffer
        	presTime11 = now_ms();
        	result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextBuffer1, nextSize1);
        	// the most likely other result is SL_RESULT_BUFFER_INSUFFICIENT,
        	// which for this code example would indicate a programming error
        	assert(SL_RESULT_SUCCESS == result);
        	(void)result;
			nextCount = 2;
		}
        else if (nextCount==4) {
        	//presTime1 = now_ms();
        	SLresult result;
        	// enqueue another buffer
        	presTime12 = now_ms();
        	result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextBuffer1, nextSize1);
        	// the most likely other result is SL_RESULT_BUFFER_INSUFFICIENT,
        	// which for this code example would indicate a programming error
        	assert(SL_RESULT_SUCCESS == result);
        	(void)result;
        }
		else if (nextCount==3) {
			//presTime1 = now_ms();
        	SLresult result;
        	// enqueue another buffer
        	presTime13 = now_ms();
        	result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextBuffer1, nextSize1);
        	// the most likely other result is SL_RESULT_BUFFER_INSUFFICIENT,
        	// which for this code example would indicate a programming error
        	assert(SL_RESULT_SUCCESS == result);
        	(void)result;
		}
		else if (nextCount==2) {
			//presTime1 = now_ms();
        	SLresult result;
        	// enqueue another buffer
        	presTime14 = now_ms();
        	result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextBuffer1, nextSize1);
        	// the most likely other result is SL_RESULT_BUFFER_INSUFFICIENT,
        	// which for this code example would indicate a programming error
        	assert(SL_RESULT_SUCCESS == result);
        	(void)result;
		}
		else if (nextCount==1) {
			double pree = now_ms();
			//presTime1 = now_ms();
        	SLresult result;
        	// enqueue another buffer
			//while (now_ms()-pree <134) {
			//}
			presTime15 = now_ms();
        	result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextBuffer1, nextSize1);
        	// the most likely other result is SL_RESULT_BUFFER_INSUFFICIENT,
        	// which for this code example would indicate a programming error
        	assert(SL_RESULT_SUCCESS == result);
        	(void)result;
		}
		else {
			double pree = now_ms();
			//presTime1 = now_ms();
        	SLresult result;
        	// enqueue another buffer
			//while (now_ms()-pree <134) {
			//}
        	result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextBuffer, nextSize);
        	// the most likely other result is SL_RESULT_BUFFER_INSUFFICIENT,
        	// which for this code example would indicate a programming error
        	assert(SL_RESULT_SUCCESS == result);
        	(void)result;
		}
    }
}

// this callback handler is called every time a buffer finishes recording
void bqRecorderCallback(SLAndroidSimpleBufferQueueItf bq, void *context)
{
	recStop = now_ms();
    assert(bq == bqRecorderBufferQueue);
    assert(NULL == context);
    // for streaming recording, here we would call Enqueue to give recorder the next buffer to fill
    // but instead, this is a one-time buffer so we stop recording
	if (countRecord>1) {
		SLresult result = (*recorderBufferQueue)->Enqueue(recorderBufferQueue, dummyBuffer,
           DUMMY_FRAMES * sizeof(short));
		recStart = now_ms();
		countRecord--;
	}
	else if (countRecord==1) {
		SLresult result = (*recorderBufferQueue)->Enqueue(recorderBufferQueue, recorderBuffer,
            RECORDER_FRAMES * sizeof(short));
		recStart = now_ms();
		countRecord--;
	}
	else {
		SLresult result;
		result = (*recorderRecord)->SetRecordState(recorderRecord, SL_RECORDSTATE_STOPPED);
		if (SL_RESULT_SUCCESS == result) {
			recorderSize = RECORDER_FRAMES * sizeof(short);
			recorderSR = SL_SAMPLINGRATE_44_1;
		}
    }
}

double Java_com_example_android_wifidirect_DeviceDetailFragment_getTime(JNIEnv* env, jclass clazz) 
{
	return presTime;
}
double Java_com_example_android_wifidirect_DeviceDetailFragment_getTime11(JNIEnv* env, jclass clazz) 
{
	return presTime11;
}
double Java_com_example_android_wifidirect_DeviceDetailFragment_getTime12(JNIEnv* env, jclass clazz) 
{
	return presTime12;
}
double Java_com_example_android_wifidirect_DeviceDetailFragment_getTime13(JNIEnv* env, jclass clazz) 
{
	return presTime13;
}
double Java_com_example_android_wifidirect_DeviceDetailFragment_getTime14(JNIEnv* env, jclass clazz) 
{
	return presTime14;
}
double Java_com_example_android_wifidirect_DeviceDetailFragment_getTime15(JNIEnv* env, jclass clazz) 
{
	return presTime15;
}
double Java_com_example_android_wifidirect_DeviceDetailFragment_recStartTime(JNIEnv* env, jclass clazz) 
{
	return recStart;
}
double Java_com_example_android_wifidirect_DeviceDetailFragment_recStopTime(JNIEnv* env, jclass clazz) 
{
	return recStop;
}
double Java_com_example_android_wifidirect_DeviceDetailFragment_playStartTime(JNIEnv* env, jclass clazz) 
{
	return playStart;
}
double Java_com_example_android_wifidirect_DeviceDetailFragment_playStopTime(JNIEnv* env, jclass clazz) 
{
	return playStop;
}
jshortArray Java_com_example_android_wifidirect_DeviceDetailFragment_getBuffer(JNIEnv* env, jclass clazz) 
{
	jshortArray result;
	result = (*env)->NewShortArray(env,RECORDER_FRAMES);
	if (result == NULL) {
     return NULL; /* out of memory error thrown */
	}
	int i;
	jshort fill[RECORDER_FRAMES];
	for (i = 0; i < RECORDER_FRAMES; i++) {
     fill[i] = recorderBuffer[i]; // put whatever logic you want to populate the values here.
	}
	(*env)->SetShortArrayRegion(env, result, 0, RECORDER_FRAMES, fill);
	return result;
}


// create the engine and output mix objects
void Java_com_example_android_wifidirect_DeviceDetailFragment_createEngine(JNIEnv* env, jclass clazz)
{
    SLresult result;

    // create engine
    result = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // realize the engine
    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // get the engine interface, which is needed in order to create other objects
    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // create output mix, with environmental reverb specified as a non-required interface
    //const SLInterfaceID ids[1] = {SL_IID_ENVIRONMENTALREVERB};
    //const SLboolean req[1] = {SL_BOOLEAN_FALSE};
    result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 0, NULL, NULL);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // realize the output mix
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // get the environmental reverb interface
    // this could fail if the environmental reverb effect is not available,
    // either because the feature is not present, excessive CPU load, or
    // the required MODIFY_AUDIO_SETTINGS permission was not requested and granted
    result = (*outputMixObject)->GetInterface(outputMixObject, SL_IID_ENVIRONMENTALREVERB,
            &outputMixEnvironmentalReverb);
    if (SL_RESULT_SUCCESS == result) {
        result = (*outputMixEnvironmentalReverb)->SetEnvironmentalReverbProperties(
                outputMixEnvironmentalReverb, &reverbSettings);
        (void)result;
    }
    // ignore unsuccessful result codes for environmental reverb, as it is optional for this example

}


// create buffer queue audio player
void Java_com_example_android_wifidirect_DeviceDetailFragment_createBufferQueueAudioPlayer(JNIEnv* env,
        jclass clazz)
{
    SLresult result;

    // configure audio source
    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 1};
    SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_44_1,
        SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_SPEAKER_FRONT_CENTER, SL_BYTEORDER_LITTLEENDIAN};
    SLDataSource audioSrc = {&loc_bufq, &format_pcm};

    // configure audio sink
   SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
   SLDataSink audioSnk = {&loc_outmix, NULL};
   //SLDataLocator_IODevice loc_dev1 = {SL_DATALOCATOR_IODEVICE, SL_IODEVICE_AUDIOOUTPUT,
           // SL_DEFAULTDEVICEID_AUDIOOUTPUT, NULL};
   //SLDataSource audioSnk = {&loc_dev1, NULL};

    // create audio player
    const SLInterfaceID ids[3] = {SL_IID_BUFFERQUEUE, SL_IID_EFFECTSEND,
            /*SL_IID_MUTESOLO,*/ SL_IID_VOLUME};
    const SLboolean req[3] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE,
            /*SL_BOOLEAN_TRUE,*/ SL_BOOLEAN_TRUE};
    result = (*engineEngine)->CreateAudioPlayer(engineEngine, &bqPlayerObject, &audioSrc, &audioSnk,
            3, ids, req);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // realize the player
    result = (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // get the play interface
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // get the buffer queue interface
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE,
            &bqPlayerBufferQueue);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // register callback on the buffer queue
    result = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, bqPlayerCallback, NULL);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // get the effect send interface
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_EFFECTSEND,
            &bqPlayerEffectSend);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

#if 0   // mute/solo is not supported for sources that are known to be mono, as this is
    // get the mute/solo interface
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_MUTESOLO, &bqPlayerMuteSolo);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
#endif

    // get the volume interface
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &bqPlayerVolume);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
}




// select the desired clip and play count, and enqueue the first buffer if idle
jboolean Java_com_example_android_wifidirect_DeviceDetailFragment_selectClip(JNIEnv* env, jclass clazz, jint which,
        jint count)
{
    switch (which) {
    case 0:     // CLIP_NONE
        nextBuffer = (short *) NULL;
        nextSize = 0;
        break;
    case 1:     // CLIP_HELLO
        nextBuffer = sawtoothBuffer1;
        nextSize = sizeof(sawtoothBuffer1);
        nextBuffer1 = sawtoothBuffer;
        nextSize1 = sizeof(sawtoothBuffer);
        break;
    case 2:     // CLIP_ANDROID
        //nextBuffer = (short *) android;
        //nextSize = sizeof(android);
        break;
    case 3:     // CLIP_SAWTOOTH
        nextBuffer = sawtoothBuffer;
        nextSize = sizeof(sawtoothBuffer);
        nextBuffer1 = sawtoothBuffer1;
        nextSize1 = sizeof(sawtoothBuffer1);
        break;
    case 4:     // CLIP_PLAYBACK
        // we recorded at 16 kHz, but are playing buffers at 8 Khz, so do a primitive down-sample
		nextBuffer = recorderBuffer;
		nextSize = RECORDER_FRAMES;
        break;
    default:
        nextBuffer = NULL;
        nextSize = 0;
        break;
    }
    nextCount = count;
    if (nextSize > 0) {
        // here we only enqueue one buffer because it is a long clip,
        // but for streaming playback we would typically enqueue at least 2 buffers to start
        SLresult result;
		playStart = now_ms();
		presTime = now_ms();
        result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextBuffer1, nextSize1);
    	// set the player's state to playing
		   				
   		result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
    	assert(SL_RESULT_SUCCESS == result);
    	(void)result;
        if (SL_RESULT_SUCCESS != result) {
            return JNI_FALSE;
        }
        
    }

    return JNI_TRUE;
}


jboolean Java_com_example_android_wifidirect_DeviceDetailFragment_createAudioRecorder(JNIEnv* env, jclass clazz)
{
    SLresult result;

    // configure audio source
    SLDataLocator_IODevice loc_dev = {SL_DATALOCATOR_IODEVICE, SL_IODEVICE_AUDIOINPUT,
            SL_DEFAULTDEVICEID_AUDIOINPUT, NULL};
    SLDataSource audioSrc = {&loc_dev, NULL};

    // configure audio sink
    SLDataLocator_AndroidSimpleBufferQueue loc_bq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
    SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_44_1,
        SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_SPEAKER_FRONT_CENTER, SL_BYTEORDER_LITTLEENDIAN};
    SLDataSink audioSnk = {&loc_bq, &format_pcm};

    // create audio recorder
    // (requires the RECORD_AUDIO permission)
    const SLInterfaceID id[1] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE};
    const SLboolean req[1] = {SL_BOOLEAN_TRUE};
    result = (*engineEngine)->CreateAudioRecorder(engineEngine, &recorderObject, &audioSrc,
            &audioSnk, 1, id, req);
    if (SL_RESULT_SUCCESS != result) {
        return JNI_FALSE;
    }

    // realize the audio recorder
    result = (*recorderObject)->Realize(recorderObject, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result) {
        return JNI_FALSE;
    }

    // get the record interface
    result = (*recorderObject)->GetInterface(recorderObject, SL_IID_RECORD, &recorderRecord);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // get the buffer queue interface
    result = (*recorderObject)->GetInterface(recorderObject, SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
            &recorderBufferQueue);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // register callback on the buffer queue
    result = (*recorderBufferQueue)->RegisterCallback(recorderBufferQueue, bqRecorderCallback,
            NULL);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    return JNI_TRUE;
}
// set the recording state for the audio recorder
void Java_com_example_android_wifidirect_DeviceDetailFragment_startRecording(JNIEnv* env, jclass clazz)
{
    SLresult result;

    // in case already recording, stop recording and clear buffer queue
    result = (*recorderRecord)->SetRecordState(recorderRecord, SL_RECORDSTATE_STOPPED);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
    result = (*recorderBufferQueue)->Clear(recorderBufferQueue);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // the buffer is not valid for playback yet
    recorderSize = 0;

    // enqueue an empty buffer to be filled by the recorder
    // (for streaming recording, we would enqueue at least 2 empty buffers to start things off)
    result = (*recorderBufferQueue)->Enqueue(recorderBufferQueue, dummyBuffer,
            DUMMY_FRAMES * sizeof(short));
	recStart = now_ms();
    // the most likely other result is SL_RESULT_BUFFER_INSUFFICIENT,
    // which for this code example would indicate a programming error
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // start recording
	countRecord = 10;
    result = (*recorderRecord)->SetRecordState(recorderRecord, SL_RECORDSTATE_RECORDING);
    recStart = now_ms();
	assert(SL_RESULT_SUCCESS == result);
    (void)result;
}
// shut down the native audio system
void Java_com_example_android_wifidirect_DeviceDetailFragment_shutdown(JNIEnv* env, jclass clazz)
{

    // destroy buffer queue audio player object, and invalidate all associated interfaces
    if (bqPlayerObject != NULL) {
        (*bqPlayerObject)->Destroy(bqPlayerObject);
        bqPlayerObject = NULL;
        bqPlayerPlay = NULL;
        bqPlayerBufferQueue = NULL;
        bqPlayerEffectSend = NULL;
        bqPlayerMuteSolo = NULL;
        bqPlayerVolume = NULL;
    }

    // destroy output mix object, and invalidate all associated interfaces
    if (outputMixObject != NULL) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = NULL;
        outputMixEnvironmentalReverb = NULL;
    }

    // destroy engine object, and invalidate all associated interfaces
    if (engineObject != NULL) {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
        engineEngine = NULL;
    }

}
