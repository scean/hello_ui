package com.android.providers.downloads.ui.pay.util;

import java.util.ArrayList;
import java.util.concurrent.Callable;


import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Allows running tasks either in background thread or in GUI thread.
 */
public class BackgroundThread extends Thread {
    /* private */ final static String TAG =XLUtil.getTagString(BackgroundThread.class);
    /* private */ final static Object LOCK = new Object();

    /* private */ static BackgroundThread instance;
    // singleton
    public static BackgroundThread instance()
    {
        if ( instance==null ) {
            synchronized( LOCK ) {
                if ( instance==null ) {
                    instance = new BackgroundThread();
                    instance.start();
                }
            }
        }
        return instance;
    }

    public static Handler getBackgroundHandler() {
        if (instance == null) {
            return null;
        }
        return instance.handler;
    }

    public static Handler getGUIHandler() {
        if (instance().guiHandler == null) {
            return null;
        }
        return instance().guiHandler;
    }

    public final static boolean CHECK_THREAD_CONTEXT = true;

    /**
     * Throws exception if not in background thread.
     */
    public final static void ensureBackground() {
        if ( CHECK_THREAD_CONTEXT && !isBackgroundThread() ) {
            XLUtil.logError(TAG,"not in background thread");
            throw new RuntimeException("ensureInBackgroundThread() is failed");
        }
    }

    /**
     * Throws exception if not in GUI thread.
     */
    public final static void ensureGUI()
    {
        if ( CHECK_THREAD_CONTEXT && isBackgroundThread() ) {
            XLUtil.logError(TAG,"not in GUI thread");
            throw new RuntimeException("ensureGUI() is failed");
        }
    }

    /* private */ Handler handler;
    /* private */ ArrayList<Runnable> posted = new ArrayList<Runnable>();
    /* private */ Handler guiHandler;
    /* private */ ArrayList<Runnable> postedGUI = new ArrayList<Runnable>();
    /* private */ boolean mNoneGUIFlag =false;

    public void setNoneGUIHandle(boolean flag) {
        mNoneGUIFlag =flag;
    }
    /**
     * Set view to post GUI tasks to.
     * @param guiTarget is view to post GUI tasks to.
     */
    public void setGUIHandler(Handler guiHandler) {
        this.guiHandler = guiHandler;
        if (guiHandler != null) {
            // forward already posted events
            synchronized(postedGUI) {
                XLUtil.logDebug(TAG,"Engine.setGUI: " + postedGUI.size() + " posted tasks to copy");
                for ( Runnable task : postedGUI )
                    guiHandler.post( task );
            }
        }
    }

    /**
     * Create background thread executor.
     */
    /* private */ BackgroundThread() {
        super();
        setName("BackgroundThread" + Integer.toHexString(hashCode()));
        Log.i("cr3", "Created new background thread instance");
    }

    @Override
    public void run() {
        Log.i("cr3", "Entering background thread");
        Looper.prepare();
        handler = new Handler() {
            public void handleMessage( Message message )
            {
                Log.d("cr3", "message: " + message);
            }
        };
        Log.i("cr3", "Background thread handler is created");
        synchronized(posted) {
            for ( Runnable task : posted ) {
                Log.i("cr3", "Copying posted bg task to handler : " + task);
                handler.post(task);
            }
            posted.clear();
        }
        Looper.loop();
        handler = null;
        instance = null;
        Log.i("cr3", "Exiting background thread");
    }

    //private final static boolean USE_LOCK = false;
    /* private */ Runnable guard( final Runnable r )
    {
        return r;
        //        if ( !USE_LOCK )
        //            return r;
        //        return new Runnable() {
        //            public void run() {
        //                synchronized (LOCK) {
        //                    r.run();
        //                }
        //            }
        //        };
    }

    /**
     * Post runnable to be executed in background thread.
     * @param task is runnable to execute in background thread.
     */
    public void postBackground( Runnable task,final long delay )
    {

        if ( mStopped ) {
            XLUtil.logDebug(TAG,"Posting task " + task + " to GUI queue since background thread is stopped");
            postGUI( task );
            return;
        }
        task = guard(task);
        if ( handler==null ) {
            synchronized(posted) {
                XLUtil.logDebug(TAG,"Adding task " + task + " to posted list since handler is not yet created");
                posted.add(task);
            }
        } else {
            handler.postDelayed(task, delay);
        }
    }

    /**
     * Post runnable to be executed in background thread.
     * @param task is runnable to execute in background thread.
     */
    public void postBackground( Runnable task )
    {

        if ( mStopped ) {
            XLUtil.logDebug(TAG,"Posting task " + task + " to GUI queue since background thread is stopped");
            postGUI( task );
            return;
        }
        task = guard(task);
        if ( handler==null ) {
            synchronized(posted) {
                XLUtil.logDebug(TAG,"Adding task " + task + " to posted list since handler is not yet created");
                posted.add(task);
            }
        } else {
            handler.post(task);
        }
    }

    /**
     * Post runnable to be executed in GUI thread
     * @param task is runnable to execute in GUI thread
     */
    public void postGUI( Runnable task )
    {
        if(mNoneGUIFlag == true){
            postBackground(task);
        }
        else{
            postGUI(task, 0);
        }
    }

    static int delayedTaskId = 0;

    /**
     * Post runnable to be executed in GUI thread
     * @param task is runnable to execute in GUI thread
     * @param delay is delay before running task, in millis
     */
    public void postGUI(final Runnable task, final long delay)
    {
        if ( guiHandler==null ) {
            synchronized( postedGUI ) {
                postedGUI.add(task);
            }
        } else {
            if ( delay>0 ) {
                final int id = ++delayedTaskId;
                //L.v("posting delayed (" + delay + ") task " + id + " " + task);
                guiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        task.run();
                        //L.v("finished delayed (" + delay + ") task " + id + " " + task);
                    }
                }, delay);
            } else
                guiHandler.post(task);
        }
    }

    /**
     * Run task instantly if called from the same thread, or post it through message queue otherwise.
     * @param task is task to execute
     */
    public void executeBackground( Runnable task )
    {

        task = guard(task);
        if (isBackgroundThread() || mStopped) {
            task.run(); // run in this thread
        } else {
            postBackground(task); // post
        }
    }

    // assume there are only two threads: main GUI and background
    public static boolean isGUIThread() {
        return !isBackgroundThread();
    }

    public static boolean isBackgroundThread() {
        return (Thread.currentThread() == instance);
    }

    public void executeGUI( Runnable task ) {
        //Handler guiHandler = guiTarget.getHandler();
        //if ( guiHandler!=null && guiHandler.getLooper().getThread()==Thread.currentThread() )
        if (isGUIThread()) {
            task.run(); // run in this thread
        } else {
            postGUI(task);
        }
    }

    public <T> Callable<T> guard( final Callable<T> task ) {
        return new Callable<T>() {
            public T call() throws Exception {
                return task.call();
            }
        };
    }

    /* private */ final static boolean DBG = false; 

    /* private */ boolean mStopped = false;

    public void quit() {
        postBackground(new Runnable() {
            @Override
            public void run() {
                if (handler != null) {
                    XLUtil.logDebug(TAG,"Calling quit() on background thread looper.");
                    handler.getLooper().quit();
                }
            }
        });
    }
}
