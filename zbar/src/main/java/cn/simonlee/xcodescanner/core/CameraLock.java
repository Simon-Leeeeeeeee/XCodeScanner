package cn.simonlee.xcodescanner.core;

import java.util.concurrent.Semaphore;

/**
 * 相机锁信号量，单例模式
 * 快速连续旋转屏幕会导致多个CameraScanner实例同时操作相机，可能会导致严重错误。因此引入单例信号量对相机的操作进行控制
 *
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-08-06
 */
class CameraLock extends Semaphore {

    private static CameraLock instance;

    private CameraLock(int permits) {
        super(permits);
    }

    private CameraLock(int permits, boolean fair) {
        super(permits, fair);
    }

    public static CameraLock getInstance() {
        if (instance == null) {
            synchronized (CameraLock.class) {
                if (instance == null) {
                    instance = new CameraLock(1);
                }
            }
        }
        return instance;
    }

}
